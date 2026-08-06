using System.Numerics;
using Dalamud.Bindings.ImGui;
using VedaAxis.Core;

namespace VedaAxis;

internal enum OverlayEmphasis
{
    Standard,
    Strong,
    Maximum,
}

internal static class OverlayPresentation
{
    public const string Standard = "STANDARD";
    public const string Strong = "STRONG";
    public const string Maximum = "MAXIMUM";

    public static OverlayEmphasis Parse(string? value) => value?.Trim().ToUpperInvariant() switch
    {
        Standard => OverlayEmphasis.Standard,
        Maximum => OverlayEmphasis.Maximum,
        _ => OverlayEmphasis.Strong,
    };

    public static string PersistedValue(OverlayEmphasis emphasis) => emphasis switch
    {
        OverlayEmphasis.Standard => Standard,
        OverlayEmphasis.Maximum => Maximum,
        _ => Strong,
    };

    public static string Label(OverlayEmphasis emphasis) => emphasis switch
    {
        OverlayEmphasis.Standard => "标准",
        OverlayEmphasis.Maximum => "极强",
        _ => "强化（推荐）",
    };

    public static void DrawFrame(
        ImDrawListPtr drawList,
        Vector2 start,
        Vector2 end,
        Vector4 color,
        AssignmentState state,
        OverlayEmphasis emphasis)
    {
        var pulse = state == AssignmentState.Highlighting
            ? 0.88f + 0.12f * (float)Math.Sin(ImGui.GetTime() * 7.5d)
            : 1f;
        var (fillMultiplier, outerPadding, borderThickness) = emphasis switch
        {
            OverlayEmphasis.Maximum => (1.78f, 5f, 5f),
            OverlayEmphasis.Strong => (1.45f, 3f, 4f),
            _ => (1f, 0f, 3f),
        };
        var filled = color with { W = Math.Clamp(color.W * fillMultiplier * pulse, 0f, 1f) };
        drawList.AddRectFilled(start, end, ImGui.GetColorU32(filled), 5f);

        if (outerPadding > 0f)
        {
            var glow = color with { W = Math.Clamp(color.W * 0.86f * pulse, 0f, 0.72f) };
            var offset = new Vector2(outerPadding);
            drawList.AddRect(start - offset, end + offset, ImGui.GetColorU32(glow), 7f, ImDrawFlags.None, 2f);
        }

        var edge = color with { W = 1f };
        drawList.AddRect(start, end, ImGui.GetColorU32(edge), 5f, ImDrawFlags.None, borderThickness);
        if (emphasis != OverlayEmphasis.Standard)
        {
            var inset = new Vector2(1.5f);
            var inner = new Vector4(1f, 1f, 1f, Math.Clamp(color.W * 1.15f * pulse, 0f, 0.82f));
            drawList.AddRect(start + inset, end - inset, ImGui.GetColorU32(inner), 3.5f, ImDrawFlags.None, 1f);
        }
    }
}
