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

        return new PlanSnapshot(
            "1.1", "0.1.4", Guid.NewGuid(), 1, Guid.NewGuid(), 1,
            Guid.Parse("9789ba9a-b761-4c44-b179-2e3e86ee0d3b"), 755, "O8S-POC", TrackMode.Eight,
            new PlanSource("IMPORTED", "https://na.finalfantasyxiv.com/lodestone/playguide/db/duty/28d9a03c886/", "POC_PENDING"),
            [],
            tracks,
            [
                Assignment(h2.TrackId, 24298, 2_000, 4_000, 8_000, 10_000),
                Assignment(h2.TrackId, 24310, 12_000, 14_000, 18_000, 20_000),
            ]);
    }

    private static ExecutionTrack Track(string slot, string name) =>
        new(Guid.NewGuid(), slot, new HashSet<uint>(), name);

    private static Assignment Assignment(
        Guid trackId, uint actionId, long highlightAt, long earliestAt, long latestAt, long impactAt) =>
        new(Guid.NewGuid(), Guid.NewGuid(), trackId, actionId, null,
            highlightAt, earliestAt, latestAt, impactAt, false,
            ConfirmationStrategy.ActionEffect, []);
}
