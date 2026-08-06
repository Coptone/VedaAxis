package dev.vedaaxis.api.rule;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static dev.vedaaxis.api.rule.MitigationEffectProfile.CalculationReadiness.DIRECT_REDUCTION;
import static dev.vedaaxis.api.rule.MitigationEffectProfile.CalculationReadiness.INVULNERABILITY_SPECIAL_CASE;
import static dev.vedaaxis.api.rule.MitigationEffectProfile.CalculationReadiness.MAX_HP_BARRIER;
import static dev.vedaaxis.api.rule.MitigationEffectProfile.CalculationReadiness.NO_DIRECT_MITIGATION;
import static dev.vedaaxis.api.rule.MitigationEffectProfile.CalculationReadiness.REQUIRES_HEALING_STATS;
import static dev.vedaaxis.api.rule.MitigationEffectProfile.Scope.ENEMY_AREA;
import static dev.vedaaxis.api.rule.MitigationEffectProfile.Scope.ENEMY_TARGET;
import static dev.vedaaxis.api.rule.MitigationEffectProfile.Scope.GROUND_AREA;
import static dev.vedaaxis.api.rule.MitigationEffectProfile.Scope.PARTY;
import static dev.vedaaxis.api.rule.MitigationEffectProfile.Scope.SELF;
import static dev.vedaaxis.api.rule.MitigationEffectProfile.Scope.TARGET;

/**
 * Hand-reviewed mitigation effects from the current XIVAPI Action sheet.
 *
 * <p>The source version is stored with every profile. This is a release-time
 * catalog, never a parser of tooltip text on a combat or render path.</p>
 */
@Service
public class AbilityEffectCatalog {
    private static final String SOURCE = "XIVAPI Action sheet game 7583112015aaef5d, 2026-08-06";
    private static final String REVIEWED = "REVIEWED";
    private static final String SGE_GALL = "SGE_KERACHOLE_TAUROCHOLE";
    private static final String PHYSICAL_RANGED_PARTY = "PHYSICAL_RANGED_PARTY_MIT";

    private final Map<Long, MitigationEffectProfile> profiles = Map.ofEntries(
            Map.entry(7535L, direct(ENEMY_AREA, 10, 0, 0, "", "敌方需处于范围内。")),
            Map.entry(7388L, maxHpBarrier(PARTY, 15, "", "可消耗战栗/戮罪/原初的血气，每层额外增加 2% 护盾。")),
            Map.entry(3540L, maxHpBarrier(PARTY, 10, "", "队友需处于 30 码范围内。")),
            Map.entry(16471L, direct(PARTY, 0, 5, 10, "", "队友需处于范围内。")),
            Map.entry(16160L, direct(PARTY, 0, 5, 10, "", "队友需处于范围内。")),
            Map.entry(188L, direct(GROUND_AREA, 10, 0, 0, "", "目标必须站在野战治疗的地面范围内。")),
            Map.entry(16536L, direct(PARTY, 10, 0, 0, "", "队友需处于 50 码范围内。")),
            Map.entry(24298L, direct(PARTY, 10, 0, 0, SGE_GALL, "不能与坚角清汁叠加；队友需处于范围内。")),
            Map.entry(24310L, mixed(PARTY, 10, 0, 0, 0, 300, "", "魔法障壁取决于实际治疗量；队友需处于范围内。")),
            Map.entry(24311L, potencyBarrier(PARTY, 200, "", "最多 5 层，受击破盾才消耗一层；需按命中段数和治疗属性计算。")),
            Map.entry(7531L, direct(SELF, 20, 0, 0, "", "仅施法者自身。")),
            Map.entry(36923L, direct(SELF, 40, 0, 0, "", "仅施法者自身。")),
            Map.entry(40L, maxHpIncrease(SELF, 20, "", "提高最大生命并恢复等量生命；仅施法者自身。")),
            Map.entry(25751L, direct(SELF, 10, 0, 0, "", "后续坚壁/盾效和武器技能治疗须按实际触发计算。")),
            Map.entry(43L, invulnerability(SELF, "", "大多数攻击不会使生命低于 1；特殊处决/机制仍须人工确认。")),
            Map.entry(36935L, mixed(SELF, 40, 0, 0, 20, 0, "", "提高最大生命并恢复等量生命；仅施法者自身。")),
            Map.entry(25758L, direct(TARGET, 15, 0, 0, "", "目标减伤持续 8 秒，后续澄清之心同为 15%。")),
            Map.entry(16152L, invulnerability(SELF, "", "将生命降至最大值的 50%，并免疫大多数攻击。")),
            Map.entry(7432L, potencyBarrier(TARGET, 500, "", "护盾数值取决于施法者治疗属性与暴击治疗。")),
            Map.entry(25861L, direct(TARGET, 15, 0, 0, "", "目标减伤持续 8 秒。")),
            Map.entry(3569L, noDirect(GROUND_AREA, "", "庇护所仅提高治疗恢复并提供持续治疗，不直接减少伤害。")),
            Map.entry(7433L, direct(PARTY, 10, 0, 0, "", "队友需处于范围内。")),
            Map.entry(25862L, noDirect(GROUND_AREA, "", "礼仪之铃通过受击触发治疗；不直接减少该段伤害。")),
            Map.entry(37011L, potencyBarrier(PARTY, 400, "", "护盾数值取决于施法者治疗属性；队友需处于范围内。")),
            Map.entry(24300L, noDirect(SELF, "", "活化只提高下一次治疗魔法威力；必须与后续治疗/护盾配对计算。")),
            Map.entry(37034L, potencyBarrier(PARTY, 360, "", "障壁为实际恢复量的 360%，取决于治疗属性与暴击；不能与鼓舞/均衡诊断叠加。")),
            Map.entry(24302L, noDirect(PARTY, "", "自生 II 提高治疗恢复并提供持续治疗，不直接减少伤害。")),
            Map.entry(24318L, noDirect(PARTY, "", "魂灵风息提供治疗，不直接减少该段伤害。")),
            Map.entry(24305L, potencyBarrier(TARGET, 300, "", "最多 5 层，受击破盾才消耗一层；需按命中段数和治疗属性计算。")),
            Map.entry(24303L, direct(TARGET, 10, 0, 0, SGE_GALL, "不能与白牛清汁叠加。")),
            Map.entry(24317L, noDirect(TARGET, "", "拯救只提高治疗恢复，不直接减少伤害。")),
            Map.entry(37035L, noDirect(PARTY, "", "智慧之爱提供后续触发治疗，不直接减少该段伤害。")),
            Map.entry(24291L, potencyBarrier(TARGET, 180, "", "护盾为实际恢复量的 180%，取决于治疗属性与暴击。")),
            Map.entry(7549L, direct(ENEMY_TARGET, 0, 10, 5, "", "敌方目标必须是即将造成伤害的敌人。")),
            Map.entry(16012L, direct(PARTY, 15, 0, 0, PHYSICAL_RANGED_PARTY, "不能与行吟者之歌/策动叠加；队友需处于范围内。")),
            Map.entry(7560L, direct(ENEMY_TARGET, 0, 5, 10, "", "敌方目标必须是即将造成伤害的敌人。"))
    );

    public MitigationEffectProfile profile(long actionId) {
        return profiles.getOrDefault(actionId, MitigationEffectProfile.unknown(actionId));
    }

    private static MitigationEffectProfile direct(
            MitigationEffectProfile.Scope scope, int all, int physical, int magical,
            String stackingGroup, String... conditions) {
        return new MitigationEffectProfile(scope, all, physical, magical, 0, 0, 0, false, stackingGroup,
                DIRECT_REDUCTION, List.of(conditions), SOURCE, REVIEWED);
    }

    private static MitigationEffectProfile maxHpBarrier(
            MitigationEffectProfile.Scope scope, int barrierPercent, String stackingGroup, String... conditions) {
        return new MitigationEffectProfile(scope, 0, 0, 0, 0, barrierPercent, 0, false, stackingGroup,
                MAX_HP_BARRIER, List.of(conditions), SOURCE, REVIEWED);
    }

    private static MitigationEffectProfile maxHpIncrease(
            MitigationEffectProfile.Scope scope, int increasePercent, String stackingGroup, String... conditions) {
        return new MitigationEffectProfile(scope, 0, 0, 0, increasePercent, 0, 0, false, stackingGroup,
                MAX_HP_BARRIER, List.of(conditions), SOURCE, REVIEWED);
    }

    private static MitigationEffectProfile potencyBarrier(
            MitigationEffectProfile.Scope scope, int curePotency, String stackingGroup, String... conditions) {
        return new MitigationEffectProfile(scope, 0, 0, 0, 0, 0, curePotency, false, stackingGroup,
                REQUIRES_HEALING_STATS, List.of(conditions), SOURCE, REVIEWED);
    }

    private static MitigationEffectProfile mixed(
            MitigationEffectProfile.Scope scope, int all, int physical, int magical,
            int maximumHpIncrease, int barrierCurePotency, String stackingGroup, String... conditions) {
        return new MitigationEffectProfile(scope, all, physical, magical, maximumHpIncrease, 0, barrierCurePotency,
                false, stackingGroup, barrierCurePotency > 0 ? REQUIRES_HEALING_STATS : DIRECT_REDUCTION,
                List.of(conditions), SOURCE, REVIEWED);
    }

    private static MitigationEffectProfile invulnerability(
            MitigationEffectProfile.Scope scope, String stackingGroup, String... conditions) {
        return new MitigationEffectProfile(scope, 0, 0, 0, 0, 0, 0, true, stackingGroup,
                INVULNERABILITY_SPECIAL_CASE, List.of(conditions), SOURCE, REVIEWED);
    }

    private static MitigationEffectProfile noDirect(
            MitigationEffectProfile.Scope scope, String stackingGroup, String... conditions) {
        return new MitigationEffectProfile(scope, 0, 0, 0, 0, 0, 0, false, stackingGroup,
                NO_DIRECT_MITIGATION, List.of(conditions), SOURCE, REVIEWED);
    }
}
