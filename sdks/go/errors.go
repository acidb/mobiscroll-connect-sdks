package mobiscroll

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strconv"
)

// MobiscrollError is the interface implemented by every typed SDK error.
// Use errors.As to extract the concrete type:
//
//	var ve *mobiscroll.ValidationError
//	if errors.As(err, &ve) { fmt.Println(ve.Details) }
type MobiscrollError interface {
	error
	Code() string
}

// AuthenticationError is returned for HTTP 401 and 403 responses (after
// token-refresh + retry has been attempted on 401).
type AuthenticationError struct{ Message string }

func (e *AuthenticationError) Error() string { return e.Message }
func (*AuthenticationError) Code() string    { return "AUTHENTICATION_ERROR" }

// NotFoundError is returned for HTTP 404 responses.
type NotFoundError struct{ Message string }

func (e *NotFoundError) Error() string { return e.Message }
func (*NotFoundError) Code() string    { return "NOT_FOUND_ERROR" }

// ValidationError is returned for HTTP 400 and 422 responses. Details holds
// the raw "details" field from the API error payload, if present.
type ValidationError struct {
	Message string
	Details json.RawMessage
}

func (e *ValidationError) Error() string { return e.Message }
func (*ValidationError) Code() string    { return "VALIDATION_ERROR" }

// RateLimitError is returned for HTTP 429 responses. RetryAfter is parsed from
// the Retry-After header (in seconds) when present.
type RateLimitError struct {
	Message    string
	RetryAfter int
}

func (e *RateLimitError) Error() string { return e.Message }
func (*RateLimitError) Code() string    { return "RATE_LIMIT_ERROR" }

// ServerError is returned for HTTP 5xx responses.
type ServerError struct {
	Message    string
	StatusCode int
}

func (e *ServerError) Error() string { return e.Message }
func (*ServerError) Code() string    { return "SERVER_ERROR" }

// NetworkError wraps a transport-level error (timeout, DNS failure, reset).
type NetworkError struct {
	Message string
	Err     error
}

func (e *NetworkError) Error() string {
	if e.Err != nil {
		return e.Message + ": " + e.Err.Error()
	}
	return e.Message
}
func (e *NetworkError) Unwrap() error { return e.Err }
func (*NetworkError) Code() string    { return "NETWORK_ERROR" }

// genericError is the fallback for unmapped status codes.
type genericError struct {
	Message    string
	StatusCode int
}

func (e *genericError) Error() string { return e.Message }
func (*genericError) Code() string    { return "MOBISCROLL_ERROR" }

// mapResponseError reads the response body and maps the status code to a
// typed SDK error. The body is fully consumed (so the caller need not close it).
func mapResponseError(resp *http.Response) error {
	body, _ := io.ReadAll(resp.Body)
	_ = resp.Body.Close()

	msg, details := extractMessage(body, resp.Status, resp.StatusCode)

	switch resp.StatusCode {
	case http.StatusUnauthorized, http.StatusForbidden:
		return &AuthenticationError{Message: msg}
	case http.StatusNotFound:
		return &NotFoundError{Message: msg}
	case http.StatusBadRequest, http.StatusUnprocessableEntity:
		return &ValidationError{Message: msg, Details: details}
	case http.StatusTooManyRequests:
		retry := 0
		if h := resp.Header.Get("Retry-After"); h != "" {
			retry, _ = strconv.Atoi(h)
		}
		return &RateLimitError{Message: msg, RetryAfter: retry}
	}

	if resp.StatusCode >= 500 && resp.StatusCode < 600 {
		return &ServerError{Message: msg, StatusCode: resp.StatusCode}
	}
	return &genericError{Message: msg, StatusCode: resp.StatusCode}
}

// extractMessage pulls a human-readable message and the optional details field
// out of a JSON error payload. Falls back to the HTTP status text.
func extractMessage(body []byte, status string, code int) (string, json.RawMessage) {
	var parsed struct {
		Message string          `json:"message"`
		Error   string          `json:"error"`
		Details json.RawMessage `json:"details"`
	}
	if len(body) > 0 && json.Unmarshal(body, &parsed) == nil {
		switch {
		case parsed.Message != "":
			return parsed.Message, parsed.Details
		case parsed.Error != "":
			return parsed.Error, parsed.Details
		}
	}
	if status != "" {
		return status, nil
	}
	return fmt.Sprintf("HTTP %d", code), nil
}

// wrapNetwork converts a transport-level error into a *NetworkError, keeping
// the original chain intact via Unwrap.
func wrapNetwork(err error) error {
	if err == nil {
		return nil
	}
	// already wrapped → return as-is
	var ne *NetworkError
	if errors.As(err, &ne) {
		return err
	}
	return &NetworkError{Message: "network error", Err: err}
}
