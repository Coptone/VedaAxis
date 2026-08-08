using System.Text.Json;
using VedaAxis.Core;

namespace VedaAxis;

internal sealed class ExecutionUploadQueue
{
    private readonly string directory;
    private readonly string failedDirectory;
    private readonly JsonSerializerOptions jsonOptions = new(JsonSerializerDefaults.Web) { WriteIndented = true };

    public ExecutionUploadQueue(string configDirectory)
    {
        directory = System.IO.Path.Combine(configDirectory, "pending-executions");
        failedDirectory = System.IO.Path.Combine(configDirectory, "failed-executions");
        Directory.CreateDirectory(directory);
        Directory.CreateDirectory(failedDirectory);
    }

    public int PendingCount => Directory.EnumerateFiles(directory, "*.json").Count();
    public int FailedCount => Directory.EnumerateFiles(failedDirectory, "*.json").Count();

    public void Enqueue(PlanRuntime runtime, DateTimeOffset startedAt, DateTimeOffset endedAt, string result)
    {
        if (runtime.Plan is null)
        {
            return;
        }

        var batch = new FightExecutionBatch(
            "1.0",
            Guid.NewGuid(),
            runtime.Plan.PlanId,
            runtime.Plan.PlanVersion,
            startedAt,
            endedAt,
            result,
            runtime.Assignments.Select(ToExecution).ToArray());
        var path = System.IO.Path.Combine(directory, $"{batch.FightExecutionId}.json");
        File.WriteAllText(path, JsonSerializer.Serialize(batch, jsonOptions));
    }

    public IReadOnlyList<PendingExecution> ReadPending()
    {
        return Directory.EnumerateFiles(directory, "*.json")
            .OrderBy(path => path, StringComparer.Ordinal)
            .Select(path => new PendingExecution(
                path,
                JsonSerializer.Deserialize<FightExecutionBatch>(File.ReadAllText(path), jsonOptions)
                ?? throw new InvalidDataException($"执行批次为空：{path}")))
            .ToArray();
    }

    public void Complete(PendingExecution pending)
    {
        File.Delete(pending.Path);
    }

    public void Fail(PendingExecution pending, string reason)
    {
        var fileName = System.IO.Path.GetFileName(pending.Path);
        var targetPath = System.IO.Path.Combine(failedDirectory, fileName);
        if (File.Exists(targetPath))
        {
            targetPath = System.IO.Path.Combine(
                failedDirectory,
                $"{System.IO.Path.GetFileNameWithoutExtension(fileName)}-{DateTimeOffset.Now:yyyyMMddHHmmss}.json");
        }

        File.Move(pending.Path, targetPath);
        File.WriteAllText(
            System.IO.Path.ChangeExtension(targetPath, ".txt"),
            $"VedaAxis execution upload was quarantined at {DateTimeOffset.Now:O}.{Environment.NewLine}{reason}{Environment.NewLine}");
    }

    private static AssignmentExecution ToExecution(AssignmentRuntime runtime)
    {
        var state = runtime.State switch
        {
            AssignmentState.Success => "SUCCESS",
            AssignmentState.Early => "EARLY",
            AssignmentState.Late => "LATE",
            AssignmentState.Invalid => "INVALID",
            AssignmentState.Highlighting or AssignmentState.Missed => "MISSED",
            _ => "CANCELLED",
        };
        return new AssignmentExecution(
            runtime.Assignment.AssignmentId,
            state,
            runtime.ObservedAtMs is { } observed ? observed - runtime.Assignment.ImpactAtMs : null,
            runtime.ObservedAtMs is not null ? "ACTION_EFFECT" : "NONE",
            runtime.AvailableAtHighlight ?? false,
            runtime.Reason);
    }
}

internal sealed record PendingExecution(string Path, FightExecutionBatch Batch);

internal sealed record FightExecutionBatch(
    string SchemaVersion,
    Guid FightExecutionId,
    Guid PlanId,
    int PlanVersion,
    DateTimeOffset StartedAt,
    DateTimeOffset EndedAt,
    string Result,
    IReadOnlyList<AssignmentExecution> Assignments);

internal sealed record AssignmentExecution(
    Guid AssignmentId,
    string State,
    long? ObservedOffsetMs,
    string Confirmation,
    bool AvailableAtHighlight,
    string? Reason);
