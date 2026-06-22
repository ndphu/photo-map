package config

import (
	"reflect"
	"testing"
)

func TestGetCSVPreserveCase(t *testing.T) {
	t.Setenv(
		"CORS_ALLOWED_ORIGINS",
		" http://localhost:5173, https://Gallery.Example.com , ",
	)

	got := getCSVPreserveCase("CORS_ALLOWED_ORIGINS")
	want := []string{"http://localhost:5173", "https://Gallery.Example.com"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("expected %#v, got %#v", want, got)
	}
}

func TestGetCSVNormalizesAdminEmails(t *testing.T) {
	t.Setenv("ADMIN_EMAILS", " Admin@Example.com,SECOND@example.com ")

	got := getCSV("ADMIN_EMAILS")
	want := []string{"admin@example.com", "second@example.com"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("expected %#v, got %#v", want, got)
	}
}
