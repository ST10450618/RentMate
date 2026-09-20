using System.Text.Json;
using System.Text.Json.Serialization;

namespace RentMate.Api.Tests;

/// <summary>
/// HttpClient's PostAsJsonAsync/ReadFromJsonAsync extension methods use
/// their own default JsonSerializerOptions, which do NOT automatically
/// match the JsonStringEnumConverter configured on the server in
/// Program.cs. Without this, tests would serialise enums like
/// SplitMethod.Equal as the integer 0, which the server - now expecting
/// the string "Equal" - would reject. Every JSON call in the test project
/// should use TestJson.Options explicitly.
/// </summary>
public static class TestJson
{
    public static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        Converters = { new JsonStringEnumConverter() }
    };
}
