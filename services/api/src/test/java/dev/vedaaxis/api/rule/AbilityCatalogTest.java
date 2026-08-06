package dev.vedaaxis.api.rule;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AbilityCatalogTest {
    @Autowired
    private AbilityCatalog catalog;

    @Test
    void exposesAnIconAssetPathForEverySeededAbility() {
        assertThat(catalog.all())
                .isNotEmpty()
                .allSatisfy(ability -> assertThat(ability.iconPath())
                        .startsWith("ui/icon/")
                        .endsWith(".tex"));
    }

    @Test
    void exposesAReviewedEffectProfileForEverySeededAbility() {
        assertThat(catalog.all())
                .isNotEmpty()
                .allSatisfy(ability -> {
                    assertThat(ability.effect()).isNotNull();
                    assertThat(ability.effect().confidence()).isEqualTo("REVIEWED");
                    assertThat(ability.effect().source()).contains("XIVAPI Action sheet");
                });
    }

    @Test
    void coversEveryNormalCombatJobWithAtLeastOneMitigationEntry() {
        Map<Integer, String> jobs = Map.ofEntries(
                Map.entry(19, "PLD"),
                Map.entry(21, "WAR"),
                Map.entry(32, "DRK"),
                Map.entry(37, "GNB"),
                Map.entry(24, "WHM"),
                Map.entry(28, "SCH"),
                Map.entry(33, "AST"),
                Map.entry(40, "SGE"),
                Map.entry(20, "MNK"),
                Map.entry(22, "DRG"),
                Map.entry(30, "NIN"),
                Map.entry(34, "SAM"),
                Map.entry(39, "RPR"),
                Map.entry(41, "VPR"),
                Map.entry(23, "BRD"),
                Map.entry(31, "MCH"),
                Map.entry(38, "DNC"),
                Map.entry(25, "BLM"),
                Map.entry(27, "SMN"),
                Map.entry(35, "RDM"),
                Map.entry(42, "PCT"));

        jobs.forEach((jobId, name) -> assertThat(catalog.all())
                .as(name)
                .anySatisfy(ability -> assertThat(ability.jobIds()).contains(jobId)));
    }

    @Test
    void includesRepresentativeMitigationAddedForDpsAndMissingHealerKits() {
        List<Long> expectedActionIds = List.of(
                16889L, // Tactician
                2887L,  // Dismantle
                2241L,  // Shade Shift
                25799L, // Radiant Aegis
                25857L, // Magick Barrier
                34685L, // Tempera Coat
                34686L, // Tempera Grassa
                24292L, // Eukrasian Prognosis
                25868L, // Expedient
                37031L  // Sun Sign
        );

        assertThat(catalog.all().stream().map(AbilityDefinition::actionId))
                .containsAll(expectedActionIds);
    }
}
