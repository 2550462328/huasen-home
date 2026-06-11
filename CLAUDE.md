## Project

**花森门户统一平台 (Huasen Unified Portal)**

统一Web门户平台：网址导航 + 多博客系统(blog-sharon, tiny-blog) + 后台管理。后端已从 Node.js/Express/MongoDB 重构为 Java Spring Boot 3.x + MySQL，前端保持 Vue 2.6 不变。

**Core Value:** 网址导航和博客系统必须稳定可用。

### Constraints

- **技术栈**: Spring Boot 3.5 (Java 17) + Vue 2.6 + ElementUI
- **数据完整性**: 必须保留所有历史数据（MongoDB → MySQL 一次性迁移）
- **向后兼容**: 前端 API 接口保持兼容，减少前端改动
- **部署方式**: Docker Compose（物理机 MySQL + Nginx，容器跑 Redis + Spring Boot）
- **数据库设计**: 独立 Schema — huasen_portal（主库）、blog_sharon（博客）
- **搜索功能**: Elasticsearch 可选，未配置时优雅降级

## Directory Structure

```
workspace-new-ai/
├── backend/                 # Spring Boot 后端（单模块）
├── frontend/
│   ├── portal/              # 用户门户（Vue 2.6 + ElementUI）
│   └── admin/               # 后台管理（Vue 2.6 + ElementUI）
├── deploy/                  # 部署三件套
│   ├── 1-init-db.sql        # 建库建表
│   ├── 2-migrate.sh         # 数据迁移（MongoDB→MySQL）
│   ├── 3-deploy.sh          # 服务部署
│   ├── .env.example         # 环境变量模板
│   ├── docker-compose.yml   # Docker 编排
│   └── nginx/               # Nginx 配置
└── origin/                  # 改造前的原始项目（只读参考）
    ├── huasenjio-compose/   # 原 Node.js 全栈项目
    ├── blog-sharon/         # 原 Halo 博客（Java/Spring Boot 1.x）
    └── tiny-blog/           # 原 Python Flask 博客
```

## Technology Stack

### Backend (backend/)
- Java 17, Spring Boot 3.5.14
- Spring Data JPA + Hibernate（MySQL）
- Spring Data Redis（缓存、黑名单、Token 池）
- Spring Data MongoDB（仅迁移工具用）
- Spring Data Elasticsearch（可选全文搜索）
- JJWT 0.12.6（JWT 认证）
- Lombok, MapStruct 1.5.5
- 七牛 SDK（文件存储）
- Maven 构建，产物 `backend-2.0.0-SNAPSHOT.jar`

### Frontend (frontend/)
- Vue.js 2.6.11 + Vue CLI 4.5
- Element UI 2.15.5
- Vuex 3.6.2 + Vue Router 3.5.2
- Axios 0.21.1
- TailwindCSS, highlight.js, ECharts
- Prettier + ESLint

### Infrastructure
- MySQL 8.x（物理机）
- Redis 6 Alpine（Docker）
- Nginx（物理机，反向代理）
- Docker Compose

## Backend Architecture

```
com.huasen/
├── app/                     # 启动类 HuasenApplication
├── common/                  # 通用层
│   ├── config/              # RedisConfig
│   ├── constant/            # RedisKeyConstants
│   ├── dto/                 # HuasenResponse
│   ├── entity/              # JPA 实体（User, Site, Article, Column...）
│   ├── exception/           # GlobalExceptionHandler, BusinessException
│   ├── filter/              # JWT、黑名单、参数解密等 Filter
│   ├── repository/          # Spring Data JPA Repository
│   ├── service/             # Redis、文件上传、七牛、配置等通用服务
│   └── util/                # AES/RSA/JWT 工具类
├── portal/                  # 门户模块（controller + service）
├── admin/                   # 管理模块（controller + service）
├── blog/
│   ├── sharon/              # blog-sharon（独立 schema blog_sharon）
│   └── tiny/                # tiny-blog（同 huasen_portal schema）
└── migration/               # MongoDB→MySQL 一次性迁移工具
```

## Conventions

### Naming
- Java 类: PascalCase（`UserController`, `BlogPostService`）
- 方法/变量: camelCase
- 常量: UPPER_SNAKE_CASE
- Vue 组件: PascalCase（`HomeWallpaper.vue`）
- 前端工具: camelCase（`debounce.js`）

### Backend Patterns
- Controller → Service → Repository 三层
- 统一响应 `HuasenResponse`
- 全局异常处理 `GlobalExceptionHandler`
- Filter 链做认证/鉴权（非 Spring Security）
- 配置通过 `application.yml` + `application-{profile}.yml`

### Frontend Patterns
- `@/` 映射 `src/`
- Vuex Store 管理状态
- Toast 组件做用户提示
- Prettier + ESLint 格式化

## Configuration

| 文件 | 用途 |
|------|------|
| `backend/src/main/resources/application.yml` | 通用配置（加密密钥、ES开关） |
| `backend/src/main/resources/application-dev.yml` | 开发环境（localhost MySQL/Redis） |
| `backend/src/main/resources/application-pro.yml` | 生产环境（Docker 网络内） |
| `backend/src/main/resources/application-config.json` | 系统配置默认值（首次启动时使用） |
| `deploy/.env` | Docker Compose 环境变量 |
| `frontend/portal/vue.config.js` | Portal 构建配置 |
| `frontend/admin/vue.config.js` | Admin 构建配置 |

### 系统配置存储

系统配置（品牌名称、主题、文章ID等）存储在 **MySQL 数据库** `system_config` 表中：

- 表结构: `config_key` (唯一键) + `config_value` (JSON格式)
- 读取优先级: 数据库 → `application-config.json`（默认模板） → 空配置
- 通过后台管理页面修改，保存到数据库
- 首次启动时表为空，会使用 `application-config.json` 作为默认值

**配置结构**：
- `site`: 品牌名称、Logo URL、备案信息、是否开启标签分类
- `theme`: 主题配色（纯色/壁纸模式、默认主题）
- `article`: 特殊文章ID（更新日志、关于、帮助）

## Development

```bash
# 后端（需本地 MySQL + Redis）
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 前端 Portal
cd frontend/portal
npm install && npm run serve

# 前端 Admin
cd frontend/admin
npm install && npm run serve
```

## Deployment

三步走，详见 `deploy/` 目录：
1. `mysql -u root -p < deploy/1-init-db.sql`
2. `bash deploy/2-migrate.sh mongodb://旧地址:27017/huasen`
3. `cp deploy/.env.example deploy/.env && bash deploy/3-deploy.sh`

<!-- GSD:project-start source:PROJECT.md -->
## Project

**花森门户统一平台 (Huasen Unified Portal)**

一个统一的Web门户平台,整合网址导航、多博客系统和后台管理功能。基于Java Spring Boot 3.x后端和Vue.js 2.6前端,提供稳定的网址收藏、博客阅读和内容管理服务。目标用户是需要高效上网冲浪环境和个人知识管理的用户。

**Core Value:** **网址导航和博客系统必须稳定可用** — 这两个核心功能是平台的基础,用户依赖它们进行日常的信息获取和内容管理。如果这两个功能不稳定,整个平台就失去了价值。

### Constraints

- **技术栈**: Spring Boot 3.x (Java 17+) + Vue 2.6 + ElementUI — 平衡现代化和稳定性
- **数据完整性**: 必须保留所有历史数据 — 用户内容不能丢失
- **向后兼容**: 前端API接口保持兼容 — 减少前端改动
- **部署方式**: Docker Compose — 保持现有部署习惯
- **数据库设计**: 独立Schema设计 — blog-sharon和tiny-blog各自独立的数据库schema
- **搜索功能**: Elasticsearch可选配置 — 后台配置控制,未配置时优雅降级
<!-- GSD:project-end -->

<!-- GSD:stack-start source:codebase/STACK.md -->
## Technology Stack

## Languages
- JavaScript (Node.js) - Backend server runtime
- JavaScript (ES6+) - Frontend application code
- JSON - Configuration and data exchange
- CSS/SCSS - Styling with Sass preprocessor
## Runtime
- Node.js 16 (Alpine 3.17 in production)
- npm - Used across all projects
- Lockfile: package-lock.json present in subprojects
## Frameworks
- Vue.js 2.6.11 - Frontend framework for portal and admin
- Express 4.17.1 - Backend web framework
- Element UI 2.15.5 - Vue component library
- Not detected - No test framework configured
- Vue CLI 4.5.0 - Frontend build tooling
- Webpack 5.67.0 - Module bundler (backend sprites)
- Babel - JavaScript transpiler
- PostCSS 7.0.39 - CSS processing
## Key Dependencies
- mongoose 5.11.13 - MongoDB ODM for data modeling
- ioredis 5.2.2 - Redis client for caching and sessions
- jsonwebtoken 8.5.1 - JWT authentication tokens
- nodemailer 6.4.17 - Email sending service
- ws 8.5.0 - WebSocket server for real-time communication
- cors 2.8.5 - Cross-origin resource sharing
- body-parser 1.19.0 - Request body parsing middleware
- express-session 1.17.1 - Session management
- cookie-parser 1.4.5 - Cookie parsing
- log4js 6.3.0 - Logging framework
- pm2 - Process manager (installed globally in Docker)
- axios 0.21.1 - HTTP client
- vue-router 3.5.2 - Vue routing
- vuex 3.6.2 - State management
- vue-i18n 8.25.0 - Internationalization
- animate.css 4.1.1 - CSS animations
- tailwindcss 1.9.6 (portal), 2.2.17 (admin) - Utility-first CSS
- echarts 5.3.1 - Data visualization
- mavon-editor 2.10.4 - Markdown editor (admin)
- showdown 1.9.1 - Markdown parser
- lodash 4.17.21 - JavaScript utility library
- moment 2.29.1 - Date/time manipulation
- multer 1.4.2 - File upload handling
- compressing 1.5.1 - File compression/decompression
- node-schedule 2.1.0 - Cron-like job scheduler
- @meltwater/fetch-favicon 1.0.4 - Favicon fetching
## Configuration
- Configuration via `huasenjio-compose/huasen-server/setting.json`
- Runtime mode set via command-line: `MODE=dev` or `MODE=pro`
- Database and Redis credentials in `config.js`
- `huasenjio-compose/huasen-frontend/portal/vue.config.js` - Portal build config
- `huasenjio-compose/huasen-frontend/admin/vue.config.js` - Admin build config
- `huasenjio-compose/huasen-server/webpack.config.js` - Server sprite generation
- `babel.config.js` - Babel transpilation settings
- `postcss.config.js` - PostCSS processing
- `tailwind.config.js` - Tailwind CSS customization
## Platform Requirements
- Node.js 16+
- MongoDB 4.2.2+ (local or Docker)
- Redis 6.0.10+ (local or Docker)
- npm package manager
- Docker with Docker Compose
- Nginx 1.23.1 for reverse proxy
- Jenkins LTS for CI/CD (optional)
- Linux host (Alpine-based containers)
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->
## Conventions

## Naming Patterns
- Vue components: PascalCase (e.g., `HomeWallpaper.vue`, `DialogForm.vue`)
- JavaScript utilities: camelCase (e.g., `debounce.js`, `copyObject.js`)
- Server files: kebab-case with suffix (e.g., `user.controller.js`, `common.middleware.js`)
- Config files: kebab-case (e.g., `i18n.config.js`, `router.config.json`)
- camelCase for all functions (e.g., `handleRequest`, `responseData`, `copyObject`)
- Middleware functions: prefix with `handle` or `check` (e.g., `handleJWT`, `checkManagePower`)
- Controller functions: action names (e.g., `login`, `register`, `backup`)
- camelCase for local variables (e.g., `timeing`, `userPassword`, `encryptPassword`)
- UPPER_SNAKE_CASE for constants (e.g., `PORT_SERVER`, `POOL_BLACKLIST`, `SESSION`)
- Vue data properties: camelCase (e.g., `isShow`, `headBgConfig`, `categoryEmpty`)
- Not applicable (JavaScript codebase, no TypeScript)
## Code Style
- Tool: Prettier
- Key settings:
- Config files: `huasenjio-compose/huasen-frontend/portal/.prettierrc`, `huasenjio-compose/huasen-frontend/admin/.prettierrc`, `huasenjio-compose/huasen-server/.prettierrc`
- Tool: ESLint
- Config: Inline in `package.json` (eslintConfig section)
- Key rules:
- Extends: `plugin:vue/essential`, `eslint:recommended`
- Parser: `babel-eslint`
## Import Organization
- `@/` maps to `src/` directory
- Direct aliases for common directories: `plugin/`, `config/`, `utils/`, `constant/`, `network/`
- Example: `import tool from 'utils/index.js'` instead of `import tool from '../../../utils/index.js'`
## Error Handling
- Server: Global error handler middleware (`handleRequestError`) in `huasenjio-compose/huasen-server/middleware/common.middleware.js`
- Server: Uncaught exceptions caught at process level in `huasenjio-compose/huasen-server/app.js`
- Server: Async errors passed to `next(err)` in middleware chain
- Frontend: Try-catch blocks for async operations
- Frontend: Toast component for user-facing error messages (`huasenjio-compose/huasen-frontend/portal/src/components/common/toast/Toast.vue`)
- Server: Standardized response format via `global.huasen.responseData(res, data, tag, msg, isSecret)`
## Logging
- Server: Centralized logging via `huasenjio-compose/huasen-server/plugin/log.js`
- Server: Log configuration in `huasenjio-compose/huasen-server/log4js.config.js`
- Frontend: Console logging (not disabled in production)
- Server: Global logger attached to `global.huasen` object
- Server: Error formatting via `global.huasen.formatError(err, message)`
## Comments
- File headers with metadata (author, date, description)
- Function JSDoc comments with `@param` and `@returns` tags
- Complex business logic explanations
- TODO/FIXME markers (minimal usage in application code)
- Used consistently in utility functions
- Format:
- Example: `huasenjio-compose/huasen-frontend/portal/src/utils/debounce.js`
- Standard format across all files:
## Function Design
- Destructured from request objects in controllers (e.g., `let { id, password } = req.huasenParams`)
- Options objects for complex configurations
- Spread operators for variable arguments (`...args`)
- Server controllers: Use `global.huasen.responseData()` instead of direct returns
- Utilities: Direct return values
- Async functions: Return promises or use async/await
- Vue methods: Modify component state directly, rarely return values
## Module Design
- Server: CommonJS `module.exports` (e.g., `module.exports = router`)
- Frontend: ES6 `export default` for single exports
- Frontend: Named exports for utilities (e.g., `export { debounce }`)
- Utilities: Export object with all functions (e.g., `export default { handleURL, copyObject, ... }`)
- Used extensively: `huasenjio-compose/huasen-frontend/portal/src/utils/index.js`
- Pattern: Import all utilities, re-export as single object
- Component registration: `huasenjio-compose/huasen-frontend/portal/src/components/common/index.js`
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->
## Architecture

## System Overview
```text
```
## Component Responsibilities
| Component | Responsibility | File |
|-----------|----------------|------|
| Nginx | Reverse proxy, static file serving, routing | `huasen-nginx/conf/nginx-open.conf` |
| Express Server | HTTP API, WebSocket, business logic | `huasen-server/app.js` |
| Portal Frontend | User-facing website navigation | `huasen-frontend/portal/` |
| Admin Frontend | Backend management dashboard | `huasen-frontend/admin/` |
| MongoDB | Primary data store for users, articles, sites | `huasen-mongo/volume/` |
| Redis | Session cache, blacklist, token pool | `huasen-redis/data/` |
| WebSocket Server | Real-time communication | `huasen-server/plugin/ws/` |
## Pattern Overview
- Nginx acts as reverse proxy and entry point
- Express.js backend follows layered architecture
- Vue.js frontends (portal and admin) are separate SPAs
- Docker Compose orchestrates all services
- MongoDB for persistence, Redis for caching
## Layers
- Purpose: User interface and client-side logic
- Location: `huasen-frontend/portal/` and `huasen-frontend/admin/`
- Contains: Vue.js components, views, routers, stores
- Depends on: Backend API via `/api/` endpoints
- Used by: End users (portal) and administrators (admin)
- Purpose: Reverse proxy, load balancing, static file serving
- Location: `huasen-nginx/conf/nginx-open.conf`
- Contains: Nginx configuration, upstream definitions
- Depends on: Express server containers
- Used by: All HTTP/WebSocket traffic
- Purpose: Business logic, API endpoints, authentication
- Location: `huasen-server/`
- Contains: Routers, controllers, services, middleware, plugins
- Depends on: MongoDB, Redis
- Used by: Nginx reverse proxy
- Purpose: Database schema definitions and data operations
- Location: `huasen-server/mongodb/model/`
- Contains: Mongoose models (User, Article, Site, Column, etc.)
- Depends on: MongoDB connection
- Used by: Controllers and services
- Purpose: Data storage and caching
- Location: `huasen-mongo/volume/` and `huasen-redis/data/`
- Contains: MongoDB collections, Redis key-value store
- Depends on: Docker volumes
- Used by: Express application layer
## Data Flow
### Primary Request Path
### WebSocket Flow
### Static File Serving
- Frontend: Vuex stores in `portal/src/store/` and `admin/src/store/`
- Backend: Global state in `global.huasenStatus` object
- Session: Redis-backed session storage via `express-session`
## Key Abstractions
- Purpose: Concurrent database operation orchestration
- Examples: `huasen-server/service/index.js`, `huasen-server/service/template.js`
- Pattern: Event-driven parallel task execution with callback aggregation
- Purpose: Request preprocessing and validation
- Examples: `huasen-server/middleware/common.middleware.js`
- Pattern: Express middleware chain with `next()` continuation
- Purpose: Shared helper functions across application
- Examples: `global.huasen.responseData`, `global.huasen.formatError`
- Pattern: Global namespace injection in `huasen-server/global/index.js`
## Entry Points
- Location: `huasen-frontend/portal/src/main.js`
- Triggers: Browser navigation to `/portal/`
- Responsibilities: Vue app initialization, router setup, store configuration
- Location: `huasen-frontend/admin/src/main.js`
- Triggers: Browser navigation to `/admin/`
- Responsibilities: Admin dashboard initialization, authentication check
- Location: `huasen-server/app.js`
- Triggers: Container startup via Docker Compose
- Responsibilities: Express server initialization, middleware setup, route registration
- Location: `huasen-server/mongodb/db.js`
- Triggers: Express server startup
- Responsibilities: MongoDB connection, default data seeding
## Architectural Constraints
- **Threading:** Single-threaded Node.js event loop; WebSocket server runs on separate port (8181)
- **Global state:** `global.huasen` and `global.huasenStatus` objects shared across all requests
- **Circular imports:** None detected; clean dependency hierarchy
- **Container networking:** Services communicate via Docker network `huasenNetwork` using service names
- **Port mapping:** External ports (80, 3000, 8181, 37017, 7379) mapped to internal container ports
- **Volume persistence:** MongoDB and Redis data persisted via Docker volumes
## Anti-Patterns
### Global State Mutation
### Middleware Parameter Decryption
## Error Handling
- Global uncaught exception handler: `process.on('uncaughtException')` in `huasen-server/app.js:79`
- Express error middleware: `handleRequestError` catches all route errors
- Formatted error responses: `global.huasen.formatError()` standardizes error format
- Logging: Log4js integration via `huasen-server/plugin/log.js`
## Cross-Cutting Concerns
<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->
## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->
## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->
## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
