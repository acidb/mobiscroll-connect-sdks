// Package querybuilder builds URL query strings with the wire format shared
// across the Mobiscroll Connect SDKs: booleans serialized as "true"/"false",
// time.Time as RFC3339, slices repeated under the same key.
package querybuilder

import (
	"net/url"
	"strconv"
	"time"
)

// Builder accumulates query parameters in insertion order.
type Builder struct {
	values url.Values
	keys   []string
}

// New returns an empty Builder.
func New() *Builder {
	return &Builder{values: url.Values{}}
}

// Add appends a key/value pair. The value's runtime type controls encoding:
//   - nil  → skipped
//   - bool → "true" / "false"
//   - time.Time → RFC3339
//   - *time.Time → RFC3339 (skipped when nil)
//   - string → as-is (skipped when empty)
//   - *string → as-is (skipped when nil or empty)
//   - int / int64 → decimal
//   - *int → decimal (skipped when nil)
//   - *bool → "true" / "false" (skipped when nil)
//   - []string → one entry per element, skipping empties
//
// Unsupported types are silently dropped; callers should pre-format anything
// exotic.
func (b *Builder) Add(key string, value any) *Builder {
	switch v := value.(type) {
	case nil:
		return b
	case bool:
		b.addOne(key, strconv.FormatBool(v))
	case *bool:
		if v != nil {
			b.addOne(key, strconv.FormatBool(*v))
		}
	case time.Time:
		if !v.IsZero() {
			b.addOne(key, v.UTC().Format(time.RFC3339))
		}
	case *time.Time:
		if v != nil && !v.IsZero() {
			b.addOne(key, v.UTC().Format(time.RFC3339))
		}
	case string:
		if v != "" {
			b.addOne(key, v)
		}
	case *string:
		if v != nil && *v != "" {
			b.addOne(key, *v)
		}
	case int:
		b.addOne(key, strconv.Itoa(v))
	case *int:
		if v != nil {
			b.addOne(key, strconv.Itoa(*v))
		}
	case int64:
		b.addOne(key, strconv.FormatInt(v, 10))
	case []string:
		for _, s := range v {
			if s != "" {
				b.addOne(key, s)
			}
		}
	}
	return b
}

// Empty reports whether no values have been added.
func (b *Builder) Empty() bool { return len(b.values) == 0 }

// Encode returns the URL-encoded query string (no leading "?"). Insertion
// order is preserved so the resulting string is stable for tests.
func (b *Builder) Encode() string {
	out := url.Values{}
	for _, k := range b.keys {
		out[k] = b.values[k]
	}
	// url.Values.Encode sorts alphabetically; we want insertion order.
	var buf []byte
	first := true
	for _, k := range b.keys {
		for _, v := range b.values[k] {
			if !first {
				buf = append(buf, '&')
			}
			first = false
			buf = append(buf, url.QueryEscape(k)...)
			buf = append(buf, '=')
			buf = append(buf, url.QueryEscape(v)...)
		}
	}
	return string(buf)
}

func (b *Builder) addOne(key, value string) {
	if _, seen := b.values[key]; !seen {
		b.keys = append(b.keys, key)
	}
	b.values.Add(key, value)
}
