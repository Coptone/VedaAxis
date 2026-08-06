using System.Numerics;
using Dalamud.Bindings.ImGui;
using Dalamud.Plugin.Services;
using FFXIVClientStructs.FFXIV.Client.UI;
using FFXIVClientStructs.FFXIV.Component.GUI;
using VedaAxis.Core;

namespace VedaAxis;

internal sealed record PartyTargetVisual(PartyMemberSnapshot Member, AssignmentState State);

internal sealed unsafe class PartyListOverlay
{
    private readonly IGameGui gameGui;
    private readonly IPluginLog log;
    private bool invalidPartyListLogged;

    public PartyListOverlay(IGameGui gameGui, IPluginLog log)
    {
        this.gameGui = gameGui;
        this.log = log;
    }

    public void Draw(IReadOnlyList<PartyTargetVisual> targets, float opacity)
    {
        if (targets.Count == 0)
        {
            return;
        }

        var addon = gameGui.GetAddonByName<AddonPartyList>("_PartyList");
        if (addon == null || !((AtkUnitBase*)addon)->IsVisible)
        {
            return;
        }

        try
        {
            var nativeMembers = addon->PartyMembers;
            var memberCount = Math.Clamp(addon->MemberCount, 0, nativeMembers.Length);
            var drawList = ImGui.GetForegroundDrawList();
            var unmatched = new List<PartyTargetVisual>(targets);

            for (var index = 0; index < memberCount; index++)
            {
                ref var nativeMember = ref nativeMembers[index];
                var nativeName = nativeMember.Name == null
                    ? string.Empty
                    : nativeMember.Name->NodeText.ToString();
                var target = unmatched.FirstOrDefault(item => NamesMatch(nativeName, item.Member.DisplayName));
                target ??= unmatched.FirstOrDefault(item => item.Member.PartyIndex == index);
                if (target is null || !TryGetNode(ref nativeMember, out var node))
                {
                    continue;
                }

                var start = new Vector2(node->ScreenX, node->ScreenY);
                var end = start + new Vector2(node->GetWidth(), node->GetHeight());
                if (!float.IsFinite(start.X) || !float.IsFinite(start.Y)
                    || !float.IsFinite(end.X) || !float.IsFinite(end.Y)
                    || end.X <= start.X || end.Y <= start.Y)
                {
                    continue;
                }

                var color = ColorFor(target.State, opacity);
                drawList.AddRectFilled(start, end, ImGui.GetColorU32(color), 4f);
                drawList.AddRect(start, end, ImGui.GetColorU32(color with { W = 1f }), 4f, ImDrawFlags.None, 3f);
                unmatched.Remove(target);
            }
        }
        catch (Exception exception) when (exception is OverflowException or ArgumentOutOfRangeException or NullReferenceException)
        {
            if (!invalidPartyListLogged)
            {
                invalidPartyListLogged = true;
                log.Warning(exception, "Skipping invalid native party-list geometry");
            }
        }
    }

    private static bool TryGetNode(ref AddonPartyList.PartyListMemberStruct member, out AtkResNode* node)
    {
        if (member.Collision != null)
        {
            node = &member.Collision->AtkResNode;
            return node->IsVisible();
        }
        if (member.PartyMemberComponent != null && member.PartyMemberComponent->OwnerNode != null)
        {
            node = &member.PartyMemberComponent->OwnerNode->AtkResNode;
            return node->IsVisible();
        }
        node = null;
        return false;
    }

    private static bool NamesMatch(string nativeName, string partyName)
    {
        var left = NormalizeName(nativeName);
        var right = NormalizeName(partyName);
        return left.Length > 0 && right.Length > 0 && (left == right || left.Contains(right) || right.Contains(left));
    }

    private static string NormalizeName(string value) =>
        new(value.Where(char.IsLetterOrDigit).Select(char.ToLowerInvariant).ToArray());

    private static Vector4 ColorFor(AssignmentState state, float opacity) => state switch
    {
        AssignmentState.Missed or AssignmentState.Late => new Vector4(0.98f, 0.25f, 0.28f, opacity * 0.40f),
        AssignmentState.Success => new Vector4(0.22f, 0.90f, 0.49f, opacity * 0.30f),
        _ => new Vector4(1f, 0.72f, 0.20f, opacity * 0.38f),
    };
}
