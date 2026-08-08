using VedaAxis.Core;

namespace VedaAxis.Core.Tests;

public sealed class PlanRuntimeObservationTests
{
    [Fact]
    public void ObservesOnlyOneMatchingAssignmentWithinCurrentWindow()
    {
        var trackId = Guid.NewGuid();
        var first = TestAssignment(trackId, 10_000);
        var second = TestAssignment(trackId, 220_000);
        var runtime = new PlanRuntime(new TimelineClock());
        var startedAt = DateTimeOffset.UtcNow;

        runtime.Load(TestPlan(trackId, [first, second]), trackId);
        runtime.Start(startedAt);
        runtime.Advance(startedAt + TimeSpan.FromMilliseconds(11_000), _ => true);

        Assert.Equal(1, runtime.ObserveAction(24298, startedAt + TimeSpan.FromMilliseconds(11_000)));

        Assert.Equal(AssignmentState.Success, runtime.Assignments[0].State);
        Assert.Equal(AssignmentState.Waiting, runtime.Assignments[1].State);
        Assert.Null(runtime.Assignments[1].ObservedAtMs);
    }

    [Fact]
    public void DoesNotObserveFutureSameActionBeforeHighlight()
    {
        var trackId = Guid.NewGuid();
        var first = TestAssignment(trackId, 10_000);
        var second = TestAssignment(trackId, 220_000);
        var runtime = new PlanRuntime(new TimelineClock());
        var startedAt = DateTimeOffset.UtcNow;

        runtime.Load(TestPlan(trackId, [first, second]), trackId);
        runtime.Start(startedAt);

        Assert.Equal(0, runtime.ObserveAction(24298, startedAt + TimeSpan.FromMilliseconds(60_000)));

        Assert.Equal(AssignmentState.Waiting, runtime.Assignments[0].State);
        Assert.Equal(AssignmentState.Waiting, runtime.Assignments[1].State);
    }

    [Fact]
    public void ObservesEquivalentActionIdOnce()
    {
        var trackId = Guid.NewGuid();
        var assignment = TestAssignment(trackId, 10_000, actionId: 37034);
        var runtime = new PlanRuntime(new TimelineClock());
        var startedAt = DateTimeOffset.UtcNow;

        runtime.Load(TestPlan(trackId, [assignment]), trackId);
        runtime.Start(startedAt);
        runtime.Advance(startedAt + TimeSpan.FromMilliseconds(11_000), _ => true);

        Assert.Equal(1, runtime.ObserveAction(24292, startedAt + TimeSpan.FromMilliseconds(11_000)));
        Assert.Equal(AssignmentState.Success, runtime.Assignments[0].State);
    }

    private static Assignment TestAssignment(Guid trackId, long earliestUseAtMs, uint actionId = 24298)
    {
        var mechanicId = Guid.NewGuid();
        return new Assignment(
            Guid.NewGuid(),
            mechanicId,
            trackId,
            actionId,
            null,
            earliestUseAtMs - 5_000,
            earliestUseAtMs,
            earliestUseAtMs + 2_000,
            earliestUseAtMs + 4_000,
            false,
            ConfirmationStrategy.ActionEffect,
            []);
    }

    private static PlanSnapshot TestPlan(Guid trackId, IReadOnlyList<Assignment> assignments) => new(
        "1.0.0",
        "0.0.0",
        Guid.NewGuid(),
        1,
        Guid.NewGuid(),
        1,
        Guid.NewGuid(),
        1363,
        "TEST",
        TrackMode.Eight,
        new PlanSource("TEST", null, "TEST"),
        [],
        [new ExecutionTrack(trackId, "H2", [40], "H2")],
        assignments);
}
