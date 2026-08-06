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

    [Fact]
    public void DeserializesVersion12TimelineContent()
    {
        const string json = """
                            {
                              "schemaVersion": "1.2",
                              "minimumPluginVersion": "0.1.5",
                              "planId": "0f4b3bc6-3095-4a32-85f8-02c367e1c177",
                              "planVersion": 1,
                              "timelineId": "ae10b230-ff1a-45df-b8fa-a720e66b4c69",
                              "timelineVersion": 2,
                              "encounterId": "c97e8840-1697-476f-a4ac-8c7996df277b",
                              "territoryId": 755,
                              "strategyTag": "IMPORTED",
                              "trackMode": "FOUR",
                              "source": { "kind": "IMPORTED", "reference": "https://raalm.com/m-spec/timelinev2.html?boss=dancing-mad&spec=sage-sage", "confidence": "POC_PENDING" },
                              "phases": [{ "phaseId": "4270bbba-f402-4cc4-bb27-0d566815dd0d", "externalId": "DM_P1", "name": "P1", "plannedAtMs": 0, "confidence": "POC_PENDING" }],
                              "mechanics": [{ "mechanicId": "0d80a50c-cd3a-4569-a7ce-4766612e3316", "externalId": "dmu-001", "phase": "P1", "name": "机制", "plannedAtMs": 15000, "durationMs": 5000, "type": "RAIDWIDE", "damageType": "UNKNOWN", "target": "全体", "actionId": null, "confidence": "POC_PENDING" }],
                              "anchors": [],
                              "tracks": [],
                              "assignments": []
                            }
                            """;

        var plan = JsonSerializer.Deserialize<PlanSnapshot>(json, Options);

        Assert.NotNull(plan);
        Assert.Single(plan.Phases!);
        Assert.Single(plan.Mechanics!);
        Assert.Equal("机制", plan.Mechanics![0].Name);
        Assert.Null(plan.Mechanics[0].ActionId);
    }

    [Fact]
    public void DeserializesTheVersion13DmuP1P2DefaultPlan()
    {
        var path = Path.Combine(AppContext.BaseDirectory, "TestData", "p1-p2-default-plan.json");
        var plan = JsonSerializer.Deserialize<PlanSnapshot>(File.ReadAllText(path), Options);

        Assert.NotNull(plan);
        Assert.Equal("1.3", plan.SchemaVersion);
        Assert.Equal(1363u, plan.TerritoryId);
        Assert.Equal(2, plan.Phases?.Count);
        Assert.All(plan.Phases!, phase =>
        {
            Assert.Equal(PhaseTimingMode.Absolute, phase.TimingMode);
            Assert.True(phase.DurationMs > 0);
        });
        Assert.Equal(76, plan.Mechanics?.Count);
        Assert.Equal(108, plan.Assignments.Count);
        Assert.Equal(15, plan.Assignments.Count(assignment => assignment.TargetTrackId is not null));
        Assert.Empty(PlanValidator.Validate(plan));
    }
}
