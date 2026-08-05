package dev.vedaaxis.api.timeline;

import dev.vedaaxis.api.common.ApiException;
import dev.vedaaxis.api.plan.PlanSnapshot;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class MSpecImportService {
    private static final String ORIGIN = "https://raalm.com";
    private static final String TIMELINE_PATH = "/m-spec/timelinev2.html";
    private static final Pattern SLUG = Pattern.compile("[a-z0-9-]{1,80}");
    private static final int BOSS_MAX_BYTES = 2 * 1024 * 1024;
    private static final int SPELLS_MAX_BYTES = 2 * 1024 * 1024;
    private static final int RANKING_MAX_BYTES = 12 * 1024 * 1024;
    private static final int MIN_RECOMMENDATION_SAMPLES = 5;
    private static final int MAX_RECOMMENDATIONS = 500;
    private static final Map<String, String> BOSS_FILES = Map.ofEntries(
            Map.entry("vamp-fatale", "m9s"),
            Map.entry("red-hot-and-deep-blue", "m10s"),
            Map.entry("the-tyrant", "m11s"),
            Map.entry("lindwurm", "m12s_p1"),
            Map.entry("lindwurm-ii", "m12s_p2"),
            Map.entry("futures-rewritten", "futures_rewritten"),
            Map.entry("dancing-mad", "dancing_mad"),
            Map.entry("the-unending-coil-of-bahamut", "the_unending_coil_of_bahamut"),
            Map.entry("the-weapons-refrain", "the_weapons_refrain"),
            Map.entry("the-epic-of-alexander", "the_epic_of_alexander"),
            Map.entry("dragonsongs-reprise", "dragonsongs_reprise"),
            Map.entry("the-omega-protocol", "the_omega_protocol"));

    private final MSpecSourceClient sourceClient;

    public MSpecImportService(MSpecSourceClient sourceClient) {
        this.sourceClient = sourceClient;
    }

    public MSpecImportCandidate importCandidate(String sourceUrl, boolean includeRecommendations) {
        Source source = parseSource(sourceUrl);
        URI bossDataUri = URI.create(ORIGIN + "/m-spec/data/" + source.bossFile() + ".json");
        URI rankingDataUri = URI.create(ORIGIN + "/m-spec/data/spec_ranking_" + source.specSlug() + "_" + source.bossSlug() + ".json");
        URI spellsDataUri = URI.create(ORIGIN + "/m-spec/data/spells_" + source.specSlug() + ".json");

        JsonNode bossData = sourceClient.fetchJson(bossDataUri, BOSS_MAX_BYTES);
        if (!bossData.isArray()) {
            throw invalid("Boss 时间轴不是数组");
        }
        Timeline timeline = parseTimeline(source.normalizedUrl(), bossData);
        if (timeline.mechanics().isEmpty()) {
            throw invalid("Boss 时间轴没有可导入的机制");
        }

        RecommendationResult recommendationResult = RecommendationResult.empty();
        if (includeRecommendations) {
            JsonNode spellData = sourceClient.fetchJson(spellsDataUri, SPELLS_MAX_BYTES);
            JsonNode rankingData = sourceClient.fetchJson(rankingDataUri, RANKING_MAX_BYTES);
            recommendationResult = parseRecommendations(source.specSlug(), spellData, rankingData);
        }

        List<String> warnings = new ArrayList<>();
        warnings.add("外部时间轴仅作为参考候选，应用后仍需通过 FFLogs 与游戏内事件校验。");
        warnings.add("导入结果不会自动保存或发布，必须在管理端明确应用并再次保存。");
        if (timeline.actionIdCount() == 0) {
            warnings.add("M-Spec Boss 数据未提供 Action ID，无法直接生成游戏内同步锚点。");
        }
        if (includeRecommendations) {
            warnings.add("减伤窗口仅保留匿名统计，不返回或保存报告 ID、角色名及样本来源标识。");
            if (timeline.phases().size() != recommendationResult.maxFightPhaseCount()) {
                warnings.add("Boss 轴阶段数与样本战斗阶段数不一致，推荐窗口仅按各样本自身阶段相对时间聚合。");
            }
        }

        MSpecImportCandidate.Stats stats = new MSpecImportCandidate.Stats(
                bossData.size(), timeline.phases().size(), timeline.mechanics().size(), timeline.actionIdCount(),
                recommendationResult.reportCount(), recommendationResult.anonymizedCastCount(),
                recommendationResult.windows().size());
        return new MSpecImportCandidate(
                "1.0", source.normalizedUrl(), source.bossSlug(), source.specSlug(), bossDataUri.toString(),
                includeRecommendations ? rankingDataUri.toString() : null, Instant.now(),
                timeline.phases(), timeline.mechanics(), recommendationResult.windows(), stats, List.copyOf(warnings));
    }

    private Timeline parseTimeline(String sourceUrl, JsonNode data) {
        List<PlanSnapshot.TimelinePhase> phases = new ArrayList<>();
        List<PlanSnapshot.TimelineMechanic> mechanics = new ArrayList<>();
        String currentPhase = "P1";
        int actionIdCount = 0;
        int index = 0;
        for (JsonNode event : data) {
            String externalId = nullableText(event, "id");
            String type = event.path("type").asText("mech").toLowerCase(Locale.ROOT);
            long plannedAtMs = sourceTimeToMs(event.path("time").asDouble(0));
            long durationMs = sourceTimeToMs(event.path("duration").asDouble(0));
            String name = localizedName(event);
            String stableKey = sourceUrl + "#" + (externalId == null ? index : externalId);
            if ("phase".equals(type)) {
                currentPhase = name;
                phases.add(new PlanSnapshot.TimelinePhase(
                        stableUuid("phase:" + stableKey), externalId, name, plannedAtMs,
                        PlanSnapshot.Confidence.POC_PENDING));
            } else {
                Long actionId = nullableLong(event, "action_id", "actionId");
                if (actionId != null && actionId > 0) {
                    actionIdCount++;
                } else {
                    actionId = null;
                }
                PlanSnapshot.MechanicType mechanicType = switch (type) {
                    case "tb" -> PlanSnapshot.MechanicType.TANK_BUSTER;
                    case "aoe" -> PlanSnapshot.MechanicType.RAIDWIDE;
                    default -> PlanSnapshot.MechanicType.MECHANIC;
                };
                String target = switch (mechanicType) {
                    case TANK_BUSTER -> "坦克";
                    case RAIDWIDE -> "全体";
                    default -> "机制目标";
                };
                mechanics.add(new PlanSnapshot.TimelineMechanic(
                        stableUuid("mechanic:" + stableKey), externalId, currentPhase, name, plannedAtMs, durationMs,
                        mechanicType, PlanSnapshot.DamageType.UNKNOWN, target, actionId,
                        PlanSnapshot.Confidence.POC_PENDING));
            }
            index++;
        }
        phases.sort(Comparator.comparingLong(PlanSnapshot.TimelinePhase::plannedAtMs));
        mechanics.sort(Comparator.comparingLong(PlanSnapshot.TimelineMechanic::plannedAtMs));
        return new Timeline(List.copyOf(phases), List.copyOf(mechanics), actionIdCount);
    }

    private RecommendationResult parseRecommendations(String specSlug, JsonNode spellsData, JsonNode rankingData) {
        if (!spellsData.isArray() || !rankingData.path("reports").isArray()) {
            throw invalid("减伤样本数据结构不符合预期");
        }
        Map<Long, Spell> spells = new HashMap<>();
        for (JsonNode spell : spellsData) {
            String category = spell.path("category").asText("");
            if (!"RAID_MIT".equals(category) && !"SINGLE_MIT".equals(category)) {
                continue;
            }
            long spellId = spell.path("spell_id").asLong(0);
            if (spellId > 0) {
                spells.put(spellId, new Spell(spellId, spell.path("name").asText("技能 " + spellId), category));
            }
        }

        Map<WindowKey, List<Long>> samples = new LinkedHashMap<>();
        int reportCount = 0;
        int anonymizedCastCount = 0;
        int maxFightPhaseCount = 0;
        for (JsonNode report : rankingData.path("reports")) {
            reportCount++;
            for (JsonNode fight : report.path("fights")) {
                List<PhasePoint> fightPhases = parseFightPhases(fight.path("phases"));
                maxFightPhaseCount = Math.max(maxFightPhaseCount, fightPhases.size());
                for (JsonNode player : fight.path("players")) {
                    if (!specSlug.equals(player.path("spec_slug").asText())) {
                        continue;
                    }
                    Map<String, Integer> occurrences = new HashMap<>();
                    for (JsonNode cast : player.path("casts")) {
                        long spellId = cast.path("spell_id").asLong(0);
                        Spell spell = spells.get(spellId);
                        if (spell == null) {
                            continue;
                        }
                        long timestampMs = cast.path("ts").asLong(-1);
                        if (timestampMs < 0) {
                            continue;
                        }
                        PhasePoint phase = phaseAt(fightPhases, timestampMs);
                        String occurrenceKey = spellId + ":" + phase.index();
                        int occurrence = occurrences.merge(occurrenceKey, 1, Integer::sum);
                        WindowKey key = new WindowKey(spell, phase.index(), occurrence);
                        samples.computeIfAbsent(key, ignored -> new ArrayList<>())
                                .add(Math.max(0, timestampMs - phase.startMs()));
                        anonymizedCastCount++;
                    }
                }
            }
        }

        List<MSpecImportCandidate.CooldownWindow> windows = samples.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= MIN_RECOMMENDATION_SAMPLES)
                .map(entry -> toWindow(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparing(MSpecImportCandidate.CooldownWindow::phase)
                        .thenComparingLong(MSpecImportCandidate.CooldownWindow::medianPhaseTimeMs)
                        .thenComparingLong(MSpecImportCandidate.CooldownWindow::spellId))
                .limit(MAX_RECOMMENDATIONS)
                .toList();
        return new RecommendationResult(windows, reportCount, anonymizedCastCount, maxFightPhaseCount);
    }

    private List<PhasePoint> parseFightPhases(JsonNode phasesNode) {
        List<PhasePoint> phases = new ArrayList<>();
        int index = 1;
        for (JsonNode phase : phasesNode) {
            phases.add(new PhasePoint(index++, Math.max(0, phase.path("ts").asLong(0))));
        }
        if (phases.isEmpty()) {
            phases.add(new PhasePoint(1, 0));
        }
        phases.sort(Comparator.comparingLong(PhasePoint::startMs));
        return phases;
    }

    private PhasePoint phaseAt(List<PhasePoint> phases, long timestampMs) {
        PhasePoint current = phases.getFirst();
        for (PhasePoint phase : phases) {
            if (phase.startMs() > timestampMs) {
                break;
            }
            current = phase;
        }
        return current;
    }

    private MSpecImportCandidate.CooldownWindow toWindow(WindowKey key, List<Long> values) {
        values.sort(Long::compareTo);
        return new MSpecImportCandidate.CooldownWindow(
                key.spell().id(), key.spell().name(), key.spell().category(), "P" + key.phaseIndex(), key.occurrence(),
                values.size(), percentile(values, 0.5), percentile(values, 0.25), percentile(values, 0.75),
                PlanSnapshot.Confidence.POC_PENDING);
    }

    private long percentile(List<Long> sorted, double percentile) {
        if (sorted.size() == 1) {
            return sorted.getFirst();
        }
        double position = percentile * (sorted.size() - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) {
            return sorted.get(lower);
        }
        double fraction = position - lower;
        return Math.round(sorted.get(lower) + (sorted.get(upper) - sorted.get(lower)) * fraction);
    }

    private Source parseSource(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw invalid("请输入 M-Spec 时间轴 URL");
        }
        try {
            URI uri = new URI(sourceUrl.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !("raalm.com".equals(host) || "www.raalm.com".equals(host))
                    || !TIMELINE_PATH.equals(uri.getPath())
                    || uri.getUserInfo() != null
                    || uri.getPort() != -1) {
                throw invalid("仅支持 raalm.com 的 HTTPS M-Spec timelinev2 页面");
            }
            Map<String, String> query = parseQuery(uri.getRawQuery());
            String bossSlug = query.get("boss");
            String specSlug = query.get("spec");
            if (bossSlug == null || specSlug == null || !SLUG.matcher(bossSlug).matches() || !SLUG.matcher(specSlug).matches()) {
                throw invalid("URL 必须包含合法的 boss 与 spec 参数");
            }
            String bossFile = BOSS_FILES.get(bossSlug);
            if (bossFile == null) {
                throw invalid("当前不支持该 M-Spec Boss：" + bossSlug);
            }
            String normalizedUrl = ORIGIN + TIMELINE_PATH + "?boss=" + bossSlug + "&spec=" + specSlug;
            return new Source(normalizedUrl, bossSlug, specSlug, bossFile);
        } catch (URISyntaxException exception) {
            throw invalid("M-Spec URL 格式无效");
        }
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new HashMap<>();
        if (rawQuery == null) {
            return values;
        }
        for (String part : rawQuery.split("&")) {
            int separator = part.indexOf('=');
            if (separator > 0) {
                values.put(part.substring(0, separator), part.substring(separator + 1));
            }
        }
        return values;
    }

    private String localizedName(JsonNode event) {
        String chinese = event.path("name_i18n").path("zh").asText("");
        if (!chinese.isBlank()) {
            return chinese;
        }
        String name = event.path("name").asText("");
        return name.isBlank() ? "未命名机制" : name;
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private Long nullableLong(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isNumber()) {
                return value.asLong();
            }
        }
        return null;
    }

    private long sourceTimeToMs(double value) {
        return Math.max(0, Math.round(value > 10_000 ? value : value * 1_000));
    }

    private UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "MSPEC_IMPORT_INVALID", message);
    }

    private record Source(String normalizedUrl, String bossSlug, String specSlug, String bossFile) {
    }

    private record Timeline(
            List<PlanSnapshot.TimelinePhase> phases,
            List<PlanSnapshot.TimelineMechanic> mechanics,
            int actionIdCount) {
    }

    private record Spell(long id, String name, String category) {
    }

    private record PhasePoint(int index, long startMs) {
    }

    private record WindowKey(Spell spell, int phaseIndex, int occurrence) {
    }

    private record RecommendationResult(
            List<MSpecImportCandidate.CooldownWindow> windows,
            int reportCount,
            int anonymizedCastCount,
            int maxFightPhaseCount) {
        private static RecommendationResult empty() {
            return new RecommendationResult(List.of(), 0, 0, 0);
        }
    }
}
