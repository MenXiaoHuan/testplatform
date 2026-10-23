# === 生产环境 Dockerfile ===
# 第一阶段：使用 Maven 3.9 + Eclipse Temurin JDK 21 作为构建基础镜像
FROM maven:3.9-eclipse-temurin-21 AS build

# 设置构建阶段的工作目录
WORKDIR /workspace

# 先复制 pom.xml 以利用 Docker 缓存机制，加速后续依赖下载
# 复制项目依赖配置文件
COPY pom.xml .

# 预下载所有 Maven 依赖，跳过测试以加速依赖缓存
RUN mvn -B -DskipTests dependency:go-offline

# 复制源代码目录
COPY src ./src

# 执行 Maven 打包，生成可执行 JAR 文件
RUN mvn -B -DskipTests package

# Docker CLI：server 用 docker run 启动 Playwright 容器，运行镜像必须带 docker 客户端。
# 官方 docker:cli 镜像是静态二进制，复制过来即可，不需要在 JRE 镜像里装整个 daemon。
FROM docker:27-cli AS dockercli

# 第二阶段：使用 Eclipse Temurin JRE 21 作为运行时基础镜像（更轻量）
FROM eclipse-temurin:21-jre

# 设置运行时工作目录
WORKDIR /app

# Git/SSH 客户端：Docker Runner 在启动容器前由 server 执行 git clone。
# 生产镜像除了 Docker CLI，还必须能 clone HTTPS/SSH 私有仓库。
RUN apt-get update \
    && apt-get install -y --no-install-recommends git git-lfs openssh-client ca-certificates \
    && git lfs install --system \
    && rm -rf /var/lib/apt/lists/*

# 创建非系统用户，以提高安全性运行应用
RUN useradd --system --create-home --uid 10001 appuser

# Docker CLI 供 Docker Runner 调用；socket 权限由 compose 的 group_add 负责。
COPY --from=dockercli /usr/local/bin/docker /usr/local/bin/docker

# 从构建阶段复制打包好的 JAR 文件到运行时镜像
COPY --from=build /workspace/target/*.jar /app/app.jar

# 切换为 appuser 用户运行应用，避免以 root 身份运行
USER appuser

# 暴露 8080 端口供外部访问
EXPOSE 8080

# 定义容器启动命令：运行 Spring Boot 应用 JAR
ENTRYPOINT ["java", "-jar", "/app/app.jar"]