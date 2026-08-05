using System.Text.Json;
using VedaAxis.Core;

namespace VedaAxis;

internal sealed class PlanFileStore
{
    private readonly string path;
    private readonly JsonSerializerOptions options = new()
    {
        PropertyNameCaseInsensitive = true,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        WriteIndented = true,
    };

    public PlanFileStore(string configDirectory)
    {
        Directory.CreateDirectory(configDirectory);
        path = System.IO.Path.Combine(configDirectory, "active-plan.json");
    }

    public string Path => path;

    public PlanSnapshot LoadOrCreateExample()
    {
        if (!File.Exists(path))
        {
            var example = ExamplePlan.Create();
            File.WriteAllText(path, JsonSerializer.Serialize(example, options));
            return example;
        }

        var plan = JsonSerializer.Deserialize<PlanSnapshot>(File.ReadAllText(path), options)
                   ?? throw new InvalidDataException("active-plan.json 内容为空");
        var issues = PlanValidator.Validate(plan);
        if (issues.Count > 0)
        {
            throw new InvalidDataException(string.Join("；", issues));
        }
        return plan;
    }

    public void Save(PlanSnapshot plan)
    {
        var issues = PlanValidator.Validate(plan);
        if (issues.Count > 0)
        {
            throw new InvalidDataException(string.Join("；", issues));
        }
        File.WriteAllText(path, JsonSerializer.Serialize(plan, options));
    }
}
