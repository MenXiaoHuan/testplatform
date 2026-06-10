# Playwright Platform Runbook

## 1. 运行前准备

本地环境默认约定如下：

1. MySQL 已启动。
2. 数据库连接使用：
   - `jdbc:mysql://localhost:3306/playwright_platform`
   - `username: root`
   - `password: 12345678`
3. 前端项目目录：`/Users/bytedance/test_platform/playwright-platform-web`
4. 后端项目目录：`/Users/bytedance/test_platform/playwright-platform-server`

## 2. 启动后端

在终端执行：

```bash
cd /Users/bytedance/test_platform/playwright-platform-server
mvn spring-boot:run
```

预期结果：

1. 控制台出现 `Started PlatformApplication`
2. 本地接口可访问：`http://localhost:8080/api/repos`

## 3. 启动前端

在另一个终端执行：

```bash
cd /Users/bytedance/test_platform/playwright-platform-web
npm install
npm run dev -- --host 0.0.0.0 --port 4173
```

预期结果：

1. 控制台出现本地开发服务地址
2. 浏览器可访问：`http://localhost:4173/`

## 4. 前后端联调检查

执行以下命令：

```bash
curl -i http://localhost:8080/api/repos
curl -i http://localhost:4173/api/repos
curl -i http://localhost:4173/api/scenes
curl -i http://localhost:4173/api/tasks
```

预期结果：

1. 四个接口均返回 `HTTP/1.1 200`
2. 当前未录入数据时，返回内容可以是空数组 `[]`

## 5. 当前支持的基础页面

前端当前可访问页面如下：

1. 仓库页：`/repos`
2. 场景页：`/scenes`
3. 任务页：`/tasks`
4. 任务详情页：`/tasks/:id`

## 6. 常见问题

### 6.1 后端启动失败

检查项：

1. MySQL 是否启动
2. 账号密码是否为 `root / 12345678`
3. `application.yml` 中的数据源地址是否指向本地库

### 6.2 前端页面打开但接口报错

检查项：

1. 后端是否启动在 `8080`
2. 前端是否启动在 `4173`
3. `vite.config.ts` 中 `/api` 代理是否指向 `http://localhost:8080`

### 6.3 MinIO 相关功能未验证

当前状态：

1. MinIO 配置项已存在
2. 真正的对象存储上传链路还未完成验证
3. 本 Runbook 先以页面和基础 API 联调为主
