namespace VedaAxis.Core;

public static class ActionEquivalence
{
    private static readonly uint[][] EquivalentGroups =
    [
        // In Dawntrail, some actions are displayed in plans by their upgraded ActionId,
        // while the native hotbar slot can still report the base ActionId.
        [17, 36920], // Sentinel / Guardian
        [44, 36923], // Vengeance / Damnation
        [3636, 36927], // Shadow Wall / Shadowed Vigil
        [16148, 36935], // Nebula / Great Nebula
        [3542, 25746], // Sheltron / Holy Sheltron
        [3551, 25751], // Raw Intuition / Bloodwhetting
        [16161, 25758], // Heart of Stone / Heart of Corundum
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
