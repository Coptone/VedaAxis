namespace VedaAxis.Core;

public enum CombatStartRejection
{
    None,
    Disabled,
    PlanMissing,
    TerritoryMissing,
    TerritoryMismatch,
    AlreadyActive,
}

public readonly record struct CombatStartDecision(bool Started, CombatStartRejection Rejection);

public sealed class CombatLifecycle
{
    public DateTimeOffset? StartedAt { get; private set; }

    public bool IsActive => StartedAt is not null;

    public CombatStartDecision TryStart(
        bool enabled,
        PlanSnapshot? plan,
        uint currentTerritoryId,
        DateTimeOffset now)
    {
        if (!enabled)
        {
            return new CombatStartDecision(false, CombatStartRejection.Disabled);
        }
        if (plan is null)
        {
            return new CombatStartDecision(false, CombatStartRejection.PlanMissing);
        }
        if (plan.TerritoryId == 0)
        {
            return new CombatStartDecision(false, CombatStartRejection.TerritoryMissing);
        }
        if (plan.TerritoryId != currentTerritoryId)
        {
            return new CombatStartDecision(false, CombatStartRejection.TerritoryMismatch);
        }
        if (StartedAt is not null)
        {
            return new CombatStartDecision(false, CombatStartRejection.AlreadyActive);
        }

        StartedAt = now;
        return new CombatStartDecision(true, CombatStartRejection.None);
    }

    public DateTimeOffset? Complete()
    {
        var startedAt = StartedAt;
        StartedAt = null;
        return startedAt;
    }
}
