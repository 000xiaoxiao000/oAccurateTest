package oatcover

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
	"time"
)

type ReportOptions struct {
	Endpoint      string
	ProjectID     string
	AppID         string
	ProfilePath   string
	VersionNumber string
	CommitID      string
	Branch        string
	CaseName      string
	HTTPClient    *http.Client
}

type reportPayload struct {
	VersionNumber string `json:"versionNumber,omitempty"`
	CommitID      string `json:"commitId,omitempty"`
	Branch        string `json:"branch,omitempty"`
	CaseName      string `json:"caseName,omitempty"`
	Timestamp     int64  `json:"timestamp"`
	CoverageData  string `json:"coverageData"`
}

func PostProfile(ctx context.Context, options ReportOptions) error {
	if strings.TrimSpace(options.Endpoint) == "" {
		return fmt.Errorf("endpoint is required")
	}
	if strings.TrimSpace(options.ProjectID) == "" {
		return fmt.Errorf("project id is required")
	}
	if strings.TrimSpace(options.AppID) == "" {
		return fmt.Errorf("app id is required")
	}
	if strings.TrimSpace(options.ProfilePath) == "" {
		return fmt.Errorf("profile path is required")
	}

	rawProfile, err := os.ReadFile(options.ProfilePath)
	if err != nil {
		return err
	}

	payload, err := json.Marshal(reportPayload{
		VersionNumber: options.VersionNumber,
		CommitID:      options.CommitID,
		Branch:        options.Branch,
		CaseName:      options.CaseName,
		Timestamp:     time.Now().UnixMilli(),
		CoverageData:  string(rawProfile),
	})
	if err != nil {
		return err
	}

	url := fmt.Sprintf("%s/api/projects/%s/apps/%s/coverage/universal/GO/report",
		strings.TrimRight(options.Endpoint, "/"), options.ProjectID, options.AppID)
	request, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(payload))
	if err != nil {
		return err
	}
	request.Header.Set("Content-Type", "application/json")

	client := options.HTTPClient
	if client == nil {
		client = http.DefaultClient
	}
	response, err := client.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	if response.StatusCode >= 200 && response.StatusCode < 300 {
		return nil
	}
	body, _ := io.ReadAll(io.LimitReader(response.Body, 4096))
	return fmt.Errorf("oAT coverage upload failed: status=%d body=%s", response.StatusCode, string(body))
}
