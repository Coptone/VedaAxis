package dev.vedaaxis.api.ai;

import dev.vedaaxis.api.common.ApiException;
import dev.vedaaxis.api.plan.PlanService;
import dev.vedaaxis.api.plan.PlanSnapshot;
import dev.vedaaxis.api.rule.PlanRuleEngine;
import dev.vedaaxis.api.rule.RuleValidationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AiCandidateService {
    private final PlanService planService;
    private final PlanRuleEngine ruleEngine;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public AiCandidateService(
            PlanService planService,
            PlanRuleEngine ruleEngine,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder,
            @Value("${vedaaxis.ai.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${vedaaxis.ai.api-key:}") String apiKey,
            @Value("${vedaaxis.ai.model:deepseek-v4-pro}") String model) {
        this.planService = planService;
        this.ruleEngine = ruleEngine;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    public AiCandidate generate(UUID ownerId, UUID planId, String instruction) {
        if (apiKey.isBlank()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED", "尚未配置 VEDAAXIS_AI_API_KEY");
        }

        PlanSnapshot base = planService.get(ownerId, planId).snapshot();
        AiPayload payload = requestCandidate(base, instruction == null ? "" : instruction.trim());
        enforceSafety(base, payload.assignments());

        PlanSnapshot candidateSnapshot = new PlanSnapshot(
                base.schemaVersion(), base.minimumPluginVersion(), base.planId(), base.planVersion(),
                base.timelineId(), base.timelineVersion(), base.encounterId(), base.strategyTag(), base.trackMode(),
                new PlanSnapshot.Source(
                        PlanSnapshot.SourceKind.AI_CANDIDATE,
                        "DeepSeek " + model,
                        PlanSnapshot.Confidence.UNVERIFIED),
                base.anchors(), base.tracks(), payload.assignments());
        RuleValidationResult validation = ruleEngine.validate(candidateSnapshot);
        String confidence = validation.valid() ? "RULE_VALIDATED" : "UNVERIFIED";
        return new AiCandidate(
                "1.0", UUID.randomUUID(), base.planId(), payload.assignments(),
                payload.reasons(), payload.warnings(), confidence, "DeepSeek", model, Instant.now(), validation);
    }

    @SuppressWarnings("unchecked")
    private AiPayload requestCandidate(PlanSnapshot base, String instruction) {
        try {
            String planJson = objectMapper.writeValueAsString(base);
            String systemPrompt = """
                    你是 FFXIV 减伤计划候选生成器。只输出 JSON，不得输出 Markdown。
                    JSON 格式必须是：
                    {"assignments":[完整 assignment 对象],"reasons":[字符串],"warnings":[字符串]}。
                    不得修改 locked=true 的任务；不得生成新轨道；不得假设未提供的技能数据；
                    时间单位全部为毫秒，且 highlightAtMs <= earliestUseAtMs <= latestUseAtMs <= impactAtMs。
                    """;
            String userPrompt = "现有计划 JSON：\n" + planJson
                    + "\n用户要求：\n" + (instruction.isBlank() ? "在不改锁定项的前提下优化覆盖和冲突。" : instruction);
            Map<String, Object> request = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)),
                    "response_format", Map.of("type", "json_object"),
                    "max_tokens", 8192,
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
            if (payload.assignments() == null || payload.reasons() == null || payload.warnings() == null) {
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

    private void enforceSafety(PlanSnapshot base, List<PlanSnapshot.Assignment> candidate) {
        Map<UUID, PlanSnapshot.Assignment> candidateById = new HashMap<>();
        for (PlanSnapshot.Assignment assignment : candidate) {
            if (candidateById.put(assignment.assignmentId(), assignment) != null) {
                throw invalidResponse("候选包含重复 assignmentId");
            }
        }

        for (PlanSnapshot.Assignment locked : base.assignments().stream().filter(PlanSnapshot.Assignment::locked).toList()) {
            if (!locked.equals(candidateById.get(locked.assignmentId()))) {
                throw invalidResponse("AI 修改或删除了锁定任务 " + locked.assignmentId());
            }
        }

        HashSet<UUID> trackIds = new HashSet<>(base.tracks().stream().map(PlanSnapshot.ExecutionTrack::trackId).toList());
        if (candidate.stream().anyMatch(assignment -> !trackIds.contains(assignment.trackId()))) {
            throw invalidResponse("AI 引用了计划之外的轨道");
        }
    }

    private ApiException invalidResponse(String detail) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "AI_RESPONSE_INVALID", "AI 候选响应无效：" + detail);
    }

    public record AiPayload(
            List<PlanSnapshot.Assignment> assignments,
            List<String> reasons,
            List<String> warnings) {
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
