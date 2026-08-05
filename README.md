# VedaAxis

FFXIV 团队减伤规划、个人执行提醒与复盘平台。项目按 PRD 的阻断 PoC、基础平台、插件 MVP、AI 与复盘四个阶段推进。

## 仓库结构

- `apps/web`：Vue 3 + TypeScript 计划编辑器、设备绑定与分享页面。
- `services/api`：Java 21 + Spring Boot + MyBatis 模块化单体服务。
- `plugins/VedaAxis`：Dalamud 插件和原生热键栏高亮 PoC。
- `contracts`：跨 Java、TypeScript、C# 的版本化 JSON Schema。
- `data/seeds`：带来源和置信状态的 DMU 样板数据。
- `compose.yaml`：本地 PostgreSQL、Redis 与 API 运行配置。
- `docs`：架构决策、PoC 操作和验收记录。

## 已实现的 MVP 基线

- Web：账户入口、4/8 轨计划编辑、规则校验、版本发布、匿名只读分享、插件绑定码授权和 AI 候选确认。
- API：邮箱密码认证、JWT/刷新令牌、设备授权、计划草稿与不可变版本、运行时计划匹配、幂等实战记录上传、DeepSeek JSON 候选适配器。
- 插件：4/8 轨通用状态机、时间锚点校时、ActionEffect 执行确认、贴合原生热键栏槽位的只读覆盖层与本地计划文件。
- 数据：固定 DMU Ikuya Mitty / LPDU 上游修订与哈希，统一保留 `POC_PENDING`。

当前仍有三项必须在游戏环境完成：国服/国际服最新版本的热键栏实机验证、国服事件链诊断，以及 DMU Action/状态/锚点的完整回放验收。未完成前不会把来源标记为 `VERIFIED`。

## 项目进度

- 当前测试版：`0.1.3`。
- FFLogs 国服 API PoC 已通过；原生热键栏高亮、国服事件链和 DMU 绝对时间轴仍在实机验收中。
- 详细阶段、PoC 和 PRD 验收项映射见 [`docs/PROGRESS.md`](docs/PROGRESS.md)。
- GitHub `main` 是源代码真源，Gitee 是国内镜像；发布后两个仓库应保持同一提交和标签。

## Codex 云端开发

仓库包含 [`AGENTS.md`](AGENTS.md)，Codex 会从中读取项目边界、验证命令和发布规则。创建 Codex 云端环境时，将环境设置脚本配置为：

```bash
bash tools/codex_setup.sh
```

脚本只安装公开依赖和官方 Dalamud 开发文件，不写入项目密钥。FFLogs、DeepSeek、数据库和 OAuth 凭据应通过 Codex 环境密钥注入，不得提交到仓库。

## Dalamud 安装与更新

在 Dalamud 设置的 `Experimental` 页面，把下面的地址加入 `Custom Plugin Repositories`：

```text
https://cdn.jsdelivr.net/gh/Coptone/VedaAxis@latest/pluginmaster.json
```

保存后打开插件安装器，搜索 `VedaAxis` 并安装。仓库清单和插件 ZIP 均使用无重定向的 raw 地址，后续版本仍通过这个地址检查和安装更新。当前发布是实机 PoC 测试版，不代表热键栏矩阵已经验收通过。

## 本地启动

1. 复制 `.env.example` 为 `.env`，只在本地填写密钥。
2. 启动 PostgreSQL 与 Redis：`docker compose up -d`。
3. 启动 API：进入 `services/api` 后运行 `mvn spring-boot:run`。
4. 安装前端依赖并启动：`pnpm install`，然后 `pnpm dev:web`。
5. Dalamud 插件构建与实机加载见 `docs/poc/dalamud.md`。
6. FFLogs 样板报告抽取见 `docs/poc/fflogs.md`；Client 凭据只放进程环境。

## 本地验证

- API：`mvn test`
- Web：`pnpm build:web && pnpm test:web`
- 插件核心：`dotnet test plugins/VedaAxis.Core.Tests/VedaAxis.Core.Tests.csproj -c Release`
- 插件：设置 `DALAMUD_HOME` 后运行 `dotnet build plugins/VedaAxis/VedaAxis.csproj -c Release`

密钥不得提交到仓库。仓库按确认结果不附加开源许可证。
