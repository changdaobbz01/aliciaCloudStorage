# 腾讯云部署手册

这份文档记录了 `Alicia Cloud Storage` 从购买腾讯云轻量应用服务器到成功访问的完整部署过程，适合第一次自己部署项目的同学照着操作。

本文默认场景：

- 云平台：腾讯云轻量应用服务器 `Lighthouse`
- 系统：`Ubuntu 24.04 LTS`
- 地域：`中国香港`
- 部署方式：`Docker Compose`
- 访问方式：`IP + 端口`

## 1. 部署目标

本次部署目标是把项目跑在一台全新的 Linux 服务器上，并能通过公网访问：

- 前端：`http://你的公网IP`
- 后端健康检查：`http://你的公网IP:8090/api/health`

## 2. 购买服务器

推荐选择：

- 产品：`轻量应用服务器 Lighthouse`
- 地域：`中国香港`
- 镜像：`Ubuntu 24.04 LTS`
- 配置：`2核 4GB`
- 时长：`1个月`
- 登录方式：`自定义密码`
- 自动续费：`关闭`

这样选的原因：

- 中国香港不涉及中国内地网站备案，适合练习完整部署流程
- `2核4G` 对 `Spring Boot + MySQL + Docker` 更稳妥
- 先买 `1个月`，便于低成本试错

## 3. 连接服务器

可以使用腾讯云的 `OrcaTerm`，也可以用其他 SSH 工具。

连接参数：

- 协议：`SSH`
- 端口：`22`
- 用户名：`ubuntu`
- 密码：购买服务器时设置的登录密码

如果连接失败，先检查轻量服务器防火墙是否已放行 `22/TCP`。

## 4. 安装 Docker 和 Compose

以下命令全部在服务器终端执行。

### 4.1 安装基础工具

```bash
sudo apt update
sudo apt install -y ca-certificates curl gnupg git
```

命令说明：

- `sudo apt update`
  作用：更新软件包索引，让系统知道最新可安装的软件版本。
- `sudo apt install -y ca-certificates curl gnupg git`
  作用：安装 HTTPS 证书、下载工具、GPG 和 Git。
  说明：
  - `curl` 用来下载 Docker 仓库密钥
  - `gnupg` 用来处理签名密钥
  - `git` 用来从 GitHub 拉代码
  - `-y` 表示自动确认安装

### 4.2 添加 Docker 官方仓库

```bash
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
```

命令说明：

- `sudo install -m 0755 -d /etc/apt/keyrings`
  作用：创建保存仓库密钥的目录。
- `curl -fsSL ... | sudo gpg --dearmor -o ...`
  作用：下载 Docker 官方 GPG 密钥，并转换成 apt 可识别的格式。
- `sudo chmod a+r ...`
  作用：让系统中的 apt 能读取这个密钥文件。

### 4.3 写入 Docker 仓库地址

```bash
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
```

命令说明：

- 作用：把 Docker 官方软件源添加到系统 apt 源列表中。
- `$(dpkg --print-architecture)`
  作用：自动识别当前 CPU 架构，比如 `amd64`
- `$(. /etc/os-release && echo "$VERSION_CODENAME")`
  作用：自动识别 Ubuntu 版本代号，比如 `noble`

### 4.4 安装 Docker Engine 和 Compose 插件

```bash
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

命令说明：

- 第二次 `apt update`
  作用：刷新刚刚添加的 Docker 软件源索引。
- `docker-ce`
  作用：Docker Engine 本体。
- `docker-compose-plugin`
  作用：提供 `docker compose` 命令。
- `docker-buildx-plugin`
  作用：增强镜像构建能力。

### 4.5 验证安装

```bash
sudo docker --version
sudo docker compose version
git --version
```

命令说明：

- `sudo docker --version`
  作用：确认 Docker 已正确安装。
- `sudo docker compose version`
  作用：确认 Compose 插件已正确安装。
- `git --version`
  作用：确认 Git 可用。

### 4.6 把当前用户加入 Docker 组

```bash
sudo usermod -aG docker $USER
```

命令说明：

- 作用：把当前登录用户加入 `docker` 用户组。
- 这样以后通常不需要每次都写 `sudo docker ...`
- 说明：这条命令执行后通常需要重新登录 SSH 才生效

## 5. 克隆项目代码

### 5.1 拉取仓库

```bash
cd ~
git clone https://github.com/changdaobbz01/aliciaCloudStorage.git
cd aliciaCloudStorage
```

命令说明：

- `cd ~`
  作用：回到当前用户主目录。
- `git clone ...`
  作用：把 GitHub 仓库完整下载到服务器。
- `cd aliciaCloudStorage`
  作用：进入项目目录。

如果仓库是私有的，GitHub 已经不支持直接用账号密码 `clone`，需要：

- 把仓库改成公开仓库
- 或使用 `Personal Access Token`

## 6. 准备环境变量

### 6.1 复制模板

```bash
cp .env.example .env
```

命令说明：

- 作用：复制一份环境变量模板，生成真正会被部署读取的 `.env` 文件。

### 6.2 生成随机 Token Secret

```bash
openssl rand -hex 32
```

命令说明：

- 作用：生成一串随机密钥，用作登录 token 的签名 secret。
- 这串值要填到 `.env` 里的 `ALICIA_AUTH_TOKEN_SECRET`

### 6.3 编辑 `.env`

```bash
nano .env
```

命令说明：

- 作用：用终端编辑器 `nano` 打开 `.env`

至少需要改这些值：

```env
MYSQL_ROOT_PASSWORD=你自己设置的强密码
MYSQL_DATABASE=alicia_cloud_storage
ALICIA_AUTH_TOKEN_SECRET=刚才生成的随机字符串
ALICIA_COS_SECRET_ID=你自己的COS SecretId
ALICIA_COS_SECRET_KEY=你自己的COS SecretKey
ALICIA_COS_REGION=ap-shanghai
ALICIA_COS_BUCKET=你自己的bucket-appid
ALICIA_COS_MAX_FILE_SIZE_BYTES=1073741824
ALICIA_STORAGE_TOTAL_SPACE_BYTES=1073741824
ALICIA_STORAGE_DEFAULT_USER_QUOTA_BYTES=536870912
```

`nano` 保存退出方法：

- `Ctrl + O`：保存
- 回车：确认文件名
- `Ctrl + X`：退出

注意：

- 不要把真实 `.env` 提交到 GitHub
- 不要把 `COS SecretKey` 发给别人

## 7. 启动服务

### 7.1 首次构建并启动

```bash
sudo docker compose up -d --build
```

命令说明：

- `docker compose up`
  作用：根据 `compose.yaml` 启动所有服务
- `-d`
  作用：后台运行，不占用当前终端
- `--build`
  作用：启动前先重新构建镜像

本项目会启动三个容器：

- `db`：MySQL
- `api`：Spring Boot 后端
- `frontend`：Nginx + React 前端

说明：

- 当前仓库已经改成多阶段 Docker 构建
- 不需要在服务器额外安装 Java 和 Node.js
- `docker compose up -d --build` 会自动完成前后端构建

## 8. 验证部署结果

### 8.1 查看容器状态

```bash
sudo docker compose ps
```

命令说明：

- 作用：查看当前服务容器是否都正常运行

理想状态应看到：

- `db`：`healthy`
- `api`：`Up`
- `frontend`：`Up`

### 8.2 查看后端日志

```bash
sudo docker compose logs --tail=100 api
```

命令说明：

- 作用：查看后端最近 100 行日志
- 重点看：
  - Flyway 是否执行成功
  - Spring Boot 是否启动成功
  - 是否有数据库连接错误

### 8.3 本机回环验证

```bash
curl http://127.0.0.1:8090/api/health
curl -i http://127.0.0.1:8091
```

命令说明：

- `curl http://127.0.0.1:8090/api/health`
  作用：验证后端健康检查是否正常
- `curl -i http://127.0.0.1:8091`
  作用：验证前端 Nginx 是否正常返回首页

### 8.4 登录接口验证

```bash
curl -i -X POST http://127.0.0.1:8090/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"13800000000","password":"Admin@123"}'
```

命令说明：

- 作用：直接在服务器内部测试登录接口
- 预期结果：返回 `200` 和 token

如果需要模拟浏览器同源访问，也可以测试：

```bash
curl -i -X POST http://127.0.0.1:8091/api/auth/login \
  -H "Origin: http://你的公网IP:8091" \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"13800000000","password":"Admin@123"}'
```

这个命令在排查 Nginx 代理和 CORS 时非常有用。

## 9. 配置腾讯云轻量服务器防火墙

### 9.1 需要放行的端口

建议至少放行：

- `22/TCP`：SSH 登录
- `80/TCP`：前端访问
- `8090/TCP`：后端健康检查或接口测试

不要对公网开放：

- `3310/TCP`：MySQL 端口

### 9.2 当前项目的访问地址

- 前端：`http://你的公网IP`
- 健康检查：`http://你的公网IP:8090/api/health`

## 10. 默认演示账号

数据库为空时，系统会自动初始化演示账号：

- 管理员：`13800000000` / `Admin@123`
- 普通用户：`13900000000` / `User@123`

部署成功后建议马上修改管理员密码。

## 11. 常用运维命令

### 11.1 更新代码

```bash
cd ~/aliciaCloudStorage
git pull
```

命令说明：

- 作用：拉取 GitHub 上最新代码

### 11.2 重新构建并启动

```bash
sudo docker compose up -d --build
```

命令说明：

- 作用：更新代码后重新构建镜像并启动服务

### 11.3 只重建部分服务

```bash
sudo docker compose up -d --build api
sudo docker compose up -d --build frontend
```

命令说明：

- 第一条：只重建后端
- 第二条：只重建前端

适合小范围修复后快速验证。

### 11.4 查看服务状态

```bash
sudo docker compose ps
```

### 11.5 查看日志

```bash
sudo docker compose logs --tail=100 api
sudo docker compose logs --tail=100 frontend
sudo docker compose logs --tail=100 db
```

命令说明：

- `api`：看后端报错
- `frontend`：看 Nginx 启动和代理情况
- `db`：看 MySQL 启动和健康状态

### 11.6 停止服务

```bash
sudo docker compose down
```

命令说明：

- 作用：停止并移除当前项目相关容器和网络
- 说明：默认不会删除命名卷里的数据库数据

### 11.7 重启服务

```bash
sudo docker compose restart
```

命令说明：

- 作用：重启所有服务
- 适合改完少量配置后快速重启

## 12. 这次实际遇到的问题和解决办法

### 问题 1：浏览器访问返回 502

现象：

- 浏览器访问前端时报 `HTTP 502`

排查方式：

```bash
curl http://127.0.0.1:8090/api/health
curl -i http://127.0.0.1:8091
sudo docker compose logs --tail=100 frontend
sudo docker compose exec frontend sh -c "wget -qO- http://api:8080/api/health || true"
```

结论：

- 服务本身没坏
- 外部访问超时是因为腾讯云轻量防火墙没有放行 `8091/8090`

解决：

- 在轻量服务器防火墙里开放 `80/TCP` 和 `8090/TCP`

### 问题 2：登录接口返回 403

现象：

- 页面打开正常
- `POST /api/auth/login` 返回 `403 Forbidden`

排查方式：

```bash
curl -i -X POST http://127.0.0.1:8090/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"13800000000","password":"Admin@123"}'
```

结论：

- 直接打后端返回 `200`
- 说明登录业务本身没问题
- 问题出在 `frontend -> api` 代理链路中的 `CORS` / `forward headers`

最终修复：

- 调整 Spring Boot 的 `forward headers` 识别
- 修正 Nginx 代理转发头
- 调整后端 CORS `allowed origin patterns`

### 问题 3：浏览器开发者工具里看到 `127.0.0.1:7890`

说明：

- 这通常表示本机 VPN / 代理软件参与了网络请求链路
- 它不一定是根因，但会让排查更复杂

建议：

- 部署问题排查时，优先先确认服务器内部 `curl` 是否正常
- 再确认浏览器代理是否对外部 IP 做了额外处理

## 13. 下一步建议

部署跑通后，建议继续做这些优化：

1. 继续收敛公网暴露面，只保留前端 `80` 端口并隐藏后端 `8090`
2. 新增 `compose.prod.yaml`，不要对公网暴露 MySQL
3. 关闭演示账号初始化，改成手工初始化管理员
4. 关闭生产环境 `show-sql`
5. 给项目绑定域名并配置 HTTPS

## 14. 一句话复盘

这次部署的最小关键路径其实就是：

```bash
git clone 仓库
cp .env.example .env
填写 .env
sudo docker compose up -d --build
sudo docker compose ps
curl http://127.0.0.1:8090/api/health
```

如果这几步都通了，后面的问题一般就是：

- 防火墙端口
- 反向代理
- CORS
- 本机代理/VPN
