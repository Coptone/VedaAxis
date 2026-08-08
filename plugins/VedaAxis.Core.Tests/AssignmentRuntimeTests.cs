using VedaAxis.Core;

namespace VedaAxis.Core.Tests;

public sealed class AssignmentRuntimeTests
{
    [Fact]
    public void TransitionsFromWaitingToHighlightingToSuccess()
    {
        var runtime = new AssignmentRuntime(TestAssignment());

        runtime.Advance(999, true);
        Assert.Equal(AssignmentState.Waiting, runtime.State);

        runtime.Advance(1_000, true);
        Assert.Equal(AssignmentState.Highlighting, runtime.State);
        Assert.True(runtime.AvailableAtHighlight);

        Assert.True(runtime.Observe(7535, 2_500));
        Assert.Equal(AssignmentState.Success, runtime.State);
    }

    [Fact]
    public void AllowsLateObservationAfterMissedWindow()
    {
        var runtime = new AssignmentRuntime(TestAssignment());
        runtime.Advance(3_001, false);
        Assert.Equal(AssignmentState.Missed, runtime.State);

        Assert.True(runtime.Observe(7535, 3_500));
        Assert.Equal(AssignmentState.Late, runtime.State);
        Assert.False(runtime.AvailableAtHighlight);
    }

    [Fact]
    public void DoesNotObserveBeforeHighlightWindow()
    {
        var runtime = new AssignmentRuntime(TestAssignment());

        Assert.False(runtime.Observe(7535, 999));
        Assert.Equal(AssignmentState.Waiting, runtime.State);
        Assert.Null(runtime.ObservedAtMs);
    }

    [Fact]
    public void DoesNotObserveAfterLateGraceWindow()
    {
        var runtime = new AssignmentRuntime(TestAssignment());
        runtime.Advance(7_001, false);

        Assert.False(runtime.Observe(7535, 7_001));
        Assert.Equal(AssignmentState.Missed, runtime.State);
        Assert.Null(runtime.ObservedAtMs);
    }

    [Fact]
    public void TreatsEquivalentUpgradedActionsAsSameAction()
    {
        var runtime = new AssignmentRuntime(TestAssignment(actionId: 37034));
        runtime.Advance(2_500, true);

        Assert.True(runtime.Observe(24292, 2_500));
        Assert.Equal(AssignmentState.Success, runtime.State);
    }

    [Fact]
    public void ResetClearsTerminalState()
    {
        var runtime = new AssignmentRuntime(TestAssignment());
        runtime.Cancel("wipe");
        runtime.Reset();

        Assert.Equal(AssignmentState.Waiting, runtime.State);
        Assert.Null(runtime.Reason);
    }

    [Fact]
    public void SuccessOverlayExpiresAfterConfirmationFlash()
    {
        var runtime = new AssignmentRuntime(TestAssignment());
        runtime.Observe(7535, 2_500);

        Assert.True(runtime.ShouldDrawOverlay(3_900));
        Assert.False(runtime.ShouldDrawOverlay(4_001));
    }

    [Fact]
    public void MissedPostImpactSupportWindowStaysVisibleUntilLatestUseGrace()
    {
        var runtime = new AssignmentRuntime(new Assignment(
            Guid.NewGuid(), Guid.NewGuid(), Guid.NewGuid(), 24302, null,
            1_000, 4_500, 10_000, 4_000, false,
            ConfirmationStrategy.StatusApply, []));

        runtime.Advance(10_001, false);

        Assert.Equal(AssignmentState.Missed, runtime.State);
        Assert.True(runtime.ShouldDrawOverlay(13_000));
        Assert.False(runtime.ShouldDrawOverlay(13_001));
    }

    private static Assignment TestAssignment(uint actionId = 7535) => new(
        Guid.NewGuid(), Guid.NewGuid(), Guid.NewGuid(), actionId, null,
        1_000, 2_000, 3_000, 4_000, false,
        ConfirmationStrategy.ActionEffect, []);
}
