namespace VedaAxis.Core;

public static class OverlaySafety
{
    public const int MaxHotbarSlotCount = 32;

    public static bool IsValidSlotCount(long slotCount) =>
        slotCount is > 0 and <= MaxHotbarSlotCount;

    public static float NormalizeOpacity(float opacity) =>
        float.IsFinite(opacity) ? Math.Clamp(opacity, 0.1f, 1f) : 0.72f;
}
