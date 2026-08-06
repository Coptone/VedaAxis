using System.Numerics;
using System.Runtime.InteropServices;
using Dalamud.Bindings.ImGui;
using Dalamud.Game.Command;
using Dalamud.Game.ClientState.Conditions;
using Dalamud.Game.DutyState;
using Dalamud.Game.ClientState.Objects.Types;
using Dalamud.Hooking;
using Dalamud.IoC;
using Dalamud.Interface.Textures;
using Dalamud.Plugin;
using Dalamud.Plugin.Services;
using Dalamud.Utility;
using FFXIVClientStructs.FFXIV.Client.Game.Character;
using FFXIVClientStructs.FFXIV.Client.Game.Object;
using VedaAxis.Core;
using GameAction = Lumina.Excel.Sheets.Action;

namespace VedaAxis;

public sealed class Plugin : IDalamudPlugin
{
    private const string CommandName = "/vedaaxis";

    [PluginService] private static IDalamudPluginInterface PluginInterface { get; set; } = null!;
    [PluginService] private static ICommandManager CommandManager { get; set; } = null!;
    [PluginService] private static IPlayerState PlayerState { get; set; } = null!;
    [PluginService] private static IClientState ClientState { get; set; } = null!;
    [PluginService] private static ICondition Condition { get; set; } = null!;
    [PluginService] private static IDutyState DutyState { get; set; } = null!;
    [PluginService] private static IObjectTable ObjectTable { get; set; } = null!;
    [PluginService] private static IPartyList PartyList { get; set; } = null!;
    [PluginService] private static IFramework Framework { get; set; } = null!;
    [PluginService] private static IGameGui GameGui { get; set; } = null!;
    [PluginService] private static IGameInteropProvider Interop { get; set; } = null!;
    [PluginService] private static IPluginLog Log { get; set; } = null!;
    [PluginService] private static IDataManager DataManager { get; set; } = null!;
    [PluginService] private static ITextureProvider TextureProvider { get; set; } = null!;

    private readonly PluginConfiguration configuration;
    private readonly PlanFileStore planStore;
    private readonly PlanRuntime runtime = new(new TimelineClock());
    private readonly CombatLifecycle combatLifecycle = new();
    private readonly HotbarOverlay overlay;
    private readonly PartyListOverlay partyListOverlay;
    private readonly DeviceAuthorizationClient deviceAuthorizationClient = new();
    private readonly ExecutionUploadQueue executionUploadQueue;
    private readonly SemaphoreSlim executionUploadLock = new(1, 1);
    private Hook<ReceiveActionEffectDelegate>? actionEffectHook;
    private CancellationTokenSource? deviceAuthorizationCancellation;
    private bool showConfig;
    private string status = "尚未加载计划";
    private string deviceCode = string.Empty;
    private string deviceCodeExpiresAt = string.Empty;
    private string deviceAuthorizationUrl = string.Empty;
    private HashSet<(uint EntityId, uint ActionId)> activeCasts = [];
    private readonly Dictionary<Guid, uint> manualPartyTargets = [];

    public Plugin()
    {
        configuration = PluginInterface.GetPluginConfig() as PluginConfiguration ?? new PluginConfiguration();
        MigrateConfiguration(configuration);
        planStore = new PlanFileStore(PluginInterface.GetPluginConfigDirectory());
        executionUploadQueue = new ExecutionUploadQueue(PluginInterface.GetPluginConfigDirectory());
        overlay = new HotbarOverlay(GameGui, Log);
        partyListOverlay = new PartyListOverlay(GameGui, Log);
        ReloadPlan();

        CommandManager.AddHandler(CommandName, new CommandInfo(OnCommand)
        {
            HelpMessage = "打开配置；/vedaaxis start 开始预览；/vedaaxis reset 重置；/vedaaxis reload 重载 active-plan.json",
        });
        PluginInterface.UiBuilder.Draw += Draw;
        PluginInterface.UiBuilder.OpenConfigUi += ToggleConfig;
        PluginInterface.UiBuilder.OpenMainUi += ToggleConfig;
        Condition.ConditionChange += OnConditionChange;
        DutyState.DutyWiped += OnDutyReset;
        DutyState.DutyRecommenced += OnDutyReset;
        DutyState.DutyCompleted += OnDutyCompleted;
        Framework.Update += OnFrameworkUpdate;
        InstallActionEffectHook();
        _ = RestoreDeviceSessionAsync();
    }

    private static void MigrateConfiguration(PluginConfiguration current)
    {
        if (current.Version >= PluginConfiguration.CurrentVersion)
        {
            return;
        }

        var normalizedApiBaseUrl = current.ApiBaseUrl?.Trim().TrimEnd('/');
        if (string.IsNullOrWhiteSpace(normalizedApiBaseUrl)
            || string.Equals(
                normalizedApiBaseUrl,
                PluginConfiguration.LegacyLocalApiBaseUrl,
                StringComparison.OrdinalIgnoreCase))
        {
            current.ApiBaseUrl = PluginConfiguration.ProductionApiBaseUrl;
        }
        if (string.Equals(current.StrategyTag, "O8S-POC", StringComparison.OrdinalIgnoreCase))
        {
            current.StrategyTag = "DMU-P1P2";
        }

        current.Version = PluginConfiguration.CurrentVersion;
        PluginInterface.SavePluginConfig(current);
    }

    public void Dispose()
    {
        actionEffectHook?.Dispose();
        deviceAuthorizationCancellation?.Cancel();
        deviceAuthorizationCancellation?.Dispose();
        deviceAuthorizationClient.Dispose();
        Framework.Update -= OnFrameworkUpdate;
        DutyState.DutyCompleted -= OnDutyCompleted;
        DutyState.DutyRecommenced -= OnDutyReset;
        DutyState.DutyWiped -= OnDutyReset;
        Condition.ConditionChange -= OnConditionChange;
        PluginInterface.UiBuilder.OpenMainUi -= ToggleConfig;
        PluginInterface.UiBuilder.OpenConfigUi -= ToggleConfig;
        PluginInterface.UiBuilder.Draw -= Draw;
        CommandManager.RemoveHandler(CommandName);
    }

    private unsafe void InstallActionEffectHook()
    {
        try
        {
            actionEffectHook = Interop.HookFromAddress<ReceiveActionEffectDelegate>(
                ActionEffectHandler.Addresses.Receive.Value, OnReceiveActionEffect);
            actionEffectHook.Enable();
            Log.Information("ActionEffect confirmation hook enabled");
        }
        catch (Exception exception)
        {
            status = "ActionEffect 钩子初始化失败；高亮仍可预览";
            Log.Error(exception, "Unable to install ActionEffect confirmation hook");
        }
    }

    private unsafe void OnReceiveActionEffect(
        uint casterEntityId,
        Character* caster,
        Vector3* targetPosition,
        ActionEffectHandler.Header* header,
        ActionEffectHandler.TargetEffects* effects,
        GameObjectId* targetEntityIds)
    {
        actionEffectHook!.Original(casterEntityId, caster, targetPosition, header, effects, targetEntityIds);
        if (header == null)
        {
            return;
        }
        var observedAt = DateTimeOffset.UtcNow;
        var anchor = runtime.ObserveAnchor(header->ActionId, AnchorKind.ActionEffect, observedAt);
        if (anchor is not null)
        {
            status = $"锚点 {anchor.Phase} / Action {anchor.ActionId} 已重同步";
        }
        if (PlayerState.IsLoaded && PlayerState.EntityId == casterEntityId)
        {
            var matched = runtime.ObserveAction(header->ActionId, observedAt);
            if (matched > 0)
            {
                status = $"Action {header->ActionId} 已确认，匹配 {matched} 项";
            }
        }
    }

    private void Draw()
    {
        if (configuration.Enabled)
        {
            runtime.Advance(DateTimeOffset.UtcNow, overlay.IsActionAvailable);
            overlay.Draw(
                runtime.Assignments,
                runtime.Clock.ElapsedMilliseconds(DateTimeOffset.UtcNow),
                Math.Clamp(configuration.OverlayOpacity, 0.1f, 1f));
            DrawPartyTargets();
            DrawMissingActionDiagnostic();
        }

        if (showConfig)
        {
            DrawConfig();
        }
    }

    private void DrawConfig()
    {
        ImGui.SetNextWindowSize(new Vector2(560, 520), ImGuiCond.FirstUseEver);
        if (!ImGui.Begin("VedaAxis 控制台###VedaAxisConfig", ref showConfig))
        {
            ImGui.End();
            return;
        }

        var enabled = configuration.Enabled;
        if (ImGui.Checkbox("启用热键栏对齐高亮", ref enabled))
        {
            configuration.Enabled = enabled;
            SaveConfiguration();
        }

        ImGui.TextDisabled("覆盖层只绘制描边，不接管鼠标或热键输入。4 轨与 8 轨使用同一运行时。 ");
        ImGui.Separator();

        var slot = configuration.LocalSlot;
        if (ImGui.InputText("本机轨道", ref slot, 8))
        {
            configuration.LocalSlot = slot.Trim().ToUpperInvariant();
        }

        var opacity = configuration.OverlayOpacity;
        if (ImGui.SliderFloat("覆盖层透明度", ref opacity, 0.1f, 1f, "%.2f"))
        {
            configuration.OverlayOpacity = opacity;
            SaveConfiguration();
        }

        if (ImGui.Button("重载计划"))
        {
            ReloadPlan();
        }
        ImGui.SameLine();
        if (ImGui.Button("载入 DMU P1/P2 默认计划"))
        {
            var plan = ExamplePlan.Create();
            planStore.Save(plan);
            configuration.StrategyTag = plan.StrategyTag;
            configuration.TrackMode = "EIGHT";
            configuration.LocalSlot = "H2";
            SaveConfiguration();
            ReloadPlan();
        }
        ImGui.SameLine();
        if (ImGui.Button("开始 / 重新开始"))
        {
            runtime.Start(DateTimeOffset.UtcNow);
            status = "时间轴已从 0:00 开始";
        }
        ImGui.SameLine();
        if (ImGui.Button("停止"))
        {
            combatLifecycle.Complete();
            runtime.Stop();
            runtime.Reset();
            activeCasts.Clear();
            status = "时间轴已停止";
        }

        ImGui.TextWrapped($"计划文件：{planStore.Path}");
        ImGui.TextWrapped($"状态：{status}");
        DrawCombatDiagnostic();
        DrawPartyTargetMapping();
        ImGui.Separator();
        ImGui.Text("账户连接");
        var apiBaseUrl = configuration.ApiBaseUrl;
        if (ImGui.InputText("API 地址", ref apiBaseUrl, 240))
        {
            configuration.ApiBaseUrl = apiBaseUrl;
            SaveConfiguration();
        }
        if (HasStoredDeviceSession())
        {
            ImGui.TextColored(new Vector4(0.35f, 0.88f, 0.58f, 1f), $"已连接设备 {configuration.DeviceId}");
            ImGui.TextDisabled("本机已完成一次性绑定；插件启动和令牌过期时会自动续期。无需重复输入绑定码。");
            var strategyTag = configuration.StrategyTag;
            if (ImGui.InputText("策略标签", ref strategyTag, 80))
            {
                configuration.StrategyTag = strategyTag.Trim();
                SaveConfiguration();
            }
            var isEight = configuration.TrackMode == "EIGHT";
            if (ImGui.RadioButton("4 轨", !isEight))
            {
                configuration.TrackMode = "FOUR";
                SaveConfiguration();
            }
            ImGui.SameLine();
            if (ImGui.RadioButton("8 轨", isEight))
            {
                configuration.TrackMode = "EIGHT";
                SaveConfiguration();
            }
            if (ImGui.Button("使用 DMU P1/P2 配置"))
            {
                configuration.StrategyTag = "DMU-P1P2";
                configuration.TrackMode = "EIGHT";
                SaveConfiguration();
                status = "已选择 DMU P1/P2；请在 Territory 1363 脱战同步";
            }
            ImGui.SameLine();
            if (ImGui.Button("使用 O8S 联调配置"))
            {
                configuration.StrategyTag = "O8S-POC";
                configuration.TrackMode = "EIGHT";
                SaveConfiguration();
                status = "已选择 O8S 联调；请在 Territory 755 脱战同步";
            }
            if (ImGui.Button("同步已发布个人计划"))
            {
                _ = SyncPublishedPlanAsync();
            }
            ImGui.TextDisabled($"待上传执行批次：{executionUploadQueue.PendingCount}");
        }
        else if (ImGui.Button("连接 VedaAxis 账户"))
        {
            BeginDeviceAuthorization();
        }
        if (!string.IsNullOrEmpty(deviceCode))
        {
            ImGui.SameLine();
            ImGui.TextColored(new Vector4(0.33f, 0.86f, 0.91f, 1f), $"绑定码 {deviceCode}（{deviceCodeExpiresAt} 前有效）");
            if (ImGui.Button("打开绑定页面"))
            {
                Util.OpenLink(deviceAuthorizationUrl);
            }
            ImGui.SameLine();
            if (ImGui.Button("复制绑定码"))
            {
                ImGui.SetClipboardText(deviceCode);
            }
            ImGui.TextDisabled("首次在网页“插件绑定”确认即可；成功后本机会自动续期，令牌可在网页撤销。");
        }
        ImGui.Separator();
        ImGui.Text("当前轨道任务");
        foreach (var item in runtime.Assignments)
        {
            ImGui.BulletText($"Action {item.Assignment.ActionId} · {item.State} · "
                             + $"{item.Assignment.EarliestUseAtMs / 1000f:0.0}s–{item.Assignment.LatestUseAtMs / 1000f:0.0}s");
        }

        ImGui.End();
    }

    private void DrawMissingActionDiagnostic()
    {
        var elapsed = runtime.Clock.ElapsedMilliseconds(DateTimeOffset.UtcNow);
        var missing = runtime.Assignments
            .Where(item => item.ShouldDrawOverlay(elapsed) && !overlay.HasVisibleSlot(item.Assignment.ActionId))
            .ToList();
        if (missing.Count == 0)
        {
            return;
        }

        ImGui.SetNextWindowPos(new Vector2(30, 220), ImGuiCond.FirstUseEver);
        ImGui.SetNextWindowBgAlpha(0.92f);
        var flags = ImGuiWindowFlags.NoInputs | ImGuiWindowFlags.AlwaysAutoResize
                    | ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoSavedSettings;
        if (ImGui.Begin("未找到技能槽###VedaAxisMissingActions", flags))
        {
            ImGui.TextColored(new Vector4(1f, 0.48f, 0.35f, 1f), "未找到技能槽");
            foreach (var item in missing)
            {
                var row = DataManager.GetExcelSheet<GameAction>().GetRowOrDefault(item.Assignment.ActionId);
                var name = row?.Name.ExtractText() ?? $"Action {item.Assignment.ActionId}";
                if (row is { } action && action.Icon > 0)
                {
                    try
                    {
                        var texture = TextureProvider.GetFromGameIcon(new GameIconLookup(action.Icon));
                        var wrap = texture.GetWrapOrDefault();
                        if (wrap is not null)
                        {
                            ImGui.Image(wrap.Handle, new Vector2(28, 28));
                            ImGui.SameLine();
                        }
                    }
                    catch (Exception exception)
                    {
                        Log.Verbose(exception, "Unable to render action icon {ActionId}", item.Assignment.ActionId);
                    }
                }
                ImGui.Text($"{name} · {item.State}");
            }
        }
        ImGui.End();
    }

    private void DrawPartyTargets()
    {
        if (runtime.Plan is null || !runtime.Clock.IsRunning)
        {
            return;
        }

        var elapsed = runtime.Clock.ElapsedMilliseconds(DateTimeOffset.UtcNow);
        var activeTargets = runtime.Assignments
            .Where(item => item.Assignment.TargetTrackId is not null && item.ShouldDrawOverlay(elapsed))
            .GroupBy(item => item.Assignment.TargetTrackId!.Value)
            .ToList();
        if (activeTargets.Count == 0)
        {
            return;
        }

        var partyMembers = ReadPartyMembers();
        var tracks = runtime.Plan.Tracks.ToDictionary(track => track.TrackId);
        List<PartyTargetVisual> visuals = [];
        foreach (var group in activeTargets)
        {
            if (!tracks.TryGetValue(group.Key, out var targetTrack))
            {
                continue;
            }
            var resolution = PartyTargetResolver.Resolve(targetTrack, partyMembers, manualPartyTargets);
            if (!resolution.Resolved || resolution.Member is null)
            {
                continue;
            }
            var state = group.MaxBy(item => PartyStatePriority(item.State))!.State;
            visuals.Add(new PartyTargetVisual(resolution.Member, state));
        }

        partyListOverlay.Draw(visuals, Math.Clamp(configuration.OverlayOpacity, 0.1f, 1f));
    }

    private void DrawPartyTargetMapping()
    {
        if (runtime.Plan is null)
        {
            return;
        }
        var targetTrackIds = runtime.Plan.Assignments
            .Where(assignment => assignment.TargetTrackId is not null)
            .Select(assignment => assignment.TargetTrackId!.Value)
            .Distinct()
            .ToList();
        if (targetTrackIds.Count == 0)
        {
            return;
        }

        ImGui.Separator();
        ImGui.Text("单体减伤目标");
        ImGui.TextDisabled("优先按职业自动识别；不唯一时可在开打前手动指定。本次映射不保存角色名。 ");
        var partyMembers = ReadPartyMembers();
        var tracks = runtime.Plan.Tracks.ToDictionary(track => track.TrackId);
        foreach (var trackId in targetTrackIds)
        {
            if (!tracks.TryGetValue(trackId, out var targetTrack))
            {
                continue;
            }
            var resolution = PartyTargetResolver.Resolve(targetTrack, partyMembers, manualPartyTargets);
            var preview = resolution.Resolved && resolution.Member is not null
                ? $"{(resolution.Manual ? "手动" : "自动")}：{resolution.Member.DisplayName}"
                : "未识别，请手动选择";
            ImGui.BeginDisabled(Condition[ConditionFlag.InCombat]);
            if (ImGui.BeginCombo($"{targetTrack.Slot}##PartyTarget{trackId}", preview))
            {
                if (ImGui.Selectable("自动识别", !manualPartyTargets.ContainsKey(trackId)))
                {
                    manualPartyTargets.Remove(trackId);
                }
                foreach (var member in partyMembers)
                {
                    var selected = manualPartyTargets.GetValueOrDefault(trackId) == member.EntityId;
                    if (ImGui.Selectable($"{member.DisplayName} · Job {member.JobId}##{trackId}-{member.EntityId}", selected))
                    {
                        manualPartyTargets[trackId] = member.EntityId;
                    }
                }
                ImGui.EndCombo();
            }
            ImGui.EndDisabled();
        }
    }

    private static List<PartyMemberSnapshot> ReadPartyMembers()
    {
        List<PartyMemberSnapshot> members = [];
        for (var index = 0; index < PartyList.Length; index++)
        {
            var member = PartyList[index];
            if (member is null || member.EntityId == 0)
            {
                continue;
            }
            members.Add(new PartyMemberSnapshot(
                index,
                member.EntityId,
                member.ClassJob.RowId,
                member.Name.TextValue));
        }
        return members;
    }

    private static int PartyStatePriority(AssignmentState state) => state switch
    {
        AssignmentState.Missed or AssignmentState.Late => 3,
        AssignmentState.Highlighting => 2,
        AssignmentState.Success => 1,
        _ => 0,
    };

    private void DrawCombatDiagnostic()
    {
        var currentTerritoryId = ClientState.TerritoryType;
        var planTerritoryId = runtime.Plan?.TerritoryId ?? 0;
        var inCombat = Condition[ConditionFlag.InCombat];
        ImGui.Text("自动战斗诊断");
        ImGui.Text($"当前 Territory：{currentTerritoryId} · 计划 Territory：{planTerritoryId}");
        if (planTerritoryId == 0)
        {
            ImGui.TextColored(new Vector4(1f, 0.66f, 0.25f, 1f), "旧版计划缺少 Territory；请同步在线计划或载入 DMU P1/P2 默认计划");
        }
        else if (planTerritoryId != currentTerritoryId)
        {
            ImGui.TextColored(new Vector4(1f, 0.42f, 0.35f, 1f), "区域不匹配，开怪不会自动启动");
        }
        else
        {
            ImGui.TextColored(new Vector4(0.35f, 0.88f, 0.58f, 1f), "区域匹配，开怪后将自动启动");
        }
        ImGui.TextDisabled($"战斗状态：{(inCombat ? "InCombat" : "Idle")} · 时间轴：{(combatLifecycle.IsActive ? "Auto" : runtime.Clock.IsRunning ? "Manual" : "Stopped")}");
    }

    private void ReloadPlan()
    {
        try
        {
            var plan = planStore.LoadOrCreateExample();
            manualPartyTargets.Clear();
            var track = plan.Tracks.FirstOrDefault(item =>
                            string.Equals(item.Slot, configuration.LocalSlot, StringComparison.OrdinalIgnoreCase))
                        ?? plan.Tracks.First();
            runtime.Load(plan, track.TrackId);
            status = $"已加载 {plan.StrategyTag} v{plan.PlanVersion} · {track.Slot} · {runtime.Assignments.Count} 项";
        }
        catch (Exception exception)
        {
            status = $"计划加载失败：{exception.Message}";
            Log.Error(exception, "Unable to load active plan");
        }
    }

    private void OnCommand(string command, string arguments)
    {
        switch (arguments.Trim().ToLowerInvariant())
        {
            case "start":
                runtime.Start(DateTimeOffset.UtcNow);
                status = "时间轴已从 0:00 开始";
                break;
            case "reset":
                runtime.Start(DateTimeOffset.UtcNow);
                status = "运行时状态已重置";
                break;
            case "reload":
                ReloadPlan();
                break;
            default:
                ToggleConfig();
                break;
        }
    }

    private void ToggleConfig()
    {
        showConfig = !showConfig;
    }

    private void SaveConfiguration()
    {
        PluginInterface.SavePluginConfig(configuration);
    }

    private void OnConditionChange(ConditionFlag flag, bool value)
    {
        if (flag != ConditionFlag.InCombat)
        {
            return;
        }
        if (value)
        {
            TryStartAutomaticFight();
        }
        else if (combatLifecycle.IsActive)
        {
            _ = FinalizeAfterCombatExitAsync();
        }
    }

    private void OnDutyReset(IDutyStateEventArgs args)
    {
        FinalizeFight("WIPE");
        runtime.Stop();
        runtime.Reset();
        activeCasts.Clear();
        status = "团灭或重新开始，运行状态已清理";
    }

    private void OnDutyCompleted(IDutyStateEventArgs args)
    {
        FinalizeFight("CLEAR");
        runtime.Stop();
        activeCasts.Clear();
        status = "副本完成，时间轴已停止";
    }

    private void OnFrameworkUpdate(IFramework framework)
    {
        if (Condition[ConditionFlag.InCombat] && !combatLifecycle.IsActive)
        {
            TryStartAutomaticFight();
        }

        var planTerritoryId = runtime.Plan?.TerritoryId ?? 0;
        if (combatLifecycle.IsActive && planTerritoryId != 0 && ClientState.TerritoryType != planTerritoryId)
        {
            FinalizeFight("ABANDONED");
            runtime.Stop();
            runtime.Reset();
            activeCasts.Clear();
            status = $"区域已变化为 {ClientState.TerritoryType}，时间轴已结束";
            return;
        }

        if (!runtime.Clock.IsRunning || planTerritoryId == 0 || ClientState.TerritoryType != planTerritoryId)
        {
            activeCasts.Clear();
            return;
        }

        HashSet<(uint EntityId, uint ActionId)> visible = [];
        foreach (var gameObject in ObjectTable)
        {
            try
            {
                if (gameObject is not IBattleNpc || gameObject is not IBattleChara battleChara
                    || !battleChara.IsCasting || battleChara.CastActionId == 0)
                {
                    continue;
                }
                var key = (battleChara.EntityId, battleChara.CastActionId);
                visible.Add(key);
                if (activeCasts.Contains(key))
                {
                    continue;
                }
                var castStartedAt = DateTimeOffset.UtcNow
                                    - TimeSpan.FromSeconds(Math.Max(0, battleChara.CurrentCastTime));
                var anchor = runtime.ObserveAnchor(
                    battleChara.CastActionId, AnchorKind.CastStart, castStartedAt);
                if (anchor is not null)
                {
                    status = $"读条锚点 {anchor.Phase} / Action {anchor.ActionId} 已重同步";
                }
            }
            catch (NullReferenceException)
            {
                // Actors may disappear while the object table is being enumerated.
            }
        }
        activeCasts = visible;
    }

    private void BeginDeviceAuthorization()
    {
        deviceAuthorizationCancellation?.Cancel();
        deviceAuthorizationCancellation?.Dispose();
        deviceAuthorizationCancellation = new CancellationTokenSource();
        deviceCode = string.Empty;
        status = "正在请求一次性绑定码…";
        _ = AuthorizeDeviceAsync(deviceAuthorizationCancellation.Token);
    }

    private async Task AuthorizeDeviceAsync(CancellationToken cancellationToken)
    {
        try
        {
            var result = await deviceAuthorizationClient.AuthorizeAsync(
                configuration.ApiBaseUrl,
                (code, expiresAt) =>
                {
                    deviceCode = code;
                    deviceCodeExpiresAt = expiresAt;
                    deviceAuthorizationUrl = $"{configuration.ApiBaseUrl.TrimEnd('/')}/device?code={Uri.EscapeDataString(code)}";
                    status = "等待网页确认设备";
                },
                cancellationToken);
            configuration.DeviceId = result.DeviceId.ToString();
            configuration.AccessToken = result.Tokens.AccessToken;
            configuration.RefreshToken = result.Tokens.RefreshToken;
            SaveConfiguration();
            deviceCode = string.Empty;
            deviceAuthorizationUrl = string.Empty;
            status = "设备授权成功；以后将自动续期，无需重复绑定";
            _ = DrainExecutionQueueAsync();
        }
        catch (OperationCanceledException)
        {
            status = "设备授权已取消";
        }
        catch (Exception exception)
        {
            status = $"设备授权失败：{exception.Message}";
            Log.Error(exception, "Device authorization failed");
        }
    }

    private bool HasStoredDeviceSession() =>
        !string.IsNullOrWhiteSpace(configuration.DeviceId)
        && !string.IsNullOrWhiteSpace(configuration.AccessToken)
        && !string.IsNullOrWhiteSpace(configuration.RefreshToken);

    private async Task RestoreDeviceSessionAsync()
    {
        if (!HasStoredDeviceSession())
        {
            return;
        }
        if (Condition[ConditionFlag.InCombat])
        {
            status = "已恢复本机账户连接；战斗中保持离线快照，不执行网络续期";
            return;
        }

        try
        {
            var tokens = await deviceAuthorizationClient.RefreshAsync(
                configuration.ApiBaseUrl, configuration.RefreshToken!, CancellationToken.None);
            configuration.AccessToken = tokens.AccessToken;
            configuration.RefreshToken = tokens.RefreshToken;
            SaveConfiguration();
            status = "已自动恢复 VedaAxis 账户连接";
            await DrainExecutionQueueAsync();
        }
        catch (HttpRequestException exception) when (exception.StatusCode == System.Net.HttpStatusCode.Unauthorized)
        {
            configuration.DeviceId = null;
            configuration.AccessToken = null;
            configuration.RefreshToken = null;
            SaveConfiguration();
            status = "设备授权已失效或已在网页撤销，请重新绑定一次";
        }
        catch (Exception exception)
        {
            status = "已保留本机账户连接；当前无法续期，将在下次联网操作重试";
            Log.Warning(exception, "Unable to refresh the stored device session during startup");
        }
    }

    private async Task SyncPublishedPlanAsync()
    {
        if (configuration.AccessToken is null || configuration.RefreshToken is null)
        {
            status = "请先连接 VedaAxis 账户";
            return;
        }
        if (Condition[ConditionFlag.InCombat])
        {
            status = "战斗中不执行网络同步，请脱战后重试";
            return;
        }

        status = "正在同步已发布个人计划…";
        try
        {
            var plan = await TryMatchPlanAsync(configuration.AccessToken, CancellationToken.None);
            planStore.Save(plan);
            ReloadPlan();
            status = $"已同步 {plan.StrategyTag} v{plan.PlanVersion}";
        }
        catch (HttpRequestException exception) when (exception.StatusCode == System.Net.HttpStatusCode.Unauthorized)
        {
            try
            {
                var tokens = await deviceAuthorizationClient.RefreshAsync(
                    configuration.ApiBaseUrl, configuration.RefreshToken, CancellationToken.None);
                configuration.AccessToken = tokens.AccessToken;
                configuration.RefreshToken = tokens.RefreshToken;
                SaveConfiguration();
                var plan = await TryMatchPlanAsync(tokens.AccessToken, CancellationToken.None);
                planStore.Save(plan);
                ReloadPlan();
                status = $"令牌已刷新并同步 {plan.StrategyTag} v{plan.PlanVersion}";
            }
            catch (Exception refreshException)
            {
                status = $"令牌刷新或同步失败：{refreshException.Message}";
                Log.Error(refreshException, "Plan synchronization after token refresh failed");
            }
        }
        catch (Exception exception)
        {
            status = $"计划同步失败：{exception.Message}";
            Log.Error(exception, "Plan synchronization failed");
        }
    }

    private Task<PlanSnapshot> TryMatchPlanAsync(string accessToken, CancellationToken cancellationToken)
    {
        var territoryId = ClientState.TerritoryType;
        if (territoryId == 0)
        {
            throw new InvalidOperationException("无法确定计划 Territory，请先进入目标副本或载入测试计划");
        }
        return deviceAuthorizationClient.MatchPlanAsync(
            configuration.ApiBaseUrl,
            accessToken,
            territoryId,
            configuration.StrategyTag,
            configuration.TrackMode,
            cancellationToken);
    }

    private void FinalizeFight(string result)
    {
        var startedAt = combatLifecycle.Complete();
        if (startedAt is null || runtime.Plan is null)
        {
            return;
        }
        if (configuration.DeviceId is null || runtime.Plan.Source.Kind == "IMPORTED")
        {
            return;
        }
        executionUploadQueue.Enqueue(runtime, startedAt.Value, DateTimeOffset.UtcNow, result);
        _ = DrainExecutionQueueAsync();
    }

    private async Task FinalizeAfterCombatExitAsync()
    {
        await Task.Delay(1_500);
        if (Condition[ConditionFlag.InCombat] || !combatLifecycle.IsActive)
        {
            return;
        }
        FinalizeFight("ABANDONED");
        runtime.Stop();
        runtime.Reset();
        activeCasts.Clear();
        status = "检测到脱战，运行状态已清理";
    }

    private void TryStartAutomaticFight()
    {
        var now = DateTimeOffset.UtcNow;
        var decision = combatLifecycle.TryStart(
            configuration.Enabled, runtime.Plan, ClientState.TerritoryType, now);
        if (decision.Started)
        {
            runtime.Start(now);
            activeCasts.Clear();
            status = $"检测到战斗开始，Territory {ClientState.TerritoryType} 时间轴已自动启动";
            return;
        }

        status = decision.Rejection switch
        {
            CombatStartRejection.Disabled => "检测到战斗，但插件高亮已禁用",
            CombatStartRejection.PlanMissing => "检测到战斗，但尚未加载有效计划",
            CombatStartRejection.TerritoryMissing => "检测到战斗，但旧版计划缺少 Territory",
            CombatStartRejection.TerritoryMismatch => $"检测到战斗，但当前 Territory {ClientState.TerritoryType} 与计划 {runtime.Plan?.TerritoryId ?? 0} 不匹配",
            CombatStartRejection.AlreadyActive => status,
            _ => status,
        };
    }

    private async Task DrainExecutionQueueAsync()
    {
        if (configuration.AccessToken is null || configuration.RefreshToken is null
            || !await executionUploadLock.WaitAsync(0))
        {
            return;
        }
        try
        {
            foreach (var pending in executionUploadQueue.ReadPending())
            {
                try
                {
                    await deviceAuthorizationClient.UploadExecutionAsync(
                        configuration.ApiBaseUrl, configuration.AccessToken, pending.Batch, CancellationToken.None);
                    executionUploadQueue.Complete(pending);
                }
                catch (HttpRequestException exception) when (exception.StatusCode == System.Net.HttpStatusCode.Unauthorized)
                {
                    var tokens = await deviceAuthorizationClient.RefreshAsync(
                        configuration.ApiBaseUrl, configuration.RefreshToken, CancellationToken.None);
                    configuration.AccessToken = tokens.AccessToken;
                    configuration.RefreshToken = tokens.RefreshToken;
                    SaveConfiguration();
                    await deviceAuthorizationClient.UploadExecutionAsync(
                        configuration.ApiBaseUrl, tokens.AccessToken, pending.Batch, CancellationToken.None);
                    executionUploadQueue.Complete(pending);
                }
            }
        }
        catch (Exception exception)
        {
            status = $"执行记录将在稍后重试：{exception.Message}";
            Log.Warning(exception, "Unable to drain execution upload queue");
        }
        finally
        {
            executionUploadLock.Release();
        }
    }

    [UnmanagedFunctionPointer(CallingConvention.Cdecl)]
    private unsafe delegate void ReceiveActionEffectDelegate(
        uint casterEntityId,
        Character* caster,
        Vector3* targetPosition,
        ActionEffectHandler.Header* header,
        ActionEffectHandler.TargetEffects* effects,
        GameObjectId* targetEntityIds);
}
