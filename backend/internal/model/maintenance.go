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

type DerivativeRepairCandidate struct {
	AssetID      string `json:"assetId"`
	Orientation  int16  `json:"orientation"`
	ThumbnailKey string `json:"thumbnailKey"`
	PreviewKey   string `json:"previewKey"`
}

type DerivativeRepairError struct {
	AssetID string `json:"assetId"`
	Stage   string `json:"stage"`
	Message string `json:"message"`
}

type RebuildDerivativesResult struct {
	DryRun          bool                        `json:"dryRun"`
	Auto            bool                        `json:"auto"`
	Parallel        int                         `json:"parallel"`
	Batches         int                         `json:"batches"`
	Candidates      int                         `json:"candidates"`
	Processed       int                         `json:"processed"`
	Repaired        int                         `json:"repaired"`
	CandidateAssets []DerivativeRepairCandidate `json:"candidateAssets,omitempty"`
	Errors          []DerivativeRepairError     `json:"errors"`
}
