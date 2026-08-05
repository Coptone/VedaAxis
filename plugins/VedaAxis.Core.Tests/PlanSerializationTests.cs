using System.Text.Json;
using VedaAxis.Core;

namespace VedaAxis.Core.Tests;

public sealed class PlanSerializationTests
{
    private static readonly JsonSerializerOptions Options = new()
    {
        PropertyNameCaseInsensitive = true,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    };

    [Fact]
    public void DeserializesAllowedJobIdsFromJsonArray()
    {
        const string json = """
                            {
                              "trackId": "89b0b7c2-c627-48bb-8324-8b2693b63403",
                              "slot": "H2",
                              "allowedJobIds": [28, 40],
                              "displayName": "盾疗"
                            }
                            """;

        var track = JsonSerializer.Deserialize<ExecutionTrack>(json, Options);

        Assert.NotNull(track);
        Assert.Equal([28u, 40u], track.AllowedJobIds);
    }
}
