package dev.vedaaxis.api.rule;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
}
