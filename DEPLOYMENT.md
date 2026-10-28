# 智能自动化测试平台 · 部署指南

> 版本 v2.0 · 2026-10-28
> 
> 本指南覆盖从**本地开发**到 **Kubernetes / 云托管** 的主流部署模式。选择哪种模式取决于团队规模、业务流量、运维能力和预算。
> 
> 相关文档：
> - `README.md`：项目概览、快速启动
> - `docs/docker.md`：Dockerfile / Compose 职责划分
> - `docs/prd-03-platform-foundation.md`：平台用户体系与权限设计

---

## 0. 快速选择你的部署模式

```
                           ┌─────────────────────┐
                           │ 你的团队有多大？       │
                           └──────────┬──────────┘
                                      │
                    ┌─────────────────┼─────────────────┐
                    │                 │                 │
                 ≤ 5 人             5~30 人            > 30 人
                    │                 │                 │
                    ▼                 ▼                 ▼
           ┌─────────────┐  ┌───────────────┐  ┌───────────────────┐
           │ 模式 B       │  │ 模式 C / E     │  │ 模式 D            │
           │ 单机 Compose │  │ 分机 / 云托管   │  │ Kubernetes        │
           │ 一台服务器    │  │ App + DB + 存储 │  │ 高可用、可弹性扩展 │
           │ 最低成本      │  │ 安全边界清晰    │  │ 适合未来大流量      │
           └─────────────┘  └───────────────┘  └───────────────────┘
```

| 模式 | 适合 | 最低规格 | 推荐规格 | 运维成本 |
|------|------|----------|----------|----------|
| **A. 本地开发** | 所有场景（本地开发调试） | 4C8G 笔记本 | 8C16G 笔记本 | 零 |
| **B. 单机 Compose** | ≤ 5 人小团队 / PoC | 2C4G VPS | 4C8G VPS + 100GB SSD | 极低 |
| **C. 分机部署** | 5~15 人团队 / 数据安全要求高 | 2×(2C4G) 云主机 | 2×(4C8G) 云主机 + 独立对象存储 | 低 |
| **D. Kubernetes** | > 15 人 / 高可用要求 / 未来扩展 | 3 节点 K8s (4C8G) | 5 节点 K8s (8C16G) + 独立数据层 | 中 |
| **E. 云托管** | 不想管基础设施 | 按需付费 | RDS + ElastiCache + S3 + ECS | 极低（供应商代劳）|

---

## 1. 架构总览

### 1.1 平台核心组件

```
┌────────────────────────────────────────────────────────────────────┐
│  用户浏览器                                                          │
└─────────────────────────┬──────────────────────────────────────────┘
                          │ HTTPS
┌─────────────────────────▼──────────────────────────────────────────┐
│  前端层：Vue 3 静态资源 + Nginx                                       │
└─────────────────────────┬──────────────────────────────────────────┘
                          │ /api 反向代理
┌─────────────────────────▼──────────────────────────────────────────┐
│  应用层：Spring Boot Server                                          │
│  ├── REST API 路由    ├── 任务调度    ├── 调度事件管理                 │
│  ├── AI Agent         ├── Runner 编排 ├── 结果解析                    │
│  ├── Cache 保护        ├── 审计日志    ├── 凭据加密                    │
│  └── Prometheus 指标（可选）                                         │
└──────┬────────┬────────┬────────────────┬──────────────────────────┘
       │        │        │                │
       ▼        ▼        ▼                ▼
    MySQL     Redis    MinIO        Docker Engine
   结构化    缓存+    对象存储     (Docker Runner)
   业务数据  Trace    截图视频
                              │
                              ▼
                     ┌─────────────────────┐
                     │ Playwright Runner   │
                     │ (短生命周期容器)     │
                     │ - 拉取测试仓库       │
                     │ - npm ci             │
                     │ - npx playwright test│
                     │ - 解析结果 + 归档产物 │
                     └─────────────────────┘
```

### 1.2 各模式的组件分布差异

| 组件 | A 本地开发 | B 单机 Compose | C 分机部署 | D Kubernetes | E 云托管 |
|------|-----------|---------------|-----------|-------------|---------|
| 前端 Web | Vite dev server | Nginx (Docker) | Nginx (Docker) | Nginx Pod + Service | S3 + CloudFront |
| 后端 Server | Spring Boot | Docker Compose | Docker Compose / systemd | Deployment + Service | ECS Fargate / Beanstalk |
| MySQL | Docker (Compose) | Docker (Compose) | **独立服务器 / 容器** | StatefulSet + PVC | **RDS MySQL** |
| Redis | Docker (Compose) | Docker (Compose) | **独立服务器 / 容器** | StatefulSet + PVC | **ElastiCache** |
| MinIO | Docker (Compose) | Docker (Compose) | **独立服务器 / MinIO** | StatefulSet + PVC | **S3** |
| Docker Runner | 本地 Docker | 同机 Docker | **同机 Docker** | **DinD / 远程 Docker** | **ECS + DinD** |

> ⚠️ **Runner 特殊说明**：平台只使用 Docker Runner——Server 通过 Docker socket 启动短生命周期 Playwright 容器。Kubernetes 模式下有两种方案（见第 14 节）：
> - **方案 1（推荐）**：Server Pod 挂载宿主机 Docker socket，Runner 容器与 Server Pod 在同一节点
> - **方案 2（更合规）**：独立 Runner Pod 用 DinD，Server 通过 Docker API 远程调度

---

## 2. 通用前置条件（所有模式共用）

### 2.1 基础要求

| 要求 | 说明 |
|------|------|
| Git | 已安装 |
| SSL 证书 | 生产 HTTPS 必需（Let's Encrypt / 云证书管理服务） |
| 域名（可选但推荐） | 正式环境建议前后端分域名 |
| 镜像仓库账号 | Docker Hub / GitHub Container Registry / Harbor / 阿里云 ACR |

### 2.2 密码与密钥清单

部署前**先准备好**所有密码和密钥，不要启动后再改：

| 项目 | 生成命令 | 用途 | 是否不可逆 |
|------|----------|------|-----------|
| MySQL root 密码 | `openssl rand -hex 24` | root 登录 | 第一次初始化后可改 |
| Redis 密码 | `openssl rand -hex 24` | AUTH | 第一次初始化后可改 |
| MinIO Secret Key | `openssl rand -base64 32` | 对象存储根密码 | 第一次初始化后可改 |
| 凭据加密密钥 | `openssl rand -base64 32` | `PLATFORM_CREDENTIAL_KEY`（AES-256-GCM，加密私有仓库凭据） | 换密钥 = 旧凭据全部作废，需重新保存 |
| DeepSeek API Key | 从 https://platform.deepseek.com 获取 | AI 助手 | 直接替换，即时生效 |

### 2.3 环境变量模板（所有模式通用）

从 `.env.example` 复制后按模式替换：

```bash
cp .env.example .env
# 编辑 .env，替换所有 <...> 占位值
```

### 2.4 通用安全清单

| 项 | 要求 |
|----|------|
| `.env` 提交到 Git | ❌ 绝对禁止，加入 `.gitignore` |
| MySQL / Redis / MinIO 暴露公网 | ❌ 禁止，只在内部网络访问 |
| 自助注册开关 | 生产环境 `PLATFORM_REGISTRATION_ENABLED=false` |
| HTTPS | 正式环境强制启用 |
| Docker socket 权限 | 只在受控服务器上部署；不要暴露 socket 到公网 |
| Runner 镜像来源 | 只用可信源，建议私有 registry 镜像扫描 |

---

## 3. 模式 A · 本地开发

### 3.1 快速启动

```bash
docker compose up -d --build
# 前端 http://localhost:5173
# 后端 http://localhost:8080
```

### 3.2 环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| Docker Desktop | ≥ 4.28 | 开启 Kubernetes 功能可选 |
| 内存 | ≥ 8GB | Docker Desktop 分配 ≥ 6GB |
| 磁盘 | ≥ 20GB 可用 | 用于镜像、Runner workspace、Docker volumes |

### 3.3 非 Docker 方式（可选）

```bash
# 先手动启动 MySQL / Redis / MinIO，然后导出 .env 变量
cd playwright-platform-server && mvn spring-boot:run -Dspring-boot.run.profiles=dev
cd playwright-platform-web && npm ci && npm run dev
```

---

## 4. 模式 B · 单机生产（Docker Compose）

> 适用：小团队、PoC 验证、首次上线。所有组件跑在一台服务器上。

### 4.1 服务器规格

| 规格 | 配置 | 说明 |
|------|------|------|
| 最低 | 2C4G + 40GB SSD | 测试环境 / PoC |
| 推荐 | 4C8G + 100GB SSD | 5~10 人团队日常使用 |
| 充足 | 8C16G + 200GB SSD | 并发任务多、AI 使用频繁 |

### 4.2 端口开放规划

| 端口 | 用途 | 公网开放 |
|------|------|---------|
| **80**（HTTP）| HTTPS 证书申请 | ✅ 是 |
| **443**（HTTPS）| 正式访问入口 | ✅ 是 |
| 10000 | MinIO API | ⚠️ 建议内网 / 来源 IP 白名单 |
| 10001 | MinIO Console | ⚠️ 建议内网 / 来源 IP 白名单 |
| 3306 | MySQL | ❌ 禁止 |
| 6379 | Redis | ❌ 禁止 |
| 8080 | 后端 | ❌ 禁止 |

### 4.3 完整部署流程

```bash
# Step 1: 准备服务器
apt update && apt install -y ca-certificates curl git openssl

# Step 2: 安装 Docker Engine
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" > /etc/apt/sources.list.d/docker.list
apt update && apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Step 3: 拉代码 + 准备 .env
cd /opt && git clone <your-repo-ssh-url> testplatform && cd testplatform
cp .env.example .env
nano .env  # 替换所有 <...>

# Step 4: 写入 DOCKER_GID（必须等于宿主机 /var/run/docker.sock 的属组）
stat -c '%g' /var/run/docker.sock
# 把输出填到 .env 的 DOCKER_GID

# Step 5: 校验 + 启动
docker compose -f docker-compose.prod.yml config
docker compose -f docker-compose.prod.yml up -d --build

# Step 6: 验证
docker compose -f docker-compose.prod.yml ps      # 全部 running/healthy
docker compose -f docker-compose.prod.yml logs -f server  # 看是否有 "Docker runner preflight passed"
curl -s http://127.0.0.1/api/auth/public-key         # 后端接口可达
docker compose -f docker-compose.prod.yml exec server docker version  # Runner 预检通过
```

### 4.4 首次注册管理员

```bash
# 1) 临时打开注册
sed -i 's/^PLATFORM_REGISTRATION_ENABLED=.*/PLATFORM_REGISTRATION_ENABLED=true/' .env
docker compose -f docker-compose.prod.yml up -d --force-recreate server
# 2) 浏览器注册管理员账号
# 3) 关闭注册并重启
sed -i 's/^PLATFORM_REGISTRATION_ENABLED=.*/PLATFORM_REGISTRATION_ENABLED=false/' .env
docker compose -f docker-compose.prod.yml up -d --force-recreate server
```

### 4.5 完整的 `.env` 示例

```env
# === 前端 ===
PLATFORM_WEB_HOST_PORT=80

# === 后端：注册与 AI ===
PLATFORM_REGISTRATION_ENABLED=false
PLATFORM_DEEPSEEK_API_KEY=sk-xxxxxxxxxxxxxxxxxxxx
PLATFORM_DEEPSEEK_BASE_URL=https://api.deepseek.com
PLATFORM_DEEPSEEK_MODEL=deepseek-chat

# === 后端：MySQL ===
PLATFORM_DB_NAME=playwright_platform
PLATFORM_DB_URL=jdbc:mysql://mysql:3306/playwright_platform?useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true&serverTimezone=UTC
PLATFORM_DB_USERNAME=root
PLATFORM_DB_PASSWORD=<strong-random-password>

# === 后端：Redis ===
PLATFORM_REDIS_HOST=redis
PLATFORM_REDIS_PORT=6379
PLATFORM_REDIS_PASSWORD=<strong-random-password>

# === 后端：MinIO ===
PLATFORM_MINIO_ENDPOINT=http://minio:9000
PLATFORM_MINIO_PUBLIC_ENDPOINT=https://your-domain.example.com/minio
PLATFORM_MINIO_API_HOST_PORT=10000
PLATFORM_MINIO_CONSOLE_HOST_PORT=10001
PLATFORM_MINIO_ACCESS_KEY=playwright_admin
PLATFORM_MINIO_SECRET_KEY=<strong-random-password>
PLATFORM_STORAGE_BUCKET=qa-report

# === 后端：Runner ===
DOCKER_GID=999
PLATFORM_RUNNER_WORKSPACE_ROOT=/runner-workspaces
PLATFORM_RUNNER_HOST_WORKSPACE_ROOT=/opt/testplatform/.runner-workspaces
PLATFORM_RUNNER_DOCKER_IMAGE=mcr.microsoft.com/playwright:v1.61.1-noble
PLATFORM_RUNNER_DOCKER_NETWORK=bridge
PLATFORM_RUNNER_DOCKER_MEMORY=2g
PLATFORM_RUNNER_DOCKER_CPUS=2
PLATFORM_RUNNER_DOCKER_CONTAINER_WORKSPACE_ROOT=/workspace/task

# === 后端：容量保护 ===
PLATFORM_TASK_CORE_POOL_SIZE=2
PLATFORM_TASK_MAX_POOL_SIZE=4
PLATFORM_TASK_QUEUE_CAPACITY=50
PLATFORM_TASK_RETENTION_DAYS=90
PLATFORM_AI_DAILY_LIMIT=50
PLATFORM_AI_MAX_CONCURRENT=4

# === 加密与 Git ===
PLATFORM_CREDENTIAL_KEY=<32-byte-base64>
PLATFORM_GIT_KNOWN_HOSTS_PATH=
PLATFORM_GIT_SUBMODULES_ENABLED=false
PLATFORM_GIT_LFS_ENABLED=false
```

---

## 5. 模式 C · 分机部署（应用 + 数据分离）

> 适用：数据安全要求高、未来可能独立扩容数据层、团队中等规模。每台服务器职责单一。

### 5.1 服务器规划

| 服务器 | 角色 | 最低规格 | 推荐规格 | 说明 |
|--------|------|----------|----------|------|
| **App 机** | Web + Server + Runner | 2C4G + 40GB SSD | 4C8G + 100GB SSD | 必须跑 Docker（Runner 用） |
| **MySQL 机** | 仅 MySQL | 2C4G + 100GB SSD | 4C8G + 500GB NVMe | SSD 越快越好 |
| **Redis + MinIO 机** | 或各自独立 | 2C4G + 100GB SSD | 4C8G + 500GB SSD | MinIO 可换云对象存储 |

### 5.2 App 机部署

```bash
# 同模式 B 的完整部署流程，但 .env 要改：
# 把 mysql / redis / minio 的 host 从 docker 内部主机名改成独立服务器 IP

# .env 关键改动
PLATFORM_DB_URL=jdbc:mysql://<mysql-server-ip>:3306/playwright_platform?useSSL=true&serverTimezone=UTC
PLATFORM_REDIS_HOST=<redis-server-ip>
PLATFORM_MINIO_ENDPOINT=http://<minio-server-ip>:9000
```

docker-compose.prod.yml **只保留** web / server / runner-workspace-init 三个服务，去掉 mysql / redis / minio。

### 5.3 MySQL 机部署

```bash
# Ubuntu 上用 apt 安装 MySQL 8.0，或用 docker run mysql:8.0 但不对外暴露 3306

# Ubuntu apt 方式
apt install mysql-server
mysql_secure_installation  # 设置 root 密码 + 禁止远程 root 登录 + 移除测试库

# 创建数据库和用户
mysql -u root -p
  CREATE DATABASE playwright_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
  CREATE USER 'platform_app'@'%' IDENTIFIED BY '<strong-random-password>';
  GRANT ALL PRIVILEGES ON playwright_platform.* TO 'platform_app'@'%';
  FLUSH PRIVILEGES;

# 配置 /etc/mysql/mysql.conf.d/mysqld.cnf
bind-address = 0.0.0.0
max_connections = 500
innodb_buffer_pool_size = 1G   # 4C8G 服务器建议物理内存 25%
slow_query_log = 1
slow_query_log_file = /var/log/mysql/slow.log
long_query_time = 2

systemctl restart mysql
systemctl enable mysql

# 防火墙：只开放 MySQL 端口给 App 机 IP
ufw allow from <app-server-ip> to any port 3306 proto tcp
```

### 5.4 Redis + MinIO 机部署

```bash
# Redis 用 docker run 或 apt install redis-server
# 密码 + 只允许 App 机连接
docker run -d --name redis --restart always \
  -p 6379:6379 \
  -e REDIS_PASSWORD=<strong-random-password> \
  redis:7 redis-server --appendonly yes --requirepass <strong-random-password>

# MinIO 用 docker run
docker run -d --name minio --restart always \
  -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=playwright_admin \
  -e MINIO_ROOT_PASSWORD=<strong-random-password> \
  -v /data/minio:/data \
  minio/minio:latest server /data --console-address ":9001"

# 防火墙
ufw allow from <app-server-ip> to any port 6379 proto tcp
ufw allow from <app-server-ip> to any port 9000 proto tcp
```

### 5.5 App 机验证

```bash
# 确认连通性
nc -zv <mysql-server-ip> 3306
nc -zv <redis-server-ip> 6379
curl -I http://<minio-server-ip>:9000/minio/health/live

# Server 启动后检查日志
docker compose -f docker-compose.prod.yml logs server | grep -E "MySQL|Redis|MinIO|runner preflight"
```

---

## 6. 模式 D · Kubernetes

> 适用：大团队、高可用要求、弹性扩缩容、未来多区域部署。**前提：已有可用的 K8s 集群**。

### 6.1 集群规格

| 节点 | 数量 | 规格 | 用途 |
|------|------|------|------|
| Master | 3 | 4C8G | 控制平面（etcd + apiserver + scheduler）|
| Worker | ≥ 5 | 8C16G | 业务 Pod 调度 |
| 数据节点 | 独立 | 4C8G + 500GB NVMe | MySQL / Redis 数据盘（可用云盘）|

### 6.2 架构概览

```
                    ┌────────────────────────────────────────────┐
                    │  Kubernetes 集群                             │
                    │                                              │
  Client ──► Ingress │
                    │   ┌──────────────────────┐
                    │   │  web Deployment (Nginx)│
                    │   │  replicas: 2          │
                    │   └─────────┬────────────┘
                    │             │ Service web-svc :80
                    │             │
                    │   ┌─────────▼────────────┐
                    │   │  server Deployment    │
                    │   │  replicas: 1~3 (HPA) │
                    │   │  Group: docker        │  ◄── 挂载宿主机 Docker socket
                    │   └─────────┬────────────┘
                    │             │ Service server-svc :8080
                    │             │
                    │   ┌─────────▼──────────────────────┐
                    │   │  Runner Pods (短生命周期，由      │
                    │   │  server 通过 Docker socket 调度) │
                    │   └─────────────────────────────────┘
                    │                                              │
                    │   ┌──────────┐  ┌──────────┐  ┌─────────┐  │
                    │   │ MySQL    │  │ Redis    │  │ MinIO   │  │
                    │   │ StatefulSet│  │ StatefulSet│  │ StatefulSet│
                    │   │ PVC: 500G │  │ PVC: 100G │  │ PVC: 1T │
                    │   └──────────┘  └──────────┘  └─────────┘  │
                    └────────────────────────────────────────────┘
```

### 6.3 需要的基础设施

| 组件 | 选择 | 说明 |
|------|------|------|
| Ingress | Nginx / Traefik / ALB | HTTPS 终止 + 路由 |
| 证书 | cert-manager + Let's Encrypt | 自动续期 |
| Storage Class | 云盘 / Longhorn / Rook-Ceph | StatefulSet PVC 供应 |
| 镜像仓库 | Harbor / ECR / GCR | 私有镜像推送 |
| 监控 | Prometheus + Grafana | 第 9 节详述 |

### 6.4 核心 Manifests（简化示例）

#### Docker socket 调度（Runner 方式）

```yaml
# server Deployment 关键点
apiVersion: apps/v1
kind: Deployment
metadata:
  name: playwright-server
spec:
  replicas: 1  # ⚠️ 当前单实例；多实例见第 13 节
  template:
    spec:
      securityContext:
        # server Pod 所在节点必须有 Docker Engine
        # Pod 内进程需要有 docker 组权限
        fsGroup: 999  # 宿主机 docker 组 GID
      containers:
      - name: server
        image: your-registry/playwright-server:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: prod-k8s
        - name: PLATFORM_DB_URL
          value: jdbc:mysql://mysql-svc:3306/playwright_platform?...
        # ... 其他 env
        volumeMounts:
        - name: docker-sock
          mountPath: /var/run/docker.sock
        - name: runner-workspace
          mountPath: /runner-workspaces
      volumes:
      - name: docker-sock
        hostPath:
          path: /var/run/docker.sock
          type: Socket
      - name: runner-workspace
        hostPath:
          path: /data/playwright/runner-workspaces
          type: DirectoryOrCreate
      # ⚠️ 使用 hostPath 意味着 Pod 绑定特定节点
      # 必须配置 nodeSelector / nodeAffinity 选对节点
      nodeSelector:
        docker-runner: "true"
```

> 🔑 **K8s 上 Docker Runner 的关键限制**：Pod 挂载宿主机 docker.sock 后，Runner 容器会在**宿主机上**被创建，不是在 Pod 内。这意味着：
> - Runner 容器日志不会出现在 Pod 日志里（会被宿主机 Docker 管理）
> - Pod 漂移到没有 Docker Engine 的节点会启动失败（所以必须 nodeSelector）
> - K8s 更推荐方案 2：独立 Runner Pod + DinD + server 通过 Docker API 远程调度（见第 14 节）

#### MySQL / Redis / MinIO StatefulSet（略，行业标准）

生产建议：
- MySQL 用 **云托管 RDS**（模式 E 方式）或 **Percona Operator** 做主从高可用
- Redis 用 **云托管 ElastiCache**（模式 E 方式）或 Redis Sentinel
- MinIO 用 **StatefulSet + 4 副本纠删码** 实现分布式对象存储

### 6.5 部署步骤

```bash
# 1) push 镜像到你的 registry
docker build -t your-registry/playwright-server:latest ./playwright-platform-server
docker build -t your-registry/playwright-web:latest ./playwright-platform-web
docker push your-registry/playwright-server:latest
docker push your-registry/playwright-web:latest

# 2) 创建命名空间和 Secret
kubectl create namespace playwright-platform
kubectl create secret generic platform-secret \
  --namespace playwright-platform \
  --from-literal=PLATFORM_DB_PASSWORD=xxx \
  --from-literal=PLATFORM_REDIS_PASSWORD=xxx \
  --from-literal=PLATFORM_MINIO_SECRET_KEY=xxx \
  --from-literal=PLATFORM_CREDENTIAL_KEY=xxx

# 3) 应用 manifests
kubectl apply -f k8s/mysql.yaml
kubectl apply -f k8s/redis.yaml
kubectl apply -f k8s/minio.yaml
kubectl apply -f k8s/server.yaml
kubectl apply -f k8s/web.yaml
kubectl apply -f k8s/ingress.yaml

# 4) 验证
kubectl get pods -n playwright-platform
kubectl get svc -n playwright-platform
kubectl describe ingress -n playwright-platform
```

### 6.6 其他 K8s 注意事项

| 项 | 说明 |
|----|------|
| **当前单实例** | server Deployment replicas 必须是 **1**（见第 13 节多实例改造）|
| HPA（自动伸缩） | 当前不能开 replicas>1，先看第 13 节再开 |
| Pod 漂移 | nodeSelector 锁定 Runner 节点，避免 Pod 漂到没有 Docker Engine 的节点 |
| DinD | 如果不想用 hostPath socket，可独立 Runner Pod 用 DinD（Docker-in-Docker）|
| PV/PVC | MySQL / Redis / MinIO 必须用 StatefulSet + PVC，不要用 hostPath |
| 镜像拉取 | 配置 imagePullSecrets 从私有 registry 拉镜像 |

---

## 7. 模式 E · 云托管

> 适用：不想管基础设施、团队小、预算充足。数据层完全用云服务，应用层用 ECS/Fargate/Beanstalk。

### 7.1 组件映射

| 平台组件 | AWS | 阿里云 | 腾讯云 |
|----------|-----|--------|--------|
| 前端静态资源 | S3 + CloudFront | OSS + CDN | COS + CDN |
| 后端 API | ECS / Fargate | ECS / ASK | TKE / CVM |
| MySQL | **RDS MySQL** | **RDS MySQL** | **云数据库 MySQL** |
| Redis | **ElastiCache Redis** | **云数据库 Redis** | **云数据库 Redis** |
| 对象存储 | **S3** | **OSS** | **COS** |
| 域名 + HTTPS | Route 53 + ACM | DNS + SSL 证书 | DNSPod + SSL 证书 |
| 负载均衡 | ALB | SLB | CLB |
| Docker Runner | ECS（独立服务）| ECS / ACK | TKE / CVM |

### 7.2 最小化云架构（AWS 示例）

```
用户 ──► CloudFront ──► ALB
                       │
        ┌──────────────┼──────────────┐
        ▼              ▼              
     Target Group  Web Task Group   Server Task Group
     (web:8080)    (ECS Fargate)    (ECS Fargate)
                                      │
          ┌───────────────────────────┼───────────────────────────┐
          ▼                           ▼                           ▼
      RDS MySQL                 ElastiCache                   S3 bucket
      (私有 VPC)                 (私有 VPC)                    (公有/私有)
          
          ▼                           ▼
     Docker Runner Tasks (ECS 独立服务)
     挂载 EFS 作为 Runner Workspace
```

### 7.3 Server Task 配置要点

| 项 | 配置 |
|----|------|
| CPU | 2 vCPU |
| 内存 | 4GB |
| 网络模式 | awsvpc（需要绑定 ENI）|
| 安全组 | 只允许访问 RDS / ElastiCache / S3 |
| EFS | 挂载 `/runner-workspaces`（Runner Workspace）|
| Docker socket | ECS Fargate 模式下不能挂载 Docker socket → **必须用 DinD** |
| 环境变量 | 全部放在 Task Definition 的 `secrets` 部分，从 SSM Parameter Store / Secrets Manager 读取 |

### 7.4 Runner 模式切换（ECS Fargate）

Fargate 没有宿主机 Docker socket，需要把 Runner 改成 DinD：

```dockerfile
# Runner DinD 镜像（给 ECS Fargate 用）
FROM docker:27-dind
# 预装 Playwright
RUN apk add --no-cache chromium nss freetype harfbuzz ca-certificates ttf-freefont

# Server 通过 Docker API 连接到 DinD Runner
# .env 改动：
PLATFORM_RUNNER_DOCKER_HOST=tcp://runner-dind:2375
# Server Deployment 中 Runner 变成一个独立的 DinD Task Group
# Server 通过 Docker API 远程调度
```

---

## 8. 容量规划指南

### 8.1 并发估算

| 指标 | 计算 | 默认 |
|------|------|------|
| 任务并发数 | = `PLATFORM_TASK_MAX_POOL_SIZE` | 单机 4，K8s 可更高 |
| 每个 Runner 资源 | 2 vCPU + 2GB 内存 | 默认值 |
| Runner 启动时间 | 镜像 pull ≈ 15~30s + npm ci ≈ 30~120s | 取决于镜像大小和依赖 |
| AI 并发数 | = `PLATFORM_AI_MAX_CONCURRENT` | 4 |
| AI 每用户每天限制 | = `PLATFORM_AI_DAILY_LIMIT` | 50 |

### 8.2 存储规划

| 数据类型 | 增长速率 | 保留策略 | 存储建议 |
|----------|----------|----------|----------|
| MySQL | 低（元数据）| 90 天 | SSD，≥ 100GB |
| Redis Trace | 中 | 90 天 TTL | SSD，≥ 50GB |
| MinIO 产物（截图/视频/Trace）| **高**（每任务 ~50~200MB）| 按策略清理 | 对象存储，≥ 500GB 起步 |
| Runner Workspace | 高（临时）| 任务完成后清理 | 本地 SSD，≥ 100GB |

### 8.3 成本估算（云环境参考）

| 模式 | 月成本（参考）| 说明 |
|------|--------------|------|
| 单机 4C8G VPS | ¥200~400 | 最低成本 |
| 分机 3×(4C8G) | ¥600~1200 | 数据安全边界清晰 |
| K8s 5 节点 | ¥2000~4000 | 含 EBS / SLB / NAT |
| 云托管（RDS + ECS + S3）| ¥1500~3000 | 按需付费 |

---

## 9. 监控、日志与告警

### 9.1 Prometheus + Grafana（推荐）

#### 暴露的 Spring Boot Actuator 端点

```yaml
# application.yml（已内置）
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  metrics:
    export:
      prometheus:
        enabled: true
```

#### 核心监控指标

| 指标 | 说明 | 告警阈值 |
|------|------|---------|
| `jvm_memory_used_bytes` | JVM 内存 | 持续 > 80% heap |
| `http_server_requests_seconds` | API P95 延迟 | > 2s |
| `platform_tasks_running` | 正在执行的任务数 | > `PLATFORM_TASK_MAX_POOL_SIZE` |
| `platform_tasks_failed_total` | 失败任务累计 | 10 分钟内 > 5 个 |
| `platform_runner_container_disk_usage` | Runner 容器磁盘 | > 85% |
| `platform_ai_calls_total` | AI 调用次数 | 监控成本趋势 |
| `platform_ai_calls_latency_seconds` | AI 响应延迟 | P95 > 60s |
| `platform_db_connection_pool_active` | DB 连接池活跃数 | > 80% max |
| `redis_memory_used_bytes` | Redis 内存 | > 80% maxmemory |

#### Grafana Dashboard 规划

```
Dashboard 1: 总览
  ├── 服务器资源（CPU/内存/网络）
  ├── 关键 API 延迟（P50/P95/P99）
  ├── 任务并发趋势
  ├── 失败率趋势
  ├── AI 调用趋势

Dashboard 2: 任务详情
  ├── 各阶段耗时（PREPARING / INSTALLING / TESTING / ARCHIVING）
  ├── Runner 容器状态
  ├── 产物归档成功率
  └── 最近失败任务列表

Dashboard 3: AI 详情
  ├── 每用户调用数
  ├── Token 消耗趋势
  ├── 并发对话数
  ├── 平均响应延迟
  └── Trace 阶段耗时分布
```

### 9.2 日志体系

| 模式 | 推荐方案 | 说明 |
|------|----------|------|
| Docker Compose | `docker logs` + 宿主机 `journalctl` | 简单够用 |
| K8s | **Loki** + `promtail` + Grafana | 从 Pod 日志统一采集 |
| 云环境 | 云日志服务（AWS CloudWatch / 阿里云 SLS）| 自动采集 |

### 9.3 告警规则示例（Prometheus Alertmanager）

```yaml
groups:
- name: platform-alerts
  rules:
  - alert: HighTaskFailureRate
    expr: increase(platform_tasks_failed_total[10m]) > 5
    for: 2m
    labels:
      severity: critical
    annotations:
      summary: "任务失败率过高"
      description: "最近 10 分钟 {{ $value }} 个任务失败"

  - alert: ServerDown
    expr: up{job="server"} == 0
    for: 1m
    labels:
      severity: critical
    annotations:
      summary: "Server 服务不可用"

  - alert: DatabaseConnectionPoolNearLimit
    expr: platform_db_connection_pool_active / platform_db_connection_pool_max > 0.8
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "DB 连接池接近上限"
```

### 9.4 健康检查端点

| 端点 | 说明 | 用途 |
|------|------|------|
| `GET /actuator/health` | 整体健康 | Docker healthcheck / K8s livenessProbe |
| `GET /actuator/health/db` | MySQL 连接 | DB 专项检查 |
| `GET /actuator/health/redis` | Redis 连接 | Redis 专项检查 |
| `GET /actuator/health/ping` | 最小存活检查 | K8s readinessProbe（轻量快速） |

---

## 10. CI/CD 流水线

### 10.1 GitHub Actions 示例

```yaml
# .github/workflows/deploy.yml
name: Deploy

on:
  push:
    branches: [main]
  workflow_dispatch:
    inputs:
      target:
        description: 部署目标
        required: true
        default: staging
        type: choice
        options: [staging, production]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
    - name: Backend Test
      run: |
        cd playwright-platform-server
        mvn test -B
    - name: Frontend Test + Build
      run: |
        cd playwright-platform-web
        npm ci && npm test && npm run build

  build-and-push:
    needs: test
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
    - name: Login to Registry
      run: echo "${{ secrets.REGISTRY_PASSWORD }}" | docker login ${{ secrets.REGISTRY }} -u ${{ secrets.REGISTRY_USERNAME }} --password-stdin
    - name: Build and Push Server
      run: |
        docker build -t ${{ secrets.REGISTRY }}/playwright-server:${{ github.sha }} ./playwright-platform-server
        docker push ${{ secrets.REGISTRY }}/playwright-server:${{ github.sha }}
        docker tag ${{ secrets.REGISTRY }}/playwright-server:${{ github.sha }} ${{ secrets.REGISTRY }}/playwright-server:latest
        docker push ${{ secrets.REGISTRY }}/playwright-server:latest
    - name: Build and Push Web
      run: |
        docker build -t ${{ secrets.REGISTRY }}/playwright-web:${{ github.sha }} ./playwright-platform-web
        docker push ${{ secrets.REGISTRY }}/playwright-web:${{ github.sha }}
        docker tag ${{ secrets.REGISTRY }}/playwright-web:${{ github.sha }} ${{ secrets.REGISTRY }}/playwright-web:latest
        docker push ${{ secrets.REGISTRY }}/playwright-web:latest

  deploy:
    needs: build-and-push
    runs-on: ubuntu-latest
    if: github.event.inputs.target == 'production'
    steps:
    - name: Deploy via SSH
      run: |
        ssh ${{ secrets.DEPLOY_USER }}@${{ secrets.DEPLOY_HOST }} << 'EOF'
          cd /opt/testplatform
          git pull
          docker compose -f docker-compose.prod.yml pull
          docker compose -f docker-compose.prod.yml up -d --build
        EOF
```

### 10.2 流水线分阶段

```
push / PR
  └── 测试（mvn test / npm test）
       │ ✅
       ▼
  构建镜像 + push 到私有 registry
       │ ✅
       ▼
  Staging 自动部署
       │ ✅ + smoke test
       ▼
  Production 手动 approve → 部署
```

---

## 11. 镜像仓库管理

### 11.1 选择

| 仓库 | 适合 | 说明 |
|------|------|------|
| Docker Hub | 小团队 | 免费版有拉取速率限制 |
| GitHub Container Registry | GitHub 用户 | 免费版公开无限，私有 500MB |
| Harbor | 企业自建 | 支持镜像扫描、复制策略 |
| 阿里云 ACR / AWS ECR | 云托管 | 与云环境深度集成 |

### 11.2 镜像版本策略

```
playwright-server:latest     ← 最新稳定，自动部署用
playwright-server:v1.0.0     ← 语义化版本，回滚用
playwright-server:main-abc123 ← feature branch CI 产出
```

**生产部署只用 semver tag**，禁止用 `latest` 做生产部署（`latest` 不可复现）。

### 11.3 镜像安全

- 启用 Docker Content Trust（DCT）签名
- 镜像上传后自动扫描（Trivy / Clair）
- 基础镜像定期更新（每季度更新一次 `eclipse-temurin` / `node` / `mysql`）
- 镜像尽量小（当前生产后端镜像 ≈ 500MB，前端 ≈ 30MB）

---

## 12. 滚动更新与零停机

### 12.1 Docker Compose（模式 B / C）

**Blue-Green 部署**（需要两套服务）：

```bash
# 生产运行在 v1
docker compose -f docker-compose.prod.yml up -d   # 当前 v1

# 准备 v2 环境
docker compose -f docker-compose.green.yml up -d --build  # v2 跑在 9080 端口（备用）

# smoke test
curl -s http://127.0.0.1:9080/api/auth/public-key

# 切流量（nginx reload 改 upstream，或切 DNS）
# 然后停 v1
docker compose -f docker-compose.blue.yml down
```

**简化版（大多数情况够用）**：

```bash
# 拉最新代码
cd /opt/testplatform && git pull

# 重建镜像，只重启有变化的服务
docker compose -f docker-compose.prod.yml up -d --build --no-deps server

# 等健康检查通过
watch docker compose -f docker-compose.prod.yml ps
# server 变成 healthy 后再重启 web
docker compose -f docker-compose.prod.yml up -d --build --no-deps web
```

### 12.2 Kubernetes（模式 D）

**Rolling Update Deployment**（已内置）：

```bash
kubectl set image deployment/playwright-server \
  server=your-registry/playwright-server:v1.1.0

# 查看滚动状态
kubectl rollout status deployment/playwright-server
# 自动：新 Pod ready → 旧 Pod terminate → 零中断
```

⚠️ **当前 server 单实例**：滚动更新会有短暂不可用（旧 Pod terminate → 新 Pod ready 间隔 ≈ 30~60s）。**建议**：
- 先看第 13 节完成多实例改造，再开 replicas ≥ 2
- 或用 Canary 发布（先让 1 个新 Pod 收 10% 流量 → 确认没问题 → 切 100%）

### 12.3 更新后验证清单

| 检查项 | 命令 |
|--------|------|
| 后端健康 | `curl -s /actuator/health | jq .status` |
| Flyway 迁移 | 日志里没有 Flyway ERROR |
| Docker Runner | `docker compose exec server docker version` 或 K8s 里 `exec server docker version` |
| 旧任务恢复 | 没有 RUNNING 状态卡住的任务 |
| API 回归 | Postman / Newman 跑一遍关键接口 |

---

## 13. 多实例注意事项（关键）

### 13.1 为什么当前不能直接开 replicas > 1

| 组件 | 单实例假设 | 多实例风险 |
|------|-----------|-----------|
| TaskRecoveryService | 启动时把所有 RUNNING 任务标记 FAILED | 两个实例同时启动 → 互相把对方的 RUNNING 任务标记 FAILED |
| 定时调度 | 单实例 Cron 扫描到期场景 | 多实例同时扫描 → 重复触发（当前靠 `schedule_event` 唯一约束 + 租约机制已解决 ✅） |
| Docker Runner | Server 直连本地 Docker socket | 多实例各自挂 socket → 任务可能跑在错误的 Runner 节点（需要统一 Runner 池 + 远程调度）|
| Agent 会话 | Caffeine 本地缓存 | 多实例下会话不共享（用户可能被负载均衡到不同 server，会话丢失）|
| Agent Trace | Redis List | Redis 本身是共享的，**没问题** ✅ |

### 13.2 改造清单

| 改造项 | 优先级 | 方案 |
|--------|--------|------|
| **TaskRecoveryService 去幂等** | P0 | 启动恢复时加分布式锁（Redis SETNX），抢到锁的实例才做恢复 |
| **Agent 会话迁移到 Redis** | P0 | 把 ChatSessionManager 的 Caffeine 换成 Redis 存储（30min TTL），所有实例共享 |
| **Runner 远程调度** | P1 | Server 通过 Docker API 远程调度 Runner（DinD 容器或远程 Docker host），不再绑定本机 socket |
| **数据库连接池调整** | P2 | 多实例加起来连接数可能很高，调整 `SPRING_DATASOURCE_HIKARICP_MAXIMUM_POOL_SIZE` |
| **定时调度只跑一个实例** | P2 | 加 `PLATFORM_SCHEDULE_ENABLED` 开关，只有标记为 leader 的实例跑调度；用 Redis 分布式 leader election |

### 13.3 Redis 分布式锁示例（TaskRecoveryService）

```java
public void recoverOrphanTasks() {
    String lockKey = "platform:task:recovery:lock";
    Boolean locked = redisTemplate.opsForValue()
        .setIfAbsent(lockKey, instanceId, Duration.ofMinutes(5));
    
    if (Boolean.TRUE.equals(locked)) {
        try {
            // 只有抢到锁的实例才执行恢复
            taskRepository.markOrphanRunningAsFailed(instanceId);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }
}
```

---

## 14. Docker Runner 跨模式适配

### 14.1 三种方案对比

| 方案 | 适用模式 | 优点 | 缺点 |
|------|---------|------|------|
| **A. 本机 Docker socket** | A/B/C | 最简单，零额外组件 | 只能单机；Pod 漂移问题 |
| **B. K8s hostPath socket** | D | 复用方案 A 的代码 | Pod 绑定节点；runner 日志在宿主机 |
| **C. DinD + Docker API 远程调用** | D/E | Server 和 Runner 解耦；可远程调度 | 多一个 DinD 容器；需要网络 |

### 14.2 方案 C（DinD）架构

```
Server Pod / Container
  │
  │ docker -H tcp://runner-dind:2375 run ...
  ▼
Runner DinD Container (TCP 端口 2375)
  │
  ├── 拉取 Playwright Runner 镜像
  ├── 启动短生命周期 Runner 容器
  └── Runner 容器执行 npm ci + playwright test
```

**DinD 容器 Dockerfile**：

```dockerfile
FROM docker:27-dind
# 预装 Playwright 浏览器
RUN apk add --no-cache chromium nss freetype harfbuzz ca-certificates ttf-freefont
EXPOSE 2375
CMD ["dockerd", "--host", "tcp://0.0.0.0:2375", "--storage-driver=overlay2"]
```

**Server 侧改动**：

```env
# 原来：
PLATFORM_RUNNER_DOCKER_HOST=unix:///var/run/docker.sock

# 改成（DinD）：
PLATFORM_RUNNER_DOCKER_HOST=tcp://runner-dind:2375
```

---

## 15. 备份与恢复

### 15.1 备份清单

| 数据 | 频率 | 方式 | 保留期 |
|------|------|------|--------|
| MySQL 全量 | 每日 | mysqldump 或 xtrabackup | 30 天 |
| MySQL binlog | 实时 | 云 RDS 自动或 binlog 采集 | 7 天 |
| MinIO 对象 | 每周 | mc mirror / 云 S3 cross-region replication | 长期 |
| Runner Workspace | 不定期 | 仅保留活跃任务 workspace | 任务完成后清理 |
| `.env` | 每次修改后 | 手动安全备份 | 永久 |

### 15.2 MySQL 备份与恢复

```bash
# Compose 模式
docker compose -f docker-compose.prod.yml exec mysql \
  mysqldump -uroot -p"$PLATFORM_DB_PASSWORD" \
  --single-transaction \
  --databases "$PLATFORM_DB_NAME" \
  | gzip > mysql-backup-$(date +%F).sql.gz

# 恢复
gunzip -c mysql-backup-2026-10-28.sql.gz | \
  docker compose -f docker-compose.prod.yml exec -T mysql \
  mysql -uroot -p"$PLATFORM_DB_PASSWORD"
```

### 15.3 MinIO 备份

```bash
# 用 mc（MinIO Client）镜像
docker run --rm -v ~/.mc:/root/.mc -v $(pwd)/backup:/backup minio/mc \
  mirror --overwrite minio-playwright/qa-report /backup/qa-report-$(date +%F)
```

### 15.4 云模式下的备份

| 组件 | 备份方式 |
|------|----------|
| RDS MySQL | 自动每日快照 + binlog（AWS 默认开启）|
| ElastiCache | 手动快照（Redis 本身数据量不大）|
| S3 | Cross-Region Replication + S3 Versioning + 生命周期策略 |

---

## 16. 安全加固

### 16.1 网络层

| 加固 | 做法 |
|------|------|
| 防火墙 | MySQL/Redis/MinIO 只开放给 App 机 IP（ufw / 安全组）|
| HTTPS | Let's Encrypt / 云证书，强制 HSTS |
| CORS | 后端配置 CORS 只允许可信前端域名 |
| VPC | 数据层全部在私有 VPC，通过 NAT 出公网 |

### 16.2 应用层

| 加固 | 做法 |
|------|------|
| 自助注册 | 生产设为 `false` |
| 密码 | BCrypt 加盐（cost ≥ 10）|
| Cookie | HttpOnly + Secure + SameSite=Lax |
| 凭据加密 | AES-256-GCM，密钥 32-byte base64 |
| Docker socket | 只在受控服务器部署，不暴露到公网 |
| `PLATFORM_CREDENTIAL_KEY` | 只存在于服务器 `.env` / K8s Secret / 云 Secret Manager |

### 16.3 镜像层

| 加固 | 做法 |
|------|------|
| 多阶段构建 | 运行镜像只有 JRE + Docker CLI + Git，不含 Maven |
| 非 root 用户 | 以 UID 10001 `appuser` 运行（Dockerfile 已内置）|
| 镜像扫描 | Trivy / Clair 定期扫描 CVE |
| 签名 | Docker Content Trust（DCT）|

### 16.4 服务器层

| 加固 | 做法 |
|------|------|
| 系统更新 | `apt upgrade` 每月执行一次 |
| SSH | 禁用密码登录，强制密钥；改默认端口（可选）|
| Fail2Ban | SSH 暴力破解防护 |
| 审计 | `auditd` 记录 Docker 操作和文件访问 |
| Docker daemon | 开启 `--userns-remap`；不直接用 root 运行 daemon |

---

## 17. 常见问题排查

### 17.1 Compose / Docker

| 现象 | 排查 |
|------|------|
| `Set PLATFORM_DB_PASSWORD in .env` | `.env` 文件不在项目根目录 / 变量名拼写错 |
| `runner-workspace-init` exited(1) | `DOCKER_GID` 不是数字；路径不存在；权限不够 |
| server `Docker runner preflight failed` | Docker daemon 没跑；`DOCKER_GID` 错；socket 没挂载 |
| server boot loop | 看日志 `docker compose logs server`，通常是 DB/Redis/MinIO 连不上 |

### 17.2 数据库

| 现象 | 排查 |
|------|------|
| MySQL 连接失败（Compse 模式）| `.env` 密码改了但 volume 里还是旧密码 → 临时改回旧密码，或 `docker compose down -v` 重建 |
| MySQL 连接失败（分机模式）| 防火墙、bind-address、用户名权限 |
| Flyway 迁移失败 | 看 server 日志；可能是版本冲突（表已存在但 Flyway 没记录）|
| Redis NOAUTH | `.env` 密码错；Redis volume 还是旧密码 |

### 17.3 Docker Runner

| 现象 | 排查 |
|------|------|
| 任务持续 FAILED + `runner preflight failed` | `docker compose exec server docker version` 看是否通 |
| Runner 镜像拉不下来 | 服务器不能访问 Docker Hub → 配镜像加速或本地 build 后 `docker save` |
| npm ci 卡死 | Runner 容器内网络访问问题；测试仓库依赖问题；Runner 资源不够（默认 2GB）|
| Playwright 执行浏览器缺失 | `PLATFORM_RUNNER_DOCKER_IMAGE` 必须是带浏览器的镜像（如 `mcr.microsoft.com/playwright`）|
| Docker socket 权限 | `DOCKER_GID` 必须等于 `stat -c '%g' /var/run/docker.sock` |

### 17.4 K8s 特定

| 现象 | 排查 |
|------|------|
| Pod 启动失败 `CrashLoopBackOff` | `kubectl logs <pod>`；大概率 `.env` 变量缺失或 DB 连不上 |
| Pod 卡在 `ContainerCreating` | PVC 没绑定 / hostPath 不存在 / imagePullSecrets 没配 |
| Pod 漂移到没有 Docker Engine 的节点 | 配置 nodeSelector 或 nodeAffinity 锁定 Runner 节点 |
| StatefulSet PVC 扩容 | 云盘扩容后 `kubectl patch pvc` + Pod 重建 |

### 17.5 通用排查命令速查

```bash
# Docker Compose 模式
docker compose -f docker-compose.prod.yml ps -a              # 状态总览
docker compose -f docker-compose.prod.yml logs --tail=200 server
docker compose -f docker-compose.prod.yml exec server docker version
docker compose -f docker-compose.prod.yml exec mysql mysql -uroot -p
docker compose -f docker-compose.prod.yml exec redis redis-cli -a <pwd> ping

# K8s 模式
kubectl get pods -A
kubectl describe pod <name> -n <ns>
kubectl logs -f <pod-name> -n <ns>
kubectl exec -it <pod-name> -n <ns> -- bash
kubectl top nodes / kubectl top pods

# 通用
curl -s http://localhost:8080/actuator/health
curl -s http://localhost/api/auth/public-key
nc -zv <host> <port>                                          # 端口连通性
ss -lntp | grep -E ':(8080|3306|6379|9000)'                   # 监听端口
df -h /var/lib/docker                                          # 磁盘
free -h                                                         # 内存
top / htop                                                      # CPU
```

---

## 18. 版本历史

| 版本 | 日期 | 变更 |
|------|------|------|
| v1.0 | 2026-10-15 | 初始版本：单机 Docker Compose 部署（原 deployment.md）|
| **v2.0** | **2026-10-28** | **大幅升级：新增模式选择、分机部署、Kubernetes、云托管、容量规划、监控日志、CI/CD、镜像仓库、滚动更新、多实例注意事项、DinD Runner 适配、安全加固** |
