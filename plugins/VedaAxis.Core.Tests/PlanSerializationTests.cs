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

    [Fact]
    public void LegacySnapshotWithoutTerritoryDeserializesWithZero()
    {
        const string json = """
                            {
                              "schemaVersion": "1.0",
                              "minimumPluginVersion": "0.1.0",
                              "planId": "0f4b3bc6-3095-4a32-85f8-02c367e1c177",
                              "planVersion": 1,
                              "timelineId": "ae10b230-ff1a-45df-b8fa-a720e66b4c69",
                              "timelineVersion": 1,
                              "encounterId": "c97e8840-1697-476f-a4ac-8c7996df277b",
                              "strategyTag": "LEGACY",
                              "trackMode": "FOUR",
                              "source": { "kind": "IMPORTED", "reference": null, "confidence": "POC_PENDING" },
                              "anchors": [],
                              "tracks": [],
                              "assignments": []
                            }
                            """;

        var plan = JsonSerializer.Deserialize<PlanSnapshot>(json, Options);

        Assert.NotNull(plan);
        Assert.Equal(0u, plan.TerritoryId);
    }
}
