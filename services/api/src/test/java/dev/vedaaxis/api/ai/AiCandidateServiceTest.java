package dev.vedaaxis.api.ai;

import dev.vedaaxis.api.common.ApiException;
import dev.vedaaxis.api.plan.ConfirmationStrategy;
import dev.vedaaxis.api.plan.PlanSnapshot;
import dev.vedaaxis.api.plan.TrackMode;
import dev.vedaaxis.api.plan.TrackSlot;
import dev.vedaaxis.api.rule.AbilityCatalog;
import dev.vedaaxis.api.rule.AbilityDefinition;
import dev.vedaaxis.api.rule.DamageEstimateAnalysisService;
import dev.vedaaxis.api.rule.MitigationEffectProfile;
import dev.vedaaxis.api.rule.PlanRuleEngine;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiCandidateServiceTest {
    @Test
    void focusedModeRejectsChangesOutsideTheFocusTrack() {
        UUID focusTrackId = UUID.fromString("10000000-0000-4000-8000-000000000001");
        UUID otherTrackId = UUID.fromString("10000000-0000-4000-8000-000000000002");
        UUID mechanicId = UUID.fromString("20000000-0000-4000-8000-000000000001");
        PlanSnapshot.Assignment focusAssignment = assignment(
                UUID.fromString("30000000-0000-4000-8000-000000000001"), mechanicId, focusTrackId, 100);
        PlanSnapshot.Assignment otherAssignment = assignment(
                UUID.fromString("30000000-0000-4000-8000-000000000002"), mechanicId, otherTrackId, 100);
        PlanSnapshot.Assignment changedOtherAssignment = new PlanSnapshot.Assignment(
                otherAssignment.assignmentId(), otherAssignment.mechanicId(), otherAssignment.trackId(),
                otherAssignment.actionId(), null, 0, 2_000, 3_000, 4_000,
                false, ConfirmationStrategy.STATUS_APPLY, List.of(), null);
        PlanSnapshot snapshot = snapshot(
                List.of(track(focusTrackId, TrackSlot.H2), track(otherTrackId, TrackSlot.MT)),
                List.of(mechanic(mechanicId)),
                List.of(focusAssignment, otherAssignment));
        AiCandidateService service = serviceWithCatalog(Map.of(100L, directAbility(100)), List.of());

        assertThatThrownBy(() -> service.enforceSafety(
                snapshot,
                List.of(focusAssignment, changedOtherAssignment),
                AiCandidateService.OptimizationMode.FOCUSED,
                focusTrackId,
                new AiCandidateService.AiSafetyOptions(false, true)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("AI_RESPONSE_INVALID"));
    }

    @Test
    void rejectsSupportOnlyChangesOnGreenMechanics() {
        UUID trackId = UUID.fromString("10000000-0000-4000-8000-000000000001");
        UUID mechanicId = UUID.fromString("20000000-0000-4000-8000-000000000001");
        PlanSnapshot snapshot = snapshot(
                List.of(track(trackId, TrackSlot.H2)),
                List.of(mechanic(mechanicId)),
                List.of());
        PlanSnapshot.Assignment candidate = assignment(
                UUID.fromString("30000000-0000-4000-8000-000000000001"), mechanicId, trackId, 200);
        DamageEstimateAnalysisService.MechanicEstimate greenEstimate =
                new DamageEstimateAnalysisService.MechanicEstimate(
                        mechanicId, "CALCULATED", 100_000L, 60_000L, 0.4,
                        DamageEstimateAnalysisService.RiskLevel.GREEN,
                        trackId, TrackSlot.H2, 6,
                        PlanSnapshot.DamageStatistic.P95, "test", List.of());
        AiCandidateService service = serviceWithCatalog(Map.of(200L, supportOnlyAbility(200)), List.of(greenEstimate));

        assertThatThrownBy(() -> service.enforceSafety(
                snapshot,
                List.of(candidate),
                AiCandidateService.OptimizationMode.GLOBAL,
                null,
                new AiCandidateService.AiSafetyOptions(false, true)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("AI_RESPONSE_INVALID"));
    }

    @Test
    void rejectsAccountsOutsideConfiguredAiAllowlist() {
        UUID ownerId = UUID.fromString("60000000-0000-4000-8000-000000000001");
        UUID allowedUserId = UUID.fromString("60000000-0000-4000-8000-000000000002");
        AiCandidateService service = serviceWithCatalog(
                Map.of(100L, directAbility(100)),
                List.of(),
                allowedUserId.toString());

        assertThatThrownBy(() -> service.generate(
                ownerId,
                UUID.fromString("40000000-0000-4000-8000-000000000001"),
                "",
                AiCandidateService.OptimizationMode.GLOBAL,
                null,
                false,
                false,
                null))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("AI_NOT_ENABLED_FOR_ACCOUNT"));
    }

    @Test
    void preserveExistingAssignmentsRejectsUpdatesToUnlockedAssignments() {
        UUID trackId = UUID.fromString("10000000-0000-4000-8000-000000000001");
        UUID mechanicId = UUID.fromString("20000000-0000-4000-8000-000000000001");
        PlanSnapshot.Assignment original = assignment(
                UUID.fromString("30000000-0000-4000-8000-000000000001"), mechanicId, trackId, 100);
        PlanSnapshot.Assignment updated = new PlanSnapshot.Assignment(
                original.assignmentId(), original.mechanicId(), original.trackId(),
                original.actionId(), null, 0, 2_000, 3_000, 4_000,
                false, ConfirmationStrategy.STATUS_APPLY, List.of(), null);
        PlanSnapshot snapshot = snapshot(
                List.of(track(trackId, TrackSlot.H2)),
                List.of(mechanic(mechanicId)),
                List.of(original));
        AiCandidateService service = serviceWithCatalog(Map.of(100L, directAbility(100)), List.of());

        assertThatThrownBy(() -> service.enforceSafety(
                snapshot,
                List.of(updated),
                AiCandidateService.OptimizationMode.GLOBAL,
                null,
                new AiCandidateService.AiSafetyOptions(true, true)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("AI_RESPONSE_INVALID"));
    }

    @Test
    void disallowGcdActionsRejectsNewGcdAssignments() {
        UUID trackId = UUID.fromString("10000000-0000-4000-8000-000000000001");
        UUID mechanicId = UUID.fromString("20000000-0000-4000-8000-000000000001");
        PlanSnapshot snapshot = snapshot(
                List.of(track(trackId, TrackSlot.H2)),
                List.of(mechanic(mechanicId)),
                List.of());
        PlanSnapshot.Assignment candidate = assignment(
                UUID.fromString("30000000-0000-4000-8000-000000000001"), mechanicId, trackId, 300);
        AiCandidateService service = serviceWithCatalog(Map.of(300L, gcdAbility(300)), List.of());

        assertThatThrownBy(() -> service.enforceSafety(
                snapshot,
                List.of(candidate),
                AiCandidateService.OptimizationMode.GLOBAL,
                null,
                new AiCandidateService.AiSafetyOptions(false, false)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("AI_RESPONSE_INVALID"));
    }

    @Test
    void appliesAiDeltaOperationsToBaseAssignments() {
        UUID trackId = UUID.fromString("10000000-0000-4000-8000-000000000001");
        UUID mechanicId = UUID.fromString("20000000-0000-4000-8000-000000000001");
        PlanSnapshot.Assignment original = assignment(
                UUID.fromString("30000000-0000-4000-8000-000000000001"), mechanicId, trackId, 100);
        PlanSnapshot.Assignment updated = new PlanSnapshot.Assignment(
                original.assignmentId(), original.mechanicId(), original.trackId(),
                original.actionId(), null, 0, 2_000, 3_000, 4_000,
                false, ConfirmationStrategy.STATUS_APPLY, List.of(), null);
        PlanSnapshot snapshot = snapshot(
                List.of(track(trackId, TrackSlot.H2)),
                List.of(mechanic(mechanicId)),
                List.of(original));
        AiCandidateService service = serviceWithCatalog(Map.of(100L, directAbility(100)), List.of());

        List<PlanSnapshot.Assignment> result = service.resolveCandidateAssignments(
                snapshot,
                new AiCandidateService.AiPayload(
                        null,
                        List.of(new AiCandidateService.AiOperation("UPDATE", original.assignmentId(), updated)),
                        List.of("moved earlier"),
                        List.of()));

        assertThat(result).containsExactly(updated);
    }

    @Test
    void addDeltaOperationsGenerateMissingAssignmentIds() {
        UUID trackId = UUID.fromString("10000000-0000-4000-8000-000000000001");
        UUID mechanicId = UUID.fromString("20000000-0000-4000-8000-000000000001");
        PlanSnapshot snapshot = snapshot(
                List.of(track(trackId, TrackSlot.H2)),
                List.of(mechanic(mechanicId)),
                List.of());
        PlanSnapshot.Assignment first = assignment(null, mechanicId, trackId, 100);
        PlanSnapshot.Assignment second = assignment(null, mechanicId, trackId, 100);
        AiCandidateService service = serviceWithCatalog(Map.of(100L, directAbility(100)), List.of());

        List<PlanSnapshot.Assignment> result = service.resolveCandidateAssignments(
                snapshot,
                new AiCandidateService.AiPayload(
                        null,
                        List.of(
                                new AiCandidateService.AiOperation("ADD", null, first),
                                new AiCandidateService.AiOperation("ADD", null, second)),
                        List.of("added unused resources"),
                        List.of()));

        assertThat(result)
                .hasSize(2)
                .extracting(PlanSnapshot.Assignment::assignmentId)
                .doesNotContainNull()
                .doesNotHaveDuplicates();
    }

    @Test
    void rejectsAiDeltaOperationForUnknownAssignment() {
        UUID trackId = UUID.fromString("10000000-0000-4000-8000-000000000001");
        UUID mechanicId = UUID.fromString("20000000-0000-4000-8000-000000000001");
        UUID unknownAssignmentId = UUID.fromString("30000000-0000-4000-8000-000000000099");
        PlanSnapshot.Assignment original = assignment(
                UUID.fromString("30000000-0000-4000-8000-000000000001"), mechanicId, trackId, 100);
        PlanSnapshot snapshot = snapshot(
                List.of(track(trackId, TrackSlot.H2)),
                List.of(mechanic(mechanicId)),
                List.of(original));
        AiCandidateService service = serviceWithCatalog(Map.of(100L, directAbility(100)), List.of());

        assertThatThrownBy(() -> service.resolveCandidateAssignments(
                snapshot,
                new AiCandidateService.AiPayload(
                        null,
                        List.of(new AiCandidateService.AiOperation("DELETE", unknownAssignmentId, null)),
                        List.of(),
                        List.of())))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("AI_RESPONSE_INVALID"));
    }

    @Test
    void extractsAssistantContentFromStringContent() {
        AiCandidateService service = serviceWithCatalog(Map.of(100L, directAbility(100)), List.of());

        String content = service.extractAssistantContent(Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of("content", "{\"operations\":[]}"),
                        "finish_reason", "stop"))));

        assertThat(content).isEqualTo("{\"operations\":[]}");
    }

    @Test
    void extractsAssistantContentFromStructuredContentParts() {
        AiCandidateService service = serviceWithCatalog(Map.of(100L, directAbility(100)), List.of());

        String content = service.extractAssistantContent(Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of(
                                "content", List.of(
                                        Map.of("type", "text", "text", "{\"operations\":"),
                                        Map.of("type", "text", "text", "[]}"))),
                        "finish_reason", "stop"))));

        assertThat(content).isEqualTo("{\"operations\":\n[]}");
    }

    @Test
    void localizesEmptyFocusedCandidateFallbackToChinese() {
        UUID trackId = UUID.fromString("10000000-0000-4000-8000-000000000001");
        UUID mechanicId = UUID.fromString("20000000-0000-4000-8000-000000000001");
        PlanSnapshot snapshot = snapshot(
                List.of(new PlanSnapshot.ExecutionTrack(trackId, TrackSlot.H2, Set.of(), "贤者")),
                List.of(mechanic(mechanicId)),
                List.of());
        AiCandidateService service = serviceWithCatalog(Map.of(100L, directAbility(100)), List.of());

        AiCandidateService.AiPayload localized = service.localizeEmptyCandidateText(
                new AiCandidateService.AiPayload(
                        null,
                        List.of(),
                        List.of("This is a FOCUSED optimization with no additions."),
                        List.of("No unlocked assignments could be added.")),
                AiCandidateService.AiResponseLanguage.ZH_CN,
                AiCandidateService.OptimizationMode.FOCUSED,
                trackId,
                new AiCandidateService.AiSafetyOptions(true, false),
                snapshot);

        assertThat(localized.reasons()).containsExactly("当前是指向优化（H2 · 贤者），在当前限制下 AI 没有找到值得新增的安排。");
        assertThat(localized.warnings().getFirst()).contains("只新增");
    }

    @Test
    void keepsEmptyCandidateTextWhenLanguageAlreadyMatches() {
        UUID trackId = UUID.fromString("10000000-0000-4000-8000-000000000001");
        UUID mechanicId = UUID.fromString("20000000-0000-4000-8000-000000000001");
        PlanSnapshot snapshot = snapshot(
                List.of(track(trackId, TrackSlot.H2)),
                List.of(mechanic(mechanicId)),
                List.of());
        AiCandidateService service = serviceWithCatalog(Map.of(100L, directAbility(100)), List.of());
        AiCandidateService.AiPayload payload = new AiCandidateService.AiPayload(
                null,
                List.of(),
                List.of("当前没有需要新增的安排。"),
                List.of("可以调整优化目标后重试。"));

        AiCandidateService.AiPayload localized = service.localizeEmptyCandidateText(
                payload,
                AiCandidateService.AiResponseLanguage.ZH_CN,
                AiCandidateService.OptimizationMode.FOCUSED,
                trackId,
                new AiCandidateService.AiSafetyOptions(true, false),
                snapshot);

        assertThat(localized).isSameAs(payload);
    }

    private AiCandidateService serviceWithCatalog(
            Map<Long, AbilityDefinition> abilities,
            List<DamageEstimateAnalysisService.MechanicEstimate> estimates) {
        return serviceWithCatalog(abilities, estimates, "");
    }

    private AiCandidateService serviceWithCatalog(
            Map<Long, AbilityDefinition> abilities,
            List<DamageEstimateAnalysisService.MechanicEstimate> estimates,
            String allowedUserIds) {
        AbilityCatalog abilityCatalog = mock(AbilityCatalog.class);
        DamageEstimateAnalysisService damageEstimateAnalysisService = mock(DamageEstimateAnalysisService.class);
        when(abilityCatalog.load()).thenReturn(abilities);
        when(abilityCatalog.all()).thenReturn(abilities.values().stream().toList());
        when(damageEstimateAnalysisService.preview(org.mockito.ArgumentMatchers.any())).thenReturn(estimates);
        return new AiCandidateService(
                mock(dev.vedaaxis.api.plan.PlanService.class),
                mock(PlanRuleEngine.class),
                abilityCatalog,
                damageEstimateAnalysisService,
                new ObjectMapper(),
                RestClient.builder(),
                "https://example.invalid",
                "test-key",
                "test-model",
                allowedUserIds,
                1_000);
    }

    private PlanSnapshot snapshot(
            List<PlanSnapshot.ExecutionTrack> tracks,
            List<PlanSnapshot.TimelineMechanic> mechanics,
            List<PlanSnapshot.Assignment> assignments) {
        return new PlanSnapshot(
                "1.3", "0.1.11",
                UUID.fromString("40000000-0000-4000-8000-000000000001"),
                1,
                UUID.fromString("40000000-0000-4000-8000-000000000002"),
                1,
                UUID.fromString("40000000-0000-4000-8000-000000000003"),
                1363,
                "TEST",
                TrackMode.EIGHT,
                new PlanSnapshot.Source(PlanSnapshot.SourceKind.PERSONAL, null, PlanSnapshot.Confidence.UNVERIFIED),
                List.of(new PlanSnapshot.TimelinePhase(
                        UUID.fromString("50000000-0000-4000-8000-000000000001"),
                        null, "P1", 0, PlanSnapshot.Confidence.UNVERIFIED)),
                mechanics,
                List.of(),
                tracks,
                assignments);
    }

    private PlanSnapshot.ExecutionTrack track(UUID trackId, TrackSlot slot) {
        return new PlanSnapshot.ExecutionTrack(trackId, slot, Set.of(), slot.name());
    }

    private PlanSnapshot.TimelineMechanic mechanic(UUID mechanicId) {
        return new PlanSnapshot.TimelineMechanic(
                mechanicId, null, "P1", "Raidwide", 10_000, 0,
                PlanSnapshot.MechanicType.RAIDWIDE,
                PlanSnapshot.DamageType.MAGICAL,
                "全体",
                1L,
                PlanSnapshot.Confidence.POC_PENDING,
                new PlanSnapshot.DamageProfile(
                        100_000,
                        PlanSnapshot.DamageBasis.OBSERVED_TARGET_ADJUSTED,
                        6,
                        PlanSnapshot.DamageStatistic.P95,
                        "test",
                        PlanSnapshot.Confidence.POC_PENDING));
    }

    private PlanSnapshot.Assignment assignment(UUID assignmentId, UUID mechanicId, UUID trackId, long actionId) {
        return new PlanSnapshot.Assignment(
                assignmentId, mechanicId, trackId, actionId, null,
                0, 1_000, 2_000, 10_000,
                false, ConfirmationStrategy.STATUS_APPLY, List.of(), null);
    }

    private AbilityDefinition directAbility(long actionId) {
        return ability(actionId, MitigationEffectProfile.CalculationReadiness.DIRECT_REDUCTION);
    }

    private AbilityDefinition gcdAbility(long actionId) {
        return ability(
                actionId,
                MitigationEffectProfile.CalculationReadiness.REQUIRES_HEALING_STATS,
                AbilityDefinition.CastCategory.GCD);
    }

    private AbilityDefinition supportOnlyAbility(long actionId) {
        return ability(actionId, MitigationEffectProfile.CalculationReadiness.REQUIRES_HEALING_STATS);
    }

    private AbilityDefinition ability(long actionId, MitigationEffectProfile.CalculationReadiness readiness) {
        return ability(actionId, readiness, AbilityDefinition.CastCategory.OGCD);
    }

    private AbilityDefinition ability(
            long actionId,
            MitigationEffectProfile.CalculationReadiness readiness,
            AbilityDefinition.CastCategory castCategory) {
        return new AbilityDefinition(
                actionId,
                "Test Ability",
                "",
                Set.of(),
                60_000,
                1,
                15_000,
                ConfirmationStrategy.STATUS_APPLY,
                "test",
                "REVIEWED",
                castCategory,
                new MitigationEffectProfile(
                        MitigationEffectProfile.Scope.PARTY,
                        readiness == MitigationEffectProfile.CalculationReadiness.DIRECT_REDUCTION ? 10 : 0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        false,
                        "",
                        readiness,
                        List.of(),
                        "test",
                        "REVIEWED"));
    }
}
