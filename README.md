# 花森门户统一平台 (Huasen Unified Portal)

一个统一的 Web 门户平台，整合网址导航、多博客系统和后台管理功能。基于 Java Spring Boot 3.x 后端和 Vue.js 2.6 前端，提供稳定的网址收藏、博客阅读和内容管理服务。

## ✨ 核心功能

- 🔖 **网址导航** - 个人网址收藏和分类管理
- 📝 **多博客系统** - 支持 blog-sharon 和 tiny-blog 两套博客
- 🎨 **主题定制** - 可自定义主题色、背景壁纸
- 🤖 **AI 增强** - 集成阿里云百炼，自动生成文章摘要和网站描述
- 🔍 **全文搜索** - 可选 Elasticsearch 支持（可降级）
- 🛡️ **访问控制** - JWT 认证、黑名单机制
- 📊 **管理后台** - 数据统计、内容管理、系统配置

## 🏗️ 技术架构

### 后端
- **框架**: Spring Boot 3.5.14 (Java 17)
- **数据库**: MySQL 8.x（主库）+ Redis 6（缓存）
- **ORM**: Spring Data JPA + Hibernate
- **搜索**: Elasticsearch 7.5（可选）
- **存储**: 七牛云对象存储
- **AI**: 阿里云百炼（通义千问）

### 前端
- **框架**: Vue.js 2.6.11 + Vue CLI 4.5
- **UI 库**: Element UI 2.15.5
- **状态管理**: Vuex 3.6.2
- **样式**: TailwindCSS + SCSS

### 部署
- **容器化**: Docker Compose
- **反向代理**: Nginx
- **进程管理**: Docker 容器自动重启

## 📁 项目结构

```
workspace-new-ai/
├── backend/                 # Spring Boot 后端
│   ├── src/main/java/com/huasen/
│   │   ├── app/            # 启动类
│   │   ├── common/         # 通用层（实体、仓库、工具）
│   │   ├── portal/         # 门户模块
│   │   ├── admin/          # 管理模块
│   │   └── blog/           # 博客模块（sharon + tiny）
│   └── src/main/resources/
│       ├── application.yml              # 通用配置
│       ├── application-dev.yml          # 开发环境
│       ├── application-pro.yml          # 生产环境
│       └── application-local.yml        # 本地配置（不提交）
├── frontend/
│   ├── portal/             # 用户门户前端
│   └── admin/              # 管理后台前端
├── extension/              # Chrome 浏览器扩展
├── deploy/                 # 部署配置
│   ├── docker-compose.yml  # Docker 编排
│   ├── .env.example        # 环境变量模板
│   └── nginx.conf          # Nginx 配置
└── CLAUDE.md               # 项目开发指南
```

## 🚀 快速开始

### 前置要求

- Java 17+
- Node.js 16+
- MySQL 8.0+
- Docker & Docker Compose（用于部署）

### 本地开发

#### 1. 准备数据库

```bash
# 创建数据库
mysql -u root -p
CREATE DATABASE huasen_portal CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE blog_sharon CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

#### 2. 配置本地环境

创建 `backend/src/main/resources/application-local.yml`（参考 `application-dev.yml`），填入你的真实配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/huasen_portal?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: your_username
    password: your_password
  data:
    redis:
      host: localhost
      port: 6379
      password: your_redis_password

qiniu:
  access-key: your_qiniu_ak
  secret-key: your_qiniu_sk
  bucket: your_bucket
  domain: your_domain

alibaba:
  bailian:
    api-key: your_bailian_key
```

#### 3. 启动后端

```bash
cd backend
mvn clean install
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

后端将运行在 `http://localhost:8080`

#### 4. 启动前端

```bash
# 用户门户
cd frontend/portal
npm install
npm run serve
# 访问 http://localhost:8000

# 管理后台
cd frontend/admin
npm install
npm run serve
# 访问 http://localhost:9000
```

### 生产部署

#### 1. 准备环境

```bash
cd deploy
cp .env.example .env
# 编辑 .env，填入生产环境配置
```

#### 2. 构建和启动

```bash
# 构建前端
cd frontend/portal && npm run build
cd frontend/admin && npm run build

# 启动服务
docker-compose up -d
```

#### 3. 访问服务

- 用户门户: `http://your-domain/`
- 管理后台: `http://your-domain/admin/`
- 后端 API: `http://your-domain/api/`

## 🔧 配置说明

### 环境变量

| 变量名 | 说明 | 示例 |
|--------|------|------|
| `MYSQL_HOST` | MySQL 主机地址 | `host.docker.internal` |
| `MYSQL_PORT` | MySQL 端口 | `3306` |
| `MYSQL_USER` | MySQL 用户名 | `huasen` |
| `MYSQL_PASSWORD` | MySQL 密码 | `your_password` |
| `REDIS_PASSWORD` | Redis 密码（自己设定） | `your_redis_password` |
| `QINIU_ACCESS_KEY` | 七牛云 AK | `your_key` |
| `QINIU_SECRET_KEY` | 七牛云 SK | `your_key` |
| `QINIU_BUCKET` | 七牛云存储桶 | `your_bucket` |
| `QINIU_DOMAIN` | 七牛云域名 | `https://cdn.example.com` |
| `BAILIAN_API_KEY` | 阿里云百炼 API Key | `sk-xxx` |
| `ES_ENABLED` | 是否启用 Elasticsearch | `true` / `false` |

### 系统配置

系统配置存储在 MySQL 的 `system_config` 表中，可通过管理后台修改：

- **品牌信息**: 站点名称、Logo、备案号
- **主题设置**: 默认主题、壁纸模式
- **特殊文章**: 更新日志、关于页面、帮助文档的文章 ID

首次启动时会使用 `backend/src/main/resources/application-config.json` 作为默认配置。

## 🔐 安全说明

- 配置文件中**不包含**真实密码和 API Key，使用环境变量注入
- 本地开发使用 `application-local.yml`（已加入 `.gitignore`）
- 生产环境使用 Docker Compose 的 `.env` 文件（已加入 `.gitignore`）
- JWT Token 有效期可配置，支持黑名单机制
- Redis 密码是首次部署时自己设定的，不是已有密码

## 📚 开发指南

详细的开发规范、架构设计和工作流程请参考 [CLAUDE.md](./CLAUDE.md)。

### 常用命令

```bash
# 后端测试
cd backend && mvn test

# 前端 Lint
cd frontend/portal && npm run lint

# 构建后端 JAR
cd backend && mvn clean package

# 查看容器日志
docker-compose logs -f backend
```

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

MIT License

---

**Core Value:** 网址导航和博客系统必须稳定可用 — 这两个核心功能是平台的基础。
