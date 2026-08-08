# 国服 Action 名称提取工具

这个工具用于从本机 FFXIV 国服客户端数据中提取 `ActionID -> 国服显示名` 映射，供 VedaAxis 补齐 DMU 机制国服译名、图标和物理/魔法属性。

## 使用方式

在仓库根目录运行：

```powershell
.\tools\extract_cn_action_names.ps1 -SqpackPath "D:\你的FFXIV目录\game\sqpack"
```

如果你只找到了 `game` 或安装根目录，也可以直接传进去，工具会尝试自动定位下面的 `sqpack`：

```powershell
.\tools\extract_cn_action_names.ps1 -SqpackPath "D:\你的FFXIV目录"
```

如果你只复制了最小数据文件，也可以直接传包含这些文件的目录：

```text
0a0000.win32.dat0
0a0000.win32.index
0a0000.win32.index2
```

例如：

```powershell
.\tools\extract_cn_action_names.ps1 -SqpackPath "C:\Users\Administrator\Desktop\0a0000.win32"
```

脚本会自动创建一个临时 `sqpack\ffxiv` 外壳目录，不会复制这几个大文件。

默认会提取：

- `data/seeds/dmu/p1-p2-damage-map.json` 中已有的 P1/P2 ActionID；
- `tools/ffxiv_cn_action_extract/dmu_p3_p5_action_ids.csv` 中整理的 P3/P4/P5 ActionID。

输出目录默认为：

```text
artifacts\cn-action-names
```

里面会生成一份 `.json` 和一份 `.csv`。把这两个文件发回来即可。

## 额外 ActionID

如果你有额外的 ActionID 文件，可以这样传：

```powershell
.\tools\extract_cn_action_names.ps1 `
  -SqpackPath "D:\你的FFXIV目录\game\sqpack" `
  -IdsFile "C:\temp\more-action-ids.txt"
```

`IdsFile` 可以是：

- JSON：工具会递归读取字段名为 `actionId` / `actionIds` 的数字；
- CSV：如果有 `actionId` 列，会读取该列；
- 普通文本：会提取里面所有数字。

## 关于 `_rsv_...`

如果输出的 `isRsv` 为 `true`，说明该 Action 在 `Action` 表里仍是 RSV 占位文本。这个不是目录错了，而是需要继续解析 RSV 文本或通过插件运行时采集读条显示名。

第一步先跑这个工具确认国服客户端能直接给出多少名称；如果 RSV 仍很多，再补第二段 RSV 解析。
