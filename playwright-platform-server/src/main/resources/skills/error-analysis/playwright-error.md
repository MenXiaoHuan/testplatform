# UI自动化异常排查 SOP

## 适用场景
Playwright 测试执行失败，包括：浏览器启动失败、页面加载超时、选择器找不到元素、断言失败、网络请求拦截异常等。

## 排查步骤（按优先级）

### 第1步：获取任务执行详情
```
调用：TaskTool.getTask(taskId, spaceId)
关注：status / currentStage / resultMessage / passedCount / failedCount
```
- status=COMPLETED + failedCount>0：用例级失败，需看具体错误
- status=FAILED：任务整体失败，看 resultMessage 定因
- currentStage 停在某阶段：定位卡住的阶段

### 第2步：分析错误日志
```
调用：LogPreprocessingTool.analyzeLogs(taskId, spaceId)
关注：Errors found / FAILED 用例列表 / stage stream 预览
```
常见错误模式：
- `Browser launch failed` → 内存不足，转【容器资源异常】
- `Timeout 30000ms exceeded` → 页面加载超时，检查网络/选择器
- `page.waitForSelector` 失败 → 选择器与实际页面不匹配
- `net::ERR_` 系列 → 网络/DNS 问题
- `Error: expect(received).toBe(expected)` → 断言失败，比对实际与期望

### 第3步：检查场景配置
```
调用：SceneTool.getSceneDetail(sceneId, spaceId)
关注：branch / testSelectorType / testSelectorValue / browser / envJson
```
- 分支是否正确？是否已合入最新代码？
- 选择器类型（CSS/XPath/text/regex）和值是否合理？
- 浏览器版本是否匹配测试要求？
- 环境变量是否正确配置（API 地址、Token 等）？

### 第4步：检查仓库配置
```
调用：RepositoryTool.getRepository(repoId, spaceId)
关注：gitUrl / defaultBranch / testRoot / runCommand
```
- Git URL 是否可访问？凭据是否过期？
- 测试目录路径是否正确？
- 运行命令模板是否包含必要参数？

## 常见故障 → 根因映射表
| 错误关键字 | 可能根因 | 排查动作 |
|-----------|---------|---------|
| `insufficient memory` | 容器内存不足 | 增大 Docker memory 限制 |
| `Timeout exceeded` | 页面加载超时/网络慢 | 增加超时配置；检查目标站点可达性 |
| `Navigation timeout` | 路由跳转超时 | 检查前端路由守卫；检查 API 响应时间 |
| `selector not found` | DOM 结构变化/选择器过时 | 更新 Playwright 选择器；检查页面是否异步渲染 |
| `net::ERR_CONNECTION` | 目标服务不可达 | 检查环境配置；确认测试环境服务正常 |
| `401/403 Forbidden` | 鉴权 Token 过期 | 更新 envJson 中的鉴权凭据 |

## 快速恢复方案
1. 重新触发任务：可能是临时网络抖动
2. 切换分支：使用稳定分支而非特性分支
3. 调整选择器：改用更稳定的 data-testid 选择器
4. 增加超时：在 envJson 中增大 timeout 配置