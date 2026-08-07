package dev.vedaaxis.api.ai;

import dev.vedaaxis.api.common.ApiException;
import dev.vedaaxis.api.plan.PlanService;
import dev.vedaaxis.api.plan.PlanSnapshot;
import dev.vedaaxis.api.rule.AbilityCatalog;
import dev.vedaaxis.api.rule.AbilityDefinition;
import dev.vedaaxis.api.rule.DamageEstimateAnalysisService;
import dev.vedaaxis.api.rule.PlanRuleEngine;
import dev.vedaaxis.api.rule.RuleValidationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AiCandidateService {
    private final PlanService planService;
    private final PlanRuleEngine ruleEngine;
    private final AbilityCatalog abilityCatalog;
    private final DamageEstimateAnalysisService damageEstimateAnalysisService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final Set<UUID> allowedUserIds;

    public AiCandidateService(
            PlanService planService,
            PlanRuleEngine ruleEngine,
            AbilityCatalog abilityCatalog,
            DamageEstimateAnalysisService damageEstimateAnalysisService,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder,
            @Value("${vedaaxis.ai.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${vedaaxis.ai.api-key:}") String apiKey,
            @Value("${vedaaxis.ai.model:deepseek-v4-pro}") String model,
            @Value("${vedaaxis.ai.allowed-user-ids:}") String allowedUserIds,
            @Value("${vedaaxis.ai.timeout-ms:90000}") long timeoutMs) {
        this.planService = planService;
        this.ruleEngine = ruleEngine;
        this.abilityCatalog = abilityCatalog;
        this.damageEstimateAnalysisService = damageEstimateAnalysisService;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(1_000, timeoutMs)));
        this.restClient = restClientBuilder.baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.apiKey = apiKey;
        this.model = model;
        this.allowedUserIds = parseAllowedUserIds(allowedUserIds);
    }

    public AiCandidate generate(
            UUID ownerId,
            UUID planId,
            String instruction,
            OptimizationMode mode,
            UUID focusTrackId,
            Boolean preserveExistingAssignments,
            Boolean allowGcdActions) {
        if (apiKey.isBlank()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED", "尚未配置 VEDAAXIS_AI_API_KEY");
        }
        if (!allowedUserIds.isEmpty() && !allowedUserIds.contains(ownerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "AI_NOT_ENABLED_FOR_ACCOUNT", "当前账号未开通 AI 候选功能");
        }

        PlanSnapshot base = planService.get(ownerId, planId).snapshot();
        OptimizationMode requestedMode = mode == null ? OptimizationMode.GLOBAL : mode;
        UUID normalizedFocusTrackId = requestedMode == OptimizationMode.FOCUSED
                ? requireFocusTrack(base, focusTrackId)
                : null;
        AiSafetyOptions safetyOptions = new AiSafetyOptions(
                preserveExistingAssignments == null || preserveExistingAssignments,
                Boolean.TRUE.equals(allowGcdActions));
        AiPayload payload = requestCandidate(
                base, instruction == null ? "" : instruction.trim(), requestedMode, normalizedFocusTrackId, safetyOptions);
        List<PlanSnapshot.Assignment> candidateAssignments = resolveCandidateAssignments(base, payload);
        enforceSafety(base, candidateAssignments, requestedMode, normalizedFocusTrackId, safetyOptions);

        PlanSnapshot candidateSnapshot = new PlanSnapshot(
                base.schemaVersion(), base.minimumPluginVersion(), base.planId(), base.planVersion(),
                base.timelineId(), base.timelineVersion(), base.encounterId(), base.territoryId(), base.strategyTag(), base.trackMode(),
                new PlanSnapshot.Source(
                        PlanSnapshot.SourceKind.AI_CANDIDATE,
                        "DeepSeek " + model,
                        PlanSnapshot.Confidence.UNVERIFIED),
                base.phases(), base.mechanics(), base.anchors(), base.tracks(), candidateAssignments);
        RuleValidationResult validation = ruleEngine.validate(candidateSnapshot);
        String confidence = validation.valid() ? "RULE_VALIDATED" : "UNVERIFIED";
        return new AiCandidate(
                "1.0", UUID.randomUUID(), base.planId(), candidateAssignments,
                payload.reasons(), payload.warnings(), confidence, "DeepSeek", model, Instant.now(), validation);
    }

    @SuppressWarnings("unchecked")
    private AiPayload requestCandidate(
            PlanSnapshot base,
            String instruction,
            OptimizationMode mode,
            UUID focusTrackId,
            AiSafetyOptions safetyOptions) {
        try {
            String planJson = objectMapper.writeValueAsString(compactPlanContext(base));
            String abilityJson = objectMapper.writeValueAsString(relevantAbilitySummaries(base));
            String damagePreviewJson = objectMapper.writeValueAsString(mechanicRiskSummary(base));
            String optimizationContextJson = objectMapper.writeValueAsString(optimizationContext(base, mode, focusTrackId, safetyOptions));
            String systemPrompt = """
                    You are a Final Fantasy XIV mitigation-planning candidate generator.
                    Output JSON only. Do not output Markdown.
                    Required JSON format:
                    {"operations":[{"op":"ADD|UPDATE|DELETE","assignmentId":"uuid for UPDATE/DELETE","assignment":{assignment object for ADD/UPDATE}}],"reasons":["..."],"warnings":["..."]}.
                    Return only changed assignments as operations. Do not return the full assignments list unless you cannot express the answer as operations.
                    For no useful change, return {"operations":[],"reasons":["..."],"warnings":["..."]}.
                    Assignment object fields must match the current plan assignment shape exactly:
                    assignmentId, mechanicId, trackId, actionId, anchorId, highlightAtMs, earliestUseAtMs, latestUseAtMs, impactAtMs, locked, confirmationStrategy, fallbacks, targetTrackId.
                    Never modify or delete locked=true assignments.
                    Never create new tracks or mechanics.
                    Only use actionId values from the available ability catalog.
                    All times are milliseconds and must satisfy highlightAtMs <= earliestUseAtMs <= latestUseAtMs <= impactAtMs.
                    Optimize mitigation, shield, healing and healing-buff utilization: prioritize RED/YELLOW risks, use long-duration skills to cover multiple mechanics, and avoid cooldown conflicts on the same track.
                    Preserve targetTrackId for single-target mitigation.
                    Do not add pure healing, healing buffs, resource skills, or unmodeled shields to GREEN mechanics unless there is a clear sustained-pressure gap.
                    Do not optimize only by raw damage size. Use attackClass: AOE uses party survival and raid mitigation, TANK_BUSTER uses tank/self/target/enemy mitigation, AUTO_ATTACK only matters under sustained tank pressure, and MECHANIC requires target/context review.
                    Healing, shields, and invulnerability may be candidate support, but do not pretend pure healing or unmodeled shields are percentage mitigation.
                    When optimizationMode=FOCUSED, only ADD, UPDATE, or DELETE unlocked assignments on focusTrackId. Other tracks are read-only context.
                    When preserveExistingAssignments=true, return only ADD operations and never UPDATE or DELETE an existing assignment.
                    When allowGcdActions=false, do not ADD or UPDATE any assignment to use an ability whose castCategory is GCD.
                    """;
            String userPrompt = "Current compact plan JSON:\n" + planJson
                    + "\nAvailable ability catalog JSON:\n" + abilityJson
                    + "\nCurrent damage/risk preview JSON:\n" + damagePreviewJson
                    + "\nOptimization mode JSON:\n" + optimizationContextJson
                    + "\nUser instruction:\n" + (instruction.isBlank()
                    ? "Optimize coverage and conflicts without changing locked assignments."
                    : instruction);
            Map<String, Object> request = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)),
                    "response_format", Map.of("type", "json_object"),
                    "max_tokens", 4096,
                    "stream", false);

            Map<String, Object> response = restClient.post()
                    .uri("/chat/completions")
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                throw invalidResponse("响应为空");
            }
            List<Object> choices = (List<Object>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw invalidResponse("缺少 choices");
            }
            Map<String, Object> choice = (Map<String, Object>) choices.getFirst();
            Map<String, Object> message = (Map<String, Object>) choice.get("message");
            String content = message == null ? null : (String) message.get("content");
            if (content == null || content.isBlank()) {
                throw invalidResponse("content 为空");
            }
            AiPayload payload = objectMapper.readValue(content, AiPayload.class);
            if ((payload.assignments() == null && payload.operations() == null)
                    || payload.reasons() == null
                    || payload.warnings() == null) {
                throw invalidResponse("候选字段不完整");
            }
            return payload;
        } catch (ApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_ERROR", "AI 服务调用失败");
        } catch (JacksonException | ClassCastException exception) {
            throw invalidResponse("JSON 无法解析");
        }
    }

    List<PlanSnapshot.Assignment> resolveCandidateAssignments(PlanSnapshot base, AiPayload payload) {
        if (payload.assignments() != null) {
            return List.copyOf(payload.assignments());
        }
        if (payload.operations() == null) {
            throw invalidResponse("缺少 assignments 或 operations");
        }

        LinkedHashMap<UUID, PlanSnapshot.Assignment> assignmentsById = new LinkedHashMap<>();
        for (PlanSnapshot.Assignment assignment : base.assignments()) {
            assignmentsById.put(assignment.assignmentId(), assignment);
        }

        for (AiOperation operation : payload.operations()) {
            if (operation == null || operation.op() == null || operation.op().isBlank()) {
                throw invalidResponse("operation 缺少 op");
            }
            String op = operation.op().trim().toUpperCase(Locale.ROOT);
            switch (op) {
                case "ADD" -> {
                    PlanSnapshot.Assignment assignment = requireOperationAssignment(operation, op);
                    if (assignmentsById.containsKey(assignment.assignmentId())) {
                        throw invalidResponse("ADD 使用了已存在的 assignmentId " + assignment.assignmentId());
                    }
                    assignmentsById.put(assignment.assignmentId(), assignment);
                }
                case "UPDATE" -> {
                    PlanSnapshot.Assignment assignment = requireOperationAssignment(operation, op);
                    UUID assignmentId = operation.assignmentId() == null
                            ? assignment.assignmentId()
                            : operation.assignmentId();
                    if (!assignmentId.equals(assignment.assignmentId())) {
                        throw invalidResponse("UPDATE 的 assignmentId 与 assignment.assignmentId 不一致");
                    }
                    if (!assignmentsById.containsKey(assignment.assignmentId())) {
                        throw invalidResponse("UPDATE 引用了不存在的 assignmentId " + assignment.assignmentId());
                    }
                    assignmentsById.put(assignment.assignmentId(), assignment);
                }
                case "DELETE" -> {
                    UUID assignmentId = operation.assignmentId();
                    if (assignmentId == null && operation.assignment() != null) {
                        assignmentId = operation.assignment().assignmentId();
                    }
                    if (assignmentId == null) {
                        throw invalidResponse("DELETE 缺少 assignmentId");
                    }
                    if (!assignmentsById.containsKey(assignmentId)) {
                        throw invalidResponse("DELETE 引用了不存在的 assignmentId " + assignmentId);
                    }
                    assignmentsById.remove(assignmentId);
                }
                default -> throw invalidResponse("未支持的 operation " + operation.op());
            }
        }

        return List.copyOf(assignmentsById.values());
    }

    private PlanSnapshot.Assignment requireOperationAssignment(AiOperation operation, String op) {
        if (operation.assignment() == null) {
            throw invalidResponse(op + " 缺少 assignment");
        }
        return operation.assignment();
    }

    private UUID requireFocusTrack(PlanSnapshot base, UUID focusTrackId) {
        if (focusTrackId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_FOCUS_TRACK_REQUIRED", "指向优化需要选择目标轨道");
        }
        boolean exists = base.tracks().stream().anyMatch(track -> track.trackId().equals(focusTrackId));
        if (!exists) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AI_FOCUS_TRACK_INVALID", "指向优化轨道不存在");
        }
        return focusTrackId;
    }

    private Set<UUID> parseAllowedUserIds(String configured) {
        if (configured == null || configured.isBlank()) return Set.of();
        return Pattern.compile("[,;\\s]+").splitAsStream(configured.trim())
                .filter(value -> !value.isBlank())
                .map(value -> {
                    try {
                        return UUID.fromString(value);
                    } catch (IllegalArgumentException exception) {
                        throw new IllegalStateException("Invalid vedaaxis.ai.allowed-user-ids entry: " + value);
                    }
                })
                .collect(Collectors.toUnmodifiableSet());
    }

    private Map<String, Object> optimizationContext(
            PlanSnapshot base,
            OptimizationMode mode,
            UUID focusTrackId,
            AiSafetyOptions safetyOptions) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("optimizationMode", mode.name());
        if (mode == OptimizationMode.FOCUSED && focusTrackId != null) {
            PlanSnapshot.ExecutionTrack focusTrack = base.tracks().stream()
                    .filter(track -> track.trackId().equals(focusTrackId))
                    .findFirst()
                    .orElseThrow();
            context.put("focusTrackId", focusTrack.trackId());
            context.put("focusTrackSlot", focusTrack.slot().name());
            context.put("focusTrackDisplayName", focusTrack.displayName());
            context.put("writeScope", "ONLY_UNLOCKED_ASSIGNMENTS_ON_FOCUS_TRACK");
        } else {
            context.put("writeScope", "ALL_UNLOCKED_ASSIGNMENTS");
        }
        context.put("preserveExistingAssignments", safetyOptions.preserveExistingAssignments());
        context.put("allowGcdActions", safetyOptions.allowGcdActions());
        context.put("existingAssignmentPolicy", safetyOptions.preserveExistingAssignments()
                ? "Only ADD new assignments. Existing assignments are immutable even when locked=false."
                : "Unlocked assignments may be updated or deleted within the write scope.");
        context.put("gcdPolicy", safetyOptions.allowGcdActions()
                ? "GCD abilities may be proposed when useful."
                : "Do not propose GCD abilities. Prefer oGCD mitigation, healing, healing buffs, shields, and resources.");
        context.put("lowRiskSupportPolicy", "Do not add or modify support-only healing/resource/unmodeled-shield assignments on GREEN mechanics.");
        context.put("tankbusterPolicy", "Do not over-optimize tankbusters by raw damage; prefer tank/self/target/enemy mitigation and preserve raid resources unless party risk also exists.");
        return context;
    }

    private List<AbilityDefinition> relevantAbilities(PlanSnapshot snapshot) {
        Set<Integer> jobIds = snapshot.tracks().stream()
                .flatMap(track -> track.allowedJobIds().stream())
                .collect(Collectors.toUnmodifiableSet());
        return abilityCatalog.all().stream()
                .filter(ability -> ability.jobIds().isEmpty()
                        || ability.jobIds().stream().anyMatch(jobIds::contains))
                .toList();
    }

    private Map<String, Object> compactPlanContext(PlanSnapshot snapshot) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("schemaVersion", snapshot.schemaVersion());
        context.put("minimumPluginVersion", snapshot.minimumPluginVersion());
        context.put("planId", snapshot.planId());
        context.put("planVersion", snapshot.planVersion());
        context.put("timelineId", snapshot.timelineId());
        context.put("timelineVersion", snapshot.timelineVersion());
        context.put("encounterId", snapshot.encounterId());
        context.put("territoryId", snapshot.territoryId());
        context.put("strategyTag", snapshot.strategyTag());
        context.put("trackMode", snapshot.trackMode());
        context.put("tracks", snapshot.tracks());
        context.put("mechanics", snapshot.mechanics());
        context.put("assignments", snapshot.assignments());
        return context;
    }

    private List<Map<String, Object>> relevantAbilitySummaries(PlanSnapshot snapshot) {
        return relevantAbilities(snapshot).stream()
                .map(this::abilitySummary)
                .toList();
    }

    private Map<String, Object> abilitySummary(AbilityDefinition ability) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("actionId", ability.actionId());
        summary.put("name", ability.name());
        summary.put("jobIds", ability.jobIds());
        summary.put("cooldownMs", ability.cooldownMs());
        summary.put("maxCharges", ability.maxCharges());
        summary.put("durationMs", ability.durationMs());
        summary.put("confirmationStrategy", ability.confirmationStrategy());
        summary.put("castCategory", ability.castCategory());
        if (ability.effect() != null) {
            Map<String, Object> effect = new LinkedHashMap<>();
            effect.put("scope", ability.effect().scope());
            effect.put("allDamageReductionPercent", ability.effect().allDamageReductionPercent());
            effect.put("physicalDamageReductionPercent", ability.effect().physicalDamageReductionPercent());
            effect.put("magicalDamageReductionPercent", ability.effect().magicalDamageReductionPercent());
            effect.put("maximumHpIncreasePercent", ability.effect().maximumHpIncreasePercent());
            effect.put("maximumHpBarrierPercent", ability.effect().maximumHpBarrierPercent());
            effect.put("barrierCurePotency", ability.effect().barrierCurePotency());
            effect.put("invulnerability", ability.effect().invulnerability());
            effect.put("stackingGroup", ability.effect().stackingGroup());
            effect.put("calculationReadiness", ability.effect().calculationReadiness());
            effect.put("conditions", ability.effect().conditions());
            summary.put("effect", effect);
        }
        return summary;
    }

    private List<AiMechanicRisk> mechanicRiskSummary(PlanSnapshot snapshot) {
        Map<UUID, Long> assignmentCounts = snapshot.assignments().stream()
                .collect(Collectors.groupingBy(
                        PlanSnapshot.Assignment::mechanicId,
                        Collectors.counting()));
        Map<UUID, DamageEstimateAnalysisService.MechanicEstimate> estimateByMechanic =
                damageEstimateAnalysisService.preview(snapshot).stream()
                        .collect(Collectors.toMap(
                                DamageEstimateAnalysisService.MechanicEstimate::mechanicId,
                                Function.identity()));
        return snapshot.mechanics().stream()
                .map(mechanic -> {
                    DamageEstimateAnalysisService.MechanicEstimate estimate = estimateByMechanic.get(mechanic.mechanicId());
                    return new AiMechanicRisk(
                            mechanic.mechanicId(),
                            mechanic.phase(),
                            mechanic.name(),
                            mechanic.plannedAtMs(),
                            mechanic.type().name(),
                            attackClass(mechanic),
                            mechanic.damageType().name(),
                            mechanic.target(),
                            estimate == null ? null : estimate.baselineDamage(),
                            estimate == null ? null : estimate.damageAfterMitigation(),
                            riskBasis(mechanic),
                            estimate == null ? "CALIBRATION_REQUIRED" : estimate.riskLevel().name(),
                            assignmentCounts.getOrDefault(mechanic.mechanicId(), 0L));
                })
                .toList();
    }

    void enforceSafety(
            PlanSnapshot base,
            List<PlanSnapshot.Assignment> candidate,
            OptimizationMode mode,
            UUID focusTrackId,
            AiSafetyOptions safetyOptions) {
        Map<UUID, PlanSnapshot.Assignment> candidateById = new HashMap<>();
        for (PlanSnapshot.Assignment assignment : candidate) {
            if (candidateById.put(assignment.assignmentId(), assignment) != null) {
                throw invalidResponse("候选包含重复 assignmentId");
            }
        }

        Map<UUID, PlanSnapshot.Assignment> baseById = base.assignments().stream()
                .collect(Collectors.toMap(PlanSnapshot.Assignment::assignmentId, Function.identity()));
        for (PlanSnapshot.Assignment locked : base.assignments().stream().filter(PlanSnapshot.Assignment::locked).toList()) {
            if (!locked.equals(candidateById.get(locked.assignmentId()))) {
                throw invalidResponse("AI 修改或删除了锁定任务 " + locked.assignmentId());
            }
        }
        if (safetyOptions.preserveExistingAssignments()) {
            for (PlanSnapshot.Assignment existing : base.assignments()) {
                if (!existing.equals(candidateById.get(existing.assignmentId()))) {
                    throw invalidResponse("AI 修改或删除了现有任务 " + existing.assignmentId());
                }
            }
        }

        HashSet<UUID> trackIds = new HashSet<>(base.tracks().stream().map(PlanSnapshot.ExecutionTrack::trackId).toList());
        if (candidate.stream().anyMatch(assignment -> !trackIds.contains(assignment.trackId()))) {
            throw invalidResponse("AI 引用了计划之外的轨道");
        }
        HashSet<UUID> mechanicIds = new HashSet<>(base.mechanics().stream().map(PlanSnapshot.TimelineMechanic::mechanicId).toList());
        if (candidate.stream().anyMatch(assignment -> !mechanicIds.contains(assignment.mechanicId()))) {
            throw invalidResponse("AI 引用了计划之外的机制");
        }
        Map<Long, AbilityDefinition> abilitiesByActionId = abilityCatalog.load();
        HashSet<Long> actionIds = new HashSet<>(abilitiesByActionId.keySet());
        if (candidate.stream().anyMatch(assignment -> !actionIds.contains(assignment.actionId()))) {
            throw invalidResponse("AI 引用了技能目录之外的 actionId");
        }
        if (!safetyOptions.allowGcdActions()) {
            enforceNoNewOrChangedGcdAssignments(candidate, baseById, abilitiesByActionId);
        }

        enforceFocusedScope(base, candidate, candidateById, mode, focusTrackId);
        enforceLowRiskSupportPolicy(base, candidate, abilitiesByActionId);
    }

    private void enforceNoNewOrChangedGcdAssignments(
            List<PlanSnapshot.Assignment> candidate,
            Map<UUID, PlanSnapshot.Assignment> baseById,
            Map<Long, AbilityDefinition> abilitiesByActionId) {
        for (PlanSnapshot.Assignment assignment : candidate) {
            PlanSnapshot.Assignment baseAssignment = baseById.get(assignment.assignmentId());
            if (baseAssignment != null && baseAssignment.equals(assignment)) continue;
            AbilityDefinition ability = abilitiesByActionId.get(assignment.actionId());
            if (ability != null && ability.castCategory() == AbilityDefinition.CastCategory.GCD) {
                throw invalidResponse("AI 试图新增或改动 GCD 技能 " + assignment.actionId());
            }
        }
    }

    private void enforceFocusedScope(
            PlanSnapshot base,
            List<PlanSnapshot.Assignment> candidate,
            Map<UUID, PlanSnapshot.Assignment> candidateById,
            OptimizationMode mode,
            UUID focusTrackId) {
        if (mode != OptimizationMode.FOCUSED) return;
        for (PlanSnapshot.Assignment baseAssignment : base.assignments()) {
            if (baseAssignment.trackId().equals(focusTrackId)) continue;
            if (!baseAssignment.equals(candidateById.get(baseAssignment.assignmentId()))) {
                throw invalidResponse("指向优化修改或删除了非目标轨道任务 " + baseAssignment.assignmentId());
            }
        }

        HashSet<UUID> baseAssignmentIds = new HashSet<>(base.assignments().stream()
                .map(PlanSnapshot.Assignment::assignmentId)
                .toList());
        if (candidate.stream()
                .anyMatch(assignment -> !assignment.trackId().equals(focusTrackId)
                        && !baseAssignmentIds.contains(assignment.assignmentId()))) {
            throw invalidResponse("指向优化新增了非目标轨道任务");
        }
    }

    private void enforceLowRiskSupportPolicy(
            PlanSnapshot base,
            List<PlanSnapshot.Assignment> candidate,
            Map<Long, AbilityDefinition> abilitiesByActionId) {
        Map<UUID, PlanSnapshot.Assignment> baseById = base.assignments().stream()
                .collect(Collectors.toMap(PlanSnapshot.Assignment::assignmentId, Function.identity()));
        Map<UUID, DamageEstimateAnalysisService.MechanicEstimate> estimateByMechanic =
                damageEstimateAnalysisService.preview(base).stream()
                        .collect(Collectors.toMap(
                                DamageEstimateAnalysisService.MechanicEstimate::mechanicId,
                                Function.identity()));
        for (PlanSnapshot.Assignment assignment : candidate) {
            PlanSnapshot.Assignment baseAssignment = baseById.get(assignment.assignmentId());
            if (baseAssignment != null && baseAssignment.equals(assignment)) continue;
            DamageEstimateAnalysisService.MechanicEstimate estimate = estimateByMechanic.get(assignment.mechanicId());
            if (estimate == null || estimate.riskLevel() != DamageEstimateAnalysisService.RiskLevel.GREEN) continue;
            AbilityDefinition ability = abilitiesByActionId.get(assignment.actionId());
            if (ability != null && isSupportOnlyForAiPolicy(ability)) {
                throw invalidResponse("AI 试图在低风险机制上新增或改动纯治疗/未建模辅助技能 " + assignment.actionId());
            }
        }
    }

    private boolean isSupportOnlyForAiPolicy(AbilityDefinition ability) {
        if (ability.effect() == null || ability.effect().calculationReadiness() == null) return true;
        return switch (ability.effect().calculationReadiness()) {
            case REQUIRES_HEALING_STATS, NO_DIRECT_MITIGATION, UNMODELED -> true;
            case DIRECT_REDUCTION, MAX_HP_BARRIER, INVULNERABILITY_SPECIAL_CASE -> false;
        };
    }

    private String attackClass(PlanSnapshot.TimelineMechanic mechanic) {
        if (mechanic.type() == PlanSnapshot.MechanicType.RAIDWIDE) return "AOE";
        if (mechanic.type() == PlanSnapshot.MechanicType.TANK_BUSTER) return "TANK_BUSTER";
        if (isAutoAttack(mechanic.name())) return "AUTO_ATTACK";
        return "MECHANIC";
    }

    private boolean isAutoAttack(String name) {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("攻击")
                || normalized.startsWith("攻击 ")
                || normalized.equals("attack")
                || normalized.startsWith("attack ");
    }

    private String riskBasis(PlanSnapshot.TimelineMechanic mechanic) {
        String category = attackClass(mechanic);
        if (Objects.equals(category, "AOE")) return "HEALER_HP_AND_RAIDWIDE_RISK";
        if (Objects.equals(category, "TANK_BUSTER") || Objects.equals(category, "AUTO_ATTACK")) {
            return "TANK_HP_AND_TANK_TARGET_RISK";
        }
        return "TARGET_AND_MECHANIC_REVIEW";
    }

    private ApiException invalidResponse(String detail) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "AI_RESPONSE_INVALID", "AI 候选响应无效：" + detail);
    }

    public enum OptimizationMode {
        GLOBAL,
        FOCUSED
    }

    public record AiPayload(
            List<PlanSnapshot.Assignment> assignments,
            List<AiOperation> operations,
            List<String> reasons,
            List<String> warnings) {
    }

    public record AiOperation(
            String op,
            UUID assignmentId,
            PlanSnapshot.Assignment assignment) {
    }

    public record AiSafetyOptions(
            boolean preserveExistingAssignments,
            boolean allowGcdActions) {
    }

    private record AiMechanicRisk(
            UUID mechanicId,
            String phase,
            String name,
            long plannedAtMs,
            String type,
            String attackClass,
            String damageType,
            String target,
            Long baselineDamage,
            Long damageAfterMitigation,
            String riskBasis,
            String riskLevel,
            long assignmentCount) {
    }

    public record AiCandidate(
            String schemaVersion,
            UUID candidateId,
            UUID basePlanId,
            List<PlanSnapshot.Assignment> assignments,
            List<String> reasons,
            List<String> warnings,
            String confidence,
            String provider,
            String model,
            Instant generatedAt,
            RuleValidationResult validation) {
    }
}
