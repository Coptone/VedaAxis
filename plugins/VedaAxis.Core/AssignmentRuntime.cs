namespace VedaAxis.Core;

public enum AssignmentState
{
    Waiting,
    Highlighting,
    Success,
    Early,
    Missed,
    Late,
    Invalid,
    Cancelled,
}

public sealed class AssignmentRuntime
{
    public AssignmentRuntime(Assignment assignment)
    {
        Assignment = assignment;
    }

    public Assignment Assignment { get; }

    public AssignmentState State { get; private set; } = AssignmentState.Waiting;

    public long? ObservedAtMs { get; private set; }

    public bool? AvailableAtHighlight { get; private set; }

    public string? Reason { get; private set; }

    public void Advance(long elapsedMs, bool actionAvailable)
    {
        if (State is AssignmentState.Success or AssignmentState.Early or AssignmentState.Late
            or AssignmentState.Invalid or AssignmentState.Cancelled)
        {
            return;
        }

        if (elapsedMs < Assignment.HighlightAtMs)
        {
            State = AssignmentState.Waiting;
            return;
        }

        AvailableAtHighlight ??= actionAvailable;
        State = elapsedMs <= Assignment.LatestUseAtMs
            ? AssignmentState.Highlighting
            : AssignmentState.Missed;
    }

    public bool Observe(uint actionId, long elapsedMs)
    {
        if (!CanObserve(actionId, elapsedMs))
        {
            return false;
        }

        ObservedAtMs = elapsedMs;
        State = elapsedMs < Assignment.EarliestUseAtMs
            ? AssignmentState.Early
            : elapsedMs <= Assignment.LatestUseAtMs
                ? AssignmentState.Success
                : AssignmentState.Late;
        return true;
    }

    public bool CanObserve(uint actionId, long elapsedMs)
    {
        if (!ActionEquivalence.Matches(Assignment.ActionId, actionId)
            || State is AssignmentState.Success or AssignmentState.Early or AssignmentState.Late
                or AssignmentState.Cancelled or AssignmentState.Invalid)
        {
            return false;
        }

        if (elapsedMs < Assignment.HighlightAtMs)
        {
            return false;
        }

        return elapsedMs <= ObservationDeadlineMs;
    }

    public long ObservationDeadlineMs => Math.Max(Assignment.ImpactAtMs, Assignment.LatestUseAtMs) + 3_000;

    public void Invalidate(string reason)
    {
        if (State is AssignmentState.Success or AssignmentState.Early or AssignmentState.Late or AssignmentState.Cancelled)
        {
            return;
        }

        State = AssignmentState.Invalid;
        Reason = reason;
    }

    public void Cancel(string? reason = null)
    {
        State = AssignmentState.Cancelled;
        Reason = reason;
    }

    public void Reset()
    {
        State = AssignmentState.Waiting;
        ObservedAtMs = null;
        AvailableAtHighlight = null;
        Reason = null;
    }

    public bool ShouldDrawOverlay(long elapsedMs)
    {
        return State switch
        {
            AssignmentState.Highlighting => true,
            AssignmentState.Success or AssignmentState.Early or AssignmentState.Late =>
                ObservedAtMs is { } observedAt && elapsedMs - observedAt <= 1_500,
            AssignmentState.Missed or AssignmentState.Invalid => elapsedMs <= Math.Max(Assignment.ImpactAtMs, Assignment.LatestUseAtMs) + 3_000,
            _ => false,
        };
    }
}
