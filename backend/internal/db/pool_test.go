package db

import (
	"context"
	"testing"
)

func TestNewPoolWithMaxConnectionsRejectsNonPositiveLimit(t *testing.T) {
	if _, err := NewPoolWithMaxConnections(context.Background(), "", 0); err == nil {
		t.Fatal("NewPoolWithMaxConnections() error = nil")
	}
}
