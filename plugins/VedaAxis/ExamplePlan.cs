using VedaAxis.Core;

namespace VedaAxis;

internal static class ExamplePlan
{
    public static PlanSnapshot Create()
    {
        var tracks = new[]
        {
            Track("MT", "主坦"), Track("ST", "副坦"), Track("H1", "治疗 1"), Track("H2", "治疗 2"),
            Track("D1", "输出 1"), Track("D2", "输出 2"), Track("D3", "输出 3"), Track("D4", "输出 4"),
        };
        var h2 = tracks.Single(track => track.Slot == "H2");
        var phaseId = Guid.Parse("4270bbba-f402-4cc4-bb27-0d566815dd0d");
        var firstMechanicId = Guid.Parse("0d80a50c-cd3a-4569-a7ce-4766612e3316");
        var secondMechanicId = Guid.Parse("a6504b58-cb5b-408a-866f-e659912be1d0");

        return new PlanSnapshot(
            "1.2", "0.1.5", Guid.NewGuid(), 1, Guid.NewGuid(), 1,
            Guid.Parse("9789ba9a-b761-4c44-b179-2e3e86ee0d3b"), 755, "O8S-POC", TrackMode.Eight,
            new PlanSource("IMPORTED", "https://na.finalfantasyxiv.com/lodestone/playguide/db/duty/28d9a03c886/", "POC_PENDING"),
            [],
            tracks,
            [
                Assignment(firstMechanicId, h2.TrackId, 24298, 2_000, 4_000, 8_000, 10_000),
                Assignment(secondMechanicId, h2.TrackId, 24310, 12_000, 14_000, 18_000, 20_000),
            ],
            [new TimelinePhase(phaseId, "O8S-P1", "P1", 0, "UNVERIFIED")],
            [
                new TimelineMechanic(firstMechanicId, null, "P1", "呼啸爆破", 10_000, 0, "RAIDWIDE", "MAGICAL", "全体", null, "UNVERIFIED"),
                new TimelineMechanic(secondMechanicId, null, "P1", "裁制之光", 20_000, 0, "RAIDWIDE", "MAGICAL", "全体", null, "UNVERIFIED"),
            ]);
    }

    private static ExecutionTrack Track(string slot, string name) =>
        new(Guid.NewGuid(), slot, new HashSet<uint>(), name);

    private static Assignment Assignment(
        Guid mechanicId, Guid trackId, uint actionId, long highlightAt, long earliestAt, long latestAt, long impactAt) =>
        new(Guid.NewGuid(), mechanicId, trackId, actionId, null,
            highlightAt, earliestAt, latestAt, impactAt, false,
            ConfirmationStrategy.ActionEffect, []);
}
