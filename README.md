# DocQuery — RAG 检索增强问答系统

> 从零搭建的个人 RAG 项目，用于验证对 RAG 架构的理解。
> 技术栈：Spring Boot 3 + Java 21 + LangChain4j + pgvector + 通义千问

## 动机

工作中维护过一个基于 ES 关键词检索的知识库系统（bomc-doc-manage），
检索效果差、没有语义理解。系统学习 RAG 后从零搭建 DocQuery，
用语义检索从根本上解决"搜不到"的问题。

## 快速启动

```bash
# 1. 启动 PostgreSQL + pgvector
docker compose up -d

# 2. 设置 API Key
set DASHSCOPE_API_KEY=your-key-here   # Windows
export DASHSCOPE_API_KEY=your-key-here # Linux/Mac

# 3. 启动应用
./mvnw spring-boot:run
```

## 设计决策

| 决策 | 选择 | 原因 |
|------|------|------|
| AI 框架 | LangChain4j | provider 抽象干净，换模型只改配置 |
| 检索 | 单路 PGvector（MVP） | 先验证语义检索价值，后续加 ES 关键词通道 |
| 分块 | 固定 512 token + 64 overlap | 先跑通链路，后续演进结构感知切片 |
| 数据库迁移 | Flyway | 从第一天管住 schema 版本 |
| 存储 | 本地文件（接口抽象） | 零依赖启动，后续可换 MinIO/S3 |
| 鉴权 | 无（单用户模式） | 聚焦 RAG 链路，鉴权在 BOMC-Cloud 已证明 |

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
