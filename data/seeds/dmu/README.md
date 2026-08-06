# DMU seed provenance

此目录固定了 MVP 使用的两套策略来源与上游修订版本：Ikuya Mitty、LPDU。它们只作为导入和比对依据，当前统一标记为 `POC_PENDING`。

FFLogs 样板校验已命中全部 7 个锚点 Action ID，但暴露出 P3 以后绝对阶段起点与上游 ACT 时间轴不一致；因此只确认动作身份和顺序，不把单场观察当作完整覆盖或可发布时间轴。

上游表格中的“写了某技能”只能证明策略文本存在，不能证明技能 Action ID、状态事件、持续时间与覆盖关系已经在当前游戏版本中得到验证。因此 VedaAxis 不会仅凭这份种子把来源提升为 `VERIFIED`，也不会自动发布由它生成的计划。

`manifest.json` 记录了上游修订、生成文件哈希、事件数量和阶段起点，后续导入器必须先核对哈希；真实数据提升置信度时，需要保留游戏版本、区域、实战记录与验证人。

## P1/P2 默认计划（0.1.7）

`p1-p2-default-plan.json` 来自所有者提供的 `p1-2.xlsx`。P1 使用附件 `P1!A1:O47`，P2 使用 `P2!A1:P31`；Excel 时间显示按“分:秒”解释，P1 为 0:00–3:23，P2 的全局时间为 3:28–6:27。两个阶段均标为 `ABSOLUTE`，全表保留 `POC_PENDING`。

种子包含 76 个机制、108 次施放提醒和 15 个 `targetTrackId`。原始 112 个技能标记中，有 4 个是同一持续效果覆盖相邻机制，生成时合并为一次施放；`p1-p2-import-audit.json` 保存数量、未映射项和特殊解释。MT1 是 MT 的个人减伤子列，不是第九条执行轨道。

## P1/P2 伤害校准（2026-08-06）

`p1-p2-damage-calibration.json` 是 6 份不同公开击杀样本的匿名聚合结果，`p1-p2-damage-map.json` 保存人工复核后的计划行与 Action ID 对照。工具按计划相对时间匹配 AOE、死刑、分摊、踩塔、点名和组合机制；连续多段伤害先按单个目标求和，互斥的多种命中动作只汇总样本而不相加，再跨样本取 P95。普通平 A 使用 Action 49746 的逐次命中 P95，并按源表 `xN` 计算整段伤害。

当前 76 行时间轴中有 59 行获得 `damageProfile`，其余 17 行是开场、Boss 归位、阶段判定、机制咏唱或失败判定等不对应一次直接承伤的时间轴标记，不再显示成“伤害值待校准”。动作的物理/魔法属性由 XIVAPI v2 `Action` sheet 交叉核对；客户端 Action 数据不提供敌方实际伤害量，因此数值仍来自 FFLogs `calculateddamage.unmitigatedAmount`。产物不含玩家名和 FFLogs 报告码，全部继续标记为 `POC_PENDING`。

以后不需要所有者逐条提供 FFLogs 链接。维护者可使用本地 `.env` 中的 OAuth 凭据自动发现近期公开击杀、下载原始事件，再生成匿名校准候选：

```bash
python tools/fflogs_collect_samples.py --zone-id 76 --encounter-id 1085 --sample-limit 6
python tools/fflogs_plan_damage_calibration.py \
  --plan data/seeds/dmu/p1-p2-default-plan.json \
  --mapping data/seeds/dmu/p1-p2-damage-map.json \
  --report-root data/fflogs-poc \
  --collected-at YYYY-MM-DD \
  --output data/seeds/dmu/p1-p2-damage-calibration.json \
  --apply-output data/seeds/dmu/p1-p2-default-plan.json
```

`data/fflogs-poc/` 始终忽略提交；只有去标识化的聚合产物可以进入版本库。平 A 的 `xN` 结果假定同一套计划减伤覆盖整段连续攻击；组合机制按单个目标实际只承受一种分支处理。P95 是特定装备、角色和队伍环境下的观测风险基线，不是官方 boss 伤害公式，也不会自动把置信度提升为 `VERIFIED`。
