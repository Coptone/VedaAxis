namespace VedaAxis.Core;

public static class ActionEquivalence
{
    private static readonly uint[][] EquivalentGroups =
    [
        // In Dawntrail, some actions are displayed in plans by their upgraded ActionId,
        // while the native hotbar slot can still report the base ActionId.
        [24292, 37034], // Eukrasian Prognosis / Eukrasian Prognosis II
    ];

    public static bool Matches(uint expectedActionId, uint observedActionId)
    {
        if (expectedActionId == observedActionId)
        {
            return true;
        }

        foreach (var group in EquivalentGroups)
        {
            var hasExpected = false;
            var hasObserved = false;
            foreach (var actionId in group)
            {
                hasExpected |= actionId == expectedActionId;
                hasObserved |= actionId == observedActionId;
            }

            if (hasExpected && hasObserved)
            {
                return true;
            }
        }

        return false;
    }

    public static IEnumerable<uint> Expand(uint actionId)
    {
        foreach (var group in EquivalentGroups)
        {
            if (group.Contains(actionId))
            {
                return group;
            }
        }

        return [actionId];
    }
}
