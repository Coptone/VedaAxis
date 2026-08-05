package dev.vedaaxis.api.timeline;

import dev.vedaaxis.api.common.ApiException;
import dev.vedaaxis.api.plan.PlanSnapshot;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MSpecImportServiceTest {
    private static final String SOURCE =
            "https://raalm.com/m-spec/timelinev2.html?boss=dancing-mad&spec=sage-sage&buddy=0";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void importsLocalizedTimelineAsUnverifiedCandidate() {
        StubSourceClient client = new StubSourceClient(objectMapper);
        client.add("https://raalm.com/m-spec/data/dancing_mad.json", """
                [
                  {"id":"DM_P1","name":"P1","time":0,"duration":0,"type":"phase"},
                  {"id":"dmu-001","name":"Revolting Ruin III","name_i18n":{"zh":"恶狠狠毁荡"},"time":15,"duration":5,"type":"tb"},
                  {"id":"dmu-002","name":"Heartless Angel","time":30,"duration":0,"type":"aoe"}
                ]
                """);

        MSpecImportCandidate candidate = new MSpecImportService(client).importCandidate(SOURCE, false);

        assertThat(candidate.sourceUrl()).isEqualTo(
                "https://raalm.com/m-spec/timelinev2.html?boss=dancing-mad&spec=sage-sage");
        assertThat(candidate.phases()).singleElement().satisfies(phase -> {
            assertThat(phase.name()).isEqualTo("P1");
            assertThat(phase.confidence()).isEqualTo(PlanSnapshot.Confidence.POC_PENDING);
        });
        assertThat(candidate.mechanics()).hasSize(2);
        assertThat(candidate.mechanics().getFirst().name()).isEqualTo("恶狠狠毁荡");
        assertThat(candidate.mechanics().getFirst().plannedAtMs()).isEqualTo(15_000);
        assertThat(candidate.mechanics().getFirst().type()).isEqualTo(PlanSnapshot.MechanicType.TANK_BUSTER);
        assertThat(candidate.stats().actionIdCount()).isZero();
        assertThat(candidate.warnings()).anyMatch(warning -> warning.contains("Action ID"));

        MSpecImportCandidate repeated = new MSpecImportService(client).importCandidate(SOURCE, false);
        assertThat(repeated.mechanics().getFirst().mechanicId())
                .isEqualTo(candidate.mechanics().getFirst().mechanicId());
    }

    @Test
    void derivesOnlyAnonymousCooldownWindows() throws Exception {
        StubSourceClient client = new StubSourceClient(objectMapper);
        client.add("https://raalm.com/m-spec/data/dancing_mad.json", """
                [
                  {"id":"DM_P1","name":"P1","time":0,"duration":0,"type":"phase"},
                  {"id":"dmu-001","name":"Raidwide","time":15,"duration":0,"type":"aoe"}
                ]
                """);
        client.add("https://raalm.com/m-spec/data/spells_sage-sage.json", """
                [
                  {"spell_id":24298,"name":"Kerachole","category":"RAID_MIT"},
                  {"spell_id":24312,"name":"Dosis","category":"GCD"}
                ]
                """);
        client.add("https://raalm.com/m-spec/data/spec_ranking_sage-sage_dancing-mad.json", """
                {"reports":[
                  {"report_id":"secret-1","fights":[{"phases":[{"ts":0},{"ts":10000}],"players":[{"source_id":1,"name":"Alice","spec_slug":"sage-sage","casts":[{"spell_id":24298,"ts":1000,"c":1}]}]}]},
                  {"report_id":"secret-2","fights":[{"phases":[{"ts":0},{"ts":10000}],"players":[{"source_id":2,"name":"Bob","spec_slug":"sage-sage","casts":[{"spell_id":24298,"ts":2000,"c":1}]}]}]},
                  {"report_id":"secret-3","fights":[{"phases":[{"ts":0},{"ts":10000}],"players":[{"source_id":3,"name":"Carol","spec_slug":"sage-sage","casts":[{"spell_id":24298,"ts":3000,"c":1}]}]}]},
                  {"report_id":"secret-4","fights":[{"phases":[{"ts":0},{"ts":10000}],"players":[{"source_id":4,"name":"Dave","spec_slug":"sage-sage","casts":[{"spell_id":24298,"ts":4000,"c":1}]}]}]},
                  {"report_id":"secret-5","fights":[{"phases":[{"ts":0},{"ts":10000}],"players":[{"source_id":5,"name":"Eve","spec_slug":"sage-sage","casts":[{"spell_id":24298,"ts":5000,"c":1}]}]}]}
                ]}
                """);

        MSpecImportCandidate candidate = new MSpecImportService(client).importCandidate(SOURCE, true);

        assertThat(candidate.recommendations()).singleElement().satisfies(window -> {
            assertThat(window.spellId()).isEqualTo(24298);
            assertThat(window.sampleCount()).isEqualTo(5);
            assertThat(window.medianPhaseTimeMs()).isEqualTo(3_000);
            assertThat(window.p25PhaseTimeMs()).isEqualTo(2_000);
            assertThat(window.p75PhaseTimeMs()).isEqualTo(4_000);
        });
        assertThat(candidate.stats().reportCount()).isEqualTo(5);
        assertThat(candidate.stats().anonymizedCastCount()).isEqualTo(5);
        assertThat(candidate.warnings()).anyMatch(warning -> warning.contains("阶段数"));
        String serialized = objectMapper.writeValueAsString(candidate);
        assertThat(serialized).doesNotContain("secret-", "Alice", "Bob", "source_id", "report_id");
    }

    @Test
    void rejectsArbitraryHostsAndUnsupportedBossesBeforeFetching() {
        StubSourceClient client = new StubSourceClient(objectMapper);
        MSpecImportService service = new MSpecImportService(client);

        assertThatThrownBy(() -> service.importCandidate(
                "https://example.com/m-spec/timelinev2.html?boss=dancing-mad&spec=sage-sage", false))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("raalm.com");
        assertThatThrownBy(() -> service.importCandidate(
                "https://raalm.com/m-spec/timelinev2.html?boss=unknown&spec=sage-sage", false))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("不支持");
        assertThat(client.fetchCount).isZero();
    }

    private static final class StubSourceClient implements MSpecSourceClient {
        private final ObjectMapper objectMapper;
        private final Map<URI, String> values = new HashMap<>();
        private int fetchCount;

        private StubSourceClient(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        private void add(String uri, String json) {
            values.put(URI.create(uri), json);
        }

        @Override
        public JsonNode fetchJson(URI uri, int maxBytes) {
            fetchCount++;
            String json = values.get(uri);
            if (json == null) {
                throw new AssertionError("Unexpected URL: " + uri);
            }
            try {
                return objectMapper.readTree(json);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }
    }
}
