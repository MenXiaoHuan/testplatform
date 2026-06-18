# 第五阶段设计：Docker Runner 容器隔离

## 一、背景

当前平台的任务执行链路已经具备队列、超时、取消、阶段日志归档、结果解析和产物归档能力。现有 Runner 通过 `ProcessBuilder` 在后端进程所在环境直接执行仓库配置的安装命令和测试命令。该模式便于本地开发，但存在以下风险：

- 测试仓库命令直接运行在后端宿主环境，隔离能力弱
- 依赖安装和测试执行可能污染后端容器或宿主机文件系统
- 取消或超时时只能强杀当前进程，子进程清理不够明确
- 后续多租户、远端执行、资源限制和权限审计缺少统一边界

第五阶段优先解决执行隔离问题：在不重构整体任务调度架构的前提下，把安装和测试阶段放入短生命周期 Docker Runner 容器中执行。

## 二、目标

1. 新增 Docker Runner 执行模式，使安装命令和测试命令在独立容器中运行。
2. 保留现有任务编排、日志归档、结果解析、产物归档和取消语义。
3. 提供 `local` 与 `docker` 两种执行模式，默认保持 `local` 兼容。
4. Docker 模式支持基础资源限制，包括 CPU、内存、网络和工作目录挂载。
5. Docker Compose 开发环境可一键验证 Docker Runner 模式。
6. 为后续独立 Runner Worker、远端 Runner 和调度队列演进保留接口边界。

## 三、非目标

- 本阶段不引入独立 Runner Worker 服务。
- 本阶段不引入消息队列、心跳、任务抢占和分布式调度。
- 本阶段不做完整多租户权限系统。
- 本阶段不实现 Kubernetes Job 或远端 Docker API 调度。
- 本阶段不强制所有环境启用 Docker Runner，仍保留本地执行模式。

## 四、方案选择

### 方案 A：短生命周期 Docker Runner 容器

后端继续负责任务编排。每个 install/test stage 通过 `docker run --rm` 启动独立容器执行命令，任务工作区通过 bind mount 挂入容器。

优点：

- 对现有架构侵入最小
- 可复用现有任务状态机、日志归档和结果解析
- Compose 环境可以快速验证
- 容器天然提供文件系统、进程和资源隔离边界

缺点：

- 需要 server 访问 Docker daemon
- 挂载 Docker socket 具备高权限，需要明确只用于开发/受控环境
- 容器镜像、网络策略和资源限制需要配置化

### 方案 B：独立 Runner Worker 服务

后端只创建任务并投递执行请求，独立 Runner Worker 拉取任务并运行容器。

优点：

- 职责边界更清晰
- 更适合横向扩容和多节点执行
- 后端不需要直接访问 Docker daemon

缺点：

- 需要引入队列、心跳、重试、任务抢占和失败恢复
- 改造范围大，不适合当前阶段作为第一步落地

### 方案 C：本地执行加固

继续使用 `ProcessBuilder`，补充命令校验、环境变量过滤、进程组清理和路径防护。

优点：

- 改动最小
- 不依赖 Docker

缺点：

- 仍然不能提供强隔离
- 对外部仓库的不可信命令防护不足

结论：第五阶段采用方案 A。

## 五、总体架构

### 5.1 执行模式

新增配置：

```yaml
platform:
  runner:
    mode: local
    docker:
      image: mcr.microsoft.com/playwright:v1.44.0-jammy
      network: bridge
      memory: 2g
      cpus: "2"
      container-workspace-root: /workspace
      remove-container: true
```

含义：

- `platform.runner.mode=local`：使用当前 `ProcessBuilder` 本地执行器。
- `platform.runner.mode=docker`：使用 Docker 执行器。
- `platform.runner.docker.image`：Runner 容器镜像。
- `platform.runner.docker.network`：Docker 网络模式，支持 `bridge`、`host`、`none` 或指定 Compose 网络名。
- `platform.runner.docker.memory`：传给 `docker run --memory`。
- `platform.runner.docker.cpus`：传给 `docker run --cpus`。
- `platform.runner.docker.container-workspace-root`：容器内工作区根目录。
- `platform.runner.docker.remove-container`：默认 true，使用 `--rm` 清理容器。

默认值选择：

- `application.yml` 默认 `local`，避免没有 Docker 的环境启动失败。
- `application-dev.yml` 可通过环境变量覆盖。
- `docker-compose.yml` 中 server 服务设置 `PLATFORM_RUNNER_MODE=docker`，并挂载 `/var/run/docker.sock`。

### 5.2 执行器边界

保留现有接口：

- `RunnerCommandExecutor`
- `RunnerCommandRequest`
- `RunnerCommandResult`
- `RunnerExecutionService`

新增实现：

- `LocalRunnerCommandExecutor`：承载现有本地执行逻辑。
- `DockerRunnerCommandExecutor`：新增 Docker 容器执行逻辑。
- `RunnerCommandExecutorConfig`：根据 `platform.runner.mode` 注入对应实现。
- `DockerRunnerProperties`：承载 Docker Runner 配置。

现有 `RunnerExecutionServiceImpl` 继续依赖 `RunnerCommandExecutor`，不直接感知 local/docker 差异。

### 5.3 工作区挂载

当前 `RunnerWorkspaceService` 仍负责：

1. 创建任务工作区根目录。
2. 清理同 taskId 已存在目录。
3. clone 指定 git 仓库和分支。
4. 返回任务 workspace 路径。

Docker Runner 执行时：

- 将任务 workspace 根目录 bind mount 到容器内，例如 `-v /tmp/playwright-platform/workspaces/101:/workspace/task:rw`。
- 将 stage 的执行目录转换为容器内路径，例如宿主 `/tmp/playwright-platform/workspaces/101/playwright_framework` 对应容器 `/workspace/task/playwright_framework`。
- 只挂载当前 task workspace，不挂载整个 workspace root。
- 执行前校验 `workingDirectory` 必须位于 task workspace 内，防止路径逃逸。

为了支持路径转换，`RunnerCommandRequest` 可增加 `workspaceRoot` 字段，或新增内部上下文对象。推荐新增轻量字段：

```java
Path workspaceRoot
```

如果为 local 模式，可直接忽略该字段；docker 模式必须使用它做 mount 边界。

## 六、Docker 命令设计

Docker Runner 构造命令示例：

```bash
docker run --rm \
  --name playwright-platform-task-101-installing-<short-id> \
  --workdir /workspace/task/playwright_framework \
  --memory 2g \
  --cpus 2 \
  --network bridge \
  -e PLAYWRIGHT_PLATFORM_MODE=true \
  -v /tmp/playwright-platform/workspaces/101:/workspace/task:rw \
  mcr.microsoft.com/playwright:v1.44.0-jammy \
  /bin/sh -lc "npm install"
```

关键约束：

- 容器名必须唯一，包含 taskId、stage 和短随机后缀。
- Docker 命令以参数数组方式构造，不拼接成单个 shell 字符串。
- 用户配置的 install/test 命令只作为容器内部 `/bin/sh -lc <command>` 的参数。
- 环境变量只允许传入平台白名单和场景配置解析后的 env，避免泄漏后端凭据。
- 默认不使用 `--privileged`。
- 默认不挂载 Docker socket 到 Runner 容器。
- 默认只挂载任务 workspace。

## 七、日志、取消与超时

### 7.1 日志

Docker Runner 仍通过 `ProcessBuilder` 启动 `docker run` 命令，并读取该进程 stdout/stderr。

现有日志行为保持不变：

- stdout/stderr 合并
- 写入临时 log file
- 统计 line count
- stage 结束后由 `TaskStageLogService` 上传到对象存储

### 7.2 取消

取消流程：

1. 用户调用取消接口，任务标记 `cancelRequested=true`。
2. 执行器轮询 cancellation supplier。
3. Docker Runner 检测到取消后：
   - 执行 `docker rm -f <containerName>`。
   - 强制终止本地 `docker run` 进程。
   - 返回 `RunnerCommandResult(canceled=true)`。
4. 上层任务状态进入 `CANCELED`。

### 7.3 超时

超时流程：

1. 执行器检测 stage 超过 timeout。
2. 执行 `docker rm -f <containerName>`。
3. 强制终止本地 `docker run` 进程。
4. 返回 `RunnerCommandResult(timedOut=true)`。
5. 上层任务状态进入 `TIMEOUT`。

### 7.4 清理兜底

即使 `docker run --rm` 正常清理，取消/超时时仍显式调用 `docker rm -f`。如果删除失败，只记录 warning，不覆盖原始任务结果。

## 八、安全设计

本阶段的安全目标是降低测试命令直接污染后端环境的风险，而不是提供完整多租户沙箱。

### 8.1 权限边界

- Docker Runner 容器不使用 privileged。
- Runner 容器不挂载 Docker socket。
- Runner 容器只挂载当前 task workspace。
- server 容器挂载 Docker socket 仅用于开发或受控部署环境。
- README 中明确 Docker socket 的高权限风险。

### 8.2 路径防护

- `workingDirectory` 必须在 task workspace 内。
- results index 和 artifact root 继续沿用当前 execution directory 内路径防护。
- Docker bind mount 只使用 normalize 后的 task workspace。

### 8.3 环境变量防护

Docker Runner 只传入以下环境变量来源：

- 平台固定变量，例如 `PLAYWRIGHT_PLATFORM_MODE=true`
- 场景运行需要的环境变量，且必须经过现有 JSON 解析与校验

默认不透传：

- 数据库用户名和密码
- MinIO access key 和 secret key
- Spring 配置
- 宿主机完整环境变量

### 8.4 网络策略

`platform.runner.docker.network` 可配置：

- `bridge`：默认，适合一般外网依赖下载
- `none`：更安全，但无法安装外部依赖
- Compose 网络名：适合 Runner 访问 Compose 内服务

本阶段默认 `bridge`，后续可为仓库或场景增加网络策略。

## 九、配置与 Compose

### 9.1 `.env.example`

新增：

```dotenv
PLATFORM_RUNNER_MODE=docker
PLATFORM_RUNNER_DOCKER_IMAGE=mcr.microsoft.com/playwright:v1.44.0-jammy
PLATFORM_RUNNER_DOCKER_NETWORK=bridge
PLATFORM_RUNNER_DOCKER_MEMORY=2g
PLATFORM_RUNNER_DOCKER_CPUS=2
```

### 9.2 `docker-compose.yml`

server 服务新增：

```yaml
environment:
  PLATFORM_RUNNER_MODE: ${PLATFORM_RUNNER_MODE:-docker}
  PLATFORM_RUNNER_DOCKER_IMAGE: ${PLATFORM_RUNNER_DOCKER_IMAGE:-mcr.microsoft.com/playwright:v1.44.0-jammy}
  PLATFORM_RUNNER_DOCKER_NETWORK: ${PLATFORM_RUNNER_DOCKER_NETWORK:-bridge}
  PLATFORM_RUNNER_DOCKER_MEMORY: ${PLATFORM_RUNNER_DOCKER_MEMORY:-2g}
  PLATFORM_RUNNER_DOCKER_CPUS: ${PLATFORM_RUNNER_DOCKER_CPUS:-2}
volumes:
  - /var/run/docker.sock:/var/run/docker.sock
  - runner-workspaces:/tmp/playwright-platform/workspaces
```

说明：

- `runner-workspaces` 保证 server 容器内 workspace 路径稳定。
- Docker daemon 看到的是宿主路径；如果使用 named volume，bind mount 到 sibling Runner 容器可能不直接可见。为避免路径不一致，推荐 Compose 开发环境使用 bind mount 到项目下 `.runner-workspaces`，并将 server 内路径与宿主路径保持一致。
- 实施时需要根据 Docker Desktop/Compose 路径映射实际验证，必要时新增 `platform.runner.host-workspace-root` 和 `platform.runner.container-workspace-root` 两个配置，分别描述 server 视角和 Docker daemon 视角的路径。

推荐最终配置模型：

```yaml
platform:
  runner:
    workspace-root: ${PLATFORM_RUNNER_WORKSPACE_ROOT:${java.io.tmpdir}/playwright-platform/workspaces}
    host-workspace-root: ${PLATFORM_RUNNER_HOST_WORKSPACE_ROOT:${PLATFORM_RUNNER_WORKSPACE_ROOT:${java.io.tmpdir}/playwright-platform/workspaces}}
    docker:
      container-workspace-root: ${PLATFORM_RUNNER_DOCKER_CONTAINER_WORKSPACE_ROOT:/workspace/task}
```

Docker mount 使用 `host-workspace-root/<taskId>:/workspace/task`，Java 文件读写使用 `workspace-root/<taskId>`。

## 十、错误处理

Docker Runner 错误映射：

- Docker 命令不存在：stage 失败，result message 包含 Docker 不可用提示。
- Docker daemon 不可访问：stage 失败，提示检查 Docker socket 或 Docker 服务。
- 镜像不存在或拉取失败：stage 失败，提示检查 Runner 镜像。
- 容器启动失败：stage 失败，日志记录 docker stderr。
- 取消：返回 canceled，不作为 failed。
- 超时：返回 timedOut，不作为普通 failed。

上层任务状态沿用现有规则：

- install stage 非 0：`INSTALL_FAILED`
- test stage 非 0：`TEST_FAILED`
- canceled：`CANCELED`
- timedOut：`TIMEOUT`

## 十一、测试策略

### 11.1 单元测试

新增或更新测试：

- Docker 命令构造包含 image、network、memory、cpus、workdir、env、mount。
- Docker mount 只允许 task workspace。
- workingDirectory 路径逃逸时拒绝执行。
- cancellation 触发 `docker rm -f` 清理。
- timeout 触发 `docker rm -f` 清理。
- local 模式仍使用本地执行器，保持向后兼容。
- Docker 配置属性可正确绑定默认值和环境变量。

### 11.2 本地验证

必须通过：

```bash
cd playwright-platform-server
mvn test

cd ../playwright-platform-web
npm test
npm run build

docker compose config
```

如果本地 Docker 可用，可额外验证：

```bash
docker compose up --build
```

并创建一个轻量测试仓库任务，验证 install/test 在 Runner 容器中执行。

## 十二、实施顺序

1. 新增 Runner 模式与 Docker Runner 配置类。
2. 将当前 `RunnerCommandExecutorImpl` 拆分为 local 实现。
3. 新增 Docker Runner 命令构造与执行实现。
4. 扩展 request/context，使 Docker Runner 能拿到 task workspace 边界。
5. 更新 task 执行链路传递 workspace root。
6. 更新 Compose 和 `.env.example`。
7. 更新 README 中 Docker Runner 使用说明和 Docker socket 风险说明。
8. 补单元测试和配置测试。
9. 运行完整验证。

## 十三、风险与缓解

### Docker socket 权限风险

风险：server 容器挂载 Docker socket 后，理论上具备宿主 Docker 控制能力。

缓解：

- 明确该模式用于本地开发或受控部署环境。
- Runner 容器不再挂载 Docker socket。
- README 中标注风险。
- 后续阶段演进到独立 Runner Worker，移除后端直接访问 Docker daemon。

### Compose 路径映射风险

风险：server 容器内路径和宿主 Docker daemon 可见路径不一致，导致 sibling container bind mount 失败。

缓解：

- 增加 `host-workspace-root` 和 `workspace-root` 分离配置。
- Compose 使用项目目录下 `.runner-workspaces` bind mount。
- 单测覆盖路径转换逻辑。

### 镜像体积和依赖下载成本

风险：Playwright 官方镜像较大，首次拉取较慢。

缓解：

- Runner 镜像可配置。
- README 标注首次启动耗时。
- 后续可构建项目专用 Runner 镜像。

## 十四、验收标准

- 默认 `local` 模式下，现有后端测试全部通过。
- `docker` 模式下，install/test stage 通过 Docker 容器执行。
- 取消和超时时会尝试删除 Runner 容器。
- stage 日志仍能归档并在任务详情中查看。
- 结果解析和产物归档路径保持兼容。
- Compose 环境能启用 Docker Runner 配置。
- README 清楚说明 Docker Runner 使用方式和 Docker socket 风险。
