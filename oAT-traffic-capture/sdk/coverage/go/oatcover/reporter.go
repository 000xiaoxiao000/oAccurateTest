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
	BuildID       string
	TestStage     string
	HTTPClient    *http.Client
}

type reportPayload struct {
	ProjectID     string `json:"projectId"`
	AppID         string `json:"appId"`
	Language      string `json:"language"`
	VersionNumber string `json:"versionNumber,omitempty"`
	CommitID      string `json:"commitId,omitempty"`
	Branch        string `json:"branch,omitempty"`
	CaseName      string `json:"caseName,omitempty"`
	BuildID       string `json:"buildId,omitempty"`
	TestStage     string `json:"testStage,omitempty"`
	Timestamp     int64  `json:"timestamp"`
	Payload       string `json:"payload"`
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
		ProjectID:     options.ProjectID,
		AppID:         options.AppID,
		Language:      "GO",
		VersionNumber: options.VersionNumber,
		CommitID:      options.CommitID,
		Branch:        options.Branch,
		CaseName:      options.CaseName,
		BuildID:       options.BuildID,
		TestStage:     defaultText(options.TestStage, "unknown"),
		Timestamp:     time.Now().UnixMilli(),
		Payload:       string(rawProfile),
	})
	if err != nil {
		return err
	}

	url := fmt.Sprintf("%s/api/v2/ingest/coverage", strings.TrimRight(options.Endpoint, "/"))
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

func defaultText(value string, fallback string) string {
	if strings.TrimSpace(value) == "" {
		return fallback
	}
	return value
}
