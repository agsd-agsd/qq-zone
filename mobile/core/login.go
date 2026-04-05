package core

import (
	"strings"

	"github.com/qinjintian/qq-zone/utils/qzone"
)

func (c *Client) StartLogin() ([]byte, error) {
	session, qrCode, err := qzone.NewQRLoginSession()
	if err != nil {
		c.setLoginState(LoginState{
			Status:  "error",
			Message: err.Error(),
		})
		return nil, err
	}

	c.mu.Lock()
	c.loginSession = session
	c.creds = nil
	c.loginState = LoginState{
		Status:  "waiting",
		Message: "QR code is ready. Scan it with another device or let mobile QQ recognize a screenshot.",
	}
	c.mu.Unlock()

	return qrCode, nil
}

func (c *Client) PollLogin() string {
	return encodeJSON(c.pollLoginState())
}

func (c *Client) pollLoginState() LoginState {
	c.mu.RLock()
	session := c.loginSession
	current := c.loginState
	c.mu.RUnlock()

	if session == nil {
		return current
	}

	result, err := session.Poll()
	if err != nil {
		state := LoginState{
			Status:  "error",
			Message: err.Error(),
		}
		c.setLoginState(state)
		return state
	}

	state := LoginState{
		Status:   result.Status,
		Message:  result.Message,
		Nickname: result.Nickname,
	}

	switch result.Status {
	case "success":
		qq := strings.TrimSpace(result.Uin)
		if qq == "" {
			state = LoginState{
				Status:  "error",
				Message: "Login succeeded, but the QQ number could not be determined.",
			}
			c.setLoginState(state)
			return state
		}

		c.mu.Lock()
		c.creds = &credentials{
			Nickname: result.Nickname,
			GTK:      result.GTK,
			Cookie:   result.Cookie,
			QQ:       qq,
		}
		c.loginState = state
		c.mu.Unlock()
		return state
	case "expired":
		c.mu.Lock()
		c.loginSession = nil
		c.loginState = state
		c.mu.Unlock()
		return state
	default:
		c.setLoginState(state)
		return state
	}
}

func (c *Client) setLoginState(state LoginState) {
	c.mu.Lock()
	c.loginState = state
	c.mu.Unlock()
}
