# 黄金集（仿制度）

语料在 `corpus/`（14 份仿制度 + 3 份干扰稿），题目在 `goldenset.json`（56 题，v1.4）。**不是工作原文**：姓名改成岗位，内网地址、账号未收录。

| 切片 | 题数 | 看什么 |
|------|------|--------|
| 回归（专名 + 同义） | 34 | `hits`/`hitsAt1`/`mrr` |
| 难例 `hard` | 8 | `hardHits` / `hardHitsAt1` / `hardMrr` |
| 错字缩写 `noisy` | 4 | `noisyHits` / `noisyHitsAt1` / `noisyMrr` |
| 易识别负例 | 6 | `emptyNegatives` / `negatives` |
| 对抗负例 | 4 | `emptyAdversarial` / `noneAdversarial` / `adversarial` |

各切片不要加总对外报。干扰稿故意写错日期和字段含义；标针只出现在正确文档。负例 / 对抗负例不计入命中，空检索还不是拒答率。

部门工时原文几乎是原型图，只写了上传 / 审核 / 确认。工时出单时点以 22 号规则为准（老交接曾写每月 1 号）。

## 验收（需通义 Key、库已启动）

```bash
# 入库仿制度（已存在同名文件则跳过）
curl -X POST http://localhost:18081/api/eval/seed-corpus
# 语料改过、库里已有同名文件时：
curl -X POST "http://localhost:18081/api/eval/seed-corpus?replace=true"

# 只跑检索，不调生成。看 Hit@5、Hit@1、rank，不要加总进简历
curl -X POST http://localhost:18081/api/eval/goldenset
```

跑完会在工作目录写下 `eval-runs/latest.json`（git 忽略）。面试对照表在 `sprint/notes/主轴-选型与指标.md` §2.1，不靠翻控制台。

语料未改时不必 `replace`，重启应用即可跑 v1.4（判定代码变了）。

## 返回值对照

| 字段 | 看什么 |
|------|--------|
| `hits` / `hitsAt1` / `mrr` | 回归 34 道：进 Top5 / 排第一 / 平均倒数排名 |
| `hardHits` / `hardHitsAt1` / `hardMrr` | 短问难例 8 道 |
| `noisyHits` / `noisyHitsAt1` / `noisyMrr` | 错字缩写 4 道 |
| `emptyNegatives` / `negatives` | 食堂类负例是否空检索 |
| `emptyAdversarial` / `noneAdversarial` / `adversarial` | 对抗负例：空检索 / 闸门 NONE / 题数。WEAK 仍生成 |
| `items[].rank` | 标针在 Top5 的第几名，未进则为 null |

Hit@k = Top-k **RRF 命中切片**里是否出现该题的 `expected_hit_contains`。类簇 ±1 的扩窗正文不计入召回。  
拒答只算 `evidenceLevel=NONE`。`items[].evidenceLevel` 看 WEAK/PARTIAL/SUFFICIENT。面试口径与已测基线：`sprint/notes/主轴-选型与指标.md` §2.1。

问答日志关键字：`ask.timing requestId=... embedMs= retrieveMs= llmMs= totalMs=`。P50/P99 用多轮日志自己算，本机数字必须带测量条件。
