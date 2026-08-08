package dev.vedaaxis.api.plan;

import dev.vedaaxis.api.common.ApiException;
import dev.vedaaxis.api.rule.PlanRuleEngine;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanServiceTest {
    private final PlanMapper mapper = mock(PlanMapper.class);
    private final PlanRuleEngine ruleEngine = mock(PlanRuleEngine.class);
    private final DefaultPlanProvider defaultPlanProvider = mock(DefaultPlanProvider.class);
    private final PlanService service = new PlanService(mapper, new ObjectMapper(), ruleEngine, defaultPlanProvider);

    @Test
    void createRequestDeserializesPartyJobIdsByTrackSlot() throws Exception {
        PlanController.CreateRequest request = new ObjectMapper().readValue("""
                {
                  "name": "固定队减伤",
                  "encounterId": "c97e8840-1697-476f-a4ac-8c7996df277b",
                  "territoryId": 1363,
                  "strategyTag": "DMU-P1P2",
                  "trackMode": "EIGHT",
                  "useDefaultTemplate": true,
                  "partyJobIds": {
                    "MT": 21,
                    "ST": 37,
                    "H1": 24,
                    "H2": 40,
                    "D1": 34,
                    "D2": 41,
                    "D3": 38,
                    "D4": 42
                  }
                }
                """, PlanController.CreateRequest.class);

        assertThat(request.partyJobIds()).containsEntry(TrackSlot.MT, 21);
        assertThat(request.partyJobIds()).containsEntry(TrackSlot.H2, 40);
    }

    @Test
    void createsPlanTracksWithSelectedPartyJobs() {
        UUID ownerId = UUID.randomUUID();
        UUID encounterId = UUID.randomUUID();
        Map<TrackSlot, Integer> partyJobs = Map.of(
                TrackSlot.MT, 21,
                TrackSlot.ST, 37,
                TrackSlot.H1, 24,
                TrackSlot.H2, 40,
                TrackSlot.D1, 34,
                TrackSlot.D2, 41,
                TrackSlot.D3, 38,
                TrackSlot.D4, 42);

        PlanService.PlanDetails created = service.create(ownerId, new PlanService.CreatePlanRequest(
                "固定队减伤", encounterId, 1363, "DMU-P1P2", TrackMode.EIGHT, false, partyJobs));

        PlanSnapshot.ExecutionTrack mt = created.snapshot().tracks().stream()
                .filter(track -> track.slot() == TrackSlot.MT)
                .findFirst()
                .orElseThrow();
        PlanSnapshot.ExecutionTrack h2 = created.snapshot().tracks().stream()
                .filter(track -> track.slot() == TrackSlot.H2)
                .findFirst()
                .orElseThrow();

        assertThat(mt.allowedJobIds()).containsExactly(21);
        assertThat(mt.displayName()).isEqualTo("MT · 战士");
        assertThat(h2.allowedJobIds()).containsExactly(40);
        assertThat(h2.displayName()).isEqualTo("H2 · 贤者");
        verify(mapper).insertPlan(org.mockito.ArgumentMatchers.argThat(row -> row.name().equals("固定队减伤")));
    }

    @Test
    void deletesOwnedPlanVersionsBeforeDeletingThePlan() {
        UUID ownerId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        PlanRow row = row(ownerId, planId);
        when(mapper.findPlan(planId.toString())).thenReturn(Optional.of(row));
        when(mapper.deletePlan(planId.toString(), ownerId.toString())).thenReturn(1);

        service.delete(ownerId, planId);

        var order = inOrder(mapper);
        order.verify(mapper).findPlan(planId.toString());
        order.verify(mapper).deleteVersions(planId.toString());
        order.verify(mapper).deletePlan(planId.toString(), ownerId.toString());
    }

    @Test
    void refusesToDeleteAnotherOwnersPlan() {
        UUID ownerId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        when(mapper.findPlan(planId.toString())).thenReturn(Optional.of(row(UUID.randomUUID(), planId)));

        assertThatThrownBy(() -> service.delete(ownerId, planId))
                .isInstanceOf(ApiException.class);

        verify(mapper, never()).deleteVersions(planId.toString());
        verify(mapper, never()).deletePlan(planId.toString(), ownerId.toString());
    }

    private PlanRow row(UUID ownerId, UUID planId) {
        Instant now = Instant.parse("2026-08-07T12:00:00Z");
        return new PlanRow(
                planId.toString(),
                ownerId.toString(),
                "测试计划",
                UUID.randomUUID().toString(),
                1363,
                "DMU-P1P2",
                "EIGHT",
                "{}",
                1,
                now,
                now);
    }
}
