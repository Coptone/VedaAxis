# FFLogs 国服 API PoC

目标样板为 `WdgtVGLAmj73Mbr8`、fight `2`。抽取器使用服务端 Client Credentials，不接收也不保存用户密码；凭据从进程环境或被 Git 忽略的本地 `.env` 读取。

```powershell
python tools/fflogs_extract.py --report WdgtVGLAmj73Mbr8 --fight-id 2
```

项目根目录 `.env`：

```dotenv
FFLOGS_CLIENT_ID=...
FFLOGS_CLIENT_SECRET=...
```

输出目录包含：

- `raw/metadata.json`：report、fight 与 masterData 原始响应；
- `raw/events-*.json`：完整分页事件原始响应；
- `events.normalized.jsonl`：稳定字段名的规范化事件层；
- `manifest.json`：端点、页数与产物索引，不含凭据。

规范化事件同时保留 `packetId`、`multiplier`、`hitType` 与 `mitigated`（仅在上游事件存在时），用于复核同一伤害包和 FFLogs 乘区，不能据此反推未公开的通用 Boss 伤害公式。

## 伤害校准候选

运行：

```powershell
python tools/fflogs_damage_candidates.py `
  --report-dir data/fflogs-poc/WdgtVGLAmj73Mbr8/fight-2
```

工具只统计敌方/NPC 对友方玩家的 `calculateddamage`，按 Action ID 输出匿名实际伤害、日志原始量、样本量、观测次数和单次最大目标数，并把普通 `Attack`、AOE 候选、多人候选和单体候选分开。产物位于被 Git 忽略的报告目录，明确标记 `promotionAllowed: false`，不会保存玩家姓名，也不会自动写入默认计划。

当前样板已能识别 `Attack` 平 A、`Light of Judgment` 等 8 人 AOE 候选、`Hyperdrive` 单体候选和 `Ultimate Embrace` 双目标候选。该证据只证明提取和目标模式识别路径成立；实际 `amount` 已包含当次减伤/护盾，`unmitigatedAmount` 又是 FFLogs 乘区前原始量，均不能直接作为通用计划伤害。因此数值仍保持 `POC_PENDING`，需要更多当前版本样本和人工机制复核后才能进入计划 `damageProfile`。

### 多报告 P1/P2 校准

2026-08-06 增加了两段可复现工具链：`fflogs_collect_samples.py` 使用公开 API 自动发现 zone 76、encounter 1085 的近期不同击杀并下载事件；`fflogs_plan_damage_calibration.py` 从被忽略的样本目录中匿名聚合。它按计划相对时间（默认 `±2.5s`）、Action ID 和目标数量匹配 AOE/死刑，同一 Action 在 5 秒内的连续命中先按单个目标求和，再跨不同战斗取 P95，至少需要 3 份报告。

本轮读取 6 份不同公开击杀，得到 11 个可追溯候选，另有 4 个机制因时间未匹配或报告数不足而保持待校准。提交的 `data/seeds/dmu/p1-p2-damage-calibration.json` 明确声明不含玩家名与报告码，`promotionAllowed` 为 `false`；原始报告仍只存在于 Git 忽略目录。该结果可驱动计划编辑器的风险预览，但仍是特定版本和样本环境的 `POC_PENDING` 观测基线，必须与实机命中继续交叉验证。

## 验证结果

`POC-03` 已于 2026-08-05 通过：默认官方端点成功读取公开样板报告，fight 2 为 `Kefka / Chaos / Exdeath` 击杀记录，持续 `1101.83` 秒；事件共 `6` 页、规范化后 `54,330` 条。产物凭据扫描通过，输出目录由 Git 忽略。

如其他国服报告不能由默认国际 API Client 读取，可通过 `--token-url` 与 `--api-url` 切换经确认的端点。

## 锚点校验

运行：

```powershell
python tools/fflogs_verify_anchors.py `
  --report-dir data/fflogs-poc/WdgtVGLAmj73Mbr8/fight-2
```

7 个种子锚点的 Action ID、事件类型和出现顺序全部命中，但只有 P1/P2 的 3 个绝对时间锚点落在 `±2s` 内，整体结论为 `MISMATCH`。P3 `Bowels of Agony` 的阶段内偏差仅 `31ms`，说明动作 ID 和阶段局部时间可信；偏差主要来自种子绝对阶段起点。该报告 51 场中阶段起点统计如下：

| 阶段 | 样本数 | FFLogs 中位起点 | 种子起点 |
| --- | ---: | ---: | ---: |
| P1 | 49 | 0.000s | 0.000s |
| P2 | 29 | 208.909s | 207.600s |
| P3 | 22 | 428.531s | 540.300s |
| P4 | 14 | 733.202s | 995.000s |
| P5 | 7 | 892.684s | 1255.600s |

FFLogs 的 P4/P5 阶段边界与上游 ACT 时间轴的阶段语义不完全相同，因此本轮不直接覆盖种子时间。绝对时间继续保留 `POC_PENDING`，需要结合插件实机阶段信号或更多当前版本完整回放确定统一基准。
