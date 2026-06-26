# 单机生产部署指南

本文档说明如何把测试平台部署到一台 Linux 服务器上。第一版推荐使用 Docker Compose 单机部署，后续再按需要升级到域名、HTTPS、镜像仓库和 CI/CD。

## 1. 准备服务器

推荐配置：

- 系统：Ubuntu 22.04 或 Ubuntu 24.04
- 配置：4 核 CPU、8GB 内存、100GB 磁盘
- 自测最低配置：2 核 CPU、4GB 内存、40GB 磁盘

云服务器安全组建议：

- 必开：`80`，用于访问前端页面
- 后续 HTTPS：`443`
- 可选：`9000`，MinIO API，只建议限制来源 IP
- 可选：`9001`，MinIO Console，只建议限制来源 IP
- 不建议公网开放：MySQL、Redis、后端内部端口

## 2. 安装 Docker

登录服务器：

```bash
ssh root@<your-server-ip>
```

安装 Docker 和 Compose 插件：

```bash
apt update
apt install -y ca-certificates curl git
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" > /etc/apt/sources.list.d/docker.list
apt update
apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

检查安装结果：

```bash
docker --version
docker compose version
```

## 3. 拉取代码

```bash
mkdir -p /opt
cd /opt
git clone https://github.com/MenXiaoHuan/testplatform.git
cd testplatform
```

## 4. 创建生产环境变量

在服务器项目目录创建 `.env`：

```bash
nano .env
```

写入以下内容，并把尖括号占位值替换为生产强密码：

```env
# Frontend
PLATFORM_WEB_HOST_PORT=80

# Backend - MySQL
PLATFORM_DB_NAME=playwright_platform
PLATFORM_DB_URL=jdbc:mysql://mysql:3306/playwright_platform?useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true&serverTimezone=UTC
PLATFORM_DB_USERNAME=root
PLATFORM_DB_PASSWORD=<your-db-password>

# Backend - Redis
PLATFORM_REDIS_HOST=redis
PLATFORM_REDIS_PORT=6379
PLATFORM_REDIS_PASSWORD=<your-redis-password>

# Backend - MinIO
PLATFORM_MINIO_ENDPOINT=http://minio:9000
PLATFORM_MINIO_INTERNAL_ENDPOINT=http://minio:9000
PLATFORM_MINIO_API_HOST_PORT=10000
PLATFORM_MINIO_CONSOLE_HOST_PORT=10001
PLATFORM_MINIO_ACCESS_KEY=<your-minio-access-key>
PLATFORM_MINIO_SECRET_KEY=<your-minio-secret-key>
PLATFORM_STORAGE_BUCKET=qa-report

# Backend - Runner
PLATFORM_RUNNER_MODE=docker
PLATFORM_RUNNER_WORKSPACE_ROOT=/workspace/.runner-workspaces
PLATFORM_RUNNER_HOST_WORKSPACE_ROOT=./.runner-workspaces
PLATFORM_RUNNER_DOCKER_IMAGE=mcr.microsoft.com/playwright:v1.44.0-jammy
PLATFORM_RUNNER_DOCKER_NETWORK=bridge
PLATFORM_RUNNER_DOCKER_MEMORY=2g
PLATFORM_RUNNER_DOCKER_CPUS=2
PLATFORM_RUNNER_DOCKER_CONTAINER_WORKSPACE_ROOT=/workspace/task
```

生产 Compose 不读取 `PLATFORM_SERVER_HOST_PORT`、`PLATFORM_MYSQL_HOST_PORT`、`PLATFORM_REDIS_HOST_PORT` 和 `PLATFORM_WEB_API_PROXY_TARGET`。后端、MySQL、Redis 只在 Docker 内部网络访问。

当前 `docker-compose.prod.yml` 是单机可运行版本：前端 Nginx 会把 `/api` 请求转发给后端。正式前后端分域名部署时，建议在反向代理层使用独立域名暴露前端和后端 API，并补充前端 API 地址配置与后端 CORS 白名单。

注意：

- `.env` 是服务器私有文件，不提交到 GitHub。
- MySQL、Redis、MinIO 第一次初始化后，修改 `.env` 密码不会自动改已有 Docker volume 内的账号密码。
- 需要重置数据时再执行 `docker compose -f docker-compose.prod.yml down -v`，该命令会删除数据卷。

## 5. 启动生产服务

先检查 Compose 配置：

```bash
docker compose -f docker-compose.prod.yml config
```

启动服务：

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

查看状态：

```bash
docker compose -f docker-compose.prod.yml ps
```

查看后端日志：

```bash
docker compose -f docker-compose.prod.yml logs -f server
```

查看所有日志：

```bash
docker compose -f docker-compose.prod.yml logs -f
```

## 6. 验证访问

如果 `PLATFORM_WEB_HOST_PORT=80`，浏览器访问：

```text
http://<your-server-ip>
```

验证项：

- 前端页面可以打开
- 前端可以请求后端接口
- 可以创建仓库和场景
- 可以运行任务
- 任务状态可以更新
- 报告、截图、trace、日志可以上传到 MinIO
- 后端日志没有 MySQL、Redis、MinIO 连接错误

## 7. 更新版本

项目更新后，在服务器项目目录执行：

```bash
cd /opt/testplatform
git pull
docker compose -f docker-compose.prod.yml up -d --build
```

只重启服务：

```bash
docker compose -f docker-compose.prod.yml restart
```

停止服务：

```bash
docker compose -f docker-compose.prod.yml down
```

## 8. 数据备份

需要备份的数据：

- `mysql-data`：业务数据库
- `redis-data`：Redis 持久化数据
- `minio-data`：报告、截图、trace、日志等对象文件
- `.runner-workspaces`：任务运行工作区，通常可按需要清理

最简单的单机备份方式是定期停止服务后打包 Docker volume 和 `.env`。正式生产建议使用云盘快照、对象存储备份或数据库定时备份。

## 9. 域名和 HTTPS

没有域名时，可以先使用 `http://<your-server-ip>` 跑通。

正式给团队使用时，建议准备前端域名和后端 API 域名，并开放：

- `80`：申请证书和 HTTP 访问
- `443`：HTTPS 访问

推荐入口形式：

```text
https://test-platform.example.com
https://api.test-platform.example.com
```

分域名的好处是前后端边界更清晰，后端 API 后续可以独立限流、监控、扩容，也方便给其他客户端复用。

分域名需要额外处理：

- DNS：把 `test-platform.example.com` 和 `api.test-platform.example.com` 都解析到服务器公网 IP。
- HTTPS：证书需要覆盖两个域名，可以使用两张证书或通配符证书。
- 前端：正式环境建议支持 `VITE_API_BASE_URL=https://api.test-platform.example.com`，避免长期依赖同域 `/api`。
- 后端：需要配置 CORS，只允许 `https://test-platform.example.com` 等可信前端域名访问。
- 反向代理：可以使用 Caddy 或 Nginx，前端域名转发到 `web`，API 域名转发到 `server:8080`。

当前仓库的生产 Compose 先保证单机部署能跑通；接入正式分域名时，再增加 Caddy/Nginx 反向代理配置和前后端跨域配置。

## 10. 安全注意事项

- 不要把 `.env` 上传到 GitHub。
- 不要公网开放 MySQL 和 Redis。
- MinIO Console 只建议对自己的 IP 开放。
- `server` 服务挂载了 `/var/run/docker.sock`，这是为了 Docker Runner 执行 Playwright 任务，只建议部署在受控服务器。
- 定期更新服务器系统、Docker 镜像和项目代码。
