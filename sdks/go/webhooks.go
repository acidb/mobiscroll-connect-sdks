package mobiscroll

import (
	"context"
	"errors"
	"net/http"
)

// webhooksService groups the webhook subscription endpoints.
type webhooksService struct{ api *apiClient }

// SubscribeWebhook subscribes to change notifications for a calendar.
// Provider and CalendarID are required; ChannelID is auto-generated
// server-side when omitted, and Expiration is a provider-specific timestamp
// (ms epoch).
func (s *webhooksService) SubscribeWebhook(ctx context.Context, params *SubscribeWebhookParams) (*SubscribeWebhookResponse, error) {
	if params == nil {
		return nil, errors.New("mobiscroll: SubscribeWebhookParams must not be nil")
	}
	out := &SubscribeWebhookResponse{}
	if err := s.api.do(ctx, http.MethodPost, "/subscribe-webhook", "", params, out); err != nil {
		return nil, err
	}
	return out, nil
}

// UnsubscribeWebhook cancels a webhook subscription. Provider and ChannelID
// are required; ResourceID is required by some providers (e.g. Google) to
// fully unsubscribe. The response reports success even if the provider-side
// subscription had already expired — check Message for details in that case,
// and treat the response as final regardless of Message.
func (s *webhooksService) UnsubscribeWebhook(ctx context.Context, params *UnsubscribeWebhookParams) (*UnsubscribeWebhookResponse, error) {
	if params == nil {
		return nil, errors.New("mobiscroll: UnsubscribeWebhookParams must not be nil")
	}
	out := &UnsubscribeWebhookResponse{}
	if err := s.api.do(ctx, http.MethodPost, "/unsubscribe-webhook", "", params, out); err != nil {
		return nil, err
	}
	return out, nil
}
