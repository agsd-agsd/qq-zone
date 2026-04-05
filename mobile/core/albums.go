package core

import (
	"errors"
	"log"
	"sort"
	"strconv"
	"strings"

	"github.com/qinjintian/qq-zone/utils/qzone"
	"github.com/tidwall/gjson"
)

func buildCredentialsFromCookieHeader(cookieHeader string) (*credentials, error) {
	cookies := parseCookieHeader(cookieHeader)
	if len(cookies) == 0 {
		return nil, errors.New("no QQ login cookies were found")
	}

	qq := firstNonEmpty(
		trimQQ(cookies["p_uin"]),
		trimQQ(cookies["uin"]),
		trimQQ(cookies["ptui_loginuin"]),
	)
	if qq == "" {
		log.Printf("qq-zone web login is missing a QQ identifier; available cookie keys=%s", strings.Join(cookieKeys(cookies), ","))
		return nil, errors.New("the QQ number could not be determined from the web login cookies")
	}

	skey := firstNonEmpty(cookies["p_skey"], cookies["skey"])
	if strings.TrimSpace(skey) == "" {
		log.Printf("qq-zone web login is missing p_skey/skey; available cookie keys=%s", strings.Join(cookieKeys(cookies), ","))
		return nil, errors.New("the web login cookies do not include p_skey or skey")
	}

	return &credentials{
		Nickname: qq,
		GTK:      gtkFromSKey(skey),
		Cookie:   encodeCookieHeader(cookies),
		QQ:       qq,
	}, nil
}

func parseCookieHeader(raw string) map[string]string {
	normalized := strings.NewReplacer("\r", ";", "\n", ";").Replace(raw)
	parts := strings.Split(normalized, ";")
	cookies := make(map[string]string, len(parts))
	for _, part := range parts {
		piece := strings.TrimSpace(part)
		if piece == "" {
			continue
		}
		kv := strings.SplitN(piece, "=", 2)
		if len(kv) != 2 {
			continue
		}
		name := strings.TrimSpace(kv[0])
		value := strings.TrimSpace(kv[1])
		if name == "" || value == "" {
			continue
		}
		cookies[name] = value
	}
	return cookies
}

func encodeCookieHeader(cookies map[string]string) string {
	keys := make([]string, 0, len(cookies))
	for key := range cookies {
		keys = append(keys, key)
	}
	sort.Strings(keys)

	pairs := make([]string, 0, len(keys))
	for _, key := range keys {
		value := strings.TrimSpace(cookies[key])
		if value == "" {
			continue
		}
		pairs = append(pairs, key+"="+value)
	}

	return strings.Join(pairs, "; ")
}

func trimQQ(raw string) string {
	trimmed := strings.TrimSpace(raw)
	trimmed = strings.Trim(trimmed, "\"'")
	trimmed = strings.TrimLeft(trimmed, "oO")
	return trimmed
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if strings.TrimSpace(value) != "" {
			return strings.TrimSpace(value)
		}
	}
	return ""
}

func gtkFromSKey(skey string) string {
	h := 5381
	for i := 0; i < len(skey); i++ {
		h += (h << 5) + int(skey[i])
	}
	return strconv.Itoa(h & 2147483647)
}

func cookieKeys(cookies map[string]string) []string {
	keys := make([]string, 0, len(cookies))
	for key := range cookies {
		if strings.TrimSpace(key) == "" {
			continue
		}
		keys = append(keys, key)
	}
	sort.Strings(keys)
	return keys
}

func (c *Client) fetchAccessibleAlbums(cred credentials) ([]gjson.Result, int, error) {
	albums, err := qzone.GetAlbumList(cred.QQ, cred.QQ, cred.GTK, cred.Cookie)
	if err != nil {
		return nil, 0, err
	}

	return filterAccessibleAlbums(albums)
}

func filterAccessibleAlbums(albums []gjson.Result) ([]gjson.Result, int, error) {
	accessible := make([]gjson.Result, 0, len(albums))
	hiddenCount := 0
	for _, album := range albums {
		if album.Get("allowAccess").Int() == 0 {
			hiddenCount++
			continue
		}

		if strings.TrimSpace(album.Get("id").String()) == "" {
			continue
		}

		accessible = append(accessible, album)
	}

	return accessible, hiddenCount, nil
}

func summarizeAlbums(albums []gjson.Result) []AlbumSummary {
	summaries := make([]AlbumSummary, 0, len(albums))
	for _, album := range albums {
		summaries = append(summaries, AlbumSummary{
			ID:    album.Get("id").String(),
			Name:  album.Get("name").String(),
			Total: int(album.Get("total").Int()),
		})
	}
	return summaries
}

func selectAlbumsByID(albums []gjson.Result, selectedIDs []string) ([]gjson.Result, []string) {
	requested := make(map[string]struct{}, len(selectedIDs))
	requestOrder := make([]string, 0, len(selectedIDs))
	for _, id := range selectedIDs {
		trimmed := strings.TrimSpace(id)
		if trimmed == "" {
			continue
		}
		if _, exists := requested[trimmed]; exists {
			continue
		}
		requested[trimmed] = struct{}{}
		requestOrder = append(requestOrder, trimmed)
	}

	selected := make([]gjson.Result, 0, len(requested))
	found := make(map[string]struct{}, len(requested))
	for _, album := range albums {
		id := strings.TrimSpace(album.Get("id").String())
		if _, exists := requested[id]; !exists {
			continue
		}
		selected = append(selected, album)
		found[id] = struct{}{}
	}

	missing := make([]string, 0)
	for _, id := range requestOrder {
		if _, exists := found[id]; !exists {
			missing = append(missing, id)
		}
	}

	return selected, missing
}
