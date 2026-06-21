package model

type CleanupUploadSessionsResult struct {
	DryRun          bool           `json:"dryRun"`
	Scanned         int            `json:"scanned"`
	DeletedObjects  []string       `json:"deletedObjects"`
	ExpiredSessions []string       `json:"expiredSessions"`
	Errors          []CleanupError `json:"errors"`
}

type CleanupError struct {
	SessionID *string `json:"sessionId,omitempty"`
	ObjectKey *string `json:"objectKey,omitempty"`
	Message   string  `json:"message"`
}

type BackfillAssetChangesResult struct {
	DryRun     bool  `json:"dryRun"`
	Candidates int64 `json:"candidates"`
	Inserted   int64 `json:"inserted"`
}
