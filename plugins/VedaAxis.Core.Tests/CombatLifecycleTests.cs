using VedaAxis.Core;

namespace VedaAxis.Core.Tests;

public sealed class CombatLifecycleTests
{
    [Fact]
    public void StartsOnlyOnceWhenPlanAndTerritoryMatch()
    {
        var lifecycle = new CombatLifecycle();
        var now = DateTimeOffset.Parse("2026-08-05T00:00:00Z");
        var plan = Plan(755);

        var first = lifecycle.TryStart(true, plan, 755, now);
        var duplicate = lifecycle.TryStart(true, plan, 755, now.AddSeconds(1));

        Assert.True(first.Started);
        Assert.Equal(CombatStartRejection.AlreadyActive, duplicate.Rejection);
        Assert.Equal(now, lifecycle.StartedAt);
    }

    [Theory]
    [InlineData(0u, 755u, CombatStartRejection.TerritoryMissing)]
    [InlineData(1363u, 755u, CombatStartRejection.TerritoryMismatch)]
    public void RejectsInvalidTerritory(
        uint planTerritory,
        uint currentTerritory,
        CombatStartRejection expected)
    {
        var decision = new CombatLifecycle().TryStart(
            true, Plan(planTerritory), currentTerritory, DateTimeOffset.UtcNow);

        Assert.False(decision.Started);
        Assert.Equal(expected, decision.Rejection);
    }

    [Fact]
    public void CompleteReturnsStartOnlyOnce()
    {
        var lifecycle = new CombatLifecycle();
        var now = DateTimeOffset.Parse("2026-08-05T00:00:00Z");
        lifecycle.TryStart(true, Plan(755), 755, now);

        Assert.Equal(now, lifecycle.Complete());
        Assert.Null(lifecycle.Complete());
    }

    private static PlanSnapshot Plan(uint territoryId)
    {
        var tracks = new[] { "T1", "H1", "D1", "D2" }
            .Select(slot => new ExecutionTrack(Guid.NewGuid(), slot, new HashSet<uint>(), slot))
            .ToArray();
        return new PlanSnapshot(
            "1.1", "0.1.4", Guid.NewGuid(), 1, Guid.NewGuid(), 1, Guid.NewGuid(), territoryId, "TEST",
            TrackMode.Four, new PlanSource("PERSONAL", null, "UNVERIFIED"), [], tracks, []);
    }
}
