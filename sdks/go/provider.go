package mobiscroll

// Provider identifies a calendar provider. The string value matches the API
// wire form, so no custom JSON marshaler is required.
type Provider string

// Supported providers.
const (
	ProviderGoogle    Provider = "google"
	ProviderMicrosoft Provider = "microsoft"
	ProviderApple     Provider = "apple"
	ProviderCalDav    Provider = "caldav"
)
