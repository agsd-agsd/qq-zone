package core

import (
	"context"
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"reflect"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/qinjintian/qq-zone/utils/filer"
	"github.com/qinjintian/qq-zone/utils/helper"
	ihttp "github.com/qinjintian/qq-zone/utils/net/http"
	"github.com/qinjintian/qq-zone/utils/qzone"
	"github.com/tidwall/gjson"
)

const mobileUserAgent = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/78.0.3904.108 Safari/537.36"

type downloadJob struct {
	id     string
	ctx    context.Context
	cancel context.CancelFunc

	mu    sync.RWMutex
	state JobState
}

type downloadTarget struct {
	Source   string
	Filename string
	Kind     string
	Headers  map[string]string
}

func newDownloadJob(id string, saveDir string, ctx context.Context, cancel context.CancelFunc) *downloadJob {
	return &downloadJob{
		id:     id,
		ctx:    ctx,
		cancel: cancel,
		state: JobState{
			Status:  "running",
			Phase:   "ready",
			SaveDir: saveDir,
			Message: "The download job has been created.",
		},
	}
}

func (j *downloadJob) JSON() string {
	j.mu.RLock()
	defer j.mu.RUnlock()

	return encodeJSON(j.state)
}

func (j *downloadJob) Update(fn func(*JobState)) {
	j.mu.Lock()
	defer j.mu.Unlock()

	fn(&j.state)
}

func (j *downloadJob) IsTerminal() bool {
	j.mu.RLock()
	defer j.mu.RUnlock()

	switch j.state.Status {
	case "success", "error", "cancelled":
		return true
	default:
		return false
	}
}

func (j *downloadJob) Cancel() {
	j.Update(func(state *JobState) {
		if state.Status == "success" || state.Status == "error" || state.Status == "cancelled" {
			return
		}

		state.Message = "Cancelling the download job..."
	})

	j.cancel()
}

func (j *downloadJob) IsCancelled() bool {
	select {
	case <-j.ctx.Done():
		return true
	default:
		return false
	}
}

func (j *downloadJob) SetProgress(phase, album, file, message string) {
	j.Update(func(state *JobState) {
		state.Status = "running"
		state.Phase = phase
		state.CurrentAlbum = album
		state.CurrentFile = file
		state.Message = message
	})
}

func (j *downloadJob) AddTotal(delta int) {
	if delta <= 0 {
		return
	}

	j.Update(func(state *JobState) {
		state.Total += delta
	})
}

func (j *downloadJob) RecordSuccess(album, file, kind, message string) {
	j.Update(func(state *JobState) {
		state.CurrentAlbum = album
		state.CurrentFile = file
		state.Success++
		if kind == "video" {
			state.Videos++
		} else {
			state.Images++
		}
		state.Message = message
	})
}

func (j *downloadJob) RecordFailure(album, file string, err error) {
	j.Update(func(state *JobState) {
		state.CurrentAlbum = album
		state.CurrentFile = file
		state.Failed++
		state.Message = err.Error()
	})
}

func (j *downloadJob) Fail(err error) {
	j.Update(func(state *JobState) {
		state.Status = "error"
		state.Phase = "error"
		state.Message = err.Error()
	})
}

func (j *downloadJob) Finish() {
	j.Update(func(state *JobState) {
		state.Status = "success"
		state.Phase = "completed"
		state.Message = "Download completed."
	})
}

func (j *downloadJob) MarkCancelled() {
	j.Update(func(state *JobState) {
		state.Status = "cancelled"
		state.Phase = "cancelled"
		state.Message = "Download cancelled."
	})
}

func (c *Client) runSelectedDownload(job *downloadJob, cred credentials, albums []gjson.Result) {
	saveRoot := filepath.Join(c.baseDir, cred.QQ, "album")
	if err := os.RemoveAll(saveRoot); err != nil {
		job.Fail(err)
		return
	}
	if err := os.MkdirAll(saveRoot, os.ModePerm); err != nil {
		job.Fail(err)
		return
	}

	job.SetProgress("listing_albums", "", "", "Preparing the selected albums...")

	cookie := cred.Cookie
	heartbeatHeader := map[string]string{
		"cookie":     cookie,
		"user-agent": mobileUserAgent,
	}
	heartbeatDone := make(chan struct{})
	go keepLoginAlive(job.ctx, qzone.GetAlbumListUrl(cred.QQ, cred.QQ, cred.GTK), heartbeatHeader, heartbeatDone)
	defer close(heartbeatDone)

	if len(albums) == 0 {
		job.Fail(fmt.Errorf("no selected albums are available for download"))
		return
	}

	for _, album := range albums {
		if job.IsCancelled() {
			job.MarkCancelled()
			return
		}

		if err := c.downloadAlbum(job, cred, &cookie, album, saveRoot); err != nil {
			if err == context.Canceled {
				job.MarkCancelled()
				return
			}

			job.Fail(err)
			return
		}
	}

	if job.IsCancelled() {
		job.MarkCancelled()
		return
	}

	job.Finish()
}

func (c *Client) downloadAlbum(job *downloadJob, cred credentials, cookie *string, album gjson.Result, saveRoot string) error {
	albumName := album.Get("name").String()
	displayAlbumName, albumPath, err := prepareAlbumDir(saveRoot, albumName)
	if err != nil {
		return err
	}

	job.SetProgress("listing_photos", displayAlbumName, "", "Loading files from the selected album...")

	photos, err := qzone.GetPhotoList(cred.QQ, cred.QQ, cookie, cred.GTK, album)
	if err != nil {
		return err
	}

	job.AddTotal(len(photos))
	existingFiles := indexExistingFiles(albumPath)

	var (
		wg  sync.WaitGroup
		sem = make(chan struct{}, c.parallel)
	)

	for _, photo := range photos {
		if job.IsCancelled() {
			break
		}

		wg.Add(1)
		sem <- struct{}{}
		go func(photo gjson.Result) {
			defer func() {
				<-sem
				wg.Done()
			}()

			if job.IsCancelled() {
				return
			}

			c.downloadMedia(job, cred.QQ, cred.GTK, *cookie, photo, album, displayAlbumName, albumPath, existingFiles)
		}(photo)
	}

	wg.Wait()
	if job.IsCancelled() {
		return context.Canceled
	}

	return nil
}

func (c *Client) downloadMedia(job *downloadJob, qq, gtk, cookie string, photo, album gjson.Result, displayAlbumName, albumPath string, existingFiles map[string]string) {
	job.SetProgress("downloading", displayAlbumName, photo.Get("name").String(), "Downloading file...")

	target, err := resolveDownloadTarget(qq, gtk, cookie, photo, album)
	if err != nil {
		job.RecordFailure(displayAlbumName, photo.Get("name").String(), err)
		return
	}

	headers := mergeHeaders(map[string]string{
		"cookie":     cookie,
		"user-agent": mobileUserAgent,
	}, target.Headers)

	targetKey := trimExtension(target.Filename)
	if existingPath, ok := existingFiles[targetKey]; ok {
		shouldSkip, err := shouldSkipExisting(existingPath, target.Source, headers)
		if err == nil && shouldSkip {
			job.RecordSuccess(displayAlbumName, filepath.Base(existingPath), target.Kind, "Skipped an unchanged file.")
			return
		}
		if err == nil && !shouldSkip {
			_ = os.Remove(existingPath)
		}
		if err != nil {
			job.RecordSuccess(displayAlbumName, filepath.Base(existingPath), target.Kind, "Skipped an existing file because the remote size could not be verified.")
			return
		}
	}

	resp, err := ihttp.Download(target.Source, filepath.Join(albumPath, target.Filename), headers, 5, 600, false)
	if err != nil {
		job.RecordFailure(displayAlbumName, target.Filename, err)
		return
	}

	savedName := target.Filename
	if filename, ok := resp["filename"].(string); ok && filename != "" {
		savedName = filename
	}

	job.RecordSuccess(displayAlbumName, savedName, target.Kind, "Download completed.")
}

func prepareAlbumDir(saveRoot string, albumName string) (string, string, error) {
	name := strings.TrimSpace(albumName)
	name = strings.TrimRight(name, ".")
	if name == "" {
		name = helper.Md5(albumName)[8:24]
	}

	albumPath := filepath.Join(saveRoot, name)
	if err := os.MkdirAll(albumPath, os.ModePerm); err != nil {
		name = helper.Md5(albumName)[8:24]
		albumPath = filepath.Join(saveRoot, name)
		if retryErr := os.MkdirAll(albumPath, os.ModePerm); retryErr != nil {
			return "", "", retryErr
		}
	}

	return name, albumPath, nil
}

func indexExistingFiles(dir string) map[string]string {
	files, err := filer.GetAllFiles(dir)
	if err != nil {
		return map[string]string{}
	}

	result := make(map[string]string, len(files))
	for _, path := range files {
		base := filepath.Base(path)
		result[trimExtension(base)] = path
	}

	return result
}

func trimExtension(filename string) string {
	ext := filepath.Ext(filename)
	if ext == "" {
		return filename
	}

	return strings.TrimSuffix(filename, ext)
}

func shouldSkipExisting(localPath, remoteURL string, headers map[string]string) (bool, error) {
	info, err := os.Stat(localPath)
	if err != nil {
		return false, err
	}

	head, err := ihttp.Head(remoteURL, headers)
	if err != nil {
		return true, err
	}

	remoteSize, err := strconv.ParseInt(head.Get("content-length"), 10, 64)
	if err != nil || remoteSize <= 0 {
		return true, err
	}

	return info.Size() >= remoteSize, nil
}

func resolveDownloadTarget(qq, gtk, cookie string, photo, album gjson.Result) (*downloadTarget, error) {
	rawShootTime := photo.Get("rawshoottime").String()
	if rawShootTime == "" {
		if rawValue := photo.Get("rawshoottime").Value(); rawValue != nil && reflect.TypeOf(rawValue).Kind() == reflect.String {
			rawShootTime = rawValue.(string)
		}
	}
	if rawShootTime == "" {
		rawShootTime = photo.Get("uploadtime").String()
	}
	if rawShootTime == "" {
		rawShootTime = time.Now().Format("2006-01-02 15:04:05")
	}

	shootTime, err := time.ParseInLocation("2006-01-02 15:04:05", rawShootTime, time.Local)
	if err != nil {
		shootTime = time.Now()
	}
	shootDate := shootTime.Format("20060102150405")
	sloc := photo.Get("sloc").String()

	if photo.Get("is_video").Bool() {
		return resolveVideoTarget(qq, gtk, cookie, photo, album, shootDate, sloc)
	}

	source := photo.Get("raw").String()
	if source == "" {
		source = photo.Get("origin_url").String()
	}
	if source == "" {
		source = photo.Get("url").String()
	}
	if source == "" {
		return nil, fmt.Errorf("the image download URL is missing")
	}

	return &downloadTarget{
		Source:   source,
		Filename: fmt.Sprintf("IMG_%s_%s_%s", shootDate[:8], shootDate[8:], helper.Md5(sloc)[8:24]),
		Kind:     "image",
	}, nil
}

func resolveVideoTarget(qq, gtk, cookie string, photo, album gjson.Result, shootDate string, sloc string) (*downloadTarget, error) {
	apiURL := fmt.Sprintf("https://h5.qzone.qq.com/proxy/domain/photo.qzone.qq.com/fcgi-bin/cgi_floatview_photo_list_v2?g_tk=%v&callback=viewer_Callback&topicId=%v&picKey=%v&cmtOrder=1&fupdate=1&plat=qzone&source=qzone&cmtNum=0&inCharset=utf-8&outCharset=utf-8&callbackFun=viewer&uin=%v&hostUin=%v&appid=4&isFirst=1", gtk, album.Get("id").String(), sloc, qq, qq)
	_, body, err := ihttp.Get(apiURL, map[string]string{
		"cookie":     cookie,
		"user-agent": mobileUserAgent,
	})
	if err != nil {
		return nil, err
	}

	payload, err := parseJSONP(string(body))
	if err != nil {
		return nil, err
	}

	data := gjson.Parse(payload).Get("data")
	videos := data.Get("photos").Array()
	if len(videos) < 1 {
		return nil, fmt.Errorf("the video download URL is missing")
	}

	index := int(data.Get("picPosInPage").Int())
	if index < 0 || index >= len(videos) {
		return nil, fmt.Errorf("the video index is invalid")
	}

	videoInfo := videos[index].Get("video_info")
	if videoInfo.Get("status").Int() != 2 {
		return nil, fmt.Errorf("the video is not ready for download")
	}

	source := videoInfo.Get("download_url").String()
	if source == "" {
		source = videoInfo.Get("video_url").String()
	}
	if source == "" {
		return nil, fmt.Errorf("the video download URL is missing")
	}

	extraHeaders := map[string]string{
		"Accept":          "*/*",
		"Accept-Encoding": "identity;q=1, *;q=0",
		"Connection":      "keep-alive",
		"Range":           "bytes=0-",
		"Referer":         fmt.Sprintf("https://user.qzone.qq.com/%v/infocenter", qq),
		"Sec-Fetch-Dest":  "video",
		"Sec-Fetch-Mode":  "no-cors",
		"Sec-Fetch-Site":  "cross-site",
	}
	parsedURL, err := url.Parse(source)
	if err == nil {
		extraHeaders["Host"] = parsedURL.Host
	}

	return &downloadTarget{
		Source:   source,
		Filename: fmt.Sprintf("VID_%s_%s_%s.mp4", shootDate[:8], shootDate[8:], helper.Md5(sloc)[8:24]),
		Kind:     "video",
		Headers:  extraHeaders,
	}, nil
}

func parseJSONP(body string) (string, error) {
	start := strings.Index(body, "(")
	end := strings.LastIndex(body, ")")
	if start == -1 || end == -1 || end <= start {
		return "", fmt.Errorf("invalid jsonp response")
	}

	payload := body[start+1 : end]
	if !gjson.Valid(payload) {
		return "", fmt.Errorf("invalid json payload")
	}

	return payload, nil
}

func mergeHeaders(base map[string]string, extra map[string]string) map[string]string {
	if len(extra) == 0 {
		return base
	}

	result := make(map[string]string, len(base)+len(extra))
	for key, value := range base {
		result[key] = value
	}
	for key, value := range extra {
		result[key] = value
	}

	return result
}

func keepLoginAlive(ctx context.Context, apiURL string, header map[string]string, done <-chan struct{}) {
	ticker := time.NewTicker(time.Minute * 10)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-done:
			return
		case <-ticker.C:
			_, _ = ihttp.Head(apiURL, header)
		}
	}
}
