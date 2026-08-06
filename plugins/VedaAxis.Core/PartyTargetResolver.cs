namespace VedaAxis.Core;

public sealed record PartyMemberSnapshot(int PartyIndex, uint EntityId, uint JobId, string DisplayName);

public sealed record PartyTargetResolution(
    bool Resolved,
    bool Manual,
    PartyMemberSnapshot? Member,
    string Reason);

public static class PartyTargetResolver
{
    private static readonly HashSet<uint> TankJobs = [1, 3, 19, 21, 32, 37];
    private static readonly HashSet<uint> HealerJobs = [6, 24, 28, 33, 40];
    private static readonly HashSet<uint> MeleeJobs = [2, 4, 20, 22, 29, 30, 34, 39, 41];
    private static readonly HashSet<uint> PhysicalRangedJobs = [5, 23, 31, 38];
    private static readonly HashSet<uint> CasterJobs = [7, 26, 27, 25, 35, 36, 42];

    public static PartyTargetResolution Resolve(
        ExecutionTrack targetTrack,
        IReadOnlyList<PartyMemberSnapshot> partyMembers,
        IReadOnlyDictionary<Guid, uint> manualMappings)
    {
        if (manualMappings.TryGetValue(targetTrack.TrackId, out var manualEntityId))
        {
            var manualMember = partyMembers.FirstOrDefault(member => member.EntityId == manualEntityId);
            if (manualMember is not null)
            {
                return new PartyTargetResolution(true, true, manualMember, "MANUAL");
            }
        }

        if (partyMembers.Count == 0)
        {
            return new PartyTargetResolution(false, false, null, "NO_PARTY_MEMBERS");
        }

        if (targetTrack.AllowedJobIds.Count == 0)
        {
            return ResolveBySlotRole(targetTrack, partyMembers)
                   ?? new PartyTargetResolution(false, false, null, "TARGET_TRACK_HAS_NO_JOB_CONSTRAINT");
        }

        var matches = partyMembers
            .Where(member => targetTrack.AllowedJobIds.Contains(member.JobId))
            .ToList();
        if (matches.Count == 1)
        {
            return new PartyTargetResolution(true, false, matches[0], "UNIQUE_JOB_MATCH");
        }

        if (matches.Count > 1)
        {
            return new PartyTargetResolution(false, false, null, "AMBIGUOUS_JOB_MATCH");
        }

        return ResolveBySlotRole(targetTrack, partyMembers)
               ?? new PartyTargetResolution(false, false, null, "NO_JOB_MATCH");
    }

    private static PartyTargetResolution? ResolveBySlotRole(
        ExecutionTrack targetTrack,
        IReadOnlyList<PartyMemberSnapshot> partyMembers)
    {
        var roleJobs = RoleJobsForSlot(targetTrack.Slot);
        if (roleJobs is null)
        {
            return null;
        }

        var matches = partyMembers
            .Where(member => roleJobs.Contains(member.JobId))
            .ToList();
        return matches.Count == 1
            ? new PartyTargetResolution(true, false, matches[0], "UNIQUE_SLOT_ROLE_MATCH")
            : new PartyTargetResolution(false, false, null,
                matches.Count == 0 ? "NO_SLOT_ROLE_MATCH" : "AMBIGUOUS_SLOT_ROLE_MATCH");
    }

    private static HashSet<uint>? RoleJobsForSlot(string slot)
    {
        return slot.ToUpperInvariant() switch
        {
            "T1" or "MT" or "ST" => TankJobs,
            "H1" or "H2" => HealerJobs,
            "D1" or "D2" => MeleeJobs,
            "D3" => PhysicalRangedJobs,
            "D4" => CasterJobs,
            _ => null,
        };
    }
}
