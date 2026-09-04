# DocQuery 业务提示词

版本由 `docquery.prompt.version` 指定，当前 `v1`。
与工作生产 / 对照仓库的差异：冲刺 `notes/口径-提示词.md`。

## 已接入问答链路

- `v1/qa-system.md`
- `v1/qa-user.md`
- `v1/qa-context.md`
- `v1/qa-refuse-none.md`
- `v1/qa-guidance-ungated.md`（当前请求使用）

## 已写、功能后补（面试可打开，勿说已跑通）

- `v1/qa-guidance-none.md` / `weak` / `partial` / `sufficient` — 四级闸门
- `v1/query-planning-system.md` + `query-planning-user.md` — DIRECT / REWRITE / DECOMPOSE
- `v1/assistant-chat.md` — ReAct CHAT，不检索
- `v1/assistant-kb-search.md` — ReAct KB_SEARCH，Observation 程序返回，单轮一次检索
