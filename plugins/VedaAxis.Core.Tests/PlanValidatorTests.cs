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
            "1.0", "0.1.0", Guid.NewGuid(), 1, Guid.NewGuid(), 1, Guid.NewGuid(), "FOUR-PLAYER",
            TrackMode.Four, new PlanSource("PERSONAL", null, "UNVERIFIED"), [], tracks, []);

        Assert.Empty(PlanValidator.Validate(plan));
    }
}
