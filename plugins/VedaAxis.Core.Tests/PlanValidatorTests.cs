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
}
