# 腾讯云中国内地部署手册

这份手册专门整理 `Alicia Cloud Storage` 在腾讯云中国内地服务器上的完整部署过程，覆盖：

- 域名与备案准备
- 中国内地轻量应用服务器部署
- 从香港测试服务器迁移数据库
- HTTPS 证书申请与启用
- 防火墙与端口收口
- 日常更新与常见故障排查

本文尽量保留了这次真实上线过程中用到的命令，并补上每条命令的作用，适合学习回顾。

## 1. 本次最终架构

当前正式环境建议理解成这条链路：

```text
浏览器 / 手机端
        |
        v
https://windwindwind-alicia.cn
        |
        v
腾讯云中国内地轻量应用服务器
        |
        +--> frontend 容器（Nginx，暴露 80/443）
        |
        +--> api 容器（Spring Boot，仅绑定 127.0.0.1:8090）
        |
        +--> db 容器（MySQL，仅绑定 127.0.0.1:3310）
        |
        +--> COS（文件对象存储）
```

当前项目关键配置：

- 前端公网端口：`80`、`443`
- 后端本机排查端口：`127.0.0.1:8090`
- 数据库本机排查端口：`127.0.0.1:3310`
- HTTPS 覆盖文件：[compose.https.yaml](/F:/webProject/AliciaCloudStorage/compose.https.yaml)
- 主 Compose 文件：[compose.yaml](/F:/webProject/AliciaCloudStorage/compose.yaml)

## 2. 适用前提

开始部署前，建议先具备这些条件：

1. 你已经有腾讯云账号。
2. 你已经购买中国内地服务器。
3. 你已经购买正式域名。
4. 域名已完成实名认证。
5. 域名已完成 ICP 备案。
6. 你已经准备好 COS 的 `SecretId` 和 `SecretKey`。
7. 仓库代码已在 GitHub。
8. 如果要迁移老数据，香港测试服务器还可以正常 SSH 登录。

## 3. 中国内地正式部署目标

本次部署完成后，目标是：

- 网站地址：`https://windwindwind-alicia.cn`
- 前端通过 HTTPS 访问
- 后端只通过 Nginx 反代暴露，不直接开放公网
- MySQL 不对公网开放
- 老环境中的用户、目录、文件元数据迁移到新服务器

## 4. 购买中国内地服务器

推荐选择：

- 产品：`轻量应用服务器 Lighthouse`
- 地域：`上海 / 广州 / 南京` 任选其一，尽量靠近主要用户
- 系统：`Ubuntu 24.04 LTS`
- 配置：`2核 2G` 起步，`2核 4G` 更稳
- 时长：至少 `3个月`，更推荐 `半年`
- 登录方式：`自定义密码`

为什么这样选：

- 轻量应用服务器更适合这类个人项目起步。
- `Ubuntu 24.04` 和本次实际部署环境一致，后面照抄命令最省心。
- `2核2G` 够备案和初版上线，`2核4G` 对 `Docker + Spring Boot + MySQL` 更从容。
- 中国内地服务器才能用于 ICP 备案和正式上线。

## 5. 域名备案与解析

### 5.1 ICP 备案

这一步在控制台完成，不在服务器里执行命令。

本次实际结果：

- 主体备案号：`鄂ICP备2026018755号`
- 网站备案号：`鄂ICP备2026018755号-2`
- 域名：`windwindwind-alicia.cn`

说明：

- 只有备案通过后，才适合在中国内地正式对公网提供网站服务。
- 域名实名认证、备案主体、腾讯云账号信息尽量保持一致。

### 5.2 DNS 解析

在腾讯云 / DNSPod 控制台里，给域名增加两条 `A` 记录：

- `@` -> `101.34.69.160`
- `www` -> `101.34.69.160`

如果你想在本机检查解析结果，可以执行：

```powershell
nslookup windwindwind-alicia.cn
nslookup www.windwindwind-alicia.cn
```

命令说明：

- `nslookup`
  作用：检查域名最终解析到了哪个 IP

## 6. SSH 登录中国内地服务器

使用 SSH 工具登录：

- 协议：`SSH`
- 端口：`22`
- 用户名：`ubuntu`
- 密码：你购买服务器时设置的密码

命令示例：

```bash
ssh ubuntu@101.34.69.160
```

命令说明：

- `ssh 用户名@IP`
  作用：从本地终端远程登录 Linux 服务器

如果登录失败，优先检查：

- 腾讯云轻量防火墙是否放行了 `22/TCP`
- 用户名是否为 `ubuntu`
- 密码是否输对

## 7. 安装 Docker 和 Compose

以下命令全部在中国内地服务器终端里执行。

### 7.1 安装 Docker

```bash
sudo apt update
sudo apt install -y git docker.io curl
sudo systemctl enable --now docker
sudo docker --version
```

命令说明：

- `sudo apt update`
  作用：更新 apt 软件包索引。
- `sudo apt install -y git docker.io curl`
  作用：安装 Git、Docker 和 curl。
- `sudo systemctl enable --now docker`
  作用：启动 Docker，并设置开机自启。
- `sudo docker --version`
  作用：确认 Docker 已正确安装。

### 7.2 手动安装 Docker Compose 插件

这次实际部署时，`docker-compose-plugin` 包在当前源里没有直接可用，所以用手动方式安装。

```bash
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
sudo docker compose version
```

命令说明：

- `mkdir -p ...`
  作用：创建 Docker CLI 插件目录。
- `curl -SL ... -o ...`
  作用：下载最新 Compose 插件二进制。
- `chmod +x ...`
  作用：给插件可执行权限。
- `sudo docker compose version`
  作用：验证 Compose 插件已可用。

## 8. 拉取项目代码

```bash
cd ~
git clone https://github.com/changdaobbz01/aliciaCloudStorage.git
cd ~/aliciaCloudStorage
git pull
```

命令说明：

- `cd ~`
  作用：回到当前用户家目录。
- `git clone ...`
  作用：把仓库第一次完整下载到服务器。
- `cd ~/aliciaCloudStorage`
  作用：进入项目目录。
- `git pull`
  作用：确保已经是最新代码。

## 9. 准备环境变量

### 9.1 新环境首次部署

如果这台中国内地服务器是全新环境，先复制模板：

```bash
cd ~/aliciaCloudStorage
cp .env.example .env
nano .env
```

至少需要填写这些变量：

```env
MYSQL_ROOT_PASSWORD=你自己设置的强密码
MYSQL_DATABASE=alicia_cloud_storage
ALICIA_AUTH_TOKEN_SECRET=随机字符串
ALICIA_AUTH_TOKEN_EXPIRE_SECONDS=604800
ALICIA_COS_SECRET_ID=你的COS SecretId
ALICIA_COS_SECRET_KEY=你的COS SecretKey
ALICIA_COS_REGION=你的COS地域
ALICIA_COS_BUCKET=你的bucket-appid
ALICIA_COS_MAX_FILE_SIZE_BYTES=1073741824
ALICIA_STORAGE_TOTAL_SPACE_BYTES=1099511627776
ALICIA_STORAGE_DEFAULT_USER_QUOTA_BYTES=53687091200
ALICIA_BOOTSTRAP_ADMIN_PHONE=你的管理员手机号
ALICIA_BOOTSTRAP_ADMIN_PASSWORD=你的管理员密码
ALICIA_BOOTSTRAP_ADMIN_NICKNAME=系统管理员
ALICIA_APP_PACKAGE_STORAGE_DIR=/app/data/app-package
```

### 9.2 如果要迁移老环境，建议直接复制旧 `.env`

本次实际迁移时，为了避免 COS、token、数据库名等配置不一致，采用了从香港服务器直接复制 `.env` 的方式。

在香港服务器执行：

```bash
scp -O ~/aliciaCloudStorage/.env ubuntu@101.34.69.160:~/aliciaCloudStorage/.env
```

命令说明：

- `scp -O 源文件 目标`
  作用：通过旧版 SCP 协议把文件从一台服务器传到另一台服务器。
- 这里的 `-O`
  作用：强制使用旧版 SCP 协议，兼容性更稳。

## 10. 从香港测试环境迁移数据库

如果你要保留老环境中的：

- 用户账号
- 文件目录结构
- 文件元数据
- 管理员设置

那就应该迁移 MySQL，而不是直接空库启动。

### 10.1 在香港服务器导出数据库

```bash
cd ~/aliciaCloudStorage
sudo docker compose exec -T db sh -lc 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' > ~/alicia-cloud-storage.sql
ls -lh ~/alicia-cloud-storage.sql
```

命令说明：

- `docker compose exec -T db ...`
  作用：在 `db` 容器里执行命令。
- `mysqldump`
  作用：导出 MySQL 数据库结构和数据。
- `> ~/alicia-cloud-storage.sql`
  作用：把导出结果保存到服务器用户家目录。
- `ls -lh`
  作用：检查 SQL 文件是否生成，以及文件大小是否合理。

### 10.2 把 SQL 文件传到中国内地服务器

在香港服务器执行：

```bash
scp -O ~/alicia-cloud-storage.sql ubuntu@101.34.69.160:~/
```

命令说明：

- 作用：把数据库导出文件复制到中国内地服务器家目录。

### 10.3 在中国内地服务器先启动数据库容器

```bash
cd ~/aliciaCloudStorage
sudo docker compose up -d db
sudo docker compose ps
```

命令说明：

- 作用：先只启动 MySQL，方便导入旧数据。

### 10.4 导入旧数据库

```bash
cd ~
sudo docker exec -i aliciacloudstorage-db-1 sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' < ~/alicia-cloud-storage.sql
sudo docker compose -f ~/aliciaCloudStorage/compose.yaml ps
```

命令说明：

- `docker exec -i ... mysql ... < sql文件`
  作用：把 SQL 文件导入正在运行的 MySQL 容器。
- `compose ps`
  作用：确认导入后数据库容器仍然健康。

## 11. 启动后端和前端

### 11.1 首次中国内地启动

如果先迁移数据库，再启动业务层，推荐执行：

```bash
cd ~/aliciaCloudStorage
sudo docker compose up -d --build api frontend
sudo docker compose ps
```

命令说明：

- `docker compose up -d --build api frontend`
  作用：构建并启动后端和前端。
- `--build`
  作用：强制重新构建镜像，避免还在用旧代码。

### 11.2 健康检查

```bash
curl http://127.0.0.1:8090/api/health
curl -I http://127.0.0.1
```

命令说明：

- `curl http://127.0.0.1:8090/api/health`
  作用：验证后端健康检查接口。
- `curl -I http://127.0.0.1`
  作用：只看前端 HTTP 响应头，不下载正文。

理想情况：

- 后端健康接口返回 JSON
- 前端 `80` 端口返回 `200` 或 `301`

## 12. 申请 SSL 证书

这一步主要在腾讯云控制台完成。

推荐做法：

1. 进入腾讯云 SSL 控制台。
2. 申请免费证书。
3. 绑定域名：`windwindwind-alicia.cn`
4. 域名验证方式：`自动 DNS 验证`
5. 算法：`RSA`
6. 等待状态变成 `已签发`
7. 下载 `Nginx` 格式证书

下载后通常会得到类似这几个文件：

- `windwindwind-alicia.cn_bundle.pem`
- `windwindwind-alicia.cn.key`
- `windwindwind-alicia.cn_bundle.crt`
- `windwindwind-alicia.cn.csr`

真正部署时只用前两个：

- `*.pem`
- `*.key`

## 13. 在服务器启用 HTTPS

### 13.1 创建证书目录

```bash
cd ~/aliciaCloudStorage
mkdir -p deploy/certs
```

### 13.2 把证书放到项目目录

最终要求这两个文件存在：

- `~/aliciaCloudStorage/deploy/certs/fullchain.pem`
- `~/aliciaCloudStorage/deploy/certs/privkey.pem`

如果你已经把证书传上去了，可以这样重命名：

```bash
cd ~/aliciaCloudStorage/deploy/certs
mv windwindwind-alicia.cn_bundle.pem fullchain.pem
mv windwindwind-alicia.cn.key privkey.pem
ls -lh
```

命令说明：

- `mv 原文件 新文件`
  作用：改成项目约定的文件名。
- `ls -lh`
  作用：确认两个证书文件已经到位。

### 13.3 用 HTTPS 覆盖配置启动前端

```bash
cd ~/aliciaCloudStorage
sudo docker compose -f compose.yaml -f compose.https.yaml up -d --build frontend
sudo docker compose ps
```

命令说明：

- `-f compose.yaml -f compose.https.yaml`
  作用：在基础服务配置上叠加 HTTPS 前端配置。
- `compose.https.yaml`
  作用：给前端增加 `443` 端口，并挂载证书文件和 SSL Nginx 配置。

### 13.4 本机验证 HTTPS

```bash
curl -k -I https://127.0.0.1
curl -I http://127.0.0.1
```

命令说明：

- `curl -k -I https://127.0.0.1`
  作用：验证本机 `443` 端口是否可用。
- `curl -I http://127.0.0.1`
  作用：验证 HTTP 是否自动跳转到 HTTPS。

理想结果：

- HTTPS 返回 `200`
- HTTP 返回 `301`

## 14. 配置腾讯云轻量防火墙

公网建议只放行：

- `22/TCP`
- `80/TCP`
- `443/TCP`

可以保留：

- `Ping`

不要对公网开放：

- `3310/TCP`
- `8090/TCP`

说明：

- 当前 [compose.yaml](/F:/webProject/AliciaCloudStorage/compose.yaml) 已经把：
  - `3310` 绑定成 `127.0.0.1:3310`
  - `8090` 绑定成 `127.0.0.1:8090`
- 所以即使不看控制台，服务本身也只允许本机排查使用这两个端口。

## 15. 正式更新命令

### 15.1 全量更新后端和前端

```bash
cd ~/aliciaCloudStorage
git pull
sudo docker compose -f compose.yaml -f compose.https.yaml up -d --build api frontend
sudo docker compose -f compose.yaml -f compose.https.yaml ps
```

命令说明：

- `git pull`
  作用：拉取最新代码。
- `up -d --build api frontend`
  作用：重新构建并启动后端和前端。
- `ps`
  作用：确认容器是否都正常。

### 15.2 只更新前端

```bash
cd ~/aliciaCloudStorage
git pull
sudo docker compose -f compose.yaml -f compose.https.yaml up -d --build frontend
```

适用场景：

- 只改了 React 页面
- 只改了 CSS
- 没有后端接口变化

### 15.3 查看当前服务器代码版本

```bash
cd ~/aliciaCloudStorage
git log --oneline -1
```

命令说明：

- 作用：确认服务器现在跑的是哪个提交版本。

这条命令非常重要，因为很多“更新后没变化”的根因，其实是：

- `git pull` 没拉下来
- Docker 重建成功了，但代码还是旧的

## 16. GitHub 拉取失败时的处理

中国内地服务器连接 GitHub 时，可能遇到：

```text
GnuTLS recv error (-110): The TLS connection was non-properly terminated.
```

这通常不是代码坏了，而是跨境链路抖动。

先试：

```bash
cd ~/aliciaCloudStorage
git config --global http.version HTTP/1.1
git pull origin main
```

如果还不稳，再试：

```bash
git config --global http.lowSpeedLimit 0
git config --global http.lowSpeedTime 999999
git fetch origin main
git log --oneline origin/main -1
git pull --ff-only origin main
```

命令说明：

- `http.version HTTP/1.1`
  作用：避免某些环境下 HTTP/2 连接不稳定。
- `git fetch`
  作用：先只拉远端引用，不急着合并。
- `git pull --ff-only`
  作用：只接受快进更新，避免意外生成 merge commit。

## 17. 502 / 页面没变化的排查

### 17.1 浏览器返回 502

优先检查：

```bash
cd ~/aliciaCloudStorage
sudo docker compose -f compose.yaml -f compose.https.yaml ps
sudo docker compose -f compose.yaml -f compose.https.yaml logs --tail=120 api
curl http://127.0.0.1:8090/api/health
```

说明：

- `frontend` 正常但 `api` 没起来时，最容易出现 502。
- `api` 日志通常会直接给出根因。

### 17.2 更新后页面没变化

优先检查：

```bash
cd ~/aliciaCloudStorage
git log --oneline -1
sudo docker compose -f compose.yaml -f compose.https.yaml ps
```

说明：

- 很多时候不是“代码没改对”，而是服务器还停在旧提交。
- 先确认 `git log`，再谈页面效果。

### 17.3 浏览器缓存导致没变化

如果服务器代码和容器都对了，再做：

- `Ctrl + F5`
- 或打开无痕窗口重新访问

## 18. 常用运维命令速查

### 18.1 看容器状态

```bash
sudo docker compose -f compose.yaml -f compose.https.yaml ps
```

### 18.2 看后端日志

```bash
sudo docker compose -f compose.yaml -f compose.https.yaml logs --tail=100 api
```

### 18.3 看前端日志

```bash
sudo docker compose -f compose.yaml -f compose.https.yaml logs --tail=100 frontend
```

### 18.4 重启全部服务

```bash
sudo docker compose -f compose.yaml -f compose.https.yaml restart
```

### 18.5 停止服务

```bash
sudo docker compose -f compose.yaml -f compose.https.yaml down
```

说明：

- `down` 默认不会删除命名卷里的数据库数据。

## 19. 一条最短上线路径

如果你已经：

- 买好中国内地服务器
- 域名备案通过
- `.env` 已准备好
- 证书已经签发并放到 `deploy/certs`

那么最短上线链路其实就是：

```bash
ssh ubuntu@你的服务器IP
cd ~/aliciaCloudStorage
git pull
sudo docker compose -f compose.yaml -f compose.https.yaml up -d --build
sudo docker compose -f compose.yaml -f compose.https.yaml ps
curl http://127.0.0.1:8090/api/health
curl -k -I https://127.0.0.1
```

如果这些都通，再去浏览器打开：

- `https://windwindwind-alicia.cn`

## 20. 复盘建议

如果你想真正学会，而不是只把项目跑起来，我建议你每次部署都按这个顺序思考：

1. 代码版本是不是最新？
2. 容器有没有成功启动？
3. 后端本机健康检查通不通？
4. 前端本机 80/443 通不通？
5. 证书有没有挂对？
6. 防火墙是不是只开放了该开的端口？
7. 页面没变化，是缓存问题还是代码没更新？

只要你把这条排查路径记住，后面绝大多数部署问题都能自己定位出来。
