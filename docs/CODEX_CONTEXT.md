# VedaAxis Codex 项目上下文

> 最近整理：2026-08-06
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

- 当前测试版是 `0.1.6`。
- GitHub [`Coptone/VedaAxis`](https://github.com/Coptone/VedaAxis) 的 `main` 是源代码真源；Gitee [`Need4Spd/VedaAxis`](https://gitee.com/Need4Spd/VedaAxis) 是国内镜像。
- Dalamud 自定义仓库地址为 `https://coptone.link/VedaAxis/pluginmaster.json`；插件包地址为 `https://coptone.link/VedaAxis/release/latest/VedaAxis.zip`。
- HTTPS 测试环境已隔离部署在 `https://coptone.link/VedaAxis/`：Web 静态文件、仅回环监听的 API 与 PostgreSQL、独立 systemd/Compose 资源和路径级 Nginx 规则均未修改根站点业务。公网健康检查、注册、登录和 M-Spec 导入已通过；数据库备份演练与插件跨网完整闭环仍未完成。
- 2026-08-06 已修复两个公网阻断：计划编辑器不再硬依赖浏览器 `structuredClone`；设备令牌的 `plugin:<UUID>` 受众不再受 32 字符数据库列限制。公网 API 级设备授权闭环已返回 `APPROVED`，但独立游戏电脑仍需实测绑定和后续同步。
- FFLogs 国服 API `POC-03` 已完成：公开报告 `WdgtVGLAmj73Mbr8` 的 fight 2 已抽取 6 页、54,330 条规范化事件；凭据和生成数据不得提交。
- Codex 云端环境名为 `Coptone/VedaAxis`，设置与维护命令均为 `bash tools/codex_setup.sh`，预安装 Node 22，并启用“普通依赖项”网络允许列表。
- 2026-08-05 的 Codex 云端只读冒烟验证通过：Web 类型检查、3 个 Web 测试和生产构建通过；API 7 个测试通过；Python 4 个测试通过；Core 16 个测试通过；Dalamud Release 构建为 0 警告、0 错误。验证任务见 [云端报告](https://chatgpt.com/codex/cloud/tasks/task_e_6a73292376b8832b87566c0ac3604b57)。
- 2026-08-05 的首个游戏内观察确认匹配技能槽会高亮、超时后会变红，证明覆盖层与提醒状态机主路径可运行；该样本是手动预览，未包含完整诊断矩阵，不能据此关闭 `POC-01` 或 `POC-02`。
- 项目所有者随后确认 O8S 自动战斗生命周期冒烟验证成功；这证明 `InCombat` 自动启动主路径可用，但没有完整诊断、版本和结束路径矩阵，仍不能关闭 `POC-01`。
- `0.1.5` 的计划契约 1.2 持久化阶段/机制；管理端可按需读取受支持的 M-Spec URL，匿名聚合减伤窗口并在预览后由用户明确应用。导入数据保持 `POC_PENDING`，不会自动保存或发布。

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
