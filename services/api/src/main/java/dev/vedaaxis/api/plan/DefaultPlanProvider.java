package dev.vedaaxis.api.plan;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
public final class DefaultPlanProvider {
    public static final UUID DMU_ENCOUNTER_ID = UUID.fromString("c97e8840-1697-476f-a4ac-8c7996df277b");
    public static final long DMU_TERRITORY_ID = 1363;
    public static final String DMU_P1_P2_STRATEGY = "DMU-P1P2";

    private final PlanSnapshot dmuP1P2;

    public DefaultPlanProvider(ObjectMapper objectMapper) {
        try (var input = new ClassPathResource("default-plans/p1-p2-default-plan.json").getInputStream()) {
            dmuP1P2 = objectMapper.readValue(input, PlanSnapshot.class);
        } catch (IOException | JacksonException exception) {
            throw new IllegalStateException("Unable to load DMU P1/P2 default plan", exception);
        }
    }

    public boolean supports(long territoryId, String strategyTag, TrackMode trackMode) {
        return territoryId == DMU_TERRITORY_ID
                && DMU_P1_P2_STRATEGY.equalsIgnoreCase(strategyTag.trim())
                && trackMode == TrackMode.EIGHT;
    }

    public PlanSnapshot create(UUID planId) {
        return new PlanSnapshot(
                dmuP1P2.schemaVersion(), dmuP1P2.minimumPluginVersion(), planId, 1,
                dmuP1P2.timelineId(), dmuP1P2.timelineVersion(), dmuP1P2.encounterId(),
                dmuP1P2.territoryId(), dmuP1P2.strategyTag(), dmuP1P2.trackMode(), dmuP1P2.source(),
                dmuP1P2.phases(), dmuP1P2.mechanics(), dmuP1P2.anchors(), dmuP1P2.tracks(), dmuP1P2.assignments());
    }

    public Optional<PlanSnapshot> match(long territoryId, String strategyTag, TrackMode trackMode) {
        return supports(territoryId, strategyTag, trackMode) ? Optional.of(dmuP1P2) : Optional.empty();
    }
}
