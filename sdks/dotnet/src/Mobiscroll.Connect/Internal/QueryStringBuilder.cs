using System.Collections.Generic;
using System.Net;
using System.Text;

namespace Mobiscroll.Connect.Internal;

/// <summary>
/// URL-encoded query string builder that matches the wire format of the Node and PHP SDKs:
/// booleans serialize as the literal strings "true"/"false" (not 1/0), and complex values
/// are expected to be pre-serialized (e.g. JSON for calendarIds) by the caller.
/// </summary>
internal sealed class QueryStringBuilder
{
    private readonly List<KeyValuePair<string, string>> _items = new();

    public QueryStringBuilder Add(string key, string? value)
    {
        if (value is not null)
        {
            _items.Add(new KeyValuePair<string, string>(key, value));
        }
        return this;
    }

    public QueryStringBuilder Add(string key, int? value)
    {
        if (value.HasValue)
        {
            _items.Add(new KeyValuePair<string, string>(key, value.Value.ToString(System.Globalization.CultureInfo.InvariantCulture)));
        }
        return this;
    }

    public QueryStringBuilder Add(string key, bool? value)
    {
        if (value.HasValue)
        {
            _items.Add(new KeyValuePair<string, string>(key, value.Value ? "true" : "false"));
        }
        return this;
    }

    public bool IsEmpty => _items.Count == 0;

    public override string ToString()
    {
        if (_items.Count == 0)
        {
            return string.Empty;
        }

        var sb = new StringBuilder();
        for (var i = 0; i < _items.Count; i++)
        {
            if (i > 0)
            {
                sb.Append('&');
            }
            sb.Append(WebUtility.UrlEncode(_items[i].Key));
            sb.Append('=');
            sb.Append(WebUtility.UrlEncode(_items[i].Value));
        }
        return sb.ToString();
    }
}
