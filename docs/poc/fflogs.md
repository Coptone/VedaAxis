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
