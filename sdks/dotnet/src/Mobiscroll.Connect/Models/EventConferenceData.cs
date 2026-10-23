using System.Collections.Generic;
using System.Text.Json.Serialization;

namespace Mobiscroll.Connect.Models;

/// <summary>
/// Conference options sent with <see cref="EventCreateData.ConferenceData"/> when creating or updating an event.
/// </summary>
public sealed class EventConferenceData
{
    /// <summary>
    /// <c>true</c> generates a provider meeting link where supported (Google Meet, Microsoft Teams); the event's
    /// <see cref="EventCreateData.Conference"/> URL and the other fields here are then ignored.
    /// </summary>
    public bool? AutoGenerate { get; set; }

    /// <summary>Optional; the calendar provider decides the conference type when the event is written.</summary>
    public string? Provider { get; set; }

    /// <summary>Further provider-specific conference fields, sent as top-level keys.</summary>
    [JsonExtensionData]
    public Dictionary<string, object>? AdditionalProperties { get; set; }
}
