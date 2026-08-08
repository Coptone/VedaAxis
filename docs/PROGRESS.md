# VedaAxis 项目进度

> 最近更新：2026-08-08
>
> 当前测试版：`0.1.14`（公网 Web、API 与插件仓库已部署，API 保持 active/UP）
>
> 进度口径：`已完成` 表示已有可复现的验证证据；`进行中` 表示代码或样板已存在，但仍缺少 PRD 要求的完整验收；`待开始` 表示尚未进入实现。

## 当前结论

VedaAxis 已形成可构建的 Web、API、Dalamud 插件和共享契约 MVP 基线，并已打通 GitHub/Gitee 代码托管、Dalamud 自定义仓库分发和隔离的公网 HTTPS 测试环境。2026-08-05 的游戏内观察已确认匹配技能槽会高亮、超时后会变红；项目所有者随后确认 O8S 自动战斗生命周期冒烟验证成功，说明 `InCombat` 自动启动主路径已经在实机工作。两次确认尚未附版本、诊断 JSONL 与完整场景矩阵，因此 `POC-01/02` 仍保持“进行中”。`0.1.5` 加入 M-Spec URL 候选导入；2026-08-06 公网环境进一步通过健康检查、注册、登录以及 Dancing Mad/Sage M-Spec 导入（4 阶段、200 个机制、3 条警告）。同日已复现并修复编辑器默认数据的初始化顺序错误，以及插件设备令牌受众超过数据库 32 字符限制导致的 500；公网 API 级“创建绑定码 → 网页批准 → 插件领取令牌”回归通过。外部数据仍为 `POC_PENDING`，不会自动保存或发布。

`0.1.7` 已把所有者提供的 `p1-2.xlsx` 转为 DMU P1/P2 默认八轨计划：计划契约 1.3 明确两个阶段均为绝对时间，包含 76 个机制、108 次施放提醒和 15 个单体目标标记；Web、API 与插件使用同一份规范 JSON，后端技能目录和发布规则测试通过。插件已实现按职业唯一匹配目标队友、重复职业时开打前手动选择，以及只读队友列表高亮；这些结论目前属于代码与自动测试证据，DMU 实机时间对齐和队友列表覆盖仍待验证。

HTTPS 当前部署已包含机制分类和 36 技能效果目录，可区分 AOE、平 A、死刑和普通机制，显示物理/魔法属性及伤害校准状态，并按具体目标、范围确认、持续时间、乘算和不可叠加组计算已建模减伤。

当前版本已按所有者确认的口径直接显示“整段机制对单个目标的原始总伤害 → 当前安排后的预计伤害”，并按 AOE/死刑分别着色；计算会扫描整条计划中在命中时仍有效的提前减伤，不再局限于同一时间轴行。FFLogs 工具链可自动发现近期公开击杀并匿名聚合，6 份不同击杀现已为 P1/P2 的 59 个直接伤害行生成多报告 P95 `damageProfile`，覆盖 AOE、死刑、平 A、分摊、踩塔、点名和 P2 组合机制；另 17 行经事件对照确认是阶段/咏唱/判定标记，界面不再误报“伤害值待校准”。自动检查覆盖连续色带边界、平 A 坦克目标、最危险轨道、跨行持续减伤、多段求和、互斥动作聚合、样本去重和匿名边界；上述数值仍为 `POC_PENDING`，尚待实机命中交叉验证。

2026-08-08 追加 Web/API 热修：预计伤害计算现在以任务自己的 `impactAtMs` 作为同机制内的真实命中点，提前覆盖到该命中点的减伤会计入“已建模减伤”和“减伤后预计伤害”；跨机制提前覆盖仍按被查看机制的 `plannedAtMs` 计算。平 A/“当前一仇”类机制默认只按 MT/T1 主坦轨道计算，只有目标文本明确为“一二仇/双坦/MT-ST”等双坦目标时才同时纳入 ST，明确为“ST/副坦/二仇”时只计算副坦轨道。验证通过 Web 40 项测试、`pnpm check:web`、`pnpm build:web`、API 54 项测试和 API JAR 构建；公网 Web 入口为 `assets/index-LJPDRlZ1.js` 与 `assets/index-Co42ICas.css`，API JAR SHA-256 为 `5e8a8f5b72fd89279a477ca3f66c76df5114b5939a78283f631acd3cb95032c9`，内部 `/actuator/health` 为 `UP`，公网未登录计划接口返回 401，`pluginmaster.json` 与 `release/latest/VedaAxis.zip` 继续返回 200；本次未更新 Dalamud 插件 ZIP，部署备份位于 `/opt/vedaaxis/backups/20260808-153511-damage-impact-target`。

2026-08-08 继续补充换 T 语义：Web 在平 A/死刑机制标题下新增“单体承伤目标”下拉，可选择默认当前一仇（MT/T1）、指定当前一仇为 MT/ST/T1，或标记为双坦/一二仇；计划内部兼容编码为 `当前一仇:ST` 等，不新增数据库列或破坏旧快照。Web 本地预计伤害和 API `/damage-estimates/preview` 均优先读取该机制级目标，换 T 后可把后续平 A/单体死刑切到 ST，使“最危险轨道”和减伤计算按新的当前一仇重算。验证通过 Web 42 项相关测试、`pnpm check:web`、`pnpm build:web`、API 55 项测试和 API JAR 构建；公网 Web 入口为 `assets/index-DKLeYi4t.js` 与 `assets/index-BUywlb4g.css`，API JAR SHA-256 为 `b82210062e87ce45bbef5e1dba457ef8a46922f53c6d99ed79a731ea671e1da4`，内部 `/actuator/health` 为 `UP`，公网未登录计划接口返回 401，`pluginmaster.json` 与 `release/latest/VedaAxis.zip` 继续返回 200；本次未更新 Dalamud 插件 ZIP，部署备份位于 `/opt/vedaaxis/backups/20260808-154939-current-enmity-target`。

2026-08-08 发布插件 `0.1.14` 并部署配套 API：插件“计划同步”页主流程改为刷新当前账号的已发布计划列表、下拉选择具体计划并按计划 ID 精确同步；旧版 Territory/方案标签/轨道模式自动匹配保留为折叠的高级兜底。API 新增 `GET /api/v1/runtime/plans` 返回当前账号 ACTIVE 发布计划摘要，以及 `GET /api/v1/runtime/plans/{planId}/published` 精确返回该发布快照。验证通过 API 全量 57 项测试、Core 34 项测试、Dalamud Release 构建 0 警告 0 错误、API JAR 构建和 `git diff --check`。公网 `pluginmaster.json` 为 `0.1.14.0`，`release/latest/VedaAxis.zip` 与 `repository/VedaAxis.zip` 均返回 HTTP 200 且 SHA-256 为 `5b6f36436bcff6c7bd7412ece0d6db32a32dc4352ae88a11e9bc7d235e4bd865`；API JAR SHA-256 为 `31fe8941ed2a227524e56882421b3a8b1bb23ac7791fd2c38f4a1ce559c5284c`，内部 `/actuator/health` 为 `UP`，公网未登录 `GET /VedaAxis/api/v1/runtime/plans` 返回 401。生产数据库只读核对显示所有者账号存在 1 个 ACTIVE 发布计划（Territory 1363、`DMU-P1P2`、`EIGHT`、v5），部署备份位于 `/opt/vedaaxis/backups/20260808-2208-runtime-plan-list`。

`0.1.8` 已增加 Territory 755、`O8S-POC` 八轨云端联调模板，Web 新建后由 API 初始化同一份规范快照，插件提供 O8S 快速配置并按 Territory/策略/轨道精确匹配。模板的 10 秒坚角清汁和 20 秒整体论仅为联调探针，不代表真实 O8S 时间轴。设备授权改为每台游戏电脑首次绑定一次，插件在非战斗启动阶段主动轮换刷新令牌，网页提供已绑定设备列表和撤销入口。Web 22 项、API 30 项、Python 9 项、Core 29 项及 Dalamud Release 构建已通过；GitHub/Gitee、Release 与公网部署均已完成，服务器和管理端已验证，游戏电脑的 O8S 完整闭环仍待实机完成。

`0.1.9` 针对跨电脑使用体验补充了账户可见性、计划列表刷新和 2K 宽屏缩放。计划数据的服务端查询始终按账户 ID 隔离：同一登录账户应跨电脑读取同一列表；页面会显示邮箱或账户摘要，并在重新获得焦点、恢复可见及每 30 秒重新拉取。插件的热键栏和单减队友列表新增强化外圈、加粗描边与待执行脉冲，默认使用强化样式；常用轨道、轨道模式、计划类型、提示强度和服务地址改用下拉选择，自定义服务地址仍保留在高级选项。仅构建和界面检查通过不代表游戏 HUD 可读性已验收，强化提示的实机截图/诊断仍待补充。

`0.1.10` 修复插件单体减伤目标识别的过窄职业匹配：默认计划的 MT/ST/H1/H2 等轨道仍保留原表格职业约束用于规则校验，但插件在当前队伍没有精确职业命中时会按轨道角色组兜底；若同角色候选超过 1 人，则不自动猜测并在设置界面显示具体原因，继续要求开打前手动选择。Core 32 项测试和 Dalamud Release 构建通过；公网插件清单与 ZIP 已更新到 `0.1.10.0`。

`0.1.11` 已完成本地构建验证并部署公网 Web/插件仓库：Web 技能选择器改为图标化分类弹窗，按“单减、团减、团血、特殊/待复核”组织，并继续按当前轨道职业过滤；缺失图标时显示占位图标。预计伤害改为以玩家 HP 为 100% 的承伤条，AOE 默认按治疗 HP，死刑和平 A 默认按防护 HP，超过血量上限红色、剩余 HP 低于 25% 黄色、其余绿色；左侧时间轴默认隐藏无伤害标记，可手动显示，且不再仅凭 `damageType` 推断“开场/回到场中”等标记为直接伤害。AI 候选入口会自动填入围绕红/黄风险、多机制覆盖、冷却冲突、单体目标和治疗/护盾复核的优化指令，服务端也会向模型提供可用技能目录与当前伤害预览摘要，并拒绝计划外机制、轨道和 actionId。插件修复手动“停止”不生成个人复盘的问题，停止时会按 `ABANDONED` 进入执行上传路径，并在设置界面显示最近执行上传状态。随后补充的 Web 调整将快速安排栏压成单行，任务窗口改为只读摘要加可拖动/输入毫秒的时间轴弹窗，并新增多轮覆盖状态提示与同轨同技能冷却禁选/图标倒计时；热修 `82f510e` 修复技能目录为空或图标缺失时任务卡片文本被压入图标列的问题，并在空技能目录响应时使用本地最小目录兜底；提交 `08a78f9` 为任务窗口编辑器增加取消回滚，并允许直接拖动时间轴上的亮起、释放、最晚和判定节点。提交 `28adaa7` 继续把“提前覆盖到本机制”和“本地冷却预警”改为图标+文字卡片行，新增顶栏全局显示大小控制（自动、100%、112%、125%、137%，写入浏览器本地存储），并为个人复盘页增加刷新按钮、空响应保护和空列表排查提示；最新热修把快速安排栏右侧“显示全部技能”和“安排技能”合并为可换行动作组，并按编辑区容器宽度响应式改为 2 列/1 列，避免中间面板变窄时按钮被裁切。验证通过：Web 35 项测试、`pnpm check:web`、`pnpm build:web`、API 32 项测试、Core 32 项测试、Dalamud Release 构建 0 警告 0 错误；本地 API JAR SHA-256 为 `47E3AD2890518667B6171ED6E7275595CFCD24FF18CEAC587EEF9B8EF47C66B0`，本地 ZIP SHA-256 为 `6fba6ff6b2416630f84325320c169cdabae70d35ebc437f970f34406544f7a68`。最新公网 Web 入口为 `assets/index-CDOnNKPj.js` 与 `assets/index-DZZP3J1M.css`，`pluginmaster.json`、`repository/VedaAxis.zip` 和 `release/latest/VedaAxis.zip` 均返回 200，未登录 `POST /VedaAxis/api/v1/fight-executions` 返回 401，证明当前上传路径已进入 API 鉴权而非 404；公网 ZIP 大小保持 99,856 字节，API 未重启且保持 `active`/`UP`。服务器 `fight_execution` 表只读核对显示仅有 2 条 2026-08-06 19:58–19:59（Asia/Shanghai）测试记录、1 个匿名用户哈希、每条 3 个任务；后续两小时实战记录未进表，访问日志曾出现插件上传 `POST /VedaAxis/api/v1/fight-executions` 的 404/401，下一步需在游戏端确认插件“执行上传状态”和本地 `pending-executions` 队列，并重新连接账号后触发重试。

提交 `1a4fdd4` 的 AI 优化范围迭代已实现“全局优化 / 指向优化”两种模式：Web 生成候选时会发送 `mode` 与 `focusTrackId`，指向优化只调整选定执行轨道，其它轨道作为只读上下文；服务端要求指向轨道存在，并在模型返回后拒绝锁定项改动、计划外轨道/机制/actionId、指向模式下非目标轨道增删改，以及绿色风险机制上的纯治疗/增疗资源/未建模盾刷屏。服务端同时把 AOE、死刑、平 A 和普通机制作为结构化 `attackClass`/`riskBasis` 提供给模型，避免按裸伤害把死刑过度排成团减。验证通过：`pnpm check:web`、Web 35 项测试、`pnpm build:web`（入口资产 `assets/index-BITH4aAO.js` 与 `assets/index-b2-OxdhK.css`）、API 34 项测试和 API JAR 构建；API JAR SHA-256 为 `51EA0454FADE9069BE121A313F4233F0FAF8E80C49D6AEAB46487825F751C9C4`。2026-08-07 16:43（Asia/Shanghai）已部署到 HTTPS 测试环境：API 重启后运行于 `127.0.0.1:18085` 且内部 `/actuator/health` 为 `UP`，公网首页引用新 JS/CSS，`/VedaAxis/api/v1/abilities` 未登录返回 401，`pluginmaster.json` 和 `release/latest/VedaAxis.zip` 继续返回 200；备份目录为 `/opt/vedaaxis/backups/20260807-1643-ai-scope-1a4fdd4`。

提交 `0a85232` 进一步把 AI 候选限制为服务器 allowlist 账号：线上虽然含有若干冒烟测试账号，但 AI 入口现在只允许指定所有者账号调用；DeepSeek Key 仅写入服务器环境文件，不进入仓库、数据库或文档。API 35 项测试通过，部署 JAR SHA-256 为 `372627C6C1685316CC2F207F7178B3A6003B4B856290EC1E2FB174584598CCC0`；2026-08-07 17:02（Asia/Shanghai）公网 API 重启后内部 `/actuator/health` 为 `UP`，环境校验显示 AI Key 存在、Key 前缀有效且 allowlist 匹配，服务器对 DeepSeek `deepseek-v4-pro` 的最小连通性请求返回 HTTP 200 和 1 个 choice。2026-08-07 17:10（Asia/Shanghai）的真实计划 AI 候选请求已进入 `/ai-candidates`，但 Nginx 访问日志显示 HTTP 504，错误日志为 upstream 30 秒读超时；已将仅限 `/VedaAxis/api/` 的 `proxy_read_timeout`/`proxy_send_timeout` 调整为 180 秒并通过 Nginx 配置测试与 reload，备份位于 `/opt/vedaaxis/backups/20260807-1724-nginx-ai-timeout`。真实在线计划的完整候选生成、差异确认和排轴质量验收仍未完成，`POC-05` 保持进行中。

2026-08-07 18:32（Asia/Shanghai）追加 API 热修：`/damage-estimates/preview` 的承伤分析不再为每个任务重复读取完整技能目录，而是每次预览只加载一次并复用；AI 候选请求也由“模型返回整张最终 assignments 列表”改为“模型返回 ADD/UPDATE/DELETE 增量 operations，后端本地合成候选再执行原有安全校验”，同时为模型调用设置 90 秒后端读超时，避免继续表现为网关级 504。验证通过：API 37 项测试和 API JAR 构建；部署 JAR SHA-256 为 `1F4D1960E6232C67BE0B6DF6F65F3E79E8B3A1E3699E677C476233E9B7B186BE`，公网 API 重启后内部 `/actuator/health` 为 `UP`，备份位于 `/opt/vedaaxis/backups/20260807-1832-ai-delta-1f4d1960`。截至部署后日志核对，尚无新的 `/ai-candidates` 真实请求进入；仍需所有者在网页登录态下重新点击 AI 优化，确认候选生成、差异展示与人工确认闭环。

提交 `8971c49` 将 API 技能目录扩展为 89 个正常战斗职业防护/减伤/护盾/无敌/关键增疗条目，覆盖坦克、治疗、近战、远敏、法系以及 VPR/PCT。Web 仍按执行轨道 `job_ids` 过滤技能，默认不会让一个职业看到其它职业技能；承伤模型仅把可保守表示的百分比减伤、最大生命/最大生命护盾计入数字，治疗威力护盾、无敌、Cover、格挡、纯治疗和增疗类技能会显示为复核提示，不会被错误当作已扣除伤害。

当前 HTTPS 环境已继续扩展为 118 条“排轴相关技能”目录：在原有防护/减伤基础上，补入白魔、学者、占星、贤者的主要治疗、增疗、资源与占星防护卡，并修正当前 Action 表中的若干 CD。Web 新增分类下拉，按“直接减伤、护盾/最大生命、治疗/增疗/资源、无敌/特殊处理”分组；选择仍受当前轨道 `job_ids` 限制，因此贤者轨道能看到 `寄生清汁 / Ixochole`，但不会看到白魔技能。纯治疗与增疗仍不计入减伤后伤害数字，只用于排轴、提示和冷却检查。默认 DMU P1/P2 表中最后一次 H2 `Zoe` 因当前 120 秒 CD 已改为 `Ixochole`，避免默认计划冷却冲突。

提交 `1042ff5` 继续优化 Web 计划编辑体验：预计伤害接口请求失败时，浏览器会基于当前计划的 `damageProfile`、已加载技能目录、持续时间、职业匹配、作用范围与不可叠加组做本地参考计算；减伤技能下拉框默认按执行轨道职业过滤，并保留“显示全部技能”兜底；伤害面板显示本机制安排、提前覆盖到当前命中的技能、冷却冲突和当前时间轴完整度。AI 入口改为先填写调整要求再生成候选，并继续遵守“候选不自动保存或发布”的边界。该提交已通过 Web 类型检查、25 项 Web 测试和生产构建，并只部署 Web 静态资源到 HTTPS 测试环境；API、插件清单和插件 ZIP 未更新。

提交 `a7dbaec` 继续调整 Web 计划编辑器的可读性：左侧机制列表改为每页 12 项的时间窗口，显示页码、当前页时间范围和本页减伤安排数，翻页时会自动选中该页第一项机制；“当前减伤后预计伤害”新增比例条，以绿色表示已被当前安排减掉的伤害比例、红色表示仍需承受的伤害比例。该提交已通过 Web 类型检查、25 项 Web 测试和生产构建，并只部署 Web 静态资源到 HTTPS 测试环境；API、插件清单和插件 ZIP 未更新。

当前优先级：

1. 为已通过的 O8S 自动启动冒烟补齐脱战/团灭/完成闭环、一次性执行批次、版本与诊断证据。
2. 从独立游戏电脑连接已部署的 HTTPS 环境，完成设备授权、计划同步、实机执行上传与网页复盘闭环；特别核对插件设置中的“执行上传状态”和本地待上传队列，确认 404/401 后是否能刷新令牌并重试成功。
3. 完成 `POC-02` 原生热键栏高亮实机矩阵和 `POC-01` 国服 Dalamud 事件链验证，保存诊断、截图或录像和版本信息。
4. 用 FFLogs 和插件事件校验 M-Spec/DMU 候选的机制时间、阶段定义与 Action ID，验证后才能提升置信状态。
5. 对已生成的 59 个 P1/P2 多报告 `damageProfile` 做实机命中交叉验证，重点核对平 A `xN` 是否完整覆盖以及 P2 互斥组合动作的目标分支；治疗属性护盾与无敌保持单独提示，不混入减伤后伤害数字。

## 下一开发迭代：MVP-1 自动战斗与在线计划闭环

| ID | 优先级 | 工作项 | 当前基础与缺口 | 完成标准 |
| --- | --- | --- | --- | --- |
| M1-01 | P0 | 遭遇与区域绑定 | `0.1.4` 已把 Territory 加入计划契约 1.1，API/Web/C# 已同步；O8S 测试遭遇使用 Territory 755。所有者已确认 O8S 自动生命周期冒烟成功，但未提交区域诊断记录 | 保存当前/计划 Territory、版本与诊断，确认在线计划按 Territory 匹配且不依赖隐藏默认值 |
| M1-02 | P0 | 自动战斗生命周期 | `0.1.4` 已加入幂等生命周期；所有者已确认 O8S 开怪自动启动冒烟成功；Core 自动测试通过 | 补齐重复事件、脱战、团灭/重开和退出/完成闭环，并确认一把战斗只生成一次执行批次，保存诊断与版本信息 |
| M1-03 | P0 | 在线数据闭环 | 公网 Web → API → PostgreSQL 的注册、登录、M-Spec 导入和完整设备授权 API 流程已通过；计划发布/匹配和执行上传代码已有基线 | 完成“网页注册/登录 → 创建设定并发布计划 → 插件绑定 → 拉取计划 → 实机执行 → 上传 → 网页复盘”，数据库可核对对应版本和执行记录 |
| M1-04 | P0 | HTTPS 测试环境 | `coptone.link/VedaAxis/` 已部署 Web、Java 21 API、独立 PostgreSQL 与路径级 Nginx 规则；服务只绑定回环地址，健康检查、静态资源、仓库清单与 ZIP 均返回 200，发布目录浏览返回 404，根站点仍返回 200；已有配置和回滚说明 | 增加并演练 PostgreSQL 定时备份/恢复，记录日志轮转与一次完整回滚；Redis 在有实际用例前不作为首版阻断项 |
| M1-05 | P0 | 测试环境端到端验收 | 公网基础链路和设备授权 API 级闭环已通过；`0.1.6` 将新安装和旧 localhost 默认配置迁移到公网 API，尚待独立游戏电脑实测 | 插件仅在非战斗阶段联网并成功绑定、同步不可变快照；断网时继续使用本地快照；战斗结束异步上传，失败进入队列并在恢复后重试；网页能够查看个人执行结果 |
| M1-06 | P2 | 用户倒计时触发 | 尚未设计，且会涉及倒计时来源、重复触发、取消和队长/队员一致性 | 不进入首版；首版自动触发稳定后再设计，不能影响 `InCombat` 主触发和手动调试兜底 |
| M1-07 | P1 | 外部时间轴候选导入 | `0.1.5` 已支持受控 M-Spec URL、Boss 阶段/机制、匿名减伤窗口预览和人工应用；计划契约 1.2 持久化阶段与机制 | 用管理端导入一次真实轴，确认替换提示、旧锚点/任务清理和不自动发布；再用 FFLogs/插件实机校验机制时间与 Action ID |
| M1-08 | P1 | DMU P1/P2 默认计划与单体目标 | `0.1.7` 已生成 P1/P2 绝对时间默认计划，API 无个人计划时可按 Territory 1363/DMU-P1P2 回退；插件可自动或手动解析目标轨道并只读高亮队友列表 | 在国服 DMU 中验证 0:00 起点、P1→P2 固定边界、技能窗口、MT/ST 目标定位和重复职业手动映射；保存版本、诊断与录像 |
| M1-09 | P0 | 机制伤害与减伤承伤模型 | 已完成 59 个直接伤害行的匿名 P95 校准和 17 个非伤害标记拆分，覆盖平 A、AOE、死刑、分摊/踩塔/点名与 P2 互斥组合动作；跨时间轴行减伤计算、AOE/死刑色带和自动检查已有基线。治疗威力护盾与无敌不从伤害数字中扣除 | 每个进入公共模板的伤害行均有版本化 Action ID/动作组、伤害属性、目标模式、可追溯伤害口径和样本/公式证据；用实机命中交叉验证数值，误差和未计入项可见，不输出无证据的 100% 保证 |

由于管理端与游戏电脑不在同一网络，HTTPS 环境已按所有者决定提前部署。当前顺序调整为 `M1-04 剩余运维项 → M1-03/M1-05 跨网闭环 → M1-01/M1-02 证据补齐`，并行继续 M1-07 的真实轴校验。当前 Web 是计划、账户、设备授权和复盘入口，并不存在必须先建设的独立运营后台；运营管理功能可按真实需要另列后续范围。

## 阶段进度

| 阶段 | 状态 | 已有成果 | 下一验收点 |
| --- | --- | --- | --- |
| Stage 0：阻断 PoC | 进行中 | FFLogs 国服公开报告读取已通过；首个游戏内样本已观察到技能槽高亮和超时变红；Dalamud 事件接入和锚点校验工具已具备 | 完成自动战斗生命周期及 POC-01、POC-02、POC-04 的实机证据闭环 |
| Stage 1：基础平台 | 进行中 | 账户、计划草稿/版本、4/8 轨编辑、规则校验、匿名分享、设备授权、计划匹配基线；HTTPS Web/API/PostgreSQL 环境已上线 | 完成跨网插件闭环、权限/版本边界和数据库备份恢复验收 |
| Stage 2：插件 MVP | 进行中 | 本地计划、任务状态机、时间锚点、ActionEffect 确认、只读热键栏描边、自动战斗事件监听、执行记录模型 | 消除固定区域配置、完成自动启停、在线计划匹配、断线快照和个人复盘闭环 |
| Stage 3：AI 与复盘 | 进行中 | DeepSeek 适配器、结构化候选计划、全局/指向优化模式、服务端写入范围和低风险刷技能防线 | 用真实计划完成候选生成、合法性校验、差异确认和统计优化验收 |
| Stage 4：内容扩展 | 进行中 | DMU 种子及来源/置信状态模型；M-Spec 按需候选导入、匿名窗口与人工应用 | 完成真实导入验收，并在 FFLogs/插件校验后扩展副本和公共模板 |

## PoC 状态

| PoC | 状态 | 证据与边界 |
| --- | --- | --- |
| POC-01 国服事件链 | 进行中 | Territory、队伍、战斗状态和 ActionEffect 接口已进入插件代码；所有者确认 O8S 自动生命周期冒烟成功，但缺少同一真实副本的完整诊断、结束路径与版本记录 |
| POC-02 原生热键栏高亮 | 进行中 | SDK 15 / .NET 10 构建通过；已修复绘制坐标溢出和 `active-plan.json` 反序列化；所有者已观察到技能槽高亮与超时变红，PRD 实机矩阵尚未全部执行 |
| POC-03 FFLogs 国服 API | 已完成 | `WdgtVGLAmj73Mbr8` fight 2 成功抽取 6 页、54,330 条规范化事件，凭据不进入产物和仓库 |
| POC-04 时间轴对齐 | 进行中 | 7 个种子锚点的 Action ID、类型和顺序均命中；阶段绝对起点仍存在语义偏差，暂不覆盖种子时间 |
| POC-05 AI 合法计划 | 进行中 | 已有 DeepSeek 适配器、JSON 候选结构、全局/指向优化范围、规则校验和人工确认路径；真实密钥/真实计划的完整验收未记录 |

详细证据见 [FFLogs PoC](poc/fflogs.md)、[Dalamud PoC](poc/dalamud.md) 和 [M-Spec 导入 PoC](poc/m-spec.md)。

## PRD 验收项映射

| 验收项 | 状态 | 当前说明 |
| --- | --- | --- |
| AC-001 跨端架构 | 进行中 | Web、API、插件、契约已分层并完成公网部署；独立游戏电脑的端到端闭环仍需验收 |
| AC-002 自动匹配计划 | 进行中 | API 匹配接口已有基线；遭遇与 Territory 尚未通过在线快照可靠绑定，插件在线拉取与实机切换未完成闭环 |
| AC-003 4/8 轨与个人轨绑定 | 进行中 | 编辑器和运行时支持 4/8 轨；完整队伍/职业矩阵待测 |
| AC-004 原生热键栏高亮 | 进行中 | 只读覆盖层已实现，禁止自动施放；实机矩阵待完成 |
| AC-005 提醒状态机 | 进行中 | 核心状态机、单元测试和一次游戏内高亮/超时观察已具备；自动开怪启停和游戏内全场景待测 |
| AC-006 时间锚点同步 | 进行中 | 阶段内锚点已有证据；绝对阶段语义尚未统一 |
| AC-007 计划编辑与版本 | 进行中 | 草稿、不可变版本、分享基线已实现；协作与异常流程待验收 |
| AC-008 规则校验 | 进行中 | Web/API 规则基线已实现；完整职业技能库与边界用例待扩充 |
| AC-009 AI 候选与人工确认 | 进行中 | 候选不能直接覆盖活动计划的约束已落实；真实提供方验收待完成 |
| AC-010 个人执行统计 | 进行中 | 执行结果模型、本地待上传队列和上传接口已有基线；本地与测试环境的数据闭环待完成 |
| AC-011 离线与弱网 | 进行中 | 插件可读取本地活动计划；在线快照更新、过期策略和恢复流程待测 |
| AC-012 版本兼容 | 进行中 | 插件清单和 API 版本基线已存在；国服/国际服升级矩阵待建立 |

## 最近里程碑

- 2026-08-08：`0.1.13` Web/API 热修已部署：任务窗口编辑器里的“机制判定”改为只读参考点，不再提供拖动节点、滑条或数字输入；仍可拖动/输入开始亮起、最早释放和最晚释放。该改动保留现有默认模板中“机制开始时间”和“真实判定时间”不完全一致的语义，不把 `impactAtMs` 强制改成 `mechanic.plannedAtMs`，避免破坏已有 DMU P1/P2 默认轴。AI 候选提示和服务端安全校验同步增强：UPDATE 现有任务时不得移动 `impactAtMs`，新增任务仍需由候选给出判定时间并经过原有规则校验。验证通过：Web 38 项测试、`pnpm check:web`、`pnpm build:web`、API 53 项测试、API JAR 构建、Core 34 项测试和 `git diff --check`。公网 Web 入口为 `assets/index-5hlaTCsz.js` 与 `assets/index-Co42ICas.css`，分别返回 HTTP 200 且长度为 283,077 与 58,970 字节；`/VedaAxis/api/v1/plans` 未登录返回 401；内部健康检查为 `UP`。API JAR SHA-256 为 `ba113718e62fa1e6c53e753bb95e5e6996790ff77a9fba04308a494b66d47c28`。本次未更新 Dalamud 插件清单或 ZIP；Web/API 部署备份位于 `/opt/vedaaxis/backups/20260808-151138-impact-readonly` 与 `/opt/vedaaxis/backups/20260808-151203-impact-readonly-api-retry`。
- 2026-08-08：发布插件 `0.1.13`，并同步部署公网 Web、API 与插件仓库。Web/API 已支持“伤害判定后抬血/恢复”类安排：纯治疗、纯恢复或暂未建模为直接减伤的技能可以放在判定后，用于回满下一轮前血量；直接减伤、护盾、最大体力、无敌等仍必须覆盖伤害判定点，防止误把伤后治疗当成本次减伤。技能选择器在技能仍处于冷却时，会在图标和禁用项提示上一次释放的技能、释放时间、对应机制和机制时间。插件端技能槽提醒窗口改为可开关、可锁定、可拖动，并在设置里支持 X/Y 坐标和左上/右上/右侧中部预设，避免挡住队伍列表；插件控制台改为“运行 / 计划同步 / 显示 / 诊断”四个页签。验证通过：Web 38 项测试、`pnpm check:web`、`pnpm build:web`、API 52 项测试、API JAR 构建、Core 34 项测试、Dalamud Release 构建 0 警告 0 错误。公网 Web 入口为 `assets/index-BkI2PpGk.js` 与 `assets/index-CCn5DfdV.css`，分别返回 HTTP 200 且长度为 283,081 与 58,496 字节；公网 `pluginmaster.json` 为 `0.1.13.0`；`release/latest/VedaAxis.zip` 和 `repository/VedaAxis.zip` 均返回 HTTP 200，公网 ZIP SHA-256 与本地一致：`41d3d8de7cc58bd42da9ffb246334eedcccb3e531ea19783e32386ea6e45a5b6`；API JAR SHA-256 为 `2d4cdcf72e7c842c29f239a42c0e3538caefe86cd2ee702fe3eaf1ec21c63fbc`；`/VedaAxis/api/v1/plans` 未登录返回 401，内部健康检查为 `UP`。部署前备份位于 `/opt/vedaaxis/backups/20260808-144726-post-impact-ui`。
- 2026-08-08：AI 优化原因/提醒语言链路已部署到 HTTPS 测试环境。Web 新增轻量 `zh-CN`/`en-US` 页面语言识别，并在生成 AI 优化候选时把 `locale` 传给 API；API 接收 `locale` 后要求模型将 `reasons` 与 `warnings` 按页面语言输出，同时在“无任何新增/修改操作且返回语言不匹配”时用本地中文/英文兜底说明替换技术化文本。验证通过：`pnpm check:web`、Web 37 项测试、`pnpm build:web`（入口资产 `assets/index-BgFHDQ4N.js` 与 `assets/index-DLqsTTwi.css`）、`AiCandidateServiceTest` 12 项、API 全量 50 项测试和 API JAR 构建；部署 JAR SHA-256 为 `3837D2C0585D8B08A5B9584CE0E8346A79DE7624666119F533749D41DEC0C578`，服务器内部 `/actuator/health` 为 `UP`，公网 `/VedaAxis/api/v1/plans` 未登录返回 401。部署前备份位于 `/opt/vedaaxis/backups/20260808-1136-ai-locale`；部署后已恢复并复核插件仓库文件，`pluginmaster.json`、`release/latest/VedaAxis.zip` 与 `repository/VedaAxis.zip` 均返回 HTTP 200，公网 ZIP SHA-256 为 `cf3b8b724b436f678cfd1349aca97df50e972b6029df2938e8a8eb10aed080cb`。AI 结果仍是候选，必须由用户查看差异并明确确认后才会写入草稿或发布。
- 2026-08-08：AI 优化候选空内容与新增任务 ID 热修已部署到 HTTPS 测试环境。服务端在 DeepSeek JSON 模式下关闭 thinking、提高输出上限并对空 `content` 自动重试一次；本次继续修复模型返回多个 `ADD` 操作但 `assignmentId` 为 `null` 时的合成错误，新增任务 ID 现在由后端生成，只有非空 ID 才会参与重复校验。验证通过：`AiCandidateServiceTest` 10 项、API 全量 48 项测试和 API JAR 构建；部署 JAR SHA-256 为 `D792ADEB89CF36031AE0127CA97EE3D79B96867BEA0A9AB56D739A8435978AA2`，服务器内部 `/actuator/health` 为 `UP`。部署前备份位于 `/opt/vedaaxis/backups/20260808-1033-ai-add-id`。真实 AI 候选仍需用户在网页登录态下重新点击“AI优化”，查看差异并明确确认后才会写入草稿或发布。
- 2026-08-08：计划列表与同步体验迭代已部署到 HTTPS 测试环境，并发布插件 `0.1.12`。Web 计划列表新增删除计划入口与二次确认，后端新增 `DELETE /plans/{planId}`，只允许删除当前账号拥有的计划，并先删除 `plan_version` 后删除 `mitigation_plan`，已上传的 `fight_execution` 复盘记录不随计划删除。新建计划入口改为“选择副本 → 选择轨道/职业 → 是否套用默认模板”的弹窗向导；职业选择会在进入编辑器后自动定位到包含该职业的轨道；创建请求新增 `useDefaultTemplate`，旧客户端未传时仍默认套模板。DMU 四轨新增服务端默认时间轴兜底，加载 P1/P2 机制但不预置八轨减伤安排，避免四轨按 `DMU-P1P2-FOUR` 同步时直接 404。插件计划类型下拉新增 DMU 八轨、DMU 四轨、O8S 三个快捷项，并增加自定义方案标签输入；同步 404 时显示 Territory、方案标签和轨道模式，提示去网页发布同一副本/方案/轨道的计划或切换方案。验证通过：`pnpm install --frozen-lockfile`、`pnpm check:web`、Web 37 项测试、`pnpm build:web`、API 43 项测试、API JAR 构建、Python 9 项测试、Core 32 项测试、Dalamud Release 构建 0 警告 0 错误和 `git diff --check`。公网 Web 入口为 `assets/index-BemnOlB7.js` 与 `assets/index-BM21OHOb.css`，分别返回 HTTP 200 且长度为 278,678 与 55,668 字节；`/VedaAxis/api/v1/plans` 未登录返回 401；API 内部健康检查为 `UP`。公网 `pluginmaster.json` 为 `0.1.12.0`，`release/latest/VedaAxis.zip` 和 `repository/VedaAxis.zip` 均返回 HTTP 200，公网 ZIP SHA-256 与本地一致：`cf3b8b724b436f678cfd1349aca97df50e972b6029df2938e8a8eb10aed080cb`；API JAR SHA-256 为 `4c4e6415a2153ad24dad05384f9507b50bdc46fc6f3512a0b734a7442f5b7eae`。部署前备份位于 `/opt/vedaaxis/backups/20260808-0006-delete-create-sync`；部署中发现新上传 `assets` 目录权限过窄导致资源被前端 fallback，已修正为 `www:www` 与可读权限并复核公网资源长度。
- 2026-08-07：AI 候选安全约束已增强并部署到 HTTPS 测试环境。Web 默认勾选“只新增，不改现有安排”，并默认关闭“允许使用 GCD 技能”；API 对旧客户端同样按“保留现有安排、禁止新增/改用 GCD”处理，候选即使返回 UPDATE/DELETE 或新 GCD 安排也会在服务端拒绝，不依赖提示词自觉。Flyway v8 为技能目录增加 `cast_category`，生产 PostgreSQL 只读回读为 118 条技能、其中 6 条 GCD：鼓舞激励之策、士气高扬之策、均衡诊断、均衡预后、魂灵风息、均衡预后 II。验证通过：`pnpm check:web`、Web 35 项测试、`pnpm build:web`、API 40 项测试和 API JAR 构建；公网 Web 入口为 `assets/index-GaPRez9Q.js` 与 `assets/index-pWB-AhuL.css`，API JAR SHA-256 为 `5D3211F78D881ABDB16FD659E639839A65DA50594699B5CA4090142ED1504EDB`，API 内部健康检查为 `UP`，Flyway 已将生产库迁移到 v8。部署前备份位于 `/opt/vedaaxis/backups/20260807-192314-ai-safety`。真实 AI 候选仍必须由用户点击生成、查看差异并明确确认后才能写入或发布。
- 2026-08-07：Web 发布失败可见性热修已部署。计划编辑器在发布返回规则校验 422 时，会在页面顶部显示“规则校验未通过”面板，列出错误/警告数量、规则代码、中文标题、机制时间、轨道、技能与窗口；点击任一规则问题可直接跳到对应机制和任务卡片。底部规则校验区同步改为完整可点击列表。验证通过：`pnpm check:web`、Web 36 项测试、`pnpm build:web` 与 `git diff --check`；公网 Web 入口为 `assets/index-DHokG80u.js` 与 `assets/index-DlNBuUyP.css`，两项静态资源均返回 HTTP 200 且长度分别为 272,233 与 52,457 字节。部署前备份位于 `/opt/vedaaxis/backups/20260807-223026-validation-ui`。该变更仅更新 Web 静态资源，未更新 API JAR 或插件 ZIP。
- 2026-08-07：提交 `0a85232` 为 AI 候选增加账号 allowlist，并已在服务器环境中配置 AI Key 与指定所有者账号。Key 未进入仓库、数据库或文档；API 35 项测试通过，公网 API 重启后健康检查 `UP`，DeepSeek `deepseek-v4-pro` 最小请求返回 HTTP 200。备份位于 `/opt/vedaaxis/backups/20260807-1701-ai-key-0a85232`。
- 2026-08-07：提交 `1a4fdd4` 为 AI 候选增加全局/指向优化模式和服务端范围防线。公网 Web 入口更新为 `assets/index-BITH4aAO.js` 与 `assets/index-b2-OxdhK.css`，API JAR SHA-256 为 `51EA0454FADE9069BE121A313F4233F0FAF8E80C49D6AEAB46487825F751C9C4`；API 重启后内部健康检查为 `UP`，公网受保护 API 返回 401，插件仓库与 ZIP 继续返回 200。备份位于 `/opt/vedaaxis/backups/20260807-1643-ai-scope-1a4fdd4`。
- 2026-08-07：`0.1.11` Web 与插件仓库已部署到公网。主要变更包括 Web 图标化分类技能弹窗、HP 口径预计伤害条、默认隐藏无伤害时间轴标记、AI 优化指令与服务端技能/伤害上下文、以及插件手动停止进入个人复盘上传路径；随后补充的 Web 调整将技能选择改为居中弹窗面板，并修正“开场/回到场中”等无直接伤害标记不会仅因 `damageType` 存在而显示；本次继续将快速安排栏压成单行，任务窗口改成只读摘要加可拖动/输入毫秒的时间轴弹窗，增加多轮覆盖状态提示与同轨同技能冷却中禁选/图标倒计时；热修 `82f510e` 修复技能目录为空或图标缺失时任务卡片文本被压入图标列的问题，并在空技能目录响应时使用本地最小目录兜底；提交 `08a78f9` 为任务窗口编辑器增加取消回滚，并允许直接拖动时间轴上的亮起、释放、最晚和判定节点。验证通过：Web 34 项测试、`pnpm check:web`、`pnpm build:web`（入口资产 `assets/index-BZ7Jv6o0.js` 与 `assets/index-kD4P5iJx.css`）、API 32 项测试、API JAR 构建、Core 32 项测试和 Dalamud Release 构建；本地 API JAR SHA-256 为 `47E3AD2890518667B6171ED6E7275595CFCD24FF18CEAC587EEF9B8EF47C66B0`，本地 `repository/VedaAxis.zip` SHA-256 为 `6fba6ff6b2416630f84325320c169cdabae70d35ebc437f970f34406544f7a68`。服务器通过 `ubuntu` 专用 SSH key 恢复访问后，仅替换 `/www/wwwroot/VedaAxis` 静态目录并保留备份 `/www/wwwroot/.vedaaxis-backups/VedaAxis-08a78f9-20260807-150744`；公网根站、`/VedaAxis/`、新 JS/CSS、`pluginmaster.json`、`repository/VedaAxis.zip` 与 `release/latest/VedaAxis.zip` 均返回 200，两个公网 ZIP SHA-256 均为 `6fba6ff6b2416630f84325320c169cdabae70d35ebc437f970f34406544f7a68`。API 未重启且保持 `active`/`UP`。
- 2026-08-07：规划技能目录与分类选择已部署到 HTTPS 测试环境。Flyway 从 v6 迁移到 v7，线上 `ability_definition` 只读回读为 118 条；关键新增/修正项包括 `寄生清汁 / Ixochole`、`灵橡清汁 / Druochole`、`拯救 / Soteria`、`混合 / Krasis`、`异想的幻光 / Fey Illumination` 和 `世界树之干 / the Bole`。Web 入口更新为 `assets/index-Bl-jepPt.js` 与 `assets/index-CLX66zBx.css`，公网根站、健康检查和新静态资源均返回 200；服务器内部健康检查为 `UP`。本地验证通过：API 32 项测试、Web 29 项测试、`pnpm check:web`、`pnpm build:web`；API JAR SHA-256 为 `369B097B1CAFD586D8B3092AE546A84BBE592E493A5543C4EB8B330414A70521`。部署前备份位于 `/opt/vedaaxis/backups/20260807-0048-planning-abilities`，包含旧 JAR 和旧 Web 静态压缩包；服务器当前缺少可直接调用的 `pg_dump/psql` 客户端，因此本次未形成单表 dump。该变更未更新插件 ZIP。
- 2026-08-07：提交 `8971c49` 的完整技能目录已部署到 HTTPS API 测试环境。Flyway 从 v5 迁移到 v6，线上 `ability_definition` 回读为 89 条；机工 `Tactician`、绘灵法师 `Tempera Coat/Grassa`、召唤 `Radiant Aegis`、赤魔 `Magick Barrier`、贤者 `Eukrasian Prognosis`、占星 `Sun Sign` 等关键新增项均已回读。服务器 JAR SHA-256 为 `8C034FDE5044F817047C32A94850E8CC14F493CD88C84231CDFBEE043720D217`，`vedaaxis-api` 为 active，公网健康检查为 `UP`；部署前备份位于 `/opt/vedaaxis/backups/20260807-001018-8971c49-ability-catalog`，包含旧 JAR 和 `ability_definition` 单表 dump。本地验证通过：API 32 项测试、Web 25 项测试和 `pnpm check:web`；该变更未更新 Web 静态文件或插件 ZIP。
- `0.1.10`：提交 `5ff8937` 已发布到 GitHub `v0.1.10` Release，并同步 GitHub/Gitee `main` 与同名标签。插件清单和 ZIP 已部署到 `https://coptone.link/VedaAxis/`；公网根站、Web、健康检查、清单和 ZIP 均返回 200，清单为 `0.1.10.0`，发布 ZIP SHA-256 为 `a601d0b62d992e788287861eeed66418f138bcd5b73c4848100bae6998f8d40c`，部署前备份位于 `/www/wwwroot/.vedaaxis-backups/plugin-5ff8937-20260806221636`。本地验证通过：Core 32 项测试及 Dalamud Release 构建 0 警告、0 错误；该版本仅修复插件单体目标识别和提示，不更新 API JAR 或 Web 静态文件。
- 2026-08-06：提交 `a7dbaec` 已推送 GitHub/Gitee `main`，并将 Web 静态构建部署到 `https://coptone.link/VedaAxis/`。公网根站、Web、`assets/index-DNqE1KYe.js`、`assets/index-DkxHyc9n.css`、健康检查、插件清单和现有插件 ZIP 均返回 200；部署前备份位于 `/www/wwwroot/.vedaaxis-backups/VedaAxis-a7dbaec-20260806215133`。本地验证使用 Node 24：`pnpm check:web` 通过、Web 25 项测试通过、`pnpm build:web` 通过。该变更未更新 API JAR、插件包或 PoC 状态。
- 2026-08-06：提交 `1042ff5` 已推送 GitHub/Gitee `main`，并将 Web 静态构建部署到 `https://coptone.link/VedaAxis/`。公网根站、Web、`assets/index-CJJz6yNx.js`、`assets/index-D_0ZrUlK.css`、健康检查、插件清单和现有插件 ZIP 均返回 200；部署前备份位于 `/www/wwwroot/.vedaaxis-backups/VedaAxis-1042ff5-20260806-212140`。本地验证使用 Node 24：`pnpm check:web` 通过、Web 25 项测试通过、`pnpm build:web` 通过。该变更未更新 API JAR、插件包或 PoC 状态。
- `0.1.9`：提交 `4534af0` 已发布到 GitHub `v0.1.9` Release，并按主库同步到 Gitee `main` 与同名标签。Web、插件清单和 ZIP 已隔离部署到 `https://coptone.link/VedaAxis/`；公网根站、Web、健康检查、清单与 ZIP 均返回 200，清单为 `0.1.9.0`，发布 ZIP SHA-256 为 `8bbb3c73410196500501a04a8ac89bd8caa9dad798fa316c917a0fea14756796`，Web 入口引用 `index-BdHBbQHc.js` 与 `index-BQ178Wqc.css`。API 未重启且保持 `active`/`UP`；部署前静态目录备份位于 `/www/wwwroot/.vedaaxis-backups/VedaAxis`，根站点未受影响。全量本地检查通过：Web 22、API 30、Python 9、Core 29 个测试及 Dalamud Release 构建；强化 HUD 的实机截图/诊断仍待补充，PoC 状态不提升。
- `0.1.8`：提交 `521ee30` 新增 O8S Territory 755 云端联调模板、Web 快速创建、插件 DMU/O8S 快速配置和启动时设备令牌自动续期；网页可查看/撤销设备。GitHub/Gitee `main`、`v0.1.8` 标签和 GitHub Release 已同步并部署到 `https://coptone.link/VedaAxis/`。服务器 JAR SHA-256 为 `262726388b95195478f4d6643737c61b28e7298eb6febc2b72c4a49beecac62b`，插件 ZIP SHA-256 为 `9cae4d9dfeae345e4284d0fd9848bcfd61132a434b9381be32f4079bf292b88c`，Web 入口引用 `index-DAL467Di.js` 与 `index-qT93mMfu.css`；`vedaaxis-api` 为 active、`127.0.0.1:18085/actuator/health` 返回 `UP`，JAR 内 O8S 种子、登录态设备列表和 O8S 新建入口均已验证。根站点与插件发布 URL 返回 200，回滚备份位于 `/opt/vedaaxis/backups/20260806-182058-521ee30`；O8S 探针与跨网执行上传仍需实机验收。
- 2026-08-06：伤害映射从仅扫描 AOE/死刑扩展到整张 P1/P2 时间轴。6 份去标识化公开击杀为 59 个直接伤害行生成 P95 基线；XIVAPI v2 Action 数据用于复核物理/魔法属性，FFLogs Action 组用于处理双 Action 同名技、互斥目标分支和可选晚期重复机制。17 个没有直接承伤事件的阶段/咏唱/判定行改为“时间轴标记”，普通平 A 只在坦克轨道计算。所有新值保持 `POC_PENDING`，等待实机交叉验证后再提升置信度。
- 2026-08-06：提交 `8eceea8` 的完整 P1/P2 伤害校准已部署到 `https://coptone.link/VedaAxis/`。服务器 JAR SHA-256 为 `5C065A350C09E2AF4C1F47962663C1BD0524A4ACE6C13055B1E858EC920C95CB`，Web 入口引用 `index-CXmgFDm7.js` 和 `index-Bip5u_xp.css`；服务器内部健康检查为 `UP`，公网 Web、新静态资源、插件清单和现有插件 ZIP 均返回 200，根站点仍返回 200。部署未更新插件包，回滚备份位于 `/opt/vedaaxis/backups/20260806-170222-8eceea8`；临时 Gitee 部署分支和腾讯云部署命令已在验证后删除。
- 2026-08-06：提交 `36aec78` 的减伤后最终伤害预览与多报告校准候选已部署到 `https://coptone.link/VedaAxis/`。服务器 JAR SHA-256 为 `A3B2C638FF329575526269033422AA0102634F5E7245D0166C568BAF2D11BBAE`，Web 入口引用 `index-DECR6QUX.js` 和 `index-Bip5u_xp.css`；登录态公网验收中，1:03“制裁之光”显示原始总伤害 284,938、已建模减伤 30.7%、减伤后 197,334、最危险轨道 MT，数字使用 `damage-risk-red`，页面控制台无错误。6 份不同公开击杀匿名聚合出 11 个 P1/P2 P95 候选，4 个机制保持待校准；Web 18 项、API 28 项、Python 8 项、Core 28 项测试以及 Dalamud Release 构建全部通过，下一步做实机数值对照。
- `0.1.7`：附件 P1/P2 减伤表已转为计划契约 1.3 的默认八轨计划；纠正 Excel `0:16` 按 16 秒而非 16 分钟解释，按持续时间合并 4 个相邻机制重复提示，并使用 100 级职业特性后的有效冷却完成规则校验。插件新增单体目标轨道解析与只读队友列表高亮；Release ZIP 的 SHA-256 为 `5198074A98BFD598A8393B0C7E9538D459406FC86E00D4269F3D20FD16D18235`，实机验收待完成。
- 2026-08-06：提交 `2316eb8` 的机制分类、36 技能效果目录、保守承伤分析与 FFLogs 匿名伤害候选提取器已部署到 `https://coptone.link/VedaAxis/`。服务器 JAR SHA-256 为 `3BDD7D122286A0CC34F5219B558BB1E8E6FC22D334AB1FDE6972F9FBB2397BF5`，Web 入口引用 `index-DB9MMF_5.js`；公网 Web、健康检查、新 JS/CSS、插件清单和现有插件 ZIP 均返回 200，API 服务保持仅回环监听。部署未更新插件包，回滚备份位于服务器 VedaAxis 专属备份目录。

- Codex 云端项目已完成环境配置与只读全量冒烟验证：Node 22、pnpm 11.9、Java 21、Python 3.12、.NET 10 和 Dalamud SDK 可用；Web、API、Python、Core 测试及 Dalamud Release 构建全部通过。项目上下文由 `AGENTS.md` 与 `docs/CODEX_CONTEXT.md` 共同承接。
- 首个游戏内测试已观察到匹配技能槽高亮并在超时后变红，证明覆盖层与提醒状态机主路径可运行；由于是手动预览且未附完整诊断矩阵，尚不提升 PoC 验证等级。
- 项目所有者确认 O8S 自动战斗生命周期冒烟验证成功；该证据支持 `InCombat` 自动启动主路径，但未附完整诊断和结束路径矩阵，暂不关闭 `POC-01` 或 M1-02。
- `0.1.6`：Web、API、PostgreSQL 和 Dalamud 仓库隔离部署到 `https://coptone.link/VedaAxis/`；根站点验证未受影响。公网注册、登录和 M-Spec 导入通过，插件默认 API 地址及旧 localhost 默认配置迁移到公网；跨网设备授权/在线计划/执行上传仍待实机完成。
- `0.1.6` 公网热修：修正编辑器默认阶段/机制数据晚于初始快照执行的初始化顺序，并补充兼容克隆和组件渲染回归测试；将 `refresh_token.audience` 从 32 扩至 80 字符；修正失效访问令牌误报 403、导致 Web 无法自动刷新登录的问题。设备授权集成测试、401 安全边界测试与 Web 自动刷新重试测试均已覆盖；独立游戏电脑实测仍是下一验收点。
- `0.1.5`：计划契约升级到 1.2 并持久化阶段/机制；新增 M-Spec 按需参考导入、来源白名单、响应上限、匿名减伤窗口统计、预览与明确人工应用。导入会清空失效锚点和任务，且不会自动保存或发布。
- `0.1.4`：新增计划契约 1.1 的 Territory 元数据、O8S（Territory 755）H2/八轨测试计划、开怪自动开始、脱战/团灭/完成/跨区自动结束、幂等执行批次和可见区域诊断；自动化检查通过，等待 O8S 国服实机验收。
- `0.1.3`：修复 `allowedJobIds` 只读集合反序列化，活动计划可正常加载。
- 插件仓库切换到无重定向的 jsDelivr 地址，降低国服网络环境读取 GitHub Release 重定向失败的概率。
- GitHub 作为源代码与发布源，Gitee 作为国内镜像；两个仓库保持同一提交历史和标签。
- FFLogs POC-03 通过，并明确记录 POC-04 的阶段语义偏差，不用错误绝对时间覆盖种子。

## 下一次更新的完成条件

- POC-02 实机矩阵每项都有：游戏版本、Dalamud API 版本、诊断 JSONL、截图/录像和结论。
- POC-01 可从一次完整战斗中复现：进入副本、开战、Boss Action、本地 ActionEffect、团灭/结束和清理。
- 自动生命周期可从正确 Territory 的真实副本复现：开怪后自动启动，脱战/团灭/完成后自动结束且只生成一次结果；区域不匹配时给出可读诊断。
- 从独立游戏电脑在 HTTPS 测试环境完成在线计划与执行记录闭环，并验证断网快照与待上传队列恢复。
- POC-04 给出统一的阶段锚点定义，并用 FFLogs 与至少一次插件实机回放交叉验证。
- GitHub `main` 的自动检查持续通过，Gitee 默认分支与标签保持同步。
