# Dalamud 阻断 PoC

## 目标

1. 读取 Territory、队伍、职业、战斗开始、团灭与 Boss 技能事件。
2. 找到所有可见原生热键栏中匹配 Action ID 的槽位。
3. 在槽位上绘制高亮并在成功、超时、取消、重载时清理。
4. 记录本地玩家的 ActionEffect，作为成功判定的首选证据。

## 实机矩阵

- 标准热键栏 1-10，横排、竖排和多行布局。
- 十字热键栏与双十字热键栏。
- 同技能放入多个槽位。
- 等级同步、升级替换和条件替换技能。
- 技能未放置、技能冷却、资源不足、插件重载和 HUD 布局切换。

## 通过标准

每个场景都需要保存插件诊断 JSONL、截图或录像、Dalamud API 版本、游戏版本和结论。只有全部通过后，`POC-02` 才能从 `POC_PENDING` 改为 `VERIFIED`。

## 当前代码状态（2026-08-05）

- `VedaAxis.Core` 已实现任务状态机、晚到补记、重置/取消、成功高亮自动清理和时间锚点纠偏。
- 插件已按 SDK 15 / .NET 10 编译，通过 `AddonActionBarBase.ActionBarSlotVector` 定位可见槽位，以前景绘制层描边，不修改节点和输入。
- 本地玩家 `ActionEffectHandler.Receive` 已接入，Action ID 命中后转为成功、过早或过晚。
- `/vedaaxis start|reset|reload` 可加载配置目录中的 `active-plan.json` 并进行 20 秒示例预览。
- 编译通过不等于实机通过；上述实机矩阵尚未执行，故结论仍为 `POC_PENDING`。

## 从 GitHub 安装测试版

1. 在游戏中执行 `/xlsettings`，打开 `Experimental`。
2. 在 `Custom Plugin Repositories` 中添加：
   `https://github.com/Coptone/VedaAxis/releases/latest/download/pluginmaster.json`
3. 保存设置，执行 `/xlplugins`，搜索并安装 `VedaAxis`。
4. 新版本发布后，Dalamud 会通过同一仓库地址获取更新。

发布资产中的 `pluginmaster.json` 是仓库索引，`VedaAxis.zip` 是实际插件包。两者都使用 GitHub 最新正式 Release 的稳定下载地址；因此用于实机测试的 Release 不标记为 GitHub prerelease。
