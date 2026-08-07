# VedaAxis Codex 项目上下文

> 最近整理：2026-08-07
>
> 用途：为本地 Codex 与 Codex 云端新任务提供精简、可审查的项目记忆。本文件不是聊天记录，也不得保存任何密钥、个人目录、角色标识或原始诊断数据。

## 1. 新任务读取顺序

开始分析或修改前，按以下顺序建立上下文：

1. 根目录 [`AGENTS.md`](../AGENTS.md)：不可突破的产品边界、验证命令和发布规则。
2. [`docs/PROGRESS.md`](PROGRESS.md)：当前已验证进度、阻断项和下一验收点。
3. [`docs/decisions.md`](decisions.md)：项目所有者已确认的产品决策。
4. [`docs/architecture.md`](architecture.md)：模块、轨道、插件运行时和 AI 边界。
5. 与任务相关的 [`docs/poc/fflogs.md`](poc/fflogs.md) 或 [`docs/poc/dalamud.md`](poc/dalamud.md)：PoC 证据标准。

若本文件与上述专项文档冲突，以更具体且更新时间更新的文档为准；发现冲突时应先说明，不得自行把未验证内容改成已完成。

## 2. 项目所有者已确认的边界

- 目标是分阶段交付完整 MVP，优先关闭会阻断插件可用性的 PoC。
- Dalamud 插件只能观察游戏状态并绘制只读覆盖层；禁止自动施法、模拟输入或改写原生热键栏。
- 国服和国际服以最新同步版本为目标，不维护人为固定的旧版本默认值。
- 同时保留 4 轨和 8 轨模式；4 轨虽然使用较少，但属于明确的扩展性要求。
- 账户基线为邮箱密码、JWT 和插件一次性绑定码；分享链接是匿名只读。
- DeepSeek 等模型只能产生未经信任的候选 JSON。候选必须经过本地规则校验、差异展示和用户明确确认，不能直接覆盖活动计划。
- 允许引用 Ikuya/Mitty 和 LPDU 的 DMU 数据，但必须保留来源、版本/修订与 `POC_PENDING` 等未验证状态。
- 仓库暂不添加开源许可证。

## 3. 当前可依赖的已验证事实

- 当前测试版是 `0.1.11`（公网 Web 与插件仓库已部署，API 保持 active/UP）。
- `0.1.11` 的本地验证证据：Web 图标化技能选择器、HP 口径预计伤害条、默认隐藏无伤害时间轴标记、AI 优化上下文和插件手动停止执行上传修复均已实现；技能选择器已进一步改为居中弹窗式分类面板，快速安排栏压成单行；任务窗口改成只读摘要加可拖动/输入毫秒的时间轴弹窗，提示多轮覆盖机会/当前覆盖状态；新增同轨同技能冷却中禁选并在图标上显示剩余冷却。热修 `82f510e` 进一步修复技能目录为空或图标缺失时任务卡片文本被压入图标列的问题，并在空技能目录响应时使用本地最小目录兜底。提交 `08a78f9` 为任务窗口编辑器增加取消回滚，并允许直接拖动时间轴上的亮起、释放、最晚和判定节点。提交 `28adaa7` 把预计伤害面板内“提前覆盖到本机制”和“本地冷却预警”改为图标+文字卡片行，新增顶栏全局显示大小控制（自动、100%、112%、125%、137%，本地存储），并为个人复盘页增加刷新按钮、空响应保护和空列表排查提示。验证通过 Web 34 项测试、`pnpm check:web`、`pnpm build:web`、API 32 项测试、API JAR 构建、Core 32 项测试和 Dalamud Release 构建 0 警告 0 错误。本地 API JAR SHA-256 为 `47E3AD2890518667B6171ED6E7275595CFCD24FF18CEAC587EEF9B8EF47C66B0`，本地 `pluginmaster.json` 为 `0.1.11.0`，`repository/VedaAxis.zip` SHA-256 为 `6fba6ff6b2416630f84325320c169cdabae70d35ebc437f970f34406544f7a68`。2026-08-07 通过 `ubuntu` SSH key 恢复服务器访问后，最新公网 Web 静态入口更新为 `assets/index-mYDNHWt0.js` 与 `assets/index-Dy6ovRSI.css`，`pluginmaster.json`、`repository/VedaAxis.zip` 和 `release/latest/VedaAxis.zip` 均返回 HTTP 200，未登录 `POST /VedaAxis/api/v1/fight-executions` 返回 401；API 未重启，内部健康检查仍为 `UP`。服务器只读核对显示 `fight_execution` 仅有 2 条 2026-08-06 19:58–19:59（Asia/Shanghai）测试记录，后续实战记录未进入服务端表；访问日志曾出现插件上传 404/401，下一步需要在游戏端查看“执行上传状态”和本地 `pending-executions` 队列，并重连账号触发重试。
- 本次 AI 优化范围迭代已在本地实现全局/指向优化：Web 生成候选时发送 `mode` 与 `focusTrackId`；指向优化只允许模型改目标轨道，服务端拒绝非目标轨道增删改、计划外轨道/机制/actionId、锁定项改动，以及绿色风险机制上的纯治疗/增疗资源/未建模盾刷屏。服务端会把 `attackClass`（AOE、TANK_BUSTER、AUTO_ATTACK、MECHANIC）和 `riskBasis` 传给模型，避免按裸伤害过度处理死刑。验证通过：`pnpm check:web`、Web 35 项测试、`pnpm build:web`、API 34 项测试和 API JAR 构建；本地 Web 入口为 `assets/index-BITH4aAO.js` 与 `assets/index-b2-OxdhK.css`，API JAR SHA-256 为 `51EA0454FADE9069BE121A313F4233F0FAF8E80C49D6AEAB46487825F751C9C4`。真实 AI Key/真实计划候选验收仍未完成。
- `0.1.10` 的发布证据：提交 `5ff8937`、GitHub/Gitee `v0.1.10` 标签、GitHub Release 资产和公网 HTTPS 插件仓库均已完成；`pluginmaster.json` 返回 `0.1.10.0`，公网 ZIP SHA-256 为 `a601d0b62d992e788287861eeed66418f138bcd5b73c4848100bae6998f8d40c`。该版本仅修复插件单体减伤目标识别：精确职业不匹配时按轨道角色组兜底，多个同角色候选时不自动猜测并显示具体原因；未更新 API JAR 或 Web 静态文件。
- `0.1.9` 的发布证据：提交 `4534af0`、GitHub/Gitee `v0.1.9` 标签、GitHub Release 资产和公网 HTTPS 部署均已完成；`pluginmaster.json` 返回 `0.1.9.0`，公网 ZIP SHA-256 为 `8bbb3c73410196500501a04a8ac89bd8caa9dad798fa316c917a0fea14756796`，Web 入口为 `index-BdHBbQHc.js` 与 `index-BQ178Wqc.css`。服务器仅更新 `/www/wwwroot/VedaAxis` 静态目录，API 保持 `active`/`UP`，发布前备份为 `/www/wwwroot/.vedaaxis-backups/VedaAxis`。
- GitHub [`Coptone/VedaAxis`](https://github.com/Coptone/VedaAxis) 的 `main` 是源代码真源；Gitee [`Need4Spd/VedaAxis`](https://gitee.com/Need4Spd/VedaAxis) 是国内镜像。
- Dalamud 自定义仓库地址为 `https://coptone.link/VedaAxis/pluginmaster.json`；插件包地址为 `https://coptone.link/VedaAxis/release/latest/VedaAxis.zip`。
- HTTPS 测试环境已隔离部署在 `https://coptone.link/VedaAxis/`：Web 静态文件、仅回环监听的 API 与 PostgreSQL、独立 systemd/Compose 资源和路径级 Nginx 规则均未修改根站点业务。公网健康检查、注册、登录和 M-Spec 导入已通过；数据库备份演练与插件跨网完整闭环仍未完成。
- 2026-08-06 已修复公网阻断：计划编辑器默认阶段/机制数据在初始快照前完成初始化，并使用兼容克隆；设备令牌的 `plugin:<UUID>` 受众不再受 32 字符数据库列限制；失效访问令牌对受保护接口返回 401，使 Web 能自动刷新令牌并重试。公网 API 级设备授权闭环已返回 `APPROVED`，但独立游戏电脑仍需实测绑定和后续同步。
- FFLogs 国服 API `POC-03` 已完成：公开报告 `WdgtVGLAmj73Mbr8` 的 fight 2 已抽取 6 页、54,330 条规范化事件；凭据和生成数据不得提交。
- Codex 云端环境名为 `Coptone/VedaAxis`，设置与维护命令均为 `bash tools/codex_setup.sh`，预安装 Node 22，并启用“普通依赖项”网络允许列表。
- 2026-08-05 的 Codex 云端只读冒烟验证通过：Web 类型检查、3 个 Web 测试和生产构建通过；API 7 个测试通过；Python 4 个测试通过；Core 16 个测试通过；Dalamud Release 构建为 0 警告、0 错误。验证任务见 [云端报告](https://chatgpt.com/codex/cloud/tasks/task_e_6a73292376b8832b87566c0ac3604b57)。
- 2026-08-05 的首个游戏内观察确认匹配技能槽会高亮、超时后会变红，证明覆盖层与提醒状态机主路径可运行；该样本是手动预览，未包含完整诊断矩阵，不能据此关闭 `POC-01` 或 `POC-02`。
- 项目所有者随后确认 O8S 自动战斗生命周期冒烟验证成功；这证明 `InCombat` 自动启动主路径可用，但没有完整诊断、版本和结束路径矩阵，仍不能关闭 `POC-01`。
- `0.1.5` 的计划契约 1.2 持久化阶段/机制；管理端可按需读取受支持的 M-Spec URL，匿名聚合减伤窗口并在预览后由用户明确应用。导入数据保持 `POC_PENDING`，不会自动保存或发布。
- `0.1.7` 的计划契约 1.3 增加阶段持续时间、`ABSOLUTE/RELATIVE` 时间模式和单体任务目标轨道。附件 `p1-2.xlsx` 已转为 DMU P1/P2 默认八轨计划（76 个机制、108 次施放提醒、15 个目标标记），自动测试与规则校验通过；尚待国服实机验证时间与队友列表覆盖位置。
- `0.1.8` 增加与 DMU 明确隔离的 Territory 755、`O8S-POC` 八轨在线计划模板，用两个贤者 ActionEffect 探针验证发布、匹配、自动开始和执行上传；探针不是 O8S 攻略时间轴。设备绑定改为每台游戏电脑首次授权一次，插件启动时在非战斗阶段轮换刷新令牌，网页可列出和撤销设备。Web 22 项、API 30 项、Python 9 项、Core 29 项及 Dalamud Release 构建已通过；提交 `521ee30`、标签和 Release 已同步 GitHub/Gitee，并部署到公网。服务器内部健康检查、Web 新入口和插件包哈希已验证；游戏电脑的 O8S 完整闭环仍需实机完成。
- `0.1.9` 将计划列表明确标识为账户隔离的云端空间，在页面获得焦点、重新可见和每 30 秒时重新拉取；同一账户在不同电脑应读到同一服务端计划列表。页面在 2K 及更宽显示器自动放大且保留无横向滚动布局。插件默认使用强化热键栏/队友列表框，常用轨道、计划、提示强度和服务地址改为下拉选择；升级不再把已选 O8S 策略标签改回 DMU。当前仍缺少强化框在真实游戏 HUD 上的截图/诊断矩阵，不能据此提升 PoC 状态。
- 提交 `1042ff5` 已为 Web 编辑器增加本地预计伤害兜底、按轨道职业过滤减伤技能、提前覆盖/冷却冲突可视化、时间轴完整度提示和 AI 指令输入。验证通过：`pnpm check:web`、Web 25 项测试和 `pnpm build:web`。公网静态部署引用 `assets/index-CJJz6yNx.js` 与 `assets/index-D_0ZrUlK.css`，根站、Web、健康检查、插件清单和现有插件 ZIP 均返回 200；仅更新 Web 静态文件，未更新 API JAR 或插件包。
- 提交 `a7dbaec` 已为 Web 编辑器左侧机制列表增加每页 12 项的时间窗口、页码、当前页时间范围和本页减伤安排数；“当前减伤后预计伤害”增加绿色已减伤/红色承伤比例条。验证通过：`pnpm check:web`、Web 25 项测试和 `pnpm build:web`。公网静态部署引用 `assets/index-DNqE1KYe.js` 与 `assets/index-DkxHyc9n.css`，根站、Web、健康检查、插件清单和现有插件 ZIP 均返回 200；仅更新 Web 静态文件，未更新 API JAR 或插件包。
- 2026-08-06 的提交 `2316eb8` 已把机制分类/伤害校准展示、当前游戏数据版本的 36 技能效果目录、目标特定保守承伤分析和 FFLogs 匿名伤害候选提取器合并到 `main` 并部署到 HTTPS 测试环境。自动测试已覆盖乘算、伤害属性、不可叠加组、最大生命护盾、目标选择和无校准拒算；DMU 默认机制的真实数值、多报告证据、治疗威力护盾与无敌特判尚未完成，不能把“已建模部分可存活”解释为通用 100% 保证。
- 提交 `36aec78` 已按所有者新口径把主结果改成单个目标的“整段机制原始总伤害 → 当前安排后的预计伤害”，AOE/死刑使用独立连续色带；计算会纳入在命中时仍有效的提前减伤。提交 `8eceea8` 又把 6 份不同击杀扩展到 P1/P2 的 59 个直接伤害行，覆盖平 A、AOE、死刑、分摊、踩塔、点名和 P2 互斥组合动作；17 个阶段/咏唱/判定行改显示为“时间轴标记”，普通平 A 只在坦克轨道计算。XIVAPI v2 Action 数据只用于复核 Action 名称和物理/魔法属性，伤害量仍取 FFLogs 匿名 P95；该提交已部署到 HTTPS 测试环境，服务器内部健康检查和公网静态资源/插件仓库验证通过。数据继续为 `POC_PENDING`，尚待实机命中复核。
- 提交 `8971c49` 已把 API 技能目录扩展到 89 个正常战斗职业防护/减伤/护盾/无敌/关键增疗条目，并部署到 HTTPS API；Flyway 已迁移到 v6，线上 `ability_definition` 回读 89 条。Web 继续按轨道 `job_ids` 过滤技能，一个职业只显示自己可用的技能；承伤模型仅把可保守表示的直接减伤、最大生命与最大生命护盾计入数字，治疗威力护盾、无敌、Cover、格挡、纯治疗和增疗类技能保持复核提示。验证通过：API 32 项测试、Web 25 项测试、`pnpm check:web`、公网健康检查 `UP`。该变更未更新 Web 静态文件或插件 ZIP。
- 2026-08-07：API Flyway v7 将技能目录扩展到 118 条规划相关技能，新增白魔、学者、占星、贤者的主要治疗/增疗/资源技能与占星防护卡；贤者 `寄生清汁 / Ixochole`、`灵橡清汁 / Druochole`、`拯救 / Soteria` 已上线，`Krasis` 中文名修正为 `混合 / Krasis`。Web 技能选择改为按“直接减伤、护盾/最大生命、治疗/增疗/资源、无敌/特殊”分组，同时继续按轨道 `job_ids` 过滤；默认 DMU P1/P2 表中最后一次 H2 `Zoe` 因当前 120 秒 CD 改为 `Ixochole`，以避免默认计划冷却冲突。验证通过：API 32 项测试、Web 29 项测试、`pnpm check:web`、`pnpm build:web`；HTTPS API v7 和 Web 静态资源已部署，线上 `ability_definition` 回读 118 条，公网健康检查 `UP`。该变更未更新插件 ZIP。

## 4. 当前尚未关闭的关键问题

以下项目仍然是“进行中”，不得仅凭编译或单元测试通过改成“已完成”：

1. `POC-01`：国服 Dalamud 完整事件链实机证据，包括进入副本、开战、Boss Action、本地 ActionEffect、团灭/结束和清理。
2. `POC-02`：原生热键栏高亮实机矩阵，需要游戏版本、Dalamud API 版本、诊断 JSONL、截图或录像及结论。
3. `POC-04`：DMU 阶段语义与绝对时间基准，需要 FFLogs 和至少一次插件实机回放交叉验证。
4. 自动战斗生命周期：`0.1.4` 已把 Territory 加入计划契约 1.1，并实现 O8S Territory 755 测试计划、区域诊断、幂等开怪启动及脱战/团灭/完成/跨区结束；所有者已确认自动启动冒烟成功，仍需补齐一次性结果、结束路径、版本和诊断闭环。
5. HTTPS 测试环境和最小部署配置已具备；仍需从独立游戏电脑完成设备授权、在线计划拉取、执行上传、断网快照与个人复盘闭环，并补做 PostgreSQL 备份恢复演练。

## 5. 工作与交接规则

- 先检查当前分支、工作区和最新 `docs/PROGRESS.md`，不要重复询问已经记录的确认项。
- 区分“源码存在”“构建通过”“自动测试通过”“国服实机通过”和“产品验收完成”。结论必须匹配证据等级。
- 修改跨端负载时，先更新 `contracts` 下的版本化 Schema，再同步 Java、TypeScript 和 C#。
- 战斗路径只能读取本地不可变计划快照；网络请求、账户逻辑和 AI 调用不得进入战斗或绘制热路径。
- 完成一项可复现验证后，同步更新 `docs/PROGRESS.md`；形成长期产品决策时更新 `docs/decisions.md`；架构边界变化时更新 `docs/architecture.md`。
- 当前实施顺序以 `docs/PROGRESS.md` 的 MVP-1 为准：HTTPS 环境已经提前部署，下一步直接进行跨网设备授权与在线计划/执行上传闭环，同时补齐自动生命周期证据。用户倒计时触发不进入首版。
- 发布前按 `AGENTS.md` 的完整检查与 GitHub/Gitee 同步流程执行。不要提交 `.env`、FFLogs/DeepSeek/OAuth 凭据、原始 PoC 数据或含角色/账号标识的日志。

## 6. 建议的新任务提示

新建 Codex 云端任务时可以从以下提示开始：

```text
先读取 AGENTS.md 与 docs/CODEX_CONTEXT.md，再根据 docs/PROGRESS.md 判断当前证据等级。仅处理本任务范围；不要重复推翻已确认决策，也不要把编译通过等同于实机验收。完成后运行相关检查，并在新增了可复现证据时更新项目进度。
```
