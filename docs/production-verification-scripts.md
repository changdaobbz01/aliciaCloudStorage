# Alicia 生产验证脚本命令手册

这份文档集中整理服务器上的生产验证、巡检、备份和发布验收命令。默认执行目录是：

```bash
cd ~/aliciaCloudStorage
```

## 1. 更新脚本前先同步代码

每次执行新脚本前，先从 Gitee 快进更新服务器仓库：

```bash
cd ~/aliciaCloudStorage
git fetch gitee main
git pull --ff-only gitee main
git log --oneline -3
```

如果服务器提示 tracked 文件有本地改动，先确认改动来源，不要直接强制覆盖。

## 2. 账号密码输入方式

多数完整验证脚本需要 Identity 账号密码。可以直接运行脚本后按提示输入，也可以在当前终端先导出变量：

```bash
read -r -p "Identity account/email/phone: " ALICIA_VERIFY_ACCOUNT
read -r -s -p "Identity password: " ALICIA_VERIFY_PASSWORD
echo
export ALICIA_VERIFY_ACCOUNT ALICIA_VERIFY_PASSWORD
```

专项脚本同时兼容 `ALICIA_IDENTITY_ACCOUNT` 和 `ALICIA_IDENTITY_PASSWORD`，但推荐统一使用 `ALICIA_VERIFY_ACCOUNT` / `ALICIA_VERIFY_PASSWORD`。

## 3. 最常用：完整生产流验收

用于大节点部署后一次性确认主线稳定：

```bash
bash deploy/scripts/verify-cloud-production-flows.sh
```

覆盖内容：

- Identity / CloudStorageApi / RAG / Nginx 主域路径总回归
- JWT `alg/iss/aud/kid`、RS256 JWKS、应用级角色、session revoke、logout
- 云盘管理员运营视图：容量、回收站、分享和分片上传统计总览，以及分享、回收站、用户容量明细接口
- 云盘文件操作专项：上传、列表、下载、HTTP Range 断点下载、ZIP、重命名、移动、回收站、恢复、彻底删除
- 分享链路专项：创建分享、提取码、详情、下载 URL、直连下载、HTTP Range 断点下载、ZIP、保存到网盘、撤销
- 静态边界：旧 `/api/auth/**`、旧 `/api/admin/users`、旧 `sys_user` 引用不回流

常用跳过开关：

```bash
ALICIA_PRODUCTION_FLOW_SKIP_ROUTE_VERIFY=true \
ALICIA_PRODUCTION_FLOW_SKIP_STORAGE_VERIFY=true \
ALICIA_PRODUCTION_FLOW_SKIP_SHARE_VERIFY=true \
ALICIA_PRODUCTION_FLOW_SKIP_BOUNDARY_CHECK=true \
  bash deploy/scripts/verify-cloud-production-flows.sh
```

只跳过不需要的部分即可，不必全部设置。

## 4. 标准路由与身份边界验证

用于每次更新 `api`、`identity`、`rag`、`frontend` 或 Nginx 路由后：

```bash
bash deploy/scripts/verify-identity-cloud-routes.sh
```

如果需要显式指定当前生产 token 算法和 `kid`：

```bash
ALICIA_VERIFY_TOKEN_ALGORITHM=RS256 \
ALICIA_VERIFY_TOKEN_KEY_ID=alicia-rs256-20260822035821 \
  bash deploy/scripts/verify-identity-cloud-routes.sh
```

如果只用普通账号验证，临时跳过管理员检查：

```bash
ALICIA_VERIFY_SKIP_ADMIN_CHECK=true \
  bash deploy/scripts/verify-identity-cloud-routes.sh
```

如果要用正式域名而不是 `https://127.0.0.1`：

```bash
ALICIA_PUBLIC_BASE_URL=https://windwindwind-alicia.cn \
  bash deploy/scripts/verify-identity-cloud-routes.sh
```

## 5. 云盘文件操作专项验证

用于改动上传、下载、移动、删除、回收站、ZIP 打包或 COS 访问相关逻辑后：

```bash
bash deploy/scripts/verify-cloud-storage-flow.sh
```

脚本会创建临时文件夹和文本文件，结束时默认清理测试数据。需要保留现场排查时：

```bash
ALICIA_STORAGE_VERIFY_KEEP_TEST_DATA=true \
  bash deploy/scripts/verify-cloud-storage-flow.sh
```

Web 和 Android 客户端需与服务端保持同一操作边界：批量移动/删除/恢复/彻底删除最多 500 项，ZIP 打包下载最多 100 项。脚本会同时验证空选择、超量选择、HTTP Range `206/416` 响应，避免客户端放行后造成线上异常。

如需观察 COS 对象清理补偿队列状态，可查看云盘库中的任务摘要：

```bash
sudo docker compose -f compose.yaml -f compose.https.yaml exec -T db sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE" -e "
SELECT status, source, COUNT(*) AS tasks, MIN(next_retry_at) AS next_retry_at, MAX(updated_at) AS latest_update
FROM cloud_object_cleanup_task
GROUP BY status, source
ORDER BY status, source;
"'
```

该队列由上传/分片/分享回滚和回收站彻底删除自动登记，后台定时重试；生产验证不主动制造 COS 故障。

## 6. 分享链路专项验证

用于改动分享创建、公开分享页、提取码、分享下载、保存到网盘或分享撤销后：

```bash
bash deploy/scripts/verify-cloud-share-flow.sh
```

脚本会创建临时文件夹、文件和分享链接，结束时默认清理测试数据。需要保留现场排查时：

```bash
ALICIA_SHARE_VERIFY_KEEP_TEST_DATA=true \
  bash deploy/scripts/verify-cloud-share-flow.sh
```

分享相关客户端边界需与服务端保持一致：单个分享最多 20 项，分享保存最多 500 项，分享 ZIP 打包下载最多 100 项。脚本会覆盖分享创建、分享保存、分享文件 HTTP Range `206/416` 响应、分享打包下载的空选择和超量选择拒绝。

## 7. 静态边界检查

用于提交或部署前确认旧身份路径没有回流：

```bash
bash deploy/scripts/check-identity-route-boundary.sh
```

检查内容：

- 源码和部署配置不再引用旧 `/api/auth/**`
- 源码和部署配置不再引用旧 `/api/admin/users`
- Identity 源码/迁移不重新拥有云盘画像字段
- 运行时代码不再引用旧 `sys_user` 表

服务器未安装 `rg` 时脚本会自动降级到 `grep`。

## 8. 生产状态快照

用于日常巡检或发布后留档：

```bash
bash deploy/scripts/collect-production-status.sh
```

需要把完整路由验证也纳入快照：

```bash
ALICIA_STATUS_RUN_ROUTE_VERIFY=true \
  bash deploy/scripts/collect-production-status.sh
```

需要把静态边界检查也纳入快照：

```bash
ALICIA_STATUS_RUN_BOUNDARY_CHECK=true \
  bash deploy/scripts/collect-production-status.sh
```

## 9. 生产备份与备份校验

大更新、数据库迁移或密钥调整前先备份：

```bash
bash deploy/scripts/backup-production-data.sh
```

校验最新备份：

```bash
bash deploy/scripts/validate-production-backup.sh
```

校验指定备份目录：

```bash
bash deploy/scripts/validate-production-backup.sh \
  deploy/generated/production-backups/<timestamp>
```

备份目录可能包含数据库 dump、`.env`、TLS 证书和 RS256 签名密钥材料，必须保持私密。

## 10. 发布脚本常用组合

常规云盘发布：

```bash
bash deploy/scripts/update-cloud-production.sh api identity rag frontend
```

发布前自动备份，发布后生成状态快照：

```bash
ALICIA_BACKUP_BEFORE_UPDATE=true \
ALICIA_COLLECT_STATUS_AFTER_UPDATE=true \
  bash deploy/scripts/update-cloud-production.sh api identity rag frontend
```

涉及文件/分享主线的大更新，发布后追加完整生产流验收：

```bash
ALICIA_VERIFY_PRODUCTION_FLOWS_AFTER_UPDATE=true \
  bash deploy/scripts/update-cloud-production.sh api frontend
```

主站和云盘一起发布：

```bash
bash deploy/scripts/update-main-and-cloud-production.sh
```

只更新 RAG：

```bash
bash deploy/scripts/update-rag-production.sh
```

Android APK 一键发布需要在本地 PowerShell 终端执行，并确保已经加载正式 release keystore 环境变量：

```powershell
. deploy/generated/android-signing/<timestamp>/android-release-signing.env.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/publish-android-release-package.ps1 -ReleaseNotes "填写本次正式更新说明"
```

该脚本默认构建并发布 `phoneAppAdd`，通过 Identity 管理员账号登录后调用 `/api/admin/app-package` 上传到服务器，并验证公开版本和下载入口。已准备好 APK 目录时可使用：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/publish-android-release-package.ps1 -SkipPrepare -PackageDir deploy/generated/android-release-packages/phoneAppAdd/<dir>
```

## 11. 推荐执行顺序

大节点上线前后推荐：

```bash
cd ~/aliciaCloudStorage
git fetch gitee main
git pull --ff-only gitee main
git log --oneline -3

bash deploy/scripts/backup-production-data.sh

ALICIA_VERIFY_PRODUCTION_FLOWS_AFTER_UPDATE=true \
ALICIA_COLLECT_STATUS_AFTER_UPDATE=true \
  bash deploy/scripts/update-cloud-production.sh api identity rag frontend
```

如果只是拉取新验证脚本，不涉及容器代码更新，则不用重建容器，直接运行对应 `verify-*` 脚本即可。
