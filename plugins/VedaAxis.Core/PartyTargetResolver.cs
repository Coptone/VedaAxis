namespace VedaAxis.Core;

public sealed record PartyMemberSnapshot(int PartyIndex, uint EntityId, uint JobId, string DisplayName);

public sealed record PartyTargetResolution(
    bool Resolved,
    bool Manual,
    PartyMemberSnapshot? Member,
    string Reason);

public static class PartyTargetResolver
{
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

        if (targetTrack.AllowedJobIds.Count == 0)
        {
            return new PartyTargetResolution(false, false, null, "TARGET_TRACK_HAS_NO_JOB_CONSTRAINT");
        }

        var matches = partyMembers
            .Where(member => targetTrack.AllowedJobIds.Contains(member.JobId))
            .ToList();
        return matches.Count == 1
            ? new PartyTargetResolution(true, false, matches[0], "UNIQUE_JOB_MATCH")
            : new PartyTargetResolution(false, false, null,
                matches.Count == 0 ? "NO_JOB_MATCH" : "AMBIGUOUS_JOB_MATCH");
    }
}
