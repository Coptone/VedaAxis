using VedaAxis.Core;

namespace VedaAxis.Core.Tests;

public sealed class PlanValidatorTests
{
    [Fact]
    public void AcceptsExactFourTrackShape()
    {
        var tracks = new[] { "T1", "H1", "D1", "D2" }
            .Select(slot => new ExecutionTrack(Guid.NewGuid(), slot, new HashSet<uint>(), slot))
            .ToArray();
        var plan = new PlanSnapshot(
            "1.1", "0.1.4", Guid.NewGuid(), 1, Guid.NewGuid(), 1, Guid.NewGuid(), 755, "FOUR-PLAYER",
            TrackMode.Four, new PlanSource("PERSONAL", null, "UNVERIFIED"), [], tracks, []);

        Assert.Empty(PlanValidator.Validate(plan));
    }

    [Fact]
    public void RejectsVersion11PlanWithoutTerritory()
    {
        var tracks = new[] { "T1", "H1", "D1", "D2" }
            .Select(slot => new ExecutionTrack(Guid.NewGuid(), slot, new HashSet<uint>(), slot))
            .ToArray();
        var plan = new PlanSnapshot(
            "1.1", "0.1.4", Guid.NewGuid(), 1, Guid.NewGuid(), 1, Guid.NewGuid(), 0, "FOUR-PLAYER",
            TrackMode.Four, new PlanSource("PERSONAL", null, "UNVERIFIED"), [], tracks, []);

        Assert.Contains(PlanValidator.Validate(plan), issue => issue.Contains("Territory ID"));
    }

    [Fact]
    public void AcceptsPostImpactSupportWindow()
    {
        var mechanicId = Guid.NewGuid();
        var tracks = new[] { "T1", "H1", "D1", "D2" }
            .Select(slot => new ExecutionTrack(Guid.NewGuid(), slot, new HashSet<uint>(), slot))
            .ToArray();
        var trackId = tracks[0].TrackId;
        var mechanics = new[]
        {
            new TimelineMechanic(
                mechanicId,
                null,
                "P1",
                "AOE",
                30_000,
                0,
                "RAIDWIDE",
                "MAGICAL",
                "全体",
                null,
                "POC_PENDING",
                null),
        };
        var assignments = new[]
        {
            new Assignment(
                Guid.NewGuid(),
                mechanicId,
                trackId,
                24302,
                null,
                27_000,
                30_500,
                36_000,
                30_000,
                false,
                ConfirmationStrategy.StatusApply,
                []),
        };
        var plan = new PlanSnapshot(
            "1.3", "0.1.13", Guid.NewGuid(), 1, Guid.NewGuid(), 1, Guid.NewGuid(), 755, "TEST",
            TrackMode.Four, new PlanSource("PERSONAL", null, "UNVERIFIED"), [], tracks, assignments, [], mechanics);

        Assert.Empty(PlanValidator.Validate(plan));
    }
}
