using System.Text.Json.Serialization;

namespace VedaAxis.Core;

[JsonConverter(typeof(JsonStringEnumConverter))]
public enum TrackMode
{
    [JsonStringEnumMemberName("FOUR")]
    Four,
    [JsonStringEnumMemberName("EIGHT")]
    Eight,
}

[JsonConverter(typeof(JsonStringEnumConverter))]
public enum ConfirmationStrategy
{
    [JsonStringEnumMemberName("ACTION_EFFECT")]
    ActionEffect,
    [JsonStringEnumMemberName("STATUS_APPLY")]
    StatusApply,
    [JsonStringEnumMemberName("COOLDOWN_CHANGE")]
    CooldownChange,
    [JsonStringEnumMemberName("COMPOSITE")]
    Composite,
}

public sealed record PlanSnapshot(
    string SchemaVersion,
    string MinimumPluginVersion,
    Guid PlanId,
    int PlanVersion,
    Guid TimelineId,
    int TimelineVersion,
    Guid EncounterId,
    string StrategyTag,
    TrackMode TrackMode,
    PlanSource Source,
    IReadOnlyList<TimelineAnchor> Anchors,
    IReadOnlyList<ExecutionTrack> Tracks,
    IReadOnlyList<Assignment> Assignments);

public sealed record PlanSource(string Kind, string? Reference, string Confidence);

[JsonConverter(typeof(JsonStringEnumConverter))]
public enum AnchorKind
{
    [JsonStringEnumMemberName("CAST_START")]
    CastStart,
    [JsonStringEnumMemberName("ACTION_EFFECT")]
    ActionEffect,
    [JsonStringEnumMemberName("STATUS_GAIN")]
    StatusGain,
}

public sealed record TimelineAnchor(
    Guid AnchorId,
    uint ActionId,
    int Occurrence,
    long PlannedAtMs,
    long OffsetMs,
    string Phase,
    AnchorKind Kind);

public sealed record ExecutionTrack(
    Guid TrackId,
    string Slot,
    IReadOnlyCollection<uint> AllowedJobIds,
    string? DisplayName);

public sealed record Assignment(
    Guid AssignmentId,
    Guid MechanicId,
    Guid TrackId,
    uint ActionId,
    Guid? AnchorId,
    long HighlightAtMs,
    long EarliestUseAtMs,
    long LatestUseAtMs,
    long ImpactAtMs,
    bool Locked,
    ConfirmationStrategy ConfirmationStrategy,
    IReadOnlyList<Fallback> Fallbacks);

public sealed record Fallback(Guid TrackId, uint ActionId);
