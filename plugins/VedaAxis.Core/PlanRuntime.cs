namespace VedaAxis.Core;

public sealed class PlanRuntime
{
    private readonly List<AssignmentRuntime> assignments = [];
    private readonly Dictionary<(uint ActionId, AnchorKind Kind), int> anchorOccurrences = [];

    public PlanRuntime(TimelineClock clock)
    {
        Clock = clock;
    }

    public TimelineClock Clock { get; }

    public IReadOnlyList<AssignmentRuntime> Assignments => assignments;

    public void Load(PlanSnapshot plan, Guid trackId)
    {
        Plan = plan;
        TrackId = trackId;
        assignments.Clear();
        anchorOccurrences.Clear();
        assignments.AddRange(plan.Assignments
            .Where(assignment => assignment.TrackId == trackId)
            .OrderBy(assignment => assignment.HighlightAtMs)
            .Select(assignment => new AssignmentRuntime(assignment)));
    }

    public PlanSnapshot? Plan { get; private set; }

    public Guid? TrackId { get; private set; }

    public void Start(DateTimeOffset now)
    {
        Reset();
        Clock.Start(now);
    }

    public void Stop()
    {
        Clock.Stop();
    }

    public void Advance(DateTimeOffset now, Func<uint, bool> isActionAvailable)
    {
        if (!Clock.IsRunning)
        {
            return;
        }

        var elapsed = Clock.ElapsedMilliseconds(now);
        foreach (var assignment in assignments)
        {
            assignment.Advance(elapsed, isActionAvailable(assignment.Assignment.ActionId));
        }
    }

    public int ObserveAction(uint actionId, DateTimeOffset observedAt)
    {
        if (!Clock.IsRunning)
        {
            return 0;
        }

        var elapsed = Clock.ElapsedMilliseconds(observedAt);
        return assignments.Count(assignment => assignment.Observe(actionId, elapsed));
    }

    public TimelineAnchor? ObserveAnchor(uint actionId, AnchorKind kind, DateTimeOffset observedAt)
    {
        if (!Clock.IsRunning || Plan is null)
        {
            return null;
        }

        var available = Plan.Anchors.Where(anchor => anchor.ActionId == actionId && anchor.Kind == kind).ToList();
        if (available.Count == 0)
        {
            return null;
        }

        var key = (actionId, kind);
        var occurrence = anchorOccurrences.GetValueOrDefault(key) + 1;
        anchorOccurrences[key] = occurrence;
        var matched = available.FirstOrDefault(anchor => anchor.Occurrence == occurrence);
        if (matched is not null)
        {
            Clock.ApplyAnchor(matched.PlannedAtMs + matched.OffsetMs, observedAt);
        }
        return matched;
    }

    public void Reset()
    {
        anchorOccurrences.Clear();
        foreach (var assignment in assignments)
        {
            assignment.Reset();
        }
    }
}
