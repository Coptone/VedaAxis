package dev.vedaaxis.api.plan;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public final class DefaultPlanProvider {
    public static final UUID DMU_ENCOUNTER_ID = UUID.fromString("c97e8840-1697-476f-a4ac-8c7996df277b");
    public static final long DMU_TERRITORY_ID = 1363;
    public static final String DMU_P1_P2_STRATEGY = "DMU-P1P2";
    public static final UUID O8S_ENCOUNTER_ID = UUID.fromString("9789ba9a-b761-4c44-b179-2e3e86ee0d3b");
    public static final long O8S_TERRITORY_ID = 755;
    public static final String O8S_POC_STRATEGY = "O8S-POC";

    private final PlanSnapshot dmuP1P2;
    private final PlanSnapshot dmuP1P2Four;
    private final PlanSnapshot o8sPoc;

    public DefaultPlanProvider(ObjectMapper objectMapper) {
        dmuP1P2 = load(objectMapper, "default-plans/p1-p2-default-plan.json");
        dmuP1P2Four = fourTrackDmuTemplate(dmuP1P2);
        o8sPoc = load(objectMapper, "default-plans/poc-default-plan.json");
    }

    public boolean supports(long territoryId, String strategyTag, TrackMode trackMode) {
        return template(territoryId, strategyTag, trackMode).isPresent();
    }

    public PlanSnapshot create(UUID planId) {
        return copyWithPlanId(dmuP1P2, planId);
    }

    public PlanSnapshot create(
            UUID planId, long territoryId, String strategyTag, TrackMode trackMode) {
        PlanSnapshot selected = template(territoryId, strategyTag, trackMode)
                .orElseThrow(() -> new IllegalArgumentException("No default plan matches the requested identity"));
        return copyWithPlanId(selected, planId);
    }

    public Optional<PlanSnapshot> match(long territoryId, String strategyTag, TrackMode trackMode) {
        return template(territoryId, strategyTag, trackMode);
    }

    public String minimumPluginVersion(
            long territoryId, String strategyTag, TrackMode trackMode, String fallback) {
        return template(territoryId, strategyTag, trackMode)
                .map(PlanSnapshot::minimumPluginVersion)
                .orElse(fallback);
    }

    private Optional<PlanSnapshot> template(long territoryId, String strategyTag, TrackMode trackMode) {
        String normalizedStrategy = strategyTag.trim();
        return List.of(dmuP1P2, dmuP1P2Four, o8sPoc).stream()
                .filter(plan -> plan.territoryId() == territoryId)
                .filter(plan -> plan.strategyTag().equalsIgnoreCase(normalizedStrategy))
                .filter(plan -> plan.trackMode() == trackMode)
                .findFirst();
    }

    private PlanSnapshot fourTrackDmuTemplate(PlanSnapshot source) {
        List<PlanSnapshot.ExecutionTrack> tracks = TrackMode.FOUR.orderedSlots().stream()
                .map(slot -> new PlanSnapshot.ExecutionTrack(
                        UUID.randomUUID(), slot, defaultJobs(slot), fourTrackDisplayName(slot)))
                .toList();
        return new PlanSnapshot(
                source.schemaVersion(), source.minimumPluginVersion(), source.planId(), source.planVersion(),
                source.timelineId(), source.timelineVersion(), source.encounterId(), source.territoryId(),
                "DMU-P1P2-FOUR", TrackMode.FOUR,
                new PlanSnapshot.Source(
                        PlanSnapshot.SourceKind.PUBLIC_TEMPLATE,
                        "dmu-p1-p2-four-track-timeline",
                        PlanSnapshot.Confidence.POC_PENDING),
                source.phases(), source.mechanics(), source.anchors(), tracks, List.of());
    }

    private Set<Integer> defaultJobs(TrackSlot slot) {
        return switch (slot) {
            case T1, MT, ST -> Set.of(19, 21, 32, 37);
            case H1, H2 -> Set.of(24, 28, 33, 40);
            case D1, D2, D3, D4 -> Set.of(20, 22, 23, 25, 27, 30, 31, 34, 35, 38, 39, 41, 42);
        };
    }

    private String fourTrackDisplayName(TrackSlot slot) {
        return switch (slot) {
            case T1 -> "T1 · 防护职业";
            case H1 -> "H1 · 治疗职业";
            case D1 -> "D1 · 进攻职业";
            case D2 -> "D2 · 进攻职业";
            default -> slot.name();
        };
    }

    private PlanSnapshot copyWithPlanId(PlanSnapshot source, UUID planId) {
        return new PlanSnapshot(
                source.schemaVersion(), source.minimumPluginVersion(), planId, 1,
                source.timelineId(), source.timelineVersion(), source.encounterId(),
                source.territoryId(), source.strategyTag(), source.trackMode(), source.source(),
                source.phases(), source.mechanics(), source.anchors(), source.tracks(), source.assignments());
    }

    private PlanSnapshot load(ObjectMapper objectMapper, String path) {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(input, PlanSnapshot.class);
        } catch (IOException | JacksonException exception) {
            throw new IllegalStateException("Unable to load default plan " + path, exception);
        }
    }
}
