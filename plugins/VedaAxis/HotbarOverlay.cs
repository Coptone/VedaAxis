using System.Numerics;
using Dalamud.Bindings.ImGui;
using Dalamud.Plugin.Services;
using FFXIVClientStructs.FFXIV.Client.Game;
using FFXIVClientStructs.FFXIV.Client.UI;
using FFXIVClientStructs.FFXIV.Component.GUI;
using VedaAxis.Core;

namespace VedaAxis;

internal sealed unsafe class HotbarOverlay
{
    private static readonly string[] AddonNames =
    [
        "_ActionBar", "_ActionBar01", "_ActionBar02", "_ActionBar03", "_ActionBar04",
        "_ActionBar05", "_ActionBar06", "_ActionBar07", "_ActionBar08", "_ActionBar09",
        "_ActionCross", "_ActionDoubleCrossL", "_ActionDoubleCrossR", "_ActionContents",
    ];

    private readonly IGameGui gameGui;

    public HotbarOverlay(IGameGui gameGui)
    {
        this.gameGui = gameGui;
    }

    public bool IsActionAvailable(uint actionId)
    {
        var manager = ActionManager.Instance();
        return manager != null && manager->GetActionStatus(ActionType.Action, actionId) == 0;
    }

    public bool HasVisibleSlot(uint actionId)
    {
        foreach (var addonName in AddonNames)
        {
            var addon = gameGui.GetAddonByName<AddonActionBarBase>(addonName);
            if (addon == null || !((AtkUnitBase*)addon)->IsVisible)
            {
                continue;
            }
            var slots = addon->ActionBarSlotVector.AsSpan();
            for (var index = 0; index < slots.Length; index++)
            {
                if ((uint)Math.Max(0, slots[index].ActionId) == actionId && slots[index].ComponentDragDrop != null)
                {
                    return true;
                }
            }
        }
        return false;
    }

    public void Draw(IReadOnlyList<AssignmentRuntime> assignments, long elapsedMs, float opacity)
    {
        var visibleStates = assignments
            .Where(item => item.ShouldDrawOverlay(elapsedMs))
            .GroupBy(item => item.Assignment.ActionId)
            .ToDictionary(group => group.Key, group => group.Last().State);
        if (visibleStates.Count == 0)
        {
            return;
        }

        var drawList = ImGui.GetForegroundDrawList();
        foreach (var addonName in AddonNames)
        {
            var addon = gameGui.GetAddonByName<AddonActionBarBase>(addonName);
            if (addon == null || !((AtkUnitBase*)addon)->IsVisible)
            {
                continue;
            }

            var slots = addon->ActionBarSlotVector.AsSpan();
            for (var index = 0; index < slots.Length; index++)
            {
                ref var slot = ref slots[index];
                if (slot.ActionId <= 0 || !visibleStates.TryGetValue((uint)slot.ActionId, out var state)
                    || slot.ComponentDragDrop == null)
                {
                    continue;
                }

                var ownerNode = slot.ComponentDragDrop->AtkComponentBase.OwnerNode;
                if (ownerNode == null)
                {
                    continue;
                }

                var node = &ownerNode->AtkResNode;
                if (!node->IsVisible())
                {
                    continue;
                }

                var start = new Vector2(node->ScreenX, node->ScreenY);
                var end = start + new Vector2(node->GetWidth(), node->GetHeight());
                var color = ColorFor(state, opacity);
                drawList.AddRectFilled(start, end, ImGui.GetColorU32(color), 5f);
                drawList.AddRect(start, end, ImGui.GetColorU32(color with { W = 1f }), 5f, ImDrawFlags.None, 3f);
            }
        }
    }

    private static Vector4 ColorFor(AssignmentState state, float opacity) => state switch
    {
        AssignmentState.Highlighting => new Vector4(0.17f, 0.83f, 0.91f, opacity * 0.42f),
        AssignmentState.Success => new Vector4(0.22f, 0.90f, 0.49f, opacity * 0.38f),
        AssignmentState.Early => new Vector4(0.97f, 0.72f, 0.20f, opacity * 0.45f),
        AssignmentState.Missed or AssignmentState.Late => new Vector4(0.98f, 0.25f, 0.28f, opacity * 0.48f),
        AssignmentState.Invalid => new Vector4(0.70f, 0.30f, 0.95f, opacity * 0.48f),
        _ => new Vector4(1f, 1f, 1f, opacity * 0.25f),
    };
}
