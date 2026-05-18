// Package testsupport provides helpers used across the SDK's tests. Not part
// of the public API; it lives in a subpackage so non-test files can import it.
package testsupport

import (
	"bytes"
	"io"
	"net/http"
	"net/http/httptest"
	"sync"
	"testing"
)

// RecordedRequest is a snapshot of one inbound request taken by MockServer.
// Body is the request body as read, not a stream.
type RecordedRequest struct {
	Method string
	Path   string // includes query string
	URL    string // full URL
	Header http.Header
	Body   []byte
}

// MockResponse describes one queued response. Body is the raw bytes (typically
// a JSON string). Status defaults to 200 when zero. Headers are merged into
// the response.
type MockResponse struct {
	Status  int
	Body    string
	Headers map[string]string
}

// MockServer is a httptest.Server with a FIFO queue of responses and a record
// of every request received. Safe for concurrent use.
type MockServer struct {
	*httptest.Server
	t        testing.TB
	mu       sync.Mutex
	queue    []MockResponse
	requests []RecordedRequest
}

// NewMockServer starts a server that responds with whatever has been queued
// via Enqueue. A test that expects N requests should queue N responses up
// front, or use Dispatch when responses depend on request inspection.
func NewMockServer(t testing.TB) *MockServer {
	t.Helper()
	m := &MockServer{t: t}
	m.Server = httptest.NewServer(http.HandlerFunc(m.handle))
	t.Cleanup(m.Close)
	return m
}

// Enqueue adds a response to the FIFO queue.
func (m *MockServer) Enqueue(r MockResponse) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.queue = append(m.queue, r)
}

// EnqueueJSON is a shortcut for {Status:200, Body:body, Headers:Content-Type=application/json}.
func (m *MockServer) EnqueueJSON(body string) {
	m.Enqueue(MockResponse{
		Status:  200,
		Body:    body,
		Headers: map[string]string{"Content-Type": "application/json"},
	})
}

// EnqueueStatus is a shortcut for an empty-body response with the given status.
func (m *MockServer) EnqueueStatus(status int) { m.Enqueue(MockResponse{Status: status}) }

// Requests returns a copy of every request received so far.
func (m *MockServer) Requests() []RecordedRequest {
	m.mu.Lock()
	defer m.mu.Unlock()
	out := make([]RecordedRequest, len(m.requests))
	copy(out, m.requests)
	return out
}

// RequestCount returns how many requests have been received.
func (m *MockServer) RequestCount() int {
	m.mu.Lock()
	defer m.mu.Unlock()
	return len(m.requests)
}

func (m *MockServer) handle(w http.ResponseWriter, r *http.Request) {
	body, _ := io.ReadAll(r.Body)
	_ = r.Body.Close()
	r.Body = io.NopCloser(bytes.NewReader(body))

	m.mu.Lock()
	m.requests = append(m.requests, RecordedRequest{
		Method: r.Method,
		Path:   r.URL.RequestURI(),
		URL:    r.URL.String(),
		Header: r.Header.Clone(),
		Body:   body,
	})
	var resp MockResponse
	if len(m.queue) == 0 {
		m.mu.Unlock()
		http.Error(w, `{"message":"mock queue empty"}`, http.StatusInternalServerError)
		return
	}
	resp = m.queue[0]
	m.queue = m.queue[1:]
	m.mu.Unlock()

	for k, v := range resp.Headers {
		w.Header().Set(k, v)
	}
	status := resp.Status
	if status == 0 {
		status = http.StatusOK
	}
	w.WriteHeader(status)
	if resp.Body != "" {
		_, _ = io.WriteString(w, resp.Body)
	}
}
