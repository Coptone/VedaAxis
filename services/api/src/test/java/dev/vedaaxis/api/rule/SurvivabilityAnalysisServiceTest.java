package dev.vedaaxis.api.rule;

import dev.vedaaxis.api.plan.ConfirmationStrategy;
import dev.vedaaxis.api.plan.PlanSnapshot;
import dev.vedaaxis.api.plan.TrackMode;
import dev.vedaaxis.api.plan.TrackSlot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SurvivabilityAnalysisServiceTest {
    private final UUID mechanicId = UUID.randomUUID();
    private final UUID targetTrackId = UUID.randomUUID();

    @Test
    void refusesNumericalAnalysisWithoutDamageCalibration() {
        AbilityCatalog catalog = mock(AbilityCatalog.class);
        SurvivabilityAnalysisService service = new SurvivabilityAnalysisService(catalog);

        SurvivabilityAnalysisService.Analysis result = service.analyze(
                snapshot(null, List.of()), mechanicId,
                new SurvivabilityAnalysisService.Request(targetTrackId, 100_000, 100_000, true, true));

        assertThat(result.status()).isEqualTo("CALIBRATION_REQUIRED");
        assertThat(result.incomingDamage()).isNull();
        assertThat(result.hardGuarantee()).isFalse();
    }

    @Test
    void resolvesSelfMitigationForTheSelectedTrack() {
        AbilityCatalog catalog = mock(AbilityCatalog.class);
        AbilityDefinition rampart = new AbilityDefinition(
                7531, "铁壁", "", Set.of(19), 90_000, 1, 20_000,
                ConfirmationStrategy.STATUS_APPLY, "test", "REVIEWED",
                new AbilityEffectCatalog().profile(7531));
        when(catalog.load()).thenReturn(Map.of(7531L, rampart));
        SurvivabilityAnalysisService service = new SurvivabilityAnalysisService(catalog);
        PlanSnapshot.Assignment assignment = new PlanSnapshot.Assignment(
                UUID.randomUUID(), mechanicId, targetTrackId, 7531, null,
                0, 0, 9_000, 10_000, false,
                ConfirmationStrategy.STATUS_APPLY, List.of(), null);
        PlanSnapshot.Assignment duplicateAssignment = new PlanSnapshot.Assignment(
                UUID.randomUUID(), mechanicId, targetTrackId, 7531, null,
                0, 0, 9_000, 10_000, false,
                ConfirmationStrategy.STATUS_APPLY, List.of(), null);
        PlanSnapshot.DamageProfile profile = new PlanSnapshot.DamageProfile(
                100_000, PlanSnapshot.DamageBasis.OBSERVED_TARGET_ADJUSTED, 10,
                PlanSnapshot.DamageStatistic.MAX_OBSERVED, "test sample", PlanSnapshot.Confidence.POC_PENDING);

        SurvivabilityAnalysisService.Analysis result = service.analyze(
                snapshot(profile, List.of(assignment, duplicateAssignment)), mechanicId,
                new SurvivabilityAnalysisService.Request(targetTrackId, 100_000, 100_000, true, true));

        assertThat(result.status()).isEqualTo("SURVIVES_WITH_MODELED_EFFECTS");
        assertThat(result.damageAfterMitigation()).isEqualTo(80_000);
        assertThat(result.remainingHp()).isEqualTo(20_000);
        assertThat(result.hardGuarantee()).isFalse();
        assertThat(result.notices()).anyMatch(value -> value.contains("按同名效果不叠加"));
        assertThat(result.notices()).anyMatch(value -> value.contains("不能作为跨装备/跨队伍"));
    }

    @Test
    void includesMitigationScheduledOnAnEarlierTimelineRowWhenItStillCoversTheHit() {
        AbilityCatalog catalog = mock(AbilityCatalog.class);
        AbilityDefinition rampart = new AbilityDefinition(
                7531, "铁壁", "", Set.of(19), 90_000, 1, 20_000,
                ConfirmationStrategy.STATUS_APPLY, "test", "REVIEWED",
                new AbilityEffectCatalog().profile(7531));
        when(catalog.load()).thenReturn(Map.of(7531L, rampart));
        SurvivabilityAnalysisService service = new SurvivabilityAnalysisService(catalog);
        UUID earlierRowId = UUID.randomUUID();
        PlanSnapshot.Assignment assignment = new PlanSnapshot.Assignment(
                UUID.randomUUID(), earlierRowId, targetTrackId, 7531, null,
                0, 0, 1_000, 1_000, false,
                ConfirmationStrategy.STATUS_APPLY, List.of(), null);
        PlanSnapshot.DamageProfile profile = new PlanSnapshot.DamageProfile(
                100_000, PlanSnapshot.DamageBasis.OBSERVED_TARGET_ADJUSTED, 10,
                PlanSnapshot.DamageStatistic.MAX_OBSERVED, "test sample", PlanSnapshot.Confidence.POC_PENDING);

        SurvivabilityAnalysisService.Analysis result = service.analyze(
                snapshot(profile, List.of(assignment)), mechanicId,
                new SurvivabilityAnalysisService.Request(targetTrackId, 100_000, 100_000, true, true));

        assertThat(result.damageAfterMitigation()).isEqualTo(80_000);
    }

    private PlanSnapshot snapshot(PlanSnapshot.DamageProfile profile, List<PlanSnapshot.Assignment> assignments) {
        return new PlanSnapshot(
                "1.3", "0.1.7", UUID.randomUUID(), 1, UUID.randomUUID(), 1,
                UUID.randomUUID(), 1234, "TEST", TrackMode.EIGHT,
                new PlanSnapshot.Source(PlanSnapshot.SourceKind.PERSONAL, null, PlanSnapshot.Confidence.UNVERIFIED),
                List.of(),
                List.of(new PlanSnapshot.TimelineMechanic(
                        mechanicId, null, "P1", "Test hit", 10_000, 0,
                        PlanSnapshot.MechanicType.TANK_BUSTER, PlanSnapshot.DamageType.MAGICAL,
                        "MT", 999L, PlanSnapshot.Confidence.POC_PENDING, profile)),
                List.of(),
                List.of(new PlanSnapshot.ExecutionTrack(targetTrackId, TrackSlot.MT, Set.of(19), "MT")),
                assignments);
    }
}
