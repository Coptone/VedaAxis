using VedaAxis.Core;

namespace VedaAxis.Core.Tests;

public sealed class ActionEquivalenceTests
{
    [Theory]
    [InlineData(17u, 36920u)]
    [InlineData(44u, 36923u)]
    [InlineData(3636u, 36927u)]
    [InlineData(16148u, 36935u)]
    [InlineData(3542u, 25746u)]
    [InlineData(3551u, 25751u)]
    [InlineData(16161u, 25758u)]
    [InlineData(24292u, 37034u)]
    public void MatchesKnownUpgradePairsBothWays(uint baseActionId, uint upgradedActionId)
    {
        Assert.True(ActionEquivalence.Matches(baseActionId, upgradedActionId));
        Assert.True(ActionEquivalence.Matches(upgradedActionId, baseActionId));
        Assert.Contains(baseActionId, ActionEquivalence.Expand(upgradedActionId));
        Assert.Contains(upgradedActionId, ActionEquivalence.Expand(baseActionId));
    }

    [Theory]
    [InlineData(16536u, 37011u)] // Temperance / Divine Caress: follow-up, not a base upgrade.
    [InlineData(16559u, 37031u)] // Neutral Sect / Sun Sign: follow-up, not a base upgrade.
    [InlineData(34685u, 34686u)] // Tempera Coat / Tempera Grassa: conditional follow-up.
    [InlineData(16464u, 25751u)] // Nascent Flash / Bloodwhetting share recast but target different units.
    public void DoesNotMatchConditionalFollowUpsOrRelatedActions(uint left, uint right)
    {
        Assert.False(ActionEquivalence.Matches(left, right));
        Assert.DoesNotContain(left, ActionEquivalence.Expand(right));
        Assert.DoesNotContain(right, ActionEquivalence.Expand(left));
    }
}
