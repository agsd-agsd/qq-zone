package bridge

import "github.com/qinjintian/qq-zone/mobile/core"

type Client struct {
	inner *core.Client
}

func NewClient(baseDir string) *Client {
	return &Client{inner: core.NewClient(baseDir)}
}

func (c *Client) StartLogin() ([]byte, error) {
	return c.inner.StartLogin()
}

func (c *Client) PollLogin() string {
	return c.inner.PollLogin()
}

func (c *Client) ImportWebLogin(cookieHeader string) string {
	return c.inner.ImportWebLogin(cookieHeader)
}

func (c *Client) ListSelfAlbums() string {
	return c.inner.ListSelfAlbums()
}

func (c *Client) StartSelfDownload() (string, error) {
	return c.inner.StartSelfDownload()
}

func (c *Client) StartSelectedDownload(selectedAlbumIDsJSON string) (string, error) {
	return c.inner.StartSelectedDownload(selectedAlbumIDsJSON)
}

func (c *Client) GetJobStatus(jobID string) string {
	return c.inner.GetJobStatus(jobID)
}

func (c *Client) CancelJob(jobID string) error {
	return c.inner.CancelJob(jobID)
}
