using VedaAxis.Core;

namespace VedaAxis.Core.Tests;

public sealed class PartyTargetResolverTests
{
    [Fact]
    public void ResolvesAUniqueJobWithoutDependingOnPartyOrder()
    {
        var track = new ExecutionTrack(Guid.NewGuid(), "MT", new HashSet<uint> { 21 }, "主坦");
        var party = new[]
        {
            new PartyMemberSnapshot(0, 100, 40, "Healer"),
            new PartyMemberSnapshot(6, 200, 21, "Tank"),
        };

        var result = PartyTargetResolver.Resolve(track, party, new Dictionary<Guid, uint>());

        Assert.True(result.Resolved);
        Assert.False(result.Manual);
        Assert.Equal(200u, result.Member?.EntityId);
        Assert.Equal(6, result.Member?.PartyIndex);
    }

    [Fact]
    public void RequiresManualMappingWhenTheJobMatchIsAmbiguous()
    {
        var track = new ExecutionTrack(Guid.NewGuid(), "D1", new HashSet<uint> { 20, 22 }, "近战");
        var party = new[]
        {
            new PartyMemberSnapshot(1, 100, 20, "Melee One"),
            new PartyMemberSnapshot(5, 200, 22, "Melee Two"),
        };

        var unresolved = PartyTargetResolver.Resolve(track, party, new Dictionary<Guid, uint>());
        var resolved = PartyTargetResolver.Resolve(
            track, party, new Dictionary<Guid, uint> { [track.TrackId] = 200 });

        Assert.False(unresolved.Resolved);
        Assert.Equal("AMBIGUOUS_JOB_MATCH", unresolved.Reason);
        Assert.True(resolved.Resolved);
        Assert.True(resolved.Manual);
        Assert.Equal("Melee Two", resolved.Member?.DisplayName);
    }

    [Fact]
    public void IgnoresAStaleManualEntityAndFallsBackToAutomaticMatching()
    {
        var track = new ExecutionTrack(Guid.NewGuid(), "ST", new HashSet<uint> { 37 }, "副坦");
        var party = new[] { new PartyMemberSnapshot(3, 300, 37, "Gunbreaker") };

        var result = PartyTargetResolver.Resolve(
            track, party, new Dictionary<Guid, uint> { [track.TrackId] = 999 });

        Assert.True(result.Resolved);
        Assert.False(result.Manual);
        Assert.Equal(300u, result.Member?.EntityId);
    }

    [Fact]
    public void FallsBackToSlotRoleWhenThePlannedJobIsNotInTheParty()
    {
        var track = new ExecutionTrack(Guid.NewGuid(), "MT", new HashSet<uint> { 21 }, "主坦 · 战士");
        var party = new[]
        {
            new PartyMemberSnapshot(0, 100, 40, "Sage"),
            new PartyMemberSnapshot(1, 200, 32, "Dark Knight"),
        };

        var result = PartyTargetResolver.Resolve(track, party, new Dictionary<Guid, uint>());

        Assert.True(result.Resolved);
        Assert.False(result.Manual);
        Assert.Equal("UNIQUE_SLOT_ROLE_MATCH", result.Reason);
        Assert.Equal(200u, result.Member?.EntityId);
    }

    [Fact]
    public void RequiresManualMappingWhenTheSlotRoleFallbackIsAmbiguous()
    {
        var track = new ExecutionTrack(Guid.NewGuid(), "ST", new HashSet<uint> { 37 }, "副坦 · 绝枪战士");
        var party = new[]
        {
            new PartyMemberSnapshot(0, 100, 19, "Paladin"),
            new PartyMemberSnapshot(1, 200, 32, "Dark Knight"),
            new PartyMemberSnapshot(2, 300, 40, "Sage"),
        };

        var result = PartyTargetResolver.Resolve(track, party, new Dictionary<Guid, uint>());

        Assert.False(result.Resolved);
        Assert.Equal("AMBIGUOUS_SLOT_ROLE_MATCH", result.Reason);
    }

    [Fact]
    public void ReportsMissingPartyMembersSeparately()
    {
        var track = new ExecutionTrack(Guid.NewGuid(), "MT", new HashSet<uint> { 21 }, "主坦");

        var result = PartyTargetResolver.Resolve(track, [], new Dictionary<Guid, uint>());

        Assert.False(result.Resolved);
        Assert.Equal("NO_PARTY_MEMBERS", result.Reason);
    }
}
