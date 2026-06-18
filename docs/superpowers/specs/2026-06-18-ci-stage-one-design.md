# 第一阶段 CI 设计

## 目的

本阶段为项目补齐最小可用 CI，让主干在每次提交和 Pull Request 时自动验证后端测试、前端依赖安装、前端测试和前端构建。目标是尽早发现不可测试、不可构建或依赖锁不一致的问题，给后续 Docker、数据库迁移、覆盖率门禁和 Runner 安全改造提供稳定基线。

## 范围

- 新增 GitHub Actions 工作流文件 `.github/workflows/ci.yml`。
- 后端执行 `mvn test`，工作目录为 `playwright-platform-server`。
- 前端执行 `npm ci`、`npm test`、`npm run build`，工作目录为 `playwright-platform-web`。
- 在 `README.md` 增加 CI 说明，明确主干门禁会执行的命令。

## 不在本阶段处理

- 不新增覆盖率门禁、lint、format 或依赖扫描。
- 不调整 Docker、Flyway、多环境 profile 或生产配置。
- 不重构 Runner 执行模型。

## 工作流设计

CI 使用 GitHub Actions，触发条件为 `push` 和 `pull_request`。

后端 Job 使用 `ubuntu-latest` 和 Java 21，并启用 Maven 缓存，执行：

```bash
mvn test
```

前端 Job 使用 `ubuntu-latest` 和 Node.js 20，并启用 npm 缓存，执行：

```bash
npm ci
npm test
npm run build
```

前后端 Job 独立运行，便于快速定位失败来源。

## 验收标准

- 本地可通过 `mvn test`。
- 本地可通过 `npm ci`、`npm test`、`npm run build`。
- GitHub Actions 配置语法清晰，路径与当前项目结构一致。
- README 中能看到 CI 会执行哪些检查。
