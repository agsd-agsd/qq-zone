package core

import (
	"context"
	"encoding/json"
	"errors"
	"path/filepath"
	"strings"
	"sync"

	"github.com/qinjintian/qq-zone/utils/helper"
	"github.com/qinjintian/qq-zone/utils/qzone"
	"github.com/tidwall/gjson"
)

const defaultParallelDownloads = 3

type credentials struct {
	Nickname string
	GTK      string
	Cookie   string
	QQ       string
}

type Client struct {
	baseDir  string
	parallel int

	mu           sync.RWMutex
	loginSession *qzone.QRLoginSession
	loginState   LoginState
	creds        *credentials
	jobs         map[string]*downloadJob
}

func NewClient(baseDir string) *Client {
	trimmed := strings.TrimSpace(baseDir)
	if trimmed == "" {
		trimmed = filepath.Join("storage", "qzone-work")
	}

	return &Client{
		baseDir:  filepath.Clean(trimmed),
		parallel: defaultParallelDownloads,
		loginState: LoginState{
			Status:  "idle",
			Message: "Please sign in before loading albums.",
		},
		jobs: make(map[string]*downloadJob),
	}
}

func (c *Client) ImportWebLogin(cookieHeader string) string {
	cred, err := buildCredentialsFromCookieHeader(cookieHeader)
	if err != nil {
		state := LoginState{
			Status:  "error",
			Message: err.Error(),
		}
		c.setLoginState(state)
		return encodeJSON(state)
	}

	if _, _, err := c.fetchAccessibleAlbums(*cred); err != nil {
		state := LoginState{
			Status:  "error",
			Message: err.Error(),
		}
		c.setLoginState(state)
		return encodeJSON(state)
	}

	state := LoginState{
		Status:   "success",
		Message:  "Signed in successfully.",
		QQ:       cred.QQ,
		Nickname: cred.Nickname,
	}

	c.mu.Lock()
	c.loginSession = nil
	c.creds = cred
	c.loginState = state
	c.mu.Unlock()

	return encodeJSON(state)
}

func (c *Client) ListSelfAlbums() string {
	c.mu.RLock()
	cred := c.creds
	c.mu.RUnlock()

	if cred == nil {
		return encodeJSON(AlbumListState{
			Status:  "error",
			Message: "Please sign in before loading albums.",
		})
	}

	accessible, hiddenCount, err := c.fetchAccessibleAlbums(*cred)
	if err != nil {
		return encodeJSON(AlbumListState{
			Status:   "error",
			Message:  err.Error(),
			QQ:       cred.QQ,
			Nickname: displayNickname(*cred),
		})
	}

	return encodeJSON(AlbumListState{
		Status:      "success",
		Message:     "Albums loaded successfully.",
		QQ:          cred.QQ,
		Nickname:    displayNickname(*cred),
		HiddenCount: hiddenCount,
		Albums:      summarizeAlbums(accessible),
	})
}

func (c *Client) StartSelfDownload() (string, error) {
	c.mu.RLock()
	cred := c.creds
	c.mu.RUnlock()

	if cred == nil {
		return "", errors.New("please sign in before starting a download")
	}

	accessible, _, err := c.fetchAccessibleAlbums(*cred)
	if err != nil {
		return "", err
	}
	if len(accessible) == 0 {
		return "", errors.New("no accessible albums are available for download")
	}

	return c.startDownloadJob(*cred, accessible)
}

func (c *Client) StartSelectedDownload(selectedAlbumIDsJSON string) (string, error) {
	c.mu.RLock()
	cred := c.creds
	c.mu.RUnlock()

	if cred == nil {
		return "", errors.New("please sign in before starting a download")
	}

	selectedIDs, err := parseSelectedAlbumIDs(selectedAlbumIDsJSON)
	if err != nil {
		return "", err
	}
	if len(selectedIDs) == 0 {
		return "", errors.New("please select at least one album")
	}

	accessible, _, err := c.fetchAccessibleAlbums(*cred)
	if err != nil {
		return "", err
	}

	selectedAlbums, missingIDs := selectAlbumsByID(accessible, selectedIDs)
	if len(missingIDs) > 0 {
		return "", errors.New("some selected albums are no longer available")
	}
	if len(selectedAlbums) == 0 {
		return "", errors.New("please select at least one accessible album")
	}

	return c.startDownloadJob(*cred, selectedAlbums)
}

func (c *Client) startDownloadJob(cred credentials, albums []gjson.Result) (string, error) {
	if len(albums) == 0 {
		return "", errors.New("no albums are available for download")
	}

	c.mu.RLock()
	activeJob := c.hasActiveJobLocked()
	c.mu.RUnlock()
	if activeJob {
		return "", errors.New("a download is already running")
	}

	saveDir := filepath.Join(c.baseDir, cred.QQ, "album")
	jobID := helper.GetRandomString(16)
	ctx, cancel := context.WithCancel(context.Background())
	job := newDownloadJob(jobID, saveDir, ctx, cancel)

	c.mu.Lock()
	c.jobs[jobID] = job
	c.mu.Unlock()

	go c.runSelectedDownload(job, cred, albums)

	return jobID, nil
}

func (c *Client) GetJobStatus(jobID string) string {
	c.mu.RLock()
	job := c.jobs[jobID]
	c.mu.RUnlock()

	if job == nil {
		return encodeJSON(JobState{
			Status:  "error",
			Phase:   "error",
			Message: "The download job does not exist.",
		})
	}

	return job.JSON()
}

func (c *Client) CancelJob(jobID string) error {
	c.mu.RLock()
	job := c.jobs[jobID]
	c.mu.RUnlock()

	if job == nil {
		return errors.New("the download job does not exist")
	}

	job.Cancel()
	return nil
}

func (c *Client) hasActiveJobLocked() bool {
	for _, job := range c.jobs {
		if !job.IsTerminal() {
			return true
		}
	}
	return false
}

func displayNickname(cred credentials) string {
	if strings.TrimSpace(cred.Nickname) != "" {
		return cred.Nickname
	}
	return cred.QQ
}

func parseSelectedAlbumIDs(raw string) ([]string, error) {
	var ids []string
	if err := json.Unmarshal([]byte(raw), &ids); err != nil {
		return nil, errors.New("selected albums must be a JSON string array")
	}

	seen := make(map[string]struct{}, len(ids))
	result := make([]string, 0, len(ids))
	for _, id := range ids {
		trimmed := strings.TrimSpace(id)
		if trimmed == "" {
			continue
		}
		if _, exists := seen[trimmed]; exists {
			continue
		}
		seen[trimmed] = struct{}{}
		result = append(result, trimmed)
	}

	return result, nil
}
