package dev.vedaaxis.api.plan;

import dev.vedaaxis.api.common.ApiException;
import dev.vedaaxis.api.rule.PlanRuleEngine;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

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
