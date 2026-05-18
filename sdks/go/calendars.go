package mobiscroll

import (
	"context"
	"net/http"
)

// calendarsService groups the calendar endpoints.
type calendarsService struct{ api *apiClient }

// List returns the calendars across all connected providers for the
// authenticated user.
func (c *calendarsService) List(ctx context.Context) ([]Calendar, error) {
	var out []Calendar
	if err := c.api.do(ctx, http.MethodGet, "/calendars", "", nil, &out); err != nil {
		return nil, err
	}
	return out, nil
}
