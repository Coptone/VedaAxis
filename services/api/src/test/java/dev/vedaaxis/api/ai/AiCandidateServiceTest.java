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
                focusTrackId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("非目标轨道");
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
                null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("低风险机制");
    }

    private AiCandidateService serviceWithCatalog(
            Map<Long, AbilityDefinition> abilities,
            List<DamageEstimateAnalysisService.MechanicEstimate> estimates) {
        AbilityCatalog abilityCatalog = mock(AbilityCatalog.class);
        DamageEstimateAnalysisService damageEstimateAnalysisService = mock(DamageEstimateAnalysisService.class);
        when(abilityCatalog.load()).thenReturn(abilities);
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
                "test-model");
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

    private AbilityDefinition supportOnlyAbility(long actionId) {
        return ability(actionId, MitigationEffectProfile.CalculationReadiness.REQUIRES_HEALING_STATS);
    }

    private AbilityDefinition ability(long actionId, MitigationEffectProfile.CalculationReadiness readiness) {
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
