package dev.vedaaxis.api.plan;

import dev.vedaaxis.api.rule.PlanRuleEngine;
import dev.vedaaxis.api.rule.RuleIssue;
import dev.vedaaxis.api.rule.DamageEstimateAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DefaultPlanProviderTest {
    @Autowired
    private DefaultPlanProvider provider;

    @Autowired
    private PlanRuleEngine ruleEngine;

    @Autowired
    private DamageEstimateAnalysisService damageEstimateAnalysisService;

    @Test
    void loadsTheDmuP1P2EightTrackTemplate() {
        PlanSnapshot snapshot = provider.create(UUID.randomUUID());

        assertThat(snapshot.schemaVersion()).isEqualTo("1.3");
        assertThat(snapshot.minimumPluginVersion()).isEqualTo("0.1.7");
        assertThat(snapshot.territoryId()).isEqualTo(1363);
        assertThat(snapshot.phases()).extracting(PlanSnapshot.TimelinePhase::timingMode)
                .containsOnly(PlanSnapshot.TimingMode.ABSOLUTE);
        assertThat(snapshot.mechanics()).hasSize(76);
        assertThat(snapshot.mechanics()).filteredOn(mechanic -> mechanic.damageProfile() != null).hasSize(59);
        assertThat(snapshot.assignments()).hasSize(108);
        assertThat(snapshot.assignments()).filteredOn(assignment -> assignment.targetTrackId() != null).hasSize(15);
    }

    @Test
    void previewsPostMitigationDamageForEveryCalibratedDefaultMechanic() {
        var estimates = damageEstimateAnalysisService.preview(provider.create(UUID.randomUUID()));

        assertThat(estimates).hasSize(76);
        assertThat(estimates).filteredOn(estimate -> estimate.damageAfterMitigation() != null).hasSize(59);
        assertThat(estimates)
                .filteredOn(estimate -> estimate.damageAfterMitigation() != null)
                .allMatch(estimate -> estimate.baselineDamage() >= estimate.damageAfterMitigation());
        assertThat(estimates)
                .filteredOn(estimate -> estimate.damageAfterMitigation() != null)
                .anyMatch(estimate -> estimate.baselineDamage() > estimate.damageAfterMitigation());
    }

    @Test
    void onlyMatchesTheExplicitDefaultStrategy() {
        assertThat(provider.match(1363, "DMU-P1P2", TrackMode.EIGHT)).isPresent();
        assertThat(provider.match(755, "O8S-POC", TrackMode.EIGHT)).isPresent();
        assertThat(provider.minimumPluginVersion(1363, "DMU-P1P2", TrackMode.EIGHT, "fallback"))
                .isEqualTo("0.1.7");
        assertThat(provider.minimumPluginVersion(755, "O8S-POC", TrackMode.EIGHT, "fallback"))
                .isEqualTo("0.1.8");
        assertThat(provider.match(1363, "OTHER", TrackMode.EIGHT)).isEmpty();
        assertThat(provider.minimumPluginVersion(1363, "OTHER", TrackMode.EIGHT, "fallback"))
                .isEqualTo("fallback");
        assertThat(provider.match(1363, "DMU-P1P2", TrackMode.FOUR)).isEmpty();
    }

    @Test
    void loadsTheO8sEightTrackLinkageTemplate() {
        PlanSnapshot snapshot = provider.create(
                UUID.randomUUID(), 755, "O8S-POC", TrackMode.EIGHT);

        assertThat(snapshot.schemaVersion()).isEqualTo("1.3");
        assertThat(snapshot.minimumPluginVersion()).isEqualTo("0.1.8");
        assertThat(snapshot.encounterId()).isEqualTo(DefaultPlanProvider.O8S_ENCOUNTER_ID);
        assertThat(snapshot.territoryId()).isEqualTo(755);
        assertThat(snapshot.strategyTag()).isEqualTo("O8S-POC");
        assertThat(snapshot.tracks()).hasSize(8);
        assertThat(snapshot.assignments()).hasSize(2);
        assertThat(snapshot.assignments()).extracting(PlanSnapshot.Assignment::actionId)
                .containsExactly(24298L, 24310L);
        assertThat(ruleEngine.validate(snapshot).issues())
                .filteredOn(issue -> issue.severity() == RuleIssue.Severity.ERROR)
                .isEmpty();
    }

    @Test
    void defaultTemplatePassesPublicationRules() {
        var result = ruleEngine.validate(provider.create(UUID.randomUUID()));

        assertThat(result.issues())
                .filteredOn(issue -> issue.severity() == RuleIssue.Severity.ERROR)
                .isEmpty();
    }
}
