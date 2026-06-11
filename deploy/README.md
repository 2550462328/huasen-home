# 部署手册

## 架构

- MySQL: 物理机
- Nginx: 物理机
- Redis: Docker 容器
- Elasticsearch: Docker 容器（全文搜索，可选）
- Backend: Docker 容器

## 打包（本地）

```bash
# 后端
cd backend && mvn clean package -DskipTests
# 产物: target/backend-2.0.0-SNAPSHOT.jar

# 前端
cd frontend/portal && npm run build   # 产物: dist/
cd frontend/admin && npm run build    # 产物: dist/
```

## 上传到服务器

```
deploy/
├── docker-compose.yml
├── .env                          # 由 .env.example 复制修改
├── nginx.conf
├── backend/
│   ├── Dockerfile
│   └── backend.jar               # 上传 jar，重命名为 backend.jar
├── portal/                       # 上传 portal/dist 内容
└── admin/                        # 上传 admin/dist 内容
```

## 部署（服务器）

```bash
# 1. 配置
cp .env.example .env
vim .env

# 2. 前端 → Nginx 目录
sudo mkdir -p /opt/huasen
sudo cp -r portal admin /opt/huasen/

# 3. Nginx
sudo cp nginx.conf /etc/nginx/sites-available/huasen.conf
sudo ln -sf /etc/nginx/sites-available/huasen.conf /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx

# 4. 后端 + Redis + ES
docker-compose up -d --build
```

## 配置说明

**`.env`** 需要配置：
- `MYSQL_HOST` - MySQL 地址（物理机用 `host.docker.internal` 或 IP）
- `REDIS_PASSWORD` - Redis 密码
- `ES_ENABLED` - 是否启用全文搜索（true/false）
- `QINIU_*` - 七牛云存储
- `BAILIAN_API_KEY` - 阿里百炼 API Key

**`nginx.conf`** 需要修改：
- `server_name` - 域名

## 更新

```bash
# 更新后端：替换 backend/backend.jar 后
docker-compose up -d --build backend

# 更新前端：替换 portal/ admin/ 后
sudo cp -r portal admin /opt/huasen/ && sudo systemctl reload nginx
```

## 运维

```bash
docker-compose logs -f backend       # 查看日志
docker-compose restart backend       # 重启后端
docker-compose restart redis         # 重启 Redis
docker-compose restart es            # 重启 Elasticsearch
sudo systemctl reload nginx          # 重启 Nginx
```
