using System.Globalization;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using Lumina;
using Lumina.Data;
using Lumina.Excel.Sheets;
using GameAction = Lumina.Excel.Sheets.Action;

var options = Options.Parse(args);
if (options.ShowHelp)
{
    Options.PrintHelp();
    return 0;
}

if (options.SqpackPath is null)
{
    Console.Error.WriteLine("error: missing --sqpack <path>");
    Options.PrintHelp();
    return 2;
}

string sqpackPath;
try
{
    sqpackPath = ResolveSqpackPath(options.SqpackPath);
}
catch (Exception exception)
{
    Console.Error.WriteLine($"error: {exception.Message}");
    return 1;
}
var outputDir = Path.GetFullPath(options.OutputDir ?? Path.Combine("artifacts", "cn-action-names"));
Directory.CreateDirectory(outputDir);

var metadata = new Dictionary<uint, ActionMetadata>();
foreach (var idsFile in options.IdsFiles)
{
    IEnumerable<ActionMetadata> entries;
    try
    {
        entries = ReadActionMetadata(idsFile).ToList();
    }
    catch (Exception exception)
    {
        Console.Error.WriteLine($"error: {exception.Message}");
        return 1;
    }

    foreach (var entry in entries)
    {
        if (!metadata.TryGetValue(entry.ActionId, out var existing))
        {
            metadata[entry.ActionId] = entry;
            continue;
        }

        metadata[entry.ActionId] = existing.Merge(entry);
    }
}

foreach (var id in options.DirectIds)
{
    metadata.TryAdd(id, new ActionMetadata(id, null, null, null));
}

if (metadata.Count == 0)
{
    Console.Error.WriteLine("error: no action ids found. Use --ids or --ids-file.");
    return 2;
}

Language language;
try
{
    language = ParseLanguage(options.Language ?? "ChineseSimplified");
}
catch (Exception exception)
{
    Console.Error.WriteLine($"error: {exception.Message}");
    return 2;
}

GameData gameData;
try
{
    gameData = new GameData(sqpackPath);
}
catch (Exception exception)
{
    Console.Error.WriteLine($"error: unable to open game data: {exception.Message}");
    return 1;
}
var cnSheet = gameData.GetExcelSheet<GameAction>(language);
if (cnSheet is null)
{
    Console.Error.WriteLine($"error: unable to load Action sheet for language {language}.");
    return 1;
}

var englishSheet = TryGetSheet(gameData, Language.English);

var rows = metadata.Values
    .OrderBy(item => item.Phase ?? "")
    .ThenBy(item => item.ActionId)
    .Select(item => ExtractAction(item, cnSheet, englishSheet))
    .ToList();

var generatedAt = DateTimeOffset.Now;
var stamp = generatedAt.ToString("yyyyMMdd-HHmmss", CultureInfo.InvariantCulture);
var jsonPath = Path.Combine(outputDir, $"vedaaxis-cn-actions-{stamp}.json");
var csvPath = Path.Combine(outputDir, $"vedaaxis-cn-actions-{stamp}.csv");

var payload = new
{
    generatedAt = generatedAt.ToString("O", CultureInfo.InvariantCulture),
    source = "local-game-sqpack",
    language = language.ToString(),
    count = rows.Count,
    missingCount = rows.Count(row => !row.Found),
    rsvCount = rows.Count(row => row.IsRsv),
    rows,
};

var jsonOptions = new JsonSerializerOptions
{
    WriteIndented = true,
    Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
};
File.WriteAllText(jsonPath, JsonSerializer.Serialize(payload, jsonOptions), new UTF8Encoding(false));
WriteCsv(csvPath, rows);

Console.WriteLine($"sqpack: {sqpackPath}");
Console.WriteLine($"language: {language}");
Console.WriteLine($"actions: {rows.Count}, missing: {payload.missingCount}, rsv: {payload.rsvCount}");
Console.WriteLine($"json: {jsonPath}");
Console.WriteLine($"csv : {csvPath}");

if (payload.rsvCount > 0)
{
    Console.WriteLine("note: Some names are still RSV placeholders. Send the output back so the RSV resolver can be added next.");
}

return 0;

static string ResolveSqpackPath(string input)
{
    var expanded = Environment.ExpandEnvironmentVariables(input);
    var full = Path.GetFullPath(expanded);
    var candidates = new[]
    {
        full,
        Path.Combine(full, "sqpack"),
        Path.Combine(full, "game", "sqpack"),
    };

    foreach (var candidate in candidates.Distinct(StringComparer.OrdinalIgnoreCase))
    {
        if (LooksLikeSqpack(candidate))
        {
            return candidate;
        }
    }

    throw new DirectoryNotFoundException(
        $"Could not locate FFXIV sqpack under '{input}'. Expected a folder containing ffxiv\\0a0000.win32.index.");
}

static bool LooksLikeSqpack(string path)
{
    return Directory.Exists(path)
           && (File.Exists(Path.Combine(path, "ffxiv", "0a0000.win32.index"))
               || File.Exists(Path.Combine(path, "ffxiv", "0a0000.win32.index2")));
}

static Language ParseLanguage(string value)
{
    if (Enum.TryParse<Language>(value, ignoreCase: true, out var language))
    {
        return language;
    }

    return value.Trim().ToLowerInvariant() switch
    {
        "chs" or "zh" or "zh-cn" or "cn" => Language.ChineseSimplified,
        "cht" or "zh-tw" or "tc" => Language.ChineseTraditional,
        "en" => Language.English,
        "ja" or "jp" => Language.Japanese,
        "de" => Language.German,
        "fr" => Language.French,
        "ko" or "kr" => Language.Korean,
        _ => throw new ArgumentException($"Unsupported language '{value}'."),
    };
}

static Lumina.Excel.ExcelSheet<GameAction>? TryGetSheet(GameData gameData, Language language)
{
    try
    {
        return gameData.GetExcelSheet<GameAction>(language);
    }
    catch
    {
        return null;
    }
}

static ExtractedAction ExtractAction(
    ActionMetadata metadata,
    Lumina.Excel.ExcelSheet<GameAction> cnSheet,
    Lumina.Excel.ExcelSheet<GameAction>? englishSheet)
{
    var row = cnSheet.GetRowOrDefault(metadata.ActionId);
    var englishRow = englishSheet?.GetRowOrDefault(metadata.ActionId);
    if (row is null)
    {
        return new ExtractedAction(
            metadata.ActionId,
            metadata.Phase,
            metadata.EnglishName,
            metadata.Notes,
            false,
            null,
            null,
            false,
            null,
            englishRow?.Name.ExtractText(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    }

    var action = row.Value;
    var rawName = action.Name.ExtractText().Trim();
    var isRsv = rawName.StartsWith("_rsv_", StringComparison.OrdinalIgnoreCase);
    var resolvedName = isRsv ? null : rawName;
    var englishName = englishRow?.Name.ExtractText().Trim();

    var attackType = action.AttackType.ValueNullable;
    var actionCategory = action.ActionCategory.ValueNullable;
    var classJob = action.ClassJob.ValueNullable;

    return new ExtractedAction(
        metadata.ActionId,
        metadata.Phase,
        metadata.EnglishName,
        metadata.Notes,
        true,
        rawName,
        resolvedName,
        isRsv,
        isRsv ? rawName : null,
        string.IsNullOrWhiteSpace(englishName) ? null : englishName,
        action.Icon,
        action.AttackType.RowId == 0 ? null : action.AttackType.RowId,
        attackType?.Name.ExtractText().Trim(),
        action.ActionCategory.RowId == 0 ? null : action.ActionCategory.RowId,
        actionCategory?.Name.ExtractText().Trim(),
        action.ClassJob.RowId == 0 ? null : action.ClassJob.RowId,
        classJob?.Name.ExtractText().Trim(),
        action.CastType,
        action.Range,
        action.EffectRange);
}

static IEnumerable<ActionMetadata> ReadActionMetadata(string inputPath)
{
    var path = Path.GetFullPath(Environment.ExpandEnvironmentVariables(inputPath));
    if (!File.Exists(path))
    {
        throw new FileNotFoundException($"Action id file not found: {path}", path);
    }

    var text = File.ReadAllText(path, Encoding.UTF8);
    var extension = Path.GetExtension(path).ToLowerInvariant();
    if (extension == ".json")
    {
        foreach (var id in ReadActionIdsFromJson(text))
        {
            yield return new ActionMetadata(id, null, null, $"source file: {Path.GetFileName(path)}");
        }

        yield break;
    }

    if (extension == ".csv")
    {
        foreach (var entry in ReadActionMetadataFromCsv(text))
        {
            yield return entry;
        }

        yield break;
    }

    foreach (var id in ReadActionIdsFromText(text))
    {
        yield return new ActionMetadata(id, null, null, $"source file: {Path.GetFileName(path)}");
    }
}

static IEnumerable<uint> ReadActionIdsFromJson(string text)
{
    var root = JsonNode.Parse(text);
    if (root is null)
    {
        yield break;
    }

    foreach (var id in WalkJson(root, null))
    {
        yield return id;
    }
}

static IEnumerable<uint> WalkJson(JsonNode node, string? key)
{
    if (node is JsonValue value)
    {
        if ((key == "actionId" || key == "actionIds") && value.TryGetValue<uint>(out var id))
        {
            yield return id;
        }

        yield break;
    }

    if (node is JsonArray array)
    {
        foreach (var child in array)
        {
            if (child is null)
            {
                continue;
            }

            foreach (var id in WalkJson(child, key))
            {
                yield return id;
            }
        }

        yield break;
    }

    if (node is JsonObject obj)
    {
        foreach (var (childKey, child) in obj)
        {
            if (child is null)
            {
                continue;
            }

            foreach (var id in WalkJson(child, childKey))
            {
                yield return id;
            }
        }
    }
}

static IEnumerable<ActionMetadata> ReadActionMetadataFromCsv(string text)
{
    var lines = text.Split(["\r\n", "\n"], StringSplitOptions.RemoveEmptyEntries);
    if (lines.Length == 0)
    {
        yield break;
    }

    var headers = SplitCsvLine(lines[0]).Select(item => item.Trim()).ToList();
    var actionIdIndex = headers.FindIndex(item => item.Equals("actionId", StringComparison.OrdinalIgnoreCase));
    if (actionIdIndex < 0)
    {
        foreach (var id in ReadActionIdsFromText(text))
        {
            yield return new ActionMetadata(id, null, null, null);
        }

        yield break;
    }

    var phaseIndex = headers.FindIndex(item => item.Equals("phase", StringComparison.OrdinalIgnoreCase));
    var englishIndex = headers.FindIndex(item => item.Equals("englishName", StringComparison.OrdinalIgnoreCase));
    var notesIndex = headers.FindIndex(item => item.Equals("notes", StringComparison.OrdinalIgnoreCase));

    foreach (var line in lines.Skip(1))
    {
        var fields = SplitCsvLine(line);
        if (actionIdIndex >= fields.Count || !uint.TryParse(fields[actionIdIndex], NumberStyles.Integer, CultureInfo.InvariantCulture, out var id))
        {
            continue;
        }

        yield return new ActionMetadata(
            id,
            GetField(fields, phaseIndex),
            GetField(fields, englishIndex),
            GetField(fields, notesIndex));
    }
}

static string? GetField(IReadOnlyList<string> fields, int index)
{
    if (index < 0 || index >= fields.Count)
    {
        return null;
    }

    var value = fields[index].Trim();
    return string.IsNullOrWhiteSpace(value) ? null : value;
}

static IEnumerable<uint> ReadActionIdsFromText(string text)
{
    foreach (var match in System.Text.RegularExpressions.Regex.Matches(text, @"\b\d{2,6}\b").Cast<System.Text.RegularExpressions.Match>())
    {
        if (uint.TryParse(match.Value, NumberStyles.Integer, CultureInfo.InvariantCulture, out var id))
        {
            yield return id;
        }
    }
}

static List<string> SplitCsvLine(string line)
{
    var result = new List<string>();
    var builder = new StringBuilder();
    var inQuotes = false;

    for (var i = 0; i < line.Length; i++)
    {
        var ch = line[i];
        if (ch == '"')
        {
            if (inQuotes && i + 1 < line.Length && line[i + 1] == '"')
            {
                builder.Append('"');
                i++;
            }
            else
            {
                inQuotes = !inQuotes;
            }

            continue;
        }

        if (ch == ',' && !inQuotes)
        {
            result.Add(builder.ToString());
            builder.Clear();
            continue;
        }

        builder.Append(ch);
    }

    result.Add(builder.ToString());
    return result;
}

static void WriteCsv(string path, IReadOnlyList<ExtractedAction> rows)
{
    var builder = new StringBuilder();
    builder.AppendLine("actionId,phase,knownEnglishName,found,name,rawName,isRsv,englishNameFromClient,icon,attackTypeId,attackTypeName,actionCategoryId,actionCategoryName,classJobId,classJobName,castType,range,effectRange,notes");
    foreach (var row in rows)
    {
        var values = new object?[]
        {
            row.ActionId,
            row.Phase,
            row.KnownEnglishName,
            row.Found,
            row.Name,
            row.RawName,
            row.IsRsv,
            row.EnglishNameFromClient,
            row.Icon,
            row.AttackTypeId,
            row.AttackTypeName,
            row.ActionCategoryId,
            row.ActionCategoryName,
            row.ClassJobId,
            row.ClassJobName,
            row.CastType,
            row.Range,
            row.EffectRange,
            row.Notes,
        };
        builder.AppendLine(string.Join(",", values.Select(EscapeCsv)));
    }

    File.WriteAllText(path, builder.ToString(), new UTF8Encoding(true));
}

static string EscapeCsv(object? value)
{
    if (value is null)
    {
        return "";
    }

    var text = Convert.ToString(value, CultureInfo.InvariantCulture) ?? "";
    if (text.Contains('"') || text.Contains(',') || text.Contains('\n') || text.Contains('\r'))
    {
        return "\"" + text.Replace("\"", "\"\"") + "\"";
    }

    return text;
}

internal sealed record Options(
    string? SqpackPath,
    string? OutputDir,
    string? Language,
    IReadOnlyList<string> IdsFiles,
    IReadOnlyList<uint> DirectIds,
    bool ShowHelp)
{
    public static Options Parse(string[] args)
    {
        string? sqpack = null;
        string? output = null;
        string? language = "ChineseSimplified";
        var files = new List<string>();
        var ids = new List<uint>();
        var showHelp = false;

        for (var i = 0; i < args.Length; i++)
        {
            var arg = args[i];
            switch (arg)
            {
                case "--help":
                case "-h":
                    showHelp = true;
                    break;
                case "--sqpack":
                    sqpack = RequireValue(args, ref i, arg);
                    break;
                case "--out":
                    output = RequireValue(args, ref i, arg);
                    break;
                case "--language":
                    language = RequireValue(args, ref i, arg);
                    break;
                case "--ids-file":
                    files.Add(RequireValue(args, ref i, arg));
                    break;
                case "--ids":
                    foreach (var raw in RequireValue(args, ref i, arg).Split([',', ';', ' ', '\t'], StringSplitOptions.RemoveEmptyEntries))
                    {
                        if (uint.TryParse(raw.Trim(), NumberStyles.Integer, CultureInfo.InvariantCulture, out var id))
                        {
                            ids.Add(id);
                        }
                    }
                    break;
                default:
                    throw new ArgumentException($"Unknown argument '{arg}'.");
            }
        }

        return new Options(sqpack, output, language, files, ids, showHelp);
    }

    public static void PrintHelp()
    {
        Console.WriteLine("""
VedaAxis FFXIV CN action extraction tool

Usage:
  dotnet run --project tools/ffxiv_cn_action_extract/VedaAxis.FfxivCnActionExtract.csproj -- --sqpack <game\sqpack> --ids-file <file>

Options:
  --sqpack <path>       FFXIV game sqpack folder, game folder, or install root.
  --ids-file <path>     JSON/CSV/text file containing actionId/actionIds.
  --ids <list>          Extra comma/space separated action ids.
  --out <dir>           Output directory. Default: artifacts/cn-action-names.
  --language <name>     Lumina language. Default: ChineseSimplified.
  --help                Show this help.
""");
    }

    private static string RequireValue(string[] args, ref int index, string name)
    {
        if (index + 1 >= args.Length)
        {
            throw new ArgumentException($"Argument {name} requires a value.");
        }

        index++;
        return args[index];
    }
}

internal sealed record ActionMetadata(uint ActionId, string? Phase, string? EnglishName, string? Notes)
{
    public ActionMetadata Merge(ActionMetadata other)
    {
        return this with
        {
            Phase = FirstNonEmpty(Phase, other.Phase),
            EnglishName = FirstNonEmpty(EnglishName, other.EnglishName),
            Notes = string.Join("; ", new[] { Notes, other.Notes }.Where(item => !string.IsNullOrWhiteSpace(item)).Distinct()),
        };
    }

    private static string? FirstNonEmpty(string? current, string? next)
    {
        return string.IsNullOrWhiteSpace(current) ? next : current;
    }
}

internal sealed record ExtractedAction(
    uint ActionId,
    string? Phase,
    string? KnownEnglishName,
    string? Notes,
    bool Found,
    string? RawName,
    string? Name,
    bool IsRsv,
    string? RsvKey,
    string? EnglishNameFromClient,
    ushort? Icon,
    uint? AttackTypeId,
    string? AttackTypeName,
    uint? ActionCategoryId,
    string? ActionCategoryName,
    uint? ClassJobId,
    string? ClassJobName,
    byte? CastType,
    sbyte? Range,
    byte? EffectRange);
