# 第四阶段设计：质量门禁、覆盖率与依赖安全扫描

## 一、目标

在第一阶段（CI 建立）的基础上，进一步补齐代码质量保障能力：

1. **覆盖率门禁框架**：前后端分别接入覆盖率报告生成能力，方便了解测试覆盖情况，为后续设置阈值做准备
2. **TypeScript 严格模式**：提升前端类型安全基线
3. **依赖安全扫描**：通过 `npm audit` 检测高危依赖漏洞
4. **CI 覆盖率 artifact**：将覆盖率报告纳入 CI 产物，便于团队查阅

## 二、变更清单

### 2.1 后端 - JaCoCo

**文件**：`playwright-platform-server/pom.xml`

**变更**：新增 `jacoco-maven-plugin`（0.8.12），绑定两个 execution：

- `prepare-agent`：在测试前加载 JaCoCo agent
- `report`：在 `test` 阶段生成 HTML/XML 覆盖率报告

**产物位置**：`playwright-platform-server/target/site/jacoco/`

**设计决策**：暂不设置 `<rules>` 硬性阈值。原因：当前测试以单元测试为主，整体覆盖率偏低；先接入框架让团队熟悉报告，后续再逐步提升覆盖率目标。

### 2.2 前端 - Vitest v8 Coverage

**文件**：`playwright-platform-web/package.json`

**变更**：新增 devDependency `@vitest/coverage-v8@3.2.6`（与当前 vitest 版本保持一致）。

**文件**：`playwright-platform-web/vite.config.ts`

**变更**：在 `test` 配置下新增 `coverage` 块：

```typescript
coverage: {
  provider: 'v8',
  reporter: ['text', 'html', 'lcov'],
  reportsDirectory: 'coverage',
}
```

**报告位置**：`playwright-platform-web/coverage/index.html`

**为什么选 v8**：v8 覆盖率由 V8 引擎原生提供，相比 istanbul 对 TypeScript/Vue SFC 的支持更直接，无需额外的 instrument 编译。

### 2.3 前端 - TypeScript Strict Mode

**文件**：`playwright-platform-web/tsconfig.app.json`

**变更**：在 `compilerOptions` 中新增 `"strict": true`。

**影响**：启用以下严格检查：

- `noImplicitAny`：不允许隐式 `any`
- `strictNullChecks`：`null`/`undefined` 需显式处理
- `strictFunctionTypes`：函数参数类型协变
- `noImplicitThis`：`this` 的类型必须可推断

原有 `noUnusedLocals`、`noUnusedParameters`、`noFallthroughCasesInSwitch` 保持不变，作为额外的 lint 层检查。

**兼容性验证**：`npm run build`（内部调用 `vue-tsc -b`）通过，表明当前代码库已满足严格模式要求。

### 2.4 前端 - npm audit fix

**变更**：修复 `form-data` 的 CRLF injection 高危漏洞（GHSA-hmw2-7cc7-3qxx）。

**验证**：`npm audit` 当前返回 0 vulnerabilities。

### 2.5 CI 工作流增强

**文件**：`.github/workflows/ci.yml`

**主要变更**：

- **后端 job 重命名**："Backend Tests" → "Backend Tests and Coverage"
- **后端新增**：JaCoCo 覆盖率报告 artifact 上传（`target/site/jacoco/`，保留 7 天）
- **前端 job 重命名**："Frontend Tests And Build" → "Frontend Tests, Build and Coverage"
- **前端测试**：`npm test -- --coverage` 生成覆盖率报告
- **前端新增**：Vitest 覆盖率报告 artifact 上传
- **前端新增**：`npm audit --audit-level=high`（`continue-on-error: true`，信息性检查，不阻塞构建）

**为什么 audit 为信息性检查**：依赖漏洞往往需要上游修复，强制阻塞会影响正常迭代。当前以告警为主，后续可考虑集成 Dependabot 自动提 PR。

### 2.6 .gitignore 增强

**文件**：`.gitignore`

**变更**：显式忽略 `playwright-platform-web/coverage/` 和 `playwright-platform-server/target/`，避免误提交覆盖率产物。

## 三、后续可扩展方向

1. **覆盖率阈值**：待覆盖率稳定上升后，在 JaCoCo 和 Vitest 配置中加入最小覆盖率阈值（如 `line >= 50%`），低于阈值则构建失败。
2. **覆盖率平台对接**：`lcov.info` 已生成，可接入 Codecov / Coveralls 展示历史趋势。
3. **依赖自动更新**：集成 Dependabot，定期扫描并提 PR 更新依赖。
4. **后端 Checkstyle / SpotBugs**：引入 Java 静态分析工具，检测代码风格与常见 bug 模式。
5. **后端 OWASP dependency-check**：类似 npm audit 的 Java 依赖漏洞扫描。
