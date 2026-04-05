package core

import "encoding/json"

type LoginState struct {
	Status   string `json:"status"`
	Message  string `json:"message"`
	QQ       string `json:"qq"`
	Nickname string `json:"nickname"`
}

type AlbumSummary struct {
	ID    string `json:"id"`
	Name  string `json:"name"`
	Total int    `json:"total"`
}

type AlbumListState struct {
	Status      string         `json:"status"`
	Message     string         `json:"message"`
	QQ          string         `json:"qq"`
	Nickname    string         `json:"nickname"`
	HiddenCount int            `json:"hiddenCount"`
	Albums      []AlbumSummary `json:"albums"`
}

type JobState struct {
	Status       string `json:"status"`
	Phase        string `json:"phase"`
	Total        int    `json:"total"`
	Success      int    `json:"success"`
	Failed       int    `json:"failed"`
	Images       int    `json:"images"`
	Videos       int    `json:"videos"`
	CurrentAlbum string `json:"currentAlbum"`
	CurrentFile  string `json:"currentFile"`
	SaveDir      string `json:"saveDir"`
	Message      string `json:"message"`
}

func encodeJSON(v interface{}) string {
	body, err := json.Marshal(v)
	if err != nil {
		fallback, _ := json.Marshal(map[string]string{
			"status":  "error",
			"message": err.Error(),
		})
		return string(fallback)
	}

	return string(body)
}
