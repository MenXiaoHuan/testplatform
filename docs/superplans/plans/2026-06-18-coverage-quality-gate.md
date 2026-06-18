# 第四阶段：质量门禁、覆盖率与依赖安全扫描

## 一、阶段目标

在已有的 CI 基础上，补齐代码质量保障能力：

- 接入后端 JaCoCo 和前端 Vitest v8 Coverage 覆盖率报告框架
- 启用前端 TypeScript strict 模式
- 修复已知高危依赖漏洞
- 在 CI 中归档覆盖率报告，便于团队查阅

## 二、实施内容

| # | 任务 | 相关文件 | 状态 |
|---|------|---------|------|
| 1 | 后端接入 JaCoCo Maven 插件 | `playwright-platform-server/pom.xml` | ✅ |
| 2 | 前端接入 Vitest v8 Coverage | `playwright-platform-web/package.json`, `vite.config.ts` | ✅ |
| 3 | 前端启用 TypeScript strict 模式 | `playwright-platform-web/tsconfig.app.json` | ✅ |
| 4 | 修复 npm audit 高危漏洞 | `playwright-platform-web/package-lock.json` | ✅ |
| 5 | CI 工作流增强，上传覆盖率 artifact | `.github/workflows/ci.yml` | ✅ |
| 6 | 更新 .gitignore 排除覆盖率产物 | `.gitignore` | ✅ |
| 7 | 更新 README 说明 | `README.md` | ✅ |
| 8 | 创建设计文档 | `docs/superplans/specs/2026-06-18-coverage-quality-gate-design.md` | ✅ |

## 三、验证方式

- **后端**：`cd playwright-platform-server && mvn test` → BUILD SUCCESS，覆盖率报告生成于 `target/site/jacoco/`
- **前端测试**：`cd playwright-platform-web && npm test -- --coverage` → 所有测试通过，报告生成于 `coverage/`
- **前端构建**：`cd playwright-platform-web && npm run build` → 成功（启用 strict 模式）
- **依赖安全**：`cd playwright-platform-web && npm audit` → 0 vulnerabilities

## 四、设计文档

详见 [`docs/superplans/specs/2026-06-18-coverage-quality-gate-design.md`](../specs/2026-06-18-coverage-quality-gate-design.md)
