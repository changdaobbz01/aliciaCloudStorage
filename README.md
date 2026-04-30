# Alicia Cloud Storage

一个基于 Spring Boot + React + MySQL + 腾讯云 COS 的轻量云盘项目，支持账号权限、文件管理、回收站、大文件分片上传、背景图个性化和管理员配额管理。

## 项目特点

- 普通用户不能自助注册，由管理员统一创建账号
- 支持登录鉴权、个人资料修改、密码修改、管理员重置他人密码
- 文件二进制内容存储在腾讯云 COS，MySQL 存储文件和文件夹元数据
- 支持文件列表、搜索、类型筛选、分页、排序
- 支持新建文件夹、上传、下载、重命名、移动
- 支持回收站、批量移动、批量删除、批量恢复、彻底删除
- 支持图片、PDF、文本、音视频预览
- 支持普通上传和大文件分片上传
- 支持断点续传、上传队列、总进度、单文件进度、失败继续、取消当前上传
- 普通用户按个人配额校验上传空间，管理员账号不受个人配额限制
- 支持用户头像和主页背景图上传，并与账号关联

## 技术栈

- 后端：Spring Boot 4、Spring Web、Spring Data JPA、Flyway
- 前端：React 19、Vite 8、Ant Design 6
- 数据库：MySQL 8
- 对象存储：Tencent Cloud COS
- 部署：Docker Compose、Nginx

## 目录结构

```text
AliciaCloudStorage/
├─ CloudStorageApi/      # Spring Boot 后端
├─ CloudStorageDB/       # 早期 SQL 初始化脚本
├─ webApp/               # React + Vite 前端
├─ phoneApp/             # 移动端占位目录
├─ compose.yaml          # 本地 / 首发用 Docker Compose
├─ .env.example          # 环境变量示例
└─ pom.xml               # Maven 根工程
```

## 运行前准备

建议准备以下环境：

- Docker Desktop 或 Docker Engine + Docker Compose Plugin
- 可用的腾讯云 COS 密钥、地域和桶名

如果你希望使用下文的“开发模式”单独运行前后端，再额外准备：

- JDK 21
- Node.js 20+ 和 npm

先复制环境变量模板：

```powershell
Copy-Item .env.example .env
```

或：

```bash
cp .env.example .env
```

至少需要正确填写这些值：

- `MYSQL_ROOT_PASSWORD`
- `ALICIA_AUTH_TOKEN_SECRET`
- `ALICIA_COS_SECRET_ID`
- `ALICIA_COS_SECRET_KEY`
- `ALICIA_COS_REGION`
- `ALICIA_COS_BUCKET`

如果数据库是全新的空库，首次启动前还建议额外填写：

- `ALICIA_BOOTSTRAP_ADMIN_PHONE`
- `ALICIA_BOOTSTRAP_ADMIN_PASSWORD`

## 最快启动方式

当前仓库已经改成多阶段 Docker 构建，最省事的启动方式是直接使用 Docker Compose。

### 1. 启动容器

```powershell
docker compose up -d --build
```

启动后默认地址：

- 前端：`http://localhost`
- 后端：`http://localhost:8090`
- 健康检查：`http://localhost:8090/api/health`
- MySQL：`localhost:3310`

查看运行状态：

```powershell
docker compose ps
docker compose logs -f api
```

## 开发模式

如果你希望前后端分开开发，推荐这样启动：

### 1. 先只启动 MySQL

```powershell
docker compose up -d db
```

### 2. 启动后端

后端默认连接 `localhost:3306`，如果你使用的是 `compose` 里的 MySQL，需要把数据源改到 `3310`。

PowerShell 示例：

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3310/alicia_cloud_storage?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
$env:SPRING_DATASOURCE_USERNAME="root"
$env:SPRING_DATASOURCE_PASSWORD="123456"
$env:ALICIA_AUTH_TOKEN_SECRET="replace-this-with-a-long-random-secret"
$env:ALICIA_COS_SECRET_ID="your-secret-id"
$env:ALICIA_COS_SECRET_KEY="your-secret-key"
$env:ALICIA_COS_REGION="ap-shanghai"
$env:ALICIA_COS_BUCKET="your-bucket-appid"

Set-Location CloudStorageApi
..\mvnw spring-boot:run
```

### 3. 启动前端

```powershell
Set-Location webApp
npm ci
npm run dev
```

前端开发地址：

- `http://localhost:5173`

## 首个管理员初始化

当前仓库不再内置默认演示账号，也不再依赖固定的演示管理员密码。

如果数据库为空，并且你希望首次启动时自动创建第一个管理员，请在 `.env` 中显式填写：

```env
ALICIA_BOOTSTRAP_ADMIN_PHONE=你的管理员手机号
ALICIA_BOOTSTRAP_ADMIN_PASSWORD=你自己设置的强密码
ALICIA_BOOTSTRAP_ADMIN_NICKNAME=系统管理员
ALICIA_BOOTSTRAP_ADMIN_AVATAR_URL=
```

如果这两个核心变量留空，系统会正常启动，但不会自动生成任何账号。

建议：

- 首次登录后立刻修改管理员密码
- 至少再创建一个正式管理员账号备用
- 空库初始化完成后，按需清空 `ALICIA_BOOTSTRAP_ADMIN_PHONE` 和 `ALICIA_BOOTSTRAP_ADMIN_PASSWORD`

## 常用命令

后端测试：

```powershell
.\mvnw -pl CloudStorageApi test
```

前端构建检查：

```powershell
Set-Location webApp
npm run build
```

停止容器：

```powershell
docker compose down
```

## Docker 说明

当前仓库的前后端镜像都使用了多阶段构建：

- 后端镜像会在构建阶段自动执行 Maven 打包
- 前端镜像会在构建阶段自动执行 `npm ci` 和 `npm run build`

因此在执行 `docker compose up -d --build` 之前，不需要手工准备 `jar` 或 `dist` 产物。

## 腾讯云首发建议

如果你想看完整的实战部署记录和命令说明，请优先阅读：

- [腾讯云部署手册](./DEPLOYMENT_TENCENT_CLOUD.md)

如果你是第一次发布，推荐先走腾讯云轻量应用服务器，而不是一开始就直接上更复杂的云架构。

建议顺序：

1. 购买一台 Ubuntu 22.04 或 24.04 的腾讯云轻量应用服务器
2. 防火墙先只放行 `22`、`80`、`443`
3. 在服务器安装 Docker 和 Docker Compose Plugin
4. 把项目代码上传到服务器，或直接 `git clone`
5. 复制 `.env.example` 为 `.env`，填写生产环境配置
6. 执行 `docker compose up -d --build`
7. 用 `docker compose ps` 和 `curl http://127.0.0.1:8090/api/health` 检查服务
8. 绑定域名，配置 HTTPS

服务器上可参考的命令顺序：

```bash
git clone https://github.com/changdaobbz01/aliciaCloudStorage.git
cd aliciaCloudStorage
cp .env.example .env

docker compose up -d --build
docker compose ps
```

## 上线前注意事项

- 不要把真实 `.env` 上传到仓库
- 真实 COS 密钥如果曾经外泄，先去腾讯云控制台轮换
- 建议生产环境不要直接暴露 MySQL 端口 `3310`
- 当前 `compose.yaml` 也暴露了后端 `8090`，正式公网环境更推荐只对外暴露 Nginx
- 当前仓库不再内置默认演示账号；如果使用了 bootstrap 管理员变量，建议首登后及时修改密码，并按需清空这些变量
- 当前后端默认开启 `show-sql`，生产环境建议关闭
- 如果使用中国内地服务器并对外提供网站服务，请按要求完成备案

## 后续可继续完善

- 增加生产专用 `compose.prod.yaml`
- 把前后端 Dockerfile 改成多阶段构建，减少手工打包步骤
- 增加 README 截图和接口说明
- 增加 CI 自动构建和自动部署
- 完善容量趋势、用户额度排序筛选等管理能力
## License

当前仓库暂未附带开源许可证。如需公开开源，建议补充 `MIT`、`Apache-2.0` 或其他明确许可证。
