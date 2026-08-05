package dev.vedaaxis.api.timeline;

import dev.vedaaxis.api.plan.PlanSnapshot;

import java.time.Instant;
import java.util.List;

public record MSpecImportCandidate(
        String schemaVersion,
        String sourceUrl,
        String bossSlug,
        String specSlug,
        String bossDataUrl,
        String rankingDataUrl,
        Instant fetchedAt,
        List<PlanSnapshot.TimelinePhase> phases,
        List<PlanSnapshot.TimelineMechanic> mechanics,
        List<CooldownWindow> recommendations,
        Stats stats,
        List<String> warnings) {

    public record CooldownWindow(
            long spellId,
            String spellName,
            String category,
            String phase,
            int occurrence,
            int sampleCount,
            long medianPhaseTimeMs,
            long p25PhaseTimeMs,
            long p75PhaseTimeMs,
            PlanSnapshot.Confidence confidence) {
    }

    public record Stats(
            int bossEventCount,
            int phaseCount,
            int mechanicCount,
            int actionIdCount,
            int reportCount,
            int anonymizedCastCount,
            int recommendationCount) {
    }
}
