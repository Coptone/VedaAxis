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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DamageEstimateAnalysisServiceTest {
    @Test
    void classifiesContinuousRiskBandsAtTheirBoundaries() {
        assertThat(DamageEstimateAnalysisService.risk(PlanSnapshot.MechanicType.RAIDWIDE, 100_000))
                .isEqualTo(DamageEstimateAnalysisService.RiskLevel.GREEN);
        assertThat(DamageEstimateAnalysisService.risk(PlanSnapshot.MechanicType.RAIDWIDE, 100_001))
                .isEqualTo(DamageEstimateAnalysisService.RiskLevel.YELLOW);
        assertThat(DamageEstimateAnalysisService.risk(PlanSnapshot.MechanicType.RAIDWIDE, 190_001))
                .isEqualTo(DamageEstimateAnalysisService.RiskLevel.RED);
        assertThat(DamageEstimateAnalysisService.risk(PlanSnapshot.MechanicType.TANK_BUSTER, 200_000))
                .isEqualTo(DamageEstimateAnalysisService.RiskLevel.GREEN);
        assertThat(DamageEstimateAnalysisService.risk(PlanSnapshot.MechanicType.TANK_BUSTER, 250_000))
                .isEqualTo(DamageEstimateAnalysisService.RiskLevel.YELLOW);
        assertThat(DamageEstimateAnalysisService.risk(PlanSnapshot.MechanicType.TANK_BUSTER, 290_000))
                .isEqualTo(DamageEstimateAnalysisService.RiskLevel.RED);
    }

    @Test
    void raidwideUsesWorstTrackAfterCurrentMitigationArrangement() {
        AbilityCatalog catalog = mock(AbilityCatalog.class);
        AbilityDefinition rampart = new AbilityDefinition(
                7531, "铁壁", "", Set.of(19), 90_000, 1, 20_000,
                ConfirmationStrategy.STATUS_APPLY, "test", "REVIEWED",
                new AbilityEffectCatalog().profile(7531));
        when(catalog.load()).thenReturn(Map.of(7531L, rampart));
        SurvivabilityAnalysisService survivability = new SurvivabilityAnalysisService(catalog);
        DamageEstimateAnalysisService service = new DamageEstimateAnalysisService(survivability, catalog);
        UUID mechanicId = UUID.randomUUID();
        UUID mt = UUID.randomUUID();
        UUID healer = UUID.randomUUID();
        PlanSnapshot.DamageProfile profile = new PlanSnapshot.DamageProfile(
                180_000, PlanSnapshot.DamageBasis.OBSERVED_TARGET_ADJUSTED, 40,
                PlanSnapshot.DamageStatistic.P95, "multi report", PlanSnapshot.Confidence.POC_PENDING);
        PlanSnapshot.Assignment rampartAssignment = new PlanSnapshot.Assignment(
                UUID.randomUUID(), mechanicId, mt, 7531, null,
                0, 0, 9_000, 10_000, false,
                ConfirmationStrategy.STATUS_APPLY, List.of(), null);
        PlanSnapshot snapshot = new PlanSnapshot(
                "1.3", "0.1.7", UUID.randomUUID(), 1, UUID.randomUUID(), 1,
                UUID.randomUUID(), 1234, "TEST", TrackMode.EIGHT,
                new PlanSnapshot.Source(PlanSnapshot.SourceKind.PERSONAL, null, PlanSnapshot.Confidence.UNVERIFIED),
                List.of(),
                List.of(new PlanSnapshot.TimelineMechanic(
                        mechanicId, null, "P1", "Raidwide", 10_000, 0,
                        PlanSnapshot.MechanicType.RAIDWIDE, PlanSnapshot.DamageType.MAGICAL,
                        "全体", 100L, PlanSnapshot.Confidence.POC_PENDING, profile)),
                List.of(),
                List.of(
                        new PlanSnapshot.ExecutionTrack(mt, TrackSlot.MT, Set.of(19), "MT"),
                        new PlanSnapshot.ExecutionTrack(healer, TrackSlot.H1, Set.of(24), "H1")),
                List.of(rampartAssignment));

        DamageEstimateAnalysisService.MechanicEstimate result = service.preview(snapshot).getFirst();

        assertThat(result.damageAfterMitigation()).isEqualTo(180_000);
        assertThat(result.worstTrackSlot()).isEqualTo(TrackSlot.H1);
        assertThat(result.riskLevel()).isEqualTo(DamageEstimateAnalysisService.RiskLevel.YELLOW);
        assertThat(result.notices()).anyMatch(value -> value.contains("整段机制"));
        verify(catalog, times(1)).load();
    }

    @Test
    void autoAttackDamageIsCalculatedOnlyAgainstTankTracks() {
        AbilityCatalog catalog = mock(AbilityCatalog.class);
        AbilityDefinition rampart = new AbilityDefinition(
                7531, "铁壁", "", Set.of(19), 90_000, 1, 20_000,
                ConfirmationStrategy.STATUS_APPLY, "test", "REVIEWED",
                new AbilityEffectCatalog().profile(7531));
        when(catalog.load()).thenReturn(Map.of(7531L, rampart));
        DamageEstimateAnalysisService service = new DamageEstimateAnalysisService(
                new SurvivabilityAnalysisService(catalog), catalog);
        UUID mechanicId = UUID.randomUUID();
        UUID mt = UUID.randomUUID();
        UUID st = UUID.randomUUID();
        UUID healer = UUID.randomUUID();
        PlanSnapshot.DamageProfile profile = new PlanSnapshot.DamageProfile(
                180_000, PlanSnapshot.DamageBasis.OBSERVED_TARGET_ADJUSTED, 40,
                PlanSnapshot.DamageStatistic.P95, "multi report", PlanSnapshot.Confidence.POC_PENDING);
        PlanSnapshot snapshot = new PlanSnapshot(
                "1.3", "0.1.7", UUID.randomUUID(), 1, UUID.randomUUID(), 1,
                UUID.randomUUID(), 1234, "TEST", TrackMode.EIGHT,
                new PlanSnapshot.Source(PlanSnapshot.SourceKind.PERSONAL, null, PlanSnapshot.Confidence.UNVERIFIED),
                List.of(),
                List.of(new PlanSnapshot.TimelineMechanic(
                        mechanicId, null, "P1", "攻击 x4", 1_000, 0,
                        PlanSnapshot.MechanicType.MECHANIC, PlanSnapshot.DamageType.PHYSICAL,
                        "当前一仇", 49746L, PlanSnapshot.Confidence.POC_PENDING, profile)),
                List.of(),
                List.of(
                        new PlanSnapshot.ExecutionTrack(mt, TrackSlot.MT, Set.of(19), "MT"),
                        new PlanSnapshot.ExecutionTrack(st, TrackSlot.ST, Set.of(32), "ST"),
                        new PlanSnapshot.ExecutionTrack(healer, TrackSlot.H1, Set.of(24), "H1")),
                List.of(new PlanSnapshot.Assignment(
                        UUID.randomUUID(), mechanicId, mt, 7531, null,
                        0, 4_000, 6_000, 6_000, false,
                        ConfirmationStrategy.STATUS_APPLY, List.of(), null)));

        DamageEstimateAnalysisService.MechanicEstimate result = service.preview(snapshot).getFirst();

        assertThat(result.damageAfterMitigation()).isEqualTo(144_000);
        assertThat(result.worstTrackSlot()).isEqualTo(TrackSlot.MT);
        assertThat(result.riskLevel()).isEqualTo(DamageEstimateAnalysisService.RiskLevel.UNCLASSIFIED);
        verify(catalog, times(1)).load();
    }

    @Test
    void explicitOffTankTargetIsCalculatedAgainstOffTankTrack() {
        AbilityCatalog catalog = mock(AbilityCatalog.class);
        AbilityDefinition rampart = new AbilityDefinition(
                7531, "铁壁", "", Set.of(19), 90_000, 1, 20_000,
                ConfirmationStrategy.STATUS_APPLY, "test", "REVIEWED",
                new AbilityEffectCatalog().profile(7531));
        when(catalog.load()).thenReturn(Map.of(7531L, rampart));
        DamageEstimateAnalysisService service = new DamageEstimateAnalysisService(
                new SurvivabilityAnalysisService(catalog), catalog);
        UUID mechanicId = UUID.randomUUID();
        UUID mt = UUID.randomUUID();
        UUID st = UUID.randomUUID();
        PlanSnapshot.DamageProfile profile = new PlanSnapshot.DamageProfile(
                100_000, PlanSnapshot.DamageBasis.OBSERVED_TARGET_ADJUSTED, 40,
                PlanSnapshot.DamageStatistic.P95, "multi report", PlanSnapshot.Confidence.POC_PENDING);
        PlanSnapshot snapshot = new PlanSnapshot(
                "1.3", "0.1.7", UUID.randomUUID(), 1, UUID.randomUUID(), 1,
                UUID.randomUUID(), 1234, "TEST", TrackMode.EIGHT,
                new PlanSnapshot.Source(PlanSnapshot.SourceKind.PERSONAL, null, PlanSnapshot.Confidence.UNVERIFIED),
                List.of(),
                List.of(new PlanSnapshot.TimelineMechanic(
                        mechanicId, null, "P1", "攻击 x4", 1_000, 0,
                        PlanSnapshot.MechanicType.MECHANIC, PlanSnapshot.DamageType.PHYSICAL,
                        "ST", 49746L, PlanSnapshot.Confidence.POC_PENDING, profile)),
                List.of(),
                List.of(
                        new PlanSnapshot.ExecutionTrack(mt, TrackSlot.MT, Set.of(19), "MT"),
                        new PlanSnapshot.ExecutionTrack(st, TrackSlot.ST, Set.of(32), "ST")),
                List.of(new PlanSnapshot.Assignment(
                        UUID.randomUUID(), mechanicId, mt, 7531, null,
                        0, 4_000, 6_000, 6_000, false,
                        ConfirmationStrategy.STATUS_APPLY, List.of(), null)));

        DamageEstimateAnalysisService.MechanicEstimate result = service.preview(snapshot).getFirst();

        assertThat(result.damageAfterMitigation()).isEqualTo(100_000);
        assertThat(result.modeledReduction()).isZero();
        assertThat(result.worstTrackSlot()).isEqualTo(TrackSlot.ST);
        verify(catalog, times(1)).load();
    }

    @Test
    void selectedCurrentEnmityTargetIsCalculatedAgainstThatTankTrack() {
        AbilityCatalog catalog = mock(AbilityCatalog.class);
        AbilityDefinition rampart = new AbilityDefinition(
                7531, "铁壁", "", Set.of(19), 90_000, 1, 20_000,
                ConfirmationStrategy.STATUS_APPLY, "test", "REVIEWED",
                new AbilityEffectCatalog().profile(7531));
        when(catalog.load()).thenReturn(Map.of(7531L, rampart));
        DamageEstimateAnalysisService service = new DamageEstimateAnalysisService(
                new SurvivabilityAnalysisService(catalog), catalog);
        UUID mechanicId = UUID.randomUUID();
        UUID mt = UUID.randomUUID();
        UUID st = UUID.randomUUID();
        PlanSnapshot.DamageProfile profile = new PlanSnapshot.DamageProfile(
                100_000, PlanSnapshot.DamageBasis.OBSERVED_TARGET_ADJUSTED, 40,
                PlanSnapshot.DamageStatistic.P95, "multi report", PlanSnapshot.Confidence.POC_PENDING);
        PlanSnapshot snapshot = new PlanSnapshot(
                "1.3", "0.1.7", UUID.randomUUID(), 1, UUID.randomUUID(), 1,
                UUID.randomUUID(), 1234, "TEST", TrackMode.EIGHT,
                new PlanSnapshot.Source(PlanSnapshot.SourceKind.PERSONAL, null, PlanSnapshot.Confidence.UNVERIFIED),
                List.of(),
                List.of(new PlanSnapshot.TimelineMechanic(
                        mechanicId, null, "P1", "攻击 x4", 1_000, 0,
                        PlanSnapshot.MechanicType.MECHANIC, PlanSnapshot.DamageType.PHYSICAL,
                        "当前一仇:ST", 49746L, PlanSnapshot.Confidence.POC_PENDING, profile)),
                List.of(),
                List.of(
                        new PlanSnapshot.ExecutionTrack(mt, TrackSlot.MT, Set.of(19), "MT"),
                        new PlanSnapshot.ExecutionTrack(st, TrackSlot.ST, Set.of(32), "ST")),
                List.of(new PlanSnapshot.Assignment(
                        UUID.randomUUID(), mechanicId, mt, 7531, null,
                        0, 4_000, 6_000, 6_000, false,
                        ConfirmationStrategy.STATUS_APPLY, List.of(), null)));

        DamageEstimateAnalysisService.MechanicEstimate result = service.preview(snapshot).getFirst();

        assertThat(result.damageAfterMitigation()).isEqualTo(100_000);
        assertThat(result.modeledReduction()).isZero();
        assertThat(result.worstTrackSlot()).isEqualTo(TrackSlot.ST);
        verify(catalog, times(1)).load();
    }
}
