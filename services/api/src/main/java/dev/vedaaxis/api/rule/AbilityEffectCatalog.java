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
 * Hand-reviewed mitigation effects from the current official job guide and
 * XIVAPI Action sheet.
 *
 * <p>The catalog intentionally models only pre-hit mitigation that can be
 * represented without guessing player stats, crits, target position, remaining
 * shield stacks, or post-hit healing. Complex actions remain selectable and
 * visible, but are reported as review items rather than silently reducing the
 * calculated damage.</p>
 */
@Service
public class AbilityEffectCatalog {
    private static final String SOURCE =
            "Official Job Guide + XIVAPI Action sheet game 7583112015aaef5d, 2026-08-07";
    private static final String REVIEWED = "REVIEWED";
    private static final String SGE_GALL = "SGE_KERACHOLE_TAUROCHOLE";
    private static final String PHYSICAL_RANGED_PARTY = "PHYSICAL_RANGED_PARTY_MIT";
    private static final String SCH_SGE_BARRIER = "SCH_SGE_GALVANIZE_EUKRASIAN";
    private static final String PCT_TEMPERA = "PCT_TEMPERA_BARRIER";

    private final Map<Long, MitigationEffectProfile> profiles = Map.ofEntries(
            // Tank role and paladin
            Map.entry(7531L, direct(SELF, 20, 0, 0, "", "仅施法者自身；强化铁壁的受治疗提高不计入伤害数字。")),
            Map.entry(7535L, direct(ENEMY_AREA, 10, 0, 0, "", "敌方需处于范围内。")),
            Map.entry(17L, direct(SELF, 30, 0, 0, "", "92 级后会升级为守护者；仅施法者自身。")),
            Map.entry(36920L, mixed(SELF, 40, 0, 0, 0, 1000, "", "92 级升级技能；1000 威力护盾取决于实际治疗量换算，未计入数值。")),
            Map.entry(22L, noDirect(SELF, "", "提高格挡/格挡率，实际减伤取决于可否格挡和格挡强度，暂不计入数字。")),
            Map.entry(3542L, direct(SELF, 15, 0, 0, "", "消耗 50 忠义；仅按基础 15% 计入。")),
            Map.entry(25746L, direct(SELF, 15, 0, 0, "", "消耗 50 忠义；前 4 秒额外 Knight's Resolve 未按全窗口计入。")),
            Map.entry(7382L, direct(TARGET, 10, 0, 0, "", "消耗 50 忠义；若施法者有铁壁/守护者会额外增强，需人工确认。")),
            Map.entry(3540L, maxHpBarrier(PARTY, 10, "", "队友需处于范围内；按目标最大生命 10% 护盾计入。")),
            Map.entry(7385L, direct(GROUND_AREA, 15, 0, 0, "", "队友必须位于骑士身后扇形区域内，且效果会因移动/转身/用技能结束。")),
            Map.entry(27L, noDirect(TARGET, "", "保护会把目标伤害转移给骑士，当前不会把目标伤害直接归零。")),
            Map.entry(30L, invulnerability(SELF, "", "多数攻击无效；特殊处决/机制仍须人工确认。")),

            // Warrior
            Map.entry(44L, direct(SELF, 30, 0, 0, "", "92 级后会升级为戕戮；仅施法者自身。")),
            Map.entry(36923L, direct(SELF, 40, 0, 0, "", "92 级升级技能；到期/受击后的治疗不计入伤害数字。")),
            Map.entry(40L, maxHpIncrease(SELF, 20, "", "提高最大生命并恢复等量生命；受治疗提高不计入伤害数字。")),
            Map.entry(3551L, direct(SELF, 10, 0, 0, "", "82 级后会升级为原初的血气；武器技能回血不计入伤害数字。")),
            Map.entry(25751L, direct(SELF, 10, 0, 0, "", "后续 Stem the Flow 4 秒额外 10% 与护盾需按施放时点复核。")),
            Map.entry(16464L, direct(TARGET, 10, 0, 0, "", "目标获得 Nascent Glint/Stem the Flow；回血和 400 威力护盾未计入。")),
            Map.entry(7388L, maxHpBarrier(PARTY, 15, "", "可消耗战栗/戕戮/原初的血气，每层额外增加 2% 护盾。")),
            Map.entry(43L, invulnerability(SELF, "", "多数攻击不会使生命低于 1；特殊处决/机制仍须人工确认。")),

            // Dark knight
            Map.entry(3636L, direct(SELF, 30, 0, 0, "", "92 级后会升级为暗影卫；仅施法者自身。")),
            Map.entry(36927L, direct(SELF, 40, 0, 0, "", "92 级升级技能；Vigilant 触发治疗不计入伤害数字。")),
            Map.entry(3634L, direct(SELF, 0, 10, 20, "", "仅施法者自身。")),
            Map.entry(7393L, maxHpBarrier(TARGET, 25, "", "按目标最大生命 25% 护盾计入；破盾后的 Dark Arts 不影响承伤数字。")),
            Map.entry(25754L, direct(TARGET, 10, 0, 0, "", "可对自身或队友；最大 2 层充能。")),
            Map.entry(16471L, direct(PARTY, 0, 5, 10, "", "队友需处于范围内。")),
            Map.entry(3638L, invulnerability(SELF, "", "Living Dead/Walking Dead/Undead Rebirth 需要按治疗量与机制类型人工确认。")),

            // Gunbreaker
            Map.entry(16140L, direct(SELF, 10, 0, 0, "", "额外招架收益取决于攻击可否招架，未计入。")),
            Map.entry(16148L, direct(SELF, 30, 0, 0, "", "92 级后会升级为大星云；仅施法者自身。")),
            Map.entry(36935L, mixed(SELF, 40, 0, 0, 20, 0, "", "92 级升级技能；提高最大生命并恢复等量生命。")),
            Map.entry(16151L, noDirect(TARGET, "", "持续恢复生命，不直接减少命中伤害。")),
            Map.entry(16161L, direct(TARGET, 15, 0, 0, "", "82 级后会升级为刚玉之心；Brutal Shell 转移护盾未计入。")),
            Map.entry(25758L, direct(TARGET, 15, 0, 0, "", "前 4 秒 Clarity of Corundum 额外 15% 未按全窗口计入；后续治疗不计入。")),
            Map.entry(16160L, direct(PARTY, 0, 5, 10, "", "队友需处于范围内。")),
            Map.entry(16152L, invulnerability(SELF, "", "将生命降至最大值的 50%，并免疫多数攻击。")),

            // White mage
            Map.entry(140L, noDirect(TARGET, "", "天赐祝福是即时治疗，不直接减少命中伤害。")),
            Map.entry(3570L, noDirect(TARGET, "", "神名是即时治疗，不直接减少命中伤害。")),
            Map.entry(3571L, noDirect(PARTY, "", "法令的治疗不从命中前承伤数字中扣除。")),
            Map.entry(7432L, potencyBarrier(TARGET, 500, "", "护盾数值取决于施法者治疗属性与暴击治疗。")),
            Map.entry(25861L, direct(TARGET, 15, 0, 0, "", "可对自身或队友。")),
            Map.entry(3569L, noDirect(GROUND_AREA, "", "庇护所仅提高治疗恢复并提供持续治疗，不直接减少伤害。")),
            Map.entry(7433L, direct(PARTY, 10, 0, 0, "", "队友需处于范围内。")),
            Map.entry(16536L, direct(PARTY, 10, 0, 0, "", "队友需处于 50 码范围内；增疗不计入伤害数字。")),
            Map.entry(25862L, noDirect(GROUND_AREA, "", "礼仪之铃通过受击/结束触发治疗；不直接减少该段伤害。")),
            Map.entry(37011L, potencyBarrier(PARTY, 400, "", "需处于范围内，且只能在 Divine Grace 可用时执行。")),

            // Scholar
            Map.entry(185L, potencyBarrier(TARGET, 180, SCH_SGE_BARRIER, "鼓舞与均衡系护盾不可叠加；数值取决于实际治疗量和暴击。")),
            Map.entry(186L, potencyBarrier(PARTY, 160, SCH_SGE_BARRIER, "士气与均衡系护盾不可叠加；数值取决于实际治疗量。")),
            Map.entry(188L, direct(GROUND_AREA, 10, 0, 0, "", "目标必须站在野战治疗阵范围内。")),
            Map.entry(189L, noDirect(TARGET, "", "生命活性法是即时治疗，不直接减少命中伤害。")),
            Map.entry(3583L, noDirect(PARTY, "", "不屈不挠之策是范围即时治疗，不直接减少命中伤害。")),
            Map.entry(3585L, noDirect(PARTY, "", "展开已有鼓舞护盾，需知道原护盾剩余值；不直接按固定数值计算。")),
            Map.entry(3586L, noDirect(SELF, "", "应急战术会改变下一次鼓舞/士气的用途，必须与后续治疗配对计算。")),
            Map.entry(3587L, noDirect(SELF, "", "转化提供以太超流并提高治疗魔法恢复量；不直接减少伤害。")),
            Map.entry(16542L, noDirect(SELF, "", "秘策确保下一次指定治疗暴击并免资源；必须与后续护盾/治疗配对计算。")),
            Map.entry(7434L, noDirect(TARGET, "", "深谋远虑之策在条件满足时治疗，不直接减少命中伤害。")),
            Map.entry(7437L, noDirect(TARGET, "", "以太契约提供持续妖精治疗，不直接减少命中伤害。")),
            Map.entry(16537L, noDirect(PARTY, "", "仙光的低语提供持续治疗，不直接减少命中伤害。")),
            Map.entry(16538L, direct(PARTY, 0, 0, 5, "", "异想的幻光仅按 5% 魔法减伤计入；治疗魔法提高不计入伤害数字。")),
            Map.entry(16543L, noDirect(PARTY, "", "仙光的祝福是范围治疗，不直接减少命中伤害。")),
            Map.entry(16545L, noDirect(PARTY, "", "炽天召唤本身提供妖精治疗与慰藉使用条件；固定承伤数字不直接扣除。")),
            Map.entry(16546L, potencyBarrier(PARTY, 250, "", "慰藉护盾等于实际恢复量；最多 2 层充能，需确认炽天使状态。")),
            Map.entry(25867L, maxHpIncrease(TARGET, 10, "", "提高目标最大生命并恢复等量生命；受治疗提高不计入伤害数字。")),
            Map.entry(25868L, direct(PARTY, 10, 0, 0, "", "队友需处于范围内。")),
            Map.entry(37014L, noDirect(PARTY, "", "炽天召唤提供持续治疗并改变鼓舞/士气形态；需与后续护盾配对计算。")),

            // Astrologian
            Map.entry(3612L, noDirect(TARGET, "", "星位合图改变治疗分配，不直接减少命中伤害。")),
            Map.entry(3613L, direct(GROUND_AREA, 10, 0, 0, "", "需要保持引导/不移动，队友需处于范围内。")),
            Map.entry(3614L, noDirect(TARGET, "", "先天禀赋是即时治疗，不直接减少命中伤害。")),
            Map.entry(7439L, noDirect(GROUND_AREA, "", "地星/星体爆轰提供延迟范围治疗，不直接减少命中伤害。")),
            Map.entry(8324L, noDirect(PARTY, "", "星体爆轰提供范围治疗，不直接减少命中伤害。")),
            Map.entry(7445L, noDirect(PARTY, "", "王冠之贵妇是范围治疗，不直接减少命中伤害。")),
            Map.entry(16553L, noDirect(PARTY, "", "天星冲日提供范围治疗和持续恢复，不直接减少命中伤害。")),
            Map.entry(16556L, potencyBarrier(TARGET, 200, "", "护盾为实际恢复量的 200%，取决于治疗属性。")),
            Map.entry(16557L, noDirect(PARTY, "", "天宫图记录并触发后续治疗，需要与触发时点配对。")),
            Map.entry(16559L, noDirect(PARTY, "", "中间学派提高治疗魔法并使后续治疗附带护盾；不单独减少伤害。")),
            Map.entry(25873L, direct(TARGET, 10, 0, 0, "", "结束时治疗不计入命中前承伤。")),
            Map.entry(25874L, noDirect(PARTY, "", "大宇宙记录伤害并在到期/触发时治疗，不直接减少该段伤害。")),
            Map.entry(37024L, noDirect(TARGET, "", "放浪神之箭提高治疗恢复；卡牌资源约束需人工确认。")),
            Map.entry(37025L, potencyBarrier(TARGET, 400, "", "建筑神之塔提供目标护盾；卡牌资源约束需人工确认。")),
            Map.entry(37027L, direct(TARGET, 10, 0, 0, "", "世界树之干提供单体减伤；卡牌资源约束需人工确认。")),
            Map.entry(37028L, noDirect(TARGET, "", "河流神之瓶提供持续治疗；卡牌资源约束需人工确认。")),
            Map.entry(37031L, direct(PARTY, 10, 0, 0, "", "只能在 Suntouched 状态下执行；队友需处于范围内。")),

            // Sage
            Map.entry(24291L, potencyBarrier(TARGET, 180, SCH_SGE_BARRIER, "均衡诊断与鼓舞/均衡预后不可叠加；数值取决于实际治疗量和暴击。")),
            Map.entry(24292L, potencyBarrier(PARTY, 320, SCH_SGE_BARRIER, "均衡预后与鼓舞/均衡诊断不可叠加；数值取决于实际治疗量。")),
            Map.entry(37034L, potencyBarrier(PARTY, 360, SCH_SGE_BARRIER, "均衡预后 II 与鼓舞/均衡诊断不可叠加；数值取决于实际治疗量。")),
            Map.entry(24294L, noDirect(TARGET, "", "拯救强化关照治疗，通常作用于关照目标；不直接减少命中伤害。")),
            Map.entry(24296L, noDirect(TARGET, "", "灵橡清汁是单体即时治疗，不直接减少命中伤害。")),
            Map.entry(24298L, direct(PARTY, 10, 0, 0, SGE_GALL, "不能与坚角清汁叠加；队友需处于范围内。")),
            Map.entry(24299L, noDirect(PARTY, "", "寄生清汁是范围即时治疗，不直接减少命中伤害。")),
            Map.entry(24303L, direct(TARGET, 10, 0, 0, SGE_GALL, "不能与白牛清汁叠加。")),
            Map.entry(24310L, mixed(PARTY, 10, 0, 0, 0, 300, "", "魔法障壁取决于实际治疗量；队友需处于范围内。")),
            Map.entry(24311L, potencyBarrier(PARTY, 200, "", "最多 5 层，受击破盾才消耗一层；需按命中段数和治疗属性计算。")),
            Map.entry(24305L, potencyBarrier(TARGET, 300, "", "最多 5 层，受击破盾才消耗一层；需按命中段数和治疗属性计算。")),
            Map.entry(24300L, noDirect(SELF, "", "活化只提高下一次治疗魔法威力；必须与后续治疗/护盾配对计算。")),
            Map.entry(24301L, noDirect(PARTY, "", "消化通过移除均衡系护盾进行治疗，需知道护盾是否存在。")),
            Map.entry(24302L, noDirect(PARTY, "", "自生 II 提高治疗恢复并提供持续治疗，不直接减少伤害。")),
            Map.entry(24309L, noDirect(SELF, "", "根素只补充蛇胆资源，不直接减少伤害。")),
            Map.entry(24317L, noDirect(TARGET, "", "混合只提高治疗恢复，不直接减少伤害。")),
            Map.entry(24318L, noDirect(PARTY, "", "魂灵风息提供治疗，不直接减少该段伤害。")),
            Map.entry(37035L, noDirect(PARTY, "", "智慧之爱提供增疗和后续触发治疗，不直接减少该段伤害。")),

            // Melee and physical ranged jobs
            Map.entry(7549L, direct(ENEMY_TARGET, 0, 10, 5, "", "敌方目标必须是即将造成伤害的敌人。")),
            Map.entry(65L, noDirect(PARTY, "", "真言提高受治疗量，不直接减少命中伤害。")),
            Map.entry(7394L, direct(SELF, 20, 0, 0, "", "仅施法者自身；受击后的回复不计入伤害数字。")),
            Map.entry(2241L, maxHpBarrier(SELF, 20, "", "仅施法者自身。")),
            Map.entry(7498L, direct(SELF, 10, 0, 0, "", "只减少下一次攻击，需确认命中次数。")),
            Map.entry(24404L, maxHpBarrier(SELF, 10, "", "仅施法者自身；破盾后的队伍 HOT 不计入伤害数字。")),
            Map.entry(7405L, direct(PARTY, 15, 0, 0, PHYSICAL_RANGED_PARTY, "不能与策动/防守之桑巴叠加；队友需处于范围内。")),
            Map.entry(16889L, direct(PARTY, 15, 0, 0, PHYSICAL_RANGED_PARTY, "不能与行吟/防守之桑巴叠加；队友需处于范围内。")),
            Map.entry(16012L, direct(PARTY, 15, 0, 0, PHYSICAL_RANGED_PARTY, "不能与行吟/策动叠加；队友需处于范围内。")),
            Map.entry(2887L, direct(ENEMY_TARGET, 10, 0, 0, "", "敌方目标必须是即将造成伤害的敌人。")),
            Map.entry(7408L, noDirect(PARTY, "", "大地神的抒情恋歌提高受治疗量，不直接减少命中伤害。")),
            Map.entry(16014L, noDirect(PARTY, "", "即兴表演本体提供持续治疗并积累层数；护盾在即兴表演结束上体现。")),
            Map.entry(25789L, maxHpBarrier(PARTY, 5, "", "即兴层数越高护盾越强；当前保守按 0 层 5% 计入，最高可达 10%。")),

            // Casters
            Map.entry(7560L, direct(ENEMY_TARGET, 0, 5, 10, "", "敌方目标必须是即将造成伤害的敌人。")),
            Map.entry(157L, maxHpBarrier(SELF, 30, "", "仅施法者自身。")),
            Map.entry(25799L, maxHpBarrier(SELF, 20, "", "需要 Carbuncle；最大 2 层充能。")),
            Map.entry(25857L, direct(PARTY, 0, 0, 10, "", "只减少魔法伤害；额外受治疗提高不计入伤害数字。")),
            Map.entry(34685L, maxHpBarrier(SELF, 20, PCT_TEMPERA, "仅施法者自身；破盾会缩短坦培拉涂层复唱。")),
            Map.entry(34686L, maxHpBarrier(PARTY, 10, PCT_TEMPERA, "需要消耗坦培拉涂层；破盾会缩短坦培拉涂层复唱。"))
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
