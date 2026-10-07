package mobiscroll

import "time"

// ResetWebhookKeyStores clears the process-wide webhook key cache.
var ResetWebhookKeyStores = resetWebhookKeyStores

// SetWebhookClock replaces the clock behind webhook verification and returns a
// func that restores it.
func SetWebhookClock(now func() time.Time) func() {
	previous := webhookNow
	webhookNow = now
	return func() { webhookNow = previous }
}
