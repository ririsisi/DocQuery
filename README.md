# DocQuery — RAG 检索增强问答系统

> 从零搭建的个人 RAG 项目，用于验证对 RAG 架构的理解。
> 技术栈：Spring Boot 3 + Java 21 + LangChain4j + pgvector + 通义千问

## 动机

工作中维护过一个基于 ES 关键词检索的知识库系统（bomc-doc-manage），
检索效果差、没有语义理解。系统学习 RAG 后从零搭建 DocQuery，
用语义检索从根本上解决"搜不到"的问题。

## 快速启动

```bash
# 1. 启动 PostgreSQL + pgvector（宿主机端口 15432，避开 Windows 保留端口 5432）
docker compose up -d

# 2. 设置 API Key
set DASHSCOPE_API_KEY=your-key-here   # Windows
export DASHSCOPE_API_KEY=your-key-here # Linux/Mac

# 3. 启动应用（DevTools 已启用，改 Java 保存后自动编译+重启）
mvn spring-boot:run
```

**开发循环**：改代码 → 停手 800ms 自动保存 → **`Ctrl+Shift+T`** 打开 Task 列表选 **DocQuery: Maven Compile** → DevTools 重启 → `curl` 验收。

## 设计决策

| 决策 | 选择 | 原因 |
|------|------|------|
| AI 框架 | LangChain4j | provider 抽象干净，换模型只改配置 |
| 检索 | 单路 PGvector（MVP） | 先验证语义检索价值，后续加 ES 关键词通道 |
| 分块 | 固定 512 token + 64 overlap | 先跑通链路，后续演进结构感知切片 |
| 数据库迁移 | Flyway | 从第一天管住 schema 版本 |
| 存储 | 本地文件（接口抽象） | 零依赖启动，后续可换 MinIO/S3 |
| 鉴权 | 无（单用户模式） | 聚焦 RAG 链路，鉴权在 BOMC-Cloud 已证明 |

## 本地环境踩坑（面试可讲）

| # | 现象 | 根因 | 解法 |
|---|------|------|------|
| 1 | `docker-credential-desktop` not in PATH | Docker credsStore 配置了 desktop，bin 未进 PATH | 补 `C:\Program Files\Docker\Docker\resources\bin` 到 PATH |
| 2 | Docker Hub pull EOF | registry 网络不稳定 | Docker Engine 加 `registry-mirrors` |
| 3 | 5432 bind forbidden，netstat 无占用 | Windows 保留端口段 5430–5529 | 宿主机改 **15432:5432**，JDBC 同步 |

详细话术见 `interview-prep/notes/docquery-decisions.md`（T-01 ~ T-05）。

## API

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/documents/upload` | 上传文档（PDF/DOCX/TXT） |
| GET  | `/api/documents` | 文档列表 |
| POST | `/api/ask` | 知识库问答 |
| POST | `/api/ask/stream` | 流式问答（SSE） |

## 项目结构

```
src/main/java/com/docquery/
├── DocQueryApplication.java
├── document/          # 文档上传 + 解析
├── chunk/             # 分块 + 向量化
├── search/            # 语义检索
├── qa/                # 问答 + 流式输出
└── common/            # 异常处理、统一响应
```
