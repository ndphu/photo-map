package service

import "testing"

func TestIsAllowedUploadStatusTransition(t *testing.T) {
	tests := []struct {
		name    string
		current string
		next    string
		allowed bool
	}{
		{name: "created to uploading", current: uploadStatusCreated, next: uploadStatusUploading, allowed: true},
		{name: "uploading to uploaded", current: uploadStatusUploading, next: uploadStatusUploaded, allowed: true},
		{name: "created to failed", current: uploadStatusCreated, next: uploadStatusFailed, allowed: true},
		{name: "uploading to failed", current: uploadStatusUploading, next: uploadStatusFailed, allowed: true},
		{name: "uploaded to failed", current: uploadStatusUploaded, next: uploadStatusFailed, allowed: true},
		{name: "completed to failed", current: uploadStatusCompleted, next: uploadStatusFailed, allowed: false},
		{name: "created to uploaded", current: uploadStatusCreated, next: uploadStatusUploaded, allowed: false},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			if actual := isAllowedUploadStatusTransition(test.current, test.next); actual != test.allowed {
				t.Fatalf("expected allowed=%t, got %t", test.allowed, actual)
			}
		})
	}
}
