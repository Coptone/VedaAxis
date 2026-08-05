namespace VedaAxis.Core;

public static class PlanValidator
{
    private static readonly string[] FourSlots = ["T1", "H1", "D1", "D2"];
    private static readonly string[] EightSlots = ["MT", "ST", "H1", "H2", "D1", "D2", "D3", "D4"];

    public static IReadOnlyList<string> Validate(PlanSnapshot plan)
    {
        List<string> issues = [];
        if (plan.SchemaVersion != "1.0")
        {
            issues.Add($"不支持的计划结构版本：{plan.SchemaVersion}");
        }

        var expectedSlots = plan.TrackMode == TrackMode.Four ? FourSlots : EightSlots;
        var actualSlots = plan.Tracks.Select(track => track.Slot).ToArray();
        if (!actualSlots.SequenceEqual(expectedSlots))
        {
            issues.Add($"{plan.TrackMode} 轨道必须依次为 {string.Join('/', expectedSlots)}");
        }

        var trackIds = plan.Tracks.Select(track => track.TrackId).ToHashSet();
        var anchorIds = plan.Anchors.Select(anchor => anchor.AnchorId).ToHashSet();
        if (anchorIds.Count != plan.Anchors.Count)
        {
            issues.Add("时间轴锚点 ID 重复");
        }
        if (plan.Anchors.GroupBy(anchor => (anchor.ActionId, anchor.Kind, anchor.Occurrence)).Any(group => group.Count() > 1))
        {
            issues.Add("同一锚点事件的发生序号重复");
        }
        foreach (var assignment in plan.Assignments)
        {
            if (!trackIds.Contains(assignment.TrackId))
            {
                issues.Add($"任务 {assignment.AssignmentId} 引用了不存在的轨道");
            }
            if (assignment.AnchorId is { } anchorId && !anchorIds.Contains(anchorId))
            {
                issues.Add($"任务 {assignment.AssignmentId} 引用了不存在的锚点");
            }
            if (assignment.HighlightAtMs > assignment.EarliestUseAtMs
                || assignment.EarliestUseAtMs > assignment.LatestUseAtMs
                || assignment.LatestUseAtMs > assignment.ImpactAtMs)
            {
                issues.Add($"任务 {assignment.AssignmentId} 的时间窗口顺序无效");
            }
        }

        return issues;
    }
}
