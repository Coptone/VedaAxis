using VedaAxis.Core;

namespace VedaAxis.Core.Tests;

public sealed class TimelineClockTests
{
    [Fact]
    public void AnchorCorrectsTimelineDrift()
    {
        var clock = new TimelineClock();
        var start = DateTimeOffset.Parse("2026-08-05T00:00:00Z");
        clock.Start(start);

        clock.ApplyAnchor(10_000, start.AddSeconds(12));

        Assert.Equal(10_000, clock.ElapsedMilliseconds(start.AddSeconds(12)));
        Assert.Equal(11_000, clock.ElapsedMilliseconds(start.AddSeconds(13)));
    }

    [Fact]
    public void PlanRuntimeAppliesMatchingActionEffectOccurrence()
    {
        var start = DateTimeOffset.Parse("2026-08-05T00:00:00Z");
        var track = new ExecutionTrack(Guid.NewGuid(), "H1", new HashSet<uint>(), "H1");
        var anchor = new TimelineAnchor(
            Guid.NewGuid(), 47858, 1, 10_000, 250, "P1", AnchorKind.ActionEffect);
        var plan = new PlanSnapshot(
            "1.1", "0.1.4", Guid.NewGuid(), 1, Guid.NewGuid(), 1, Guid.NewGuid(), 755, "test",
            TrackMode.Four, new PlanSource("PERSONAL", null, "UNVERIFIED"), [anchor],
            [
                new ExecutionTrack(Guid.NewGuid(), "T1", new HashSet<uint>(), "T1"),
                track,
                new ExecutionTrack(Guid.NewGuid(), "D1", new HashSet<uint>(), "D1"),
                new ExecutionTrack(Guid.NewGuid(), "D2", new HashSet<uint>(), "D2"),
            ], []);
        var runtime = new PlanRuntime(new TimelineClock());
        runtime.Load(plan, track.TrackId);
        runtime.Start(start);

        var matched = runtime.ObserveAnchor(47858, AnchorKind.ActionEffect, start.AddSeconds(13));

        Assert.Equal(anchor, matched);
        Assert.Equal(10_250, runtime.Clock.ElapsedMilliseconds(start.AddSeconds(13)));
    }
}
