using System;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace Mobiscroll.Connect;

[JsonConverter(typeof(ProviderJsonConverter))]
public enum Provider
{
    Google,
    Microsoft,
    Apple,
    CalDav,
}

public static class ProviderExtensions
{
    public static string ToWireString(this Provider provider) => provider switch
    {
        Provider.Google => "google",
        Provider.Microsoft => "microsoft",
        Provider.Apple => "apple",
        Provider.CalDav => "caldav",
        _ => throw new ArgumentOutOfRangeException(nameof(provider), provider, null),
    };

    public static Provider FromWireString(string value) => value switch
    {
        "google" => Provider.Google,
        "microsoft" => Provider.Microsoft,
        "apple" => Provider.Apple,
        "caldav" => Provider.CalDav,
        _ => throw new ArgumentException($"Unknown provider value '{value}'", nameof(value)),
    };
}

internal sealed class ProviderJsonConverter : JsonConverter<Provider>
{
    public override Provider Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        var value = reader.GetString()
            ?? throw new JsonException("Expected string for Provider");
        return ProviderExtensions.FromWireString(value);
    }

    public override void Write(Utf8JsonWriter writer, Provider value, JsonSerializerOptions options)
    {
        writer.WriteStringValue(value.ToWireString());
    }
}
