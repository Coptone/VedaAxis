package dev.vedaaxis.api.rule;

import dev.vedaaxis.api.plan.ConfirmationStrategy;
import dev.vedaaxis.api.plan.PlanSnapshot;
import dev.vedaaxis.api.plan.TrackMode;
import dev.vedaaxis.api.plan.TrackSlot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlanRuleEngineTest {
    private static final long KERACHOLE = 24298L;
    private final PlanRuleEngine engine = new PlanRuleEngine(null);
    private final Map<Long, AbilityDefinition> catalog = Map.of(
            KERACHOLE,
            new AbilityDefinition(
                    KERACHOLE, "Kerachole", "ui/icon/003000/003666.tex", Set.of(40), 30_000, 1, 15_000,
                    ConfirmationStrategy.STATUS_APPLY, "test", "VERIFIED"));

    @Test
    void acceptsAValidEightTrackPlan() {
        PlanSnapshot snapshot = snapshot(List.of(assignment(20_000, 25_000, 30_000, 38_000)));

        RuleValidationResult result = engine.validate(snapshot, catalog);

        assertThat(result.valid()).isTrue();
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void rejectsCooldownConflict() {
        PlanSnapshot.Assignment first = assignment(20_000, 25_000, 30_000, 38_000);
        PlanSnapshot.Assignment second = assignment(35_000, 40_000, 44_000, 50_000);
        PlanSnapshot snapshot = snapshot(List.of(first, second));

        RuleValidationResult result = engine.validate(snapshot, catalog);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(RuleIssue::code).contains("COOLDOWN_CONFLICT");
    }

    @Test
    void rejectsWindowThatCannotGuaranteeCoverage() {
        PlanSnapshot snapshot = snapshot(List.of(assignment(5_000, 10_000, 20_000, 30_000)));

        RuleValidationResult result = engine.validate(snapshot, catalog);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(RuleIssue::code).contains("COVERAGE_GAP");
    }

    @Test
    void rejectsAssignmentThatReferencesUnknownImportedMechanic() {
        PlanSnapshot base = snapshot(List.of(assignment(20_000, 25_000, 30_000, 38_000)));
        PlanSnapshot withMechanic = new PlanSnapshot(
                base.schemaVersion(), base.minimumPluginVersion(), base.planId(), base.planVersion(),
                base.timelineId(), base.timelineVersion(), base.encounterId(), base.territoryId(), base.strategyTag(),
                base.trackMode(), base.source(), List.of(),
                List.of(new PlanSnapshot.TimelineMechanic(
                        UUID.randomUUID(), "m-1", "P1", "机制", 38_000, 0,
                        PlanSnapshot.MechanicType.RAIDWIDE, PlanSnapshot.DamageType.UNKNOWN, "全体", null,
                        PlanSnapshot.Confidence.POC_PENDING)),
                base.anchors(), base.tracks(), base.assignments());

        RuleValidationResult result = engine.validate(withMechanic, catalog);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(RuleIssue::code).contains("UNKNOWN_MECHANIC");
    }

    private PlanSnapshot snapshot(List<PlanSnapshot.Assignment> assignments) {
        List<PlanSnapshot.ExecutionTrack> tracks = new ArrayList<>();
        for (TrackSlot slot : TrackMode.EIGHT.orderedSlots()) {
            Set<Integer> jobs = slot == TrackSlot.H2 ? Set.of(40) : Set.of();
            tracks.add(new PlanSnapshot.ExecutionTrack(
                    slot == TrackSlot.H2 ? h2TrackId() : UUID.nameUUIDFromBytes(slot.name().getBytes()),
                    slot, jobs, slot.name()));
        }
        return new PlanSnapshot(
                "1.2", "0.1.5", UUID.randomUUID(), 1, UUID.randomUUID(), 1, UUID.randomUUID(),
                755, "test", TrackMode.EIGHT,
                new PlanSnapshot.Source(PlanSnapshot.SourceKind.PERSONAL, null, PlanSnapshot.Confidence.UNVERIFIED),
                List.of(), List.of(), List.of(), tracks, assignments);
    }

    private PlanSnapshot.Assignment assignment(long highlight, long earliest, long latest, long impact) {
        return new PlanSnapshot.Assignment(
                UUID.randomUUID(), UUID.randomUUID(), h2TrackId(), KERACHOLE, null,
                highlight, earliest, latest, impact, false, ConfirmationStrategy.STATUS_APPLY, List.of());
    }

    private UUID h2TrackId() {
        return UUID.nameUUIDFromBytes("H2".getBytes());
    }
}
