package mobiscroll

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
)

// webhooksService groups the webhook endpoints: subscribe, unsubscribe, and
// verifying deliveries.
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

// VerifyWebhook verifies that a webhook delivery came from Mobiscroll Connect
// and returns it parsed. It returns a *WebhookVerificationError when the
// delivery is not genuine; respond with a 4xx and do not process it.
//
// payload is the raw request body, exactly as received. headers are the
// request headers; names are matched case-insensitively.
//
// The public keys are fetched from /.well-known/webhook-keys on first use and
// cached for the whole process, shared by every Client, and refreshed as the
// endpoint's Cache-Control allows. When no signature matches, the keys are
// fetched again once (at most once a minute) before the delivery is rejected,
// so a key rotation never rejects genuine deliveries. The key set by
// WithWebhookPublicKey is used only when the endpoint cannot be reached. If ctx
// ends while waiting for the keys, ctx.Err() is returned.
//
//	func handle(w http.ResponseWriter, r *http.Request) {
//	    body, err := io.ReadAll(r.Body)
//	    if err != nil {
//	        http.Error(w, "bad request", http.StatusBadRequest)
//	        return
//	    }
//	    delivery, err := client.Webhooks().VerifyWebhook(r.Context(), body, r.Header)
//	    var ve *mobiscroll.WebhookVerificationError
//	    if errors.As(err, &ve) {
//	        http.Error(w, "invalid signature", http.StatusUnauthorized)
//	        return
//	    }
//	    ...
//	}
func (s *webhooksService) VerifyWebhook(ctx context.Context, payload []byte, headers http.Header) (*WebhookDelivery, error) {
	keysURL, err := webhookKeysURL(s.api.cfg.baseURL)
	if err != nil {
		return nil, err
	}
	store := webhookKeyStoreFor(keysURL)
	pinned := s.api.cfg.webhookPublicKey
	verify := func() error {
		keys, err := store.getKeys(ctx, s.api.keysHTTP)
		if err != nil {
			return err
		}
		// Never merge the pinned key into fetched keys: after an emergency
		// rotation a retired pinned key must stop verifying.
		if len(keys) == 0 && pinned != "" {
			keys = []string{pinned}
		}
		return VerifyWebhookSignature(payload, headers, keys)
	}

	if err := verify(); err != nil {
		var ve *WebhookVerificationError
		retryable := errors.As(err, &ve) &&
			(ve.Reason == WebhookNoMatchingSignature || ve.Reason == WebhookNoPublicKeys)
		if !retryable || !store.canRefetch() {
			return nil, err
		}
		if err := store.refresh(ctx, s.api.keysHTTP); err != nil {
			return nil, err
		}
		if err := verify(); err != nil {
			return nil, err
		}
	}

	delivery := &WebhookDelivery{}
	if err := json.Unmarshal(payload, delivery); err != nil {
		return nil, &WebhookVerificationError{Message: "webhook payload is not valid JSON", Reason: WebhookInvalidPayload}
	}
	return delivery, nil
}
