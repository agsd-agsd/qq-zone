package core

import (
	"testing"

	"github.com/tidwall/gjson"
)

func TestBuildCredentialsFromCookieHeader(t *testing.T) {
	cred, err := buildCredentialsFromCookieHeader("p_uin=o123456; p_skey=abc123; pt4_token=token")
	if err != nil {
		t.Fatalf("expected credentials, got error: %v", err)
	}

	if cred.QQ != "123456" {
		t.Fatalf("expected qq 123456, got %s", cred.QQ)
	}
	if cred.Nickname != "123456" {
		t.Fatalf("expected nickname fallback to qq, got %s", cred.Nickname)
	}
	if cred.Cookie == "" {
		t.Fatal("expected normalized cookie header")
	}
	if cred.GTK == "" {
		t.Fatal("expected gtk to be calculated")
	}
}

func TestBuildCredentialsFromCookieHeaderFallbacks(t *testing.T) {
	cred, err := buildCredentialsFromCookieHeader("uin=o654321; skey=skeyOnly")
	if err != nil {
		t.Fatalf("expected credentials, got error: %v", err)
	}

	if cred.QQ != "654321" {
		t.Fatalf("expected qq 654321, got %s", cred.QQ)
	}

	cred, err = buildCredentialsFromCookieHeader(`ptui_loginuin="o777888"; p_skey=abc123`)
	if err != nil {
		t.Fatalf("expected ptui_loginuin fallback to work, got error: %v", err)
	}

	if cred.QQ != "777888" {
		t.Fatalf("expected qq 777888, got %s", cred.QQ)
	}
}

func TestBuildCredentialsFromCookieHeaderErrors(t *testing.T) {
	_, err := buildCredentialsFromCookieHeader("p_uin=o123456")
	if err == nil {
		t.Fatal("expected missing skey to fail")
	}

	_, err = buildCredentialsFromCookieHeader("p_skey=abc123")
	if err == nil {
		t.Fatal("expected missing qq to fail")
	}
}

func TestParseSelectedAlbumIDs(t *testing.T) {
	ids, err := parseSelectedAlbumIDs(`["a1","a2","a1",""," a3 "]`)
	if err != nil {
		t.Fatalf("expected selected ids, got error: %v", err)
	}

	expected := []string{"a1", "a2", "a3"}
	if len(ids) != len(expected) {
		t.Fatalf("expected %d ids, got %d", len(expected), len(ids))
	}
	for i := range expected {
		if ids[i] != expected[i] {
			t.Fatalf("expected id %s at index %d, got %s", expected[i], i, ids[i])
		}
	}
}

func TestFilterAccessibleAlbumsAndSelection(t *testing.T) {
	albumsJSON := `[
		{"id":"a1","name":"Visible One","total":3,"allowAccess":1},
		{"id":"a2","name":"Hidden","total":9,"allowAccess":0},
		{"id":"a3","name":"Visible Two","total":5,"allowAccess":1}
	]`

	albums := gjson.Parse(albumsJSON).Array()
	accessible, hiddenCount, err := filterAccessibleAlbums(albums)
	if err != nil {
		t.Fatalf("expected albums to be filtered, got error: %v", err)
	}
	if hiddenCount != 1 {
		t.Fatalf("expected hidden count 1, got %d", hiddenCount)
	}
	if len(accessible) != 2 {
		t.Fatalf("expected 2 accessible albums, got %d", len(accessible))
	}

	selected, missing := selectAlbumsByID(accessible, []string{"a3", "missing", "a1"})
	if len(missing) != 1 || missing[0] != "missing" {
		t.Fatalf("expected one missing id, got %#v", missing)
	}
	if len(selected) != 2 {
		t.Fatalf("expected 2 selected albums, got %d", len(selected))
	}
	if selected[0].Get("id").String() != "a1" || selected[1].Get("id").String() != "a3" {
		t.Fatalf("expected selected albums to preserve server order, got %s then %s", selected[0].Get("id").String(), selected[1].Get("id").String())
	}
}
