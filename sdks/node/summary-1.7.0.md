### Event lookup

Event lists can now return specific events by their IDs. They can also include the removed occurrences of a series, and the response carries messages about providers the API could not read.

### Webhook mode

A subscription can ask for series mode, which delivers a single master event for each changed Google series rather than one event per occurrence. Subscriptions that do not ask follow the application's default setting.
