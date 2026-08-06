using System.Text.Json;
using VedaAxis.Core;

namespace VedaAxis;

internal static class ExamplePlan
{
    private static readonly JsonSerializerOptions Options = new()
    {
        PropertyNameCaseInsensitive = true,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    };

    public static PlanSnapshot Create()
    {
        using var stream = typeof(ExamplePlan).Assembly.GetManifestResourceStream(
                               "VedaAxis.DefaultPlans.dmu-p1-p2-eight.json")
                           ?? throw new InvalidOperationException("DMU P1/P2 default plan resource is missing");
        return JsonSerializer.Deserialize<PlanSnapshot>(stream, Options)
               ?? throw new InvalidDataException("DMU P1/P2 default plan is empty");
    }
}
