package handler

import "testing"

func TestParseChangeCursor(t *testing.T) {
	tests := []struct {
		value   string
		want    int64
		wantErr bool
	}{
		{value: "", want: 0},
		{value: "0", want: 0},
		{value: "101", want: 101},
		{value: "-1", wantErr: true},
		{value: "invalid", wantErr: true},
	}
	for _, test := range tests {
		got, err := parseChangeCursor(test.value)
		if test.wantErr != (err != nil) {
			t.Fatalf("parseChangeCursor(%q) error = %v", test.value, err)
		}
		if got != test.want {
			t.Fatalf("parseChangeCursor(%q) = %d, want %d", test.value, got, test.want)
		}
	}
}
