package mobiscroll_test

import (
	"context"
	"errors"
	"testing"

	mobiscroll "github.com/acidb/mobiscroll-connect-sdks/sdks/go"
	"github.com/acidb/mobiscroll-connect-sdks/sdks/go/testsupport"
)

func TestErrorMapping(t *testing.T) {
	tests := []struct {
		name    string
		status  int
		body    string
		headers map[string]string
		check   func(t *testing.T, err error)
	}{
		{
			name:   "401 -> AuthenticationError (after refresh fails)",
			status: 401,
			body:   `{"message":"bad token"}`,
			check: func(t *testing.T, err error) {
				var ae *mobiscroll.AuthenticationError
				if !errors.As(err, &ae) {
					t.Fatalf("expected *AuthenticationError, got %T: %v", err, err)
				}
			},
		},
		{
			name:   "403 -> AuthenticationError",
			status: 403,
			body:   `{"message":"no scope"}`,
			check: func(t *testing.T, err error) {
				var ae *mobiscroll.AuthenticationError
				if !errors.As(err, &ae) {
					t.Fatalf("expected *AuthenticationError, got %T: %v", err, err)
				}
			},
		},
		{
			name:   "404 -> NotFoundError",
			status: 404,
			body:   `{"message":"missing"}`,
			check: func(t *testing.T, err error) {
				var nf *mobiscroll.NotFoundError
				if !errors.As(err, &nf) {
					t.Fatalf("expected *NotFoundError, got %T: %v", err, err)
				}
			},
		},
		{
			name:   "400 -> ValidationError with details",
			status: 400,
			body:   `{"message":"bad","details":{"field":"title"}}`,
			check: func(t *testing.T, err error) {
				var ve *mobiscroll.ValidationError
				if !errors.As(err, &ve) {
					t.Fatalf("expected *ValidationError, got %T: %v", err, err)
				}
				if ve.Message != "bad" {
					t.Errorf("message: %q", ve.Message)
				}
				if !contains(string(ve.Details), `"field"`) {
					t.Errorf("expected details to contain field, got %s", string(ve.Details))
				}
			},
		},
		{
			name:   "422 -> ValidationError",
			status: 422,
			body:   `{"message":"unprocessable"}`,
			check: func(t *testing.T, err error) {
				var ve *mobiscroll.ValidationError
				if !errors.As(err, &ve) {
					t.Fatalf("expected *ValidationError, got %T: %v", err, err)
				}
			},
		},
		{
			name:    "429 -> RateLimitError with Retry-After",
			status:  429,
			body:    `{"message":"slow"}`,
			headers: map[string]string{"Retry-After": "42"},
			check: func(t *testing.T, err error) {
				var re *mobiscroll.RateLimitError
				if !errors.As(err, &re) {
					t.Fatalf("expected *RateLimitError, got %T: %v", err, err)
				}
				if re.RetryAfter != 42 {
					t.Errorf("expected RetryAfter=42, got %d", re.RetryAfter)
				}
			},
		},
		{
			name:   "503 -> ServerError",
			status: 503,
			body:   `{"message":"down"}`,
			check: func(t *testing.T, err error) {
				var se *mobiscroll.ServerError
				if !errors.As(err, &se) {
					t.Fatalf("expected *ServerError, got %T: %v", err, err)
				}
				if se.StatusCode != 503 {
					t.Errorf("expected status 503, got %d", se.StatusCode)
				}
			},
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			srv := testsupport.NewMockServer(t)
			// For 401, the SDK will try a refresh first; queue a 401 on that too.
			if tc.status == 401 {
				srv.Enqueue(testsupport.MockResponse{Status: 401, Body: tc.body,
					Headers: map[string]string{"Content-Type": "application/json"}})
				srv.Enqueue(testsupport.MockResponse{Status: 401, Body: `{"message":"refresh failed"}`,
					Headers: map[string]string{"Content-Type": "application/json"}})
			} else {
				h := map[string]string{"Content-Type": "application/json"}
				for k, v := range tc.headers {
					h[k] = v
				}
				srv.Enqueue(testsupport.MockResponse{Status: tc.status, Body: tc.body, Headers: h})
			}
			c := mobiscroll.NewClient("id", "secret", "https://app/cb",
				mobiscroll.WithBaseURL(srv.URL+"/api"),
			)
			c.SetCredentials(&mobiscroll.TokenResponse{AccessToken: "at", RefreshToken: "rt"})
			_, err := c.Calendars().List(context.Background())
			if err == nil {
				t.Fatalf("expected error, got nil")
			}
			tc.check(t, err)
		})
	}
}

func contains(haystack, needle string) bool {
	return len(needle) == 0 || (len(haystack) >= len(needle) && stringIndex(haystack, needle) >= 0)
}

func stringIndex(s, sub string) int {
	for i := 0; i+len(sub) <= len(s); i++ {
		if s[i:i+len(sub)] == sub {
			return i
		}
	}
	return -1
}
