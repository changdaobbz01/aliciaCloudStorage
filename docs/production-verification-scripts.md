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

## 2. 本地前端拆分验收

Windows 本地需要一次性验收四个前端时，可在云盘仓库执行平台级脚本：

```powershell
cd F:\webProject\AliciaCloudStorage
powershell -ExecutionPolicy Bypass -File deploy\scripts\verify-platform-frontend-split-local.ps1
```

Ubuntu/Git Bash/macOS 环境可跑 Bash 入口；默认同样会构建四个前端，只做静态边界与字段级 API 契约检查时加 `--skip-build`：

```bash
ALICIA_MAIN_SITE_PROJECT_DIR=../mainSite bash deploy/scripts/verify-platform-frontend-split-local.sh --skip-build
```

PowerShell 和 Bash 平台脚本会覆盖四个前端的构建、静态职责边界、身份路由边界、共享个人资料弹窗，以及四端 Identity 客户端与 `mainSite/mainSiteApi` 的字段级 API 契约。默认主站仓库为同级 `..\mainSite` / `../mainSite`，路径不同时可通过参数或 `ALICIA_MAIN_SITE_PROJECT_DIR` 指定。

## 3. 账号密码输入方式

多数完整验证脚本需要 Identity 账号密码。可以直接运行脚本后按提示输入，也可以在当前终端先导出变量：

```bash
read -r -p "Identity account/email/phone: " ALICIA_VERIFY_ACCOUNT
read -r -s -p "Identity password: " ALICIA_VERIFY_PASSWORD
echo
export ALICIA_VERIFY_ACCOUNT ALICIA_VERIFY_PASSWORD
```

专项脚本同时兼容 `ALICIA_IDENTITY_ACCOUNT` 和 `ALICIA_IDENTITY_PASSWORD`，但推荐统一使用 `ALICIA_VERIFY_ACCOUNT` / `ALICIA_VERIFY_PASSWORD`。

## 4. 最常用：完整生产流验收

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

## 5. 标准路由与身份边界验证

用于每次更新 `api`、`identity`、`rag`、`frontend` 或 Nginx 路由后：

```bash
bash deploy/scripts/verify-identity-cloud-routes.sh
```

脚本会覆盖主站登录、后台 `returnTo`、云盘 `/cloudPan/`、身份后台 `/console/identity/users`、`/console/identity/roles`、`/console/identity/sessions` 与 `/console/identity/audit`、云盘后台 `/console/cloud/users`、`/console/cloud/operations` 与 `/console/cloud/app-package`、Identity/RAG 健康、管理员云盘接口和旧身份路径边界。身份后台的主站容器直连路由仍由 `mainSite` 的 `verify-main-site-routes.sh` 覆盖；前端静态边界脚本还会确认普通云盘端、云盘后台、主站门户和身份后台的 `returnTo`、session 同步、bundle size 守卫与分包策略仍在。

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

## 6. 云盘文件操作专项验证

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

## 7. 分享链路专项验证

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

## 8. 静态边界检查

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

用于提交、部署或生产回归前确认后端 API 路由归属和身份后台管理权限兜底：

```bash
bash deploy/scripts/verify-backend-api-boundaries.sh
```

检查内容：

- `CloudStorageApi` Controller 只暴露云盘业务 API 前缀
- `mainSite/mainSiteApi` Controller 只暴露 `/api/identity/**`
- 云盘后台权限只接受全局管理员和 `cloud/CLOUD_ADMIN`
- 身份后台 Controller 传递 Authorization
- 身份后台委托的管理服务方法直接调用 `requireAdminUser`

## 9. 生产状态快照

用于日常巡检或发布后留档：

```bash
bash deploy/scripts/collect-production-status.sh
```

默认快照会同时输出云盘仓库和 `~/mainSite` 仓库的最近提交与 tracked 文件状态，并探测主站、统一登录、普通云盘、云盘分享深链、app下载页、身份后台、云盘后台、Identity、CloudStorageApi 和 RAG 的主要入口；同时会检查 `/console`、`/cloudPan`、`/console/cloud` 和旧 `/cloudPan/login` 是否按平台规范跳转，避免入口虽然可访问但 Location 已经漂移。

需要把完整路由验证也纳入快照：

```bash
ALICIA_STATUS_RUN_ROUTE_VERIFY=true \
  bash deploy/scripts/collect-production-status.sh
```

开启后会先调用 `~/mainSite/deploy/scripts/verify-main-site-routes.sh`，再调用云盘仓库的 `deploy/scripts/verify-identity-cloud-routes.sh`，同时覆盖主站容器直连路由和主域网关路由。

需要把静态边界检查也纳入快照：

```bash
ALICIA_STATUS_RUN_BOUNDARY_CHECK=true \
  bash deploy/scripts/collect-production-status.sh
```

开启后会同时运行主站前端边界、云盘前端边界、旧身份路径边界和后端 API 归属边界检查，避免只看云盘仓库而漏掉 `mainSite/webApp` 或 `mainSite/userSite` 的拆分回退。

需要把平台级四端前端拆分验收也纳入快照：

```bash
ALICIA_STATUS_RUN_FRONTEND_SPLIT_CHECK=true \
  bash deploy/scripts/collect-production-status.sh
```

开启后会调用 `deploy/scripts/verify-platform-frontend-split-local.sh --skip-build`，复用四端 `returnTo`、session 同步、源码边界、共享个人资料弹窗和字段级 API 契约检查；它不重新构建前端，并默认带上 `ALICIA_VERIFY_RETURN_TO_DISABLE_TYPESCRIPT=1`，不要求四个前端已经安装本地 `typescript` 包，适合发布后留档或服务器侧快速确认当前源码边界。

## 10. 生产备份与备份校验

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

## 11. 发布脚本常用组合

常规云盘发布：

```bash
bash deploy/scripts/update-cloud-production.sh
```

默认组合是 `api frontend`，用于覆盖 `sysManage` 与 `CloudStorageApi` 响应契约联动的更新，例如运营明细 `appRoles` 角色标签。纯前端文案或样式更新才建议显式传 `frontend`。生产 Identity 由 `mainSite` 发布。

Redis 主读期间，云盘和联合发布会加载已生效的 Redis 覆盖，以 `--no-deps` 发布指定服务，避免前端依赖连带重建 RAG。RAG 镜像走[只更新 RAG](#只更新-rag)的独立入口。

`sysManage` 构建会先运行 `verify:api-contracts`，比对后台 TypeScript 类型与 CloudStorageApi 运营 DTO、分页响应、APK 响应和查询参数，避免只改一侧造成生产 UI 字段漂移。

发布前自动备份，发布后生成状态快照：

```bash
ALICIA_BACKUP_BEFORE_UPDATE=true \
ALICIA_COLLECT_STATUS_AFTER_UPDATE=true \
  bash deploy/scripts/update-cloud-production.sh api frontend
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

前端管理台拆分阶段优先使用这个联合入口，它会按顺序发布 `mainSite/webApp`、`mainSite/userSite`、`AliciaCloudStorage/webApp` 和 `AliciaCloudStorage/sysManage`，并在云盘网关更新后补跑主站公网路由复核，覆盖 `/console/identity/**` 与 `/console/cloud/**` 的最终挂载状态。需要发布后留档时可加上：

```bash
ALICIA_COLLECT_STATUS_AFTER_UPDATE=true \
  bash deploy/scripts/update-main-and-cloud-production.sh
```

联合发布会接管这个状态快照开关，等最终主站公网路由复核完成后再运行 `collect-production-status.sh`。

### 只更新 RAG

从已审查的候选检出目录运行脚本。确认已加载通过验收的镜像，将验收记录中的完整 `sha256:…` 镜像 ID 赋给 `candidate_id`，并传入现有生产 Compose 项目目录。

```bash
bash deploy/scripts/update-rag-production.sh "$HOME/aliciaCloudStorage" --candidate-image "$candidate_id"
bash deploy/scripts/update-rag-production.sh "$HOME/aliciaCloudStorage" --candidate-image "$candidate_id" --apply
```

第一条脚本调用只做预检。第二条在正式切换窗口执行单服务镜像切换。脚本校验线上 Compose 配置指纹、当前镜像和健康，保留旧镜像标签并记录当次切换。依赖、本机或公网健康验收失败时，自动恢复上一镜像并复核。

启用 Redis 主读后，脚本从 `deploy/generated/rag-conversation/latest` 读取生效记录，沿用其中的 `redis.env` 和 `compose.redis.yaml`。切换和镜像回滚都只重建 RAG，继续使用运行中的 Redis。内存模式使用基础 Compose 文件，会话按 RAG 重启行为重新建立。

需要手动恢复时，将最新一次发布输出的 `Release record` 路径赋给 `release_record`。回滚也默认做预检，确认后再加 `--apply`。

```bash
bash deploy/scripts/update-rag-production.sh "$HOME/aliciaCloudStorage" --rollback "$release_record"
bash deploy/scripts/update-rag-production.sh "$HOME/aliciaCloudStorage" --rollback "$release_record" --apply
```

记录位于生产项目的 `deploy/generated/rag-releases/`，保存镜像 ID、Compose 配置指纹及状态。Redis 模式的镜像发布也保存当时的 Redis 记录名，回滚要求该记录仍生效。保留发布记录和对应旧镜像直到回滚窗口结束。避免与联合发布、手工换标签或修改同一 Compose 配置同时执行。

如果发布进程被强制终止并遗留 `.lock`，先确认同一项目没有在运行的发布或回滚进程，再用 `rmdir` 移除该空目录。根据最新记录检查实际运行镜像，必要时执行手动回滚。

#### 配置 RAG Redis 会话主读

会话主读通过 `compose.rag-conversation-redis.yaml` 接入，保留生产 `.env`。Redis 使用仅与 RAG 共享的内部网络和专用 AOF 卷，访问需通过 ACL。首次启用使用新加密密钥，旧会话从新上下文开始。执行任务登记保持关闭。

在生产项目目录使用已审核版本中的覆盖文件和 ACL 模板。准备步骤只写候选文件，原配置可以直接用于回退。

```bash
umask 077
mkdir -p deploy/generated/rag-conversation
redis_dir=$(mktemp -d "$PWD/deploy/generated/rag-conversation/release-XXXXXXXX")
cp -- compose.rag-conversation-redis.yaml "$redis_dir/compose.redis.yaml"
password=$(openssl rand -hex 32)
key=$(openssl rand -base64 32)
hash=$(printf '%s' "$password" | sha256sum | cut -d' ' -f1)
sed "s/RAG_REDIS_PASSWORD_SHA256/$hash/" deploy/redis/rag-conversation.acl.example > "$redis_dir/redis.acl"
chmod 644 "$redis_dir/redis.acl"
printf 'ALICIA_RAG_CONVERSATION_REDIS_PASSWORD=%s\nALICIA_RAG_CONVERSATION_REDIS_KEY=%s\nALICIA_RAG_CONVERSATION_REDIS_ACL_FILE=%s\n' \
  "$password" "$key" "$redis_dir/redis.acl" > "$redis_dir/redis.env"
unset password key hash
printf 'prepared\n' > "$redis_dir/status"

base=(sudo docker compose --env-file .env -f compose.yaml -f compose.https.yaml)
primary=(sudo docker compose --env-file .env --env-file "$redis_dir/redis.env" \
  -f compose.yaml -f compose.https.yaml -f "$redis_dir/compose.redis.yaml" --profile rag-execution-foundation)
"${primary[@]}" config --quiet
```

候选目录权限为 `700`，环境文件为 `600`。Redis 容器需要读取 ACL 文件，密码在该文件中仅以哈希存储。执行切换前核对基础配置对应 `MEMORY`（或缺省）、任务登记关闭、运行镜像与 RAG 镜像标签一致。确认固定版本的 Redis 镜像已加载，以及主机有 Redis 增量、AOF 重写和服务重建所需的容量。核对主机 `vm.overcommit_memory` 和同机 AOF 重写结果。Redis 官方建议在 Linux 上将该值设为 `1`。 [Redis 运维文档](https://redis.io/docs/latest/operate/oss_and_stack/management/admin/)

切换窗口内先启动 Redis，待其通过容器健康检查，再重建 RAG。其余服务沿用当前实例。

```bash
"${primary[@]}" up -d --no-deps --pull never --wait --wait-timeout 60 rag-conversation-redis
"${primary[@]}" up -d --no-deps --no-build --pull never --force-recreate rag
```

检查本机和公网 RAG 健康、Identity 与云盘依赖，并用专用测试账号完成两轮合成澄清，核对会话轮次、Redis 中的新加密写入和零任务登记。容器健康检查验证 Redis 要求认证，会话写入验收确认应用侧认证和持久化。

验收通过后登记生效记录，后续 RAG 镜像发布和回滚会读取它。

```bash
printf 'applied\n' > "$redis_dir/status"
printf '%s\n' "$(basename "$redis_dir")" > "$redis_dir/latest"
mv -f -- "$redis_dir/latest" "$PWD/deploy/generated/rag-conversation/latest"
```

回退时用基础配置重建同一 RAG 镜像。确认本机及公网健康，旧会话以无草稿的新上下文开始，再停止 Redis 并保留 AOF 卷及候选配置。

```bash
"${base[@]}" up -d --no-deps --no-build --pull never --force-recreate rag
```

```bash
"${primary[@]}" stop rag-conversation-redis
printf 'rolled-back\n' > "$redis_dir/status"
```

主读期间的 RAG 重建沿用 Redis 覆盖文件和同一套会话凭据。镜像回滚脚本仍按自身发布记录和 Compose 指纹校验，主读配置回退使用上述基础配置。

Android APK 一键发布需要在本地 PowerShell 终端执行，并确保已经加载正式 release keystore 环境变量：

```powershell
. deploy/generated/android-signing/<timestamp>/android-release-signing.env.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/publish-android-release-package.ps1 -ReleaseNotes "填写本次正式更新说明"
```

该脚本默认构建并发布 `phoneAppAdd`，通过 Identity 管理员账号登录后调用 `/api/admin/app-package` 上传到服务器，并验证公开版本和下载入口。已准备好 APK 目录时可使用：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/publish-android-release-package.ps1 -SkipPrepare -PackageDir deploy/generated/android-release-packages/phoneAppAdd/<dir>
```

如果要让服务器常规更新命令同步发布 Git 上的 APK，先在本地把最新 signed APK 整理到当前发布目录并提交：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/stage-android-git-package.ps1
git add deploy/android-app-package/current.apk deploy/android-app-package/current.apk.sha256 deploy/android-app-package/version-name.txt deploy/android-app-package/release-notes.txt
git commit -m "Stage Android APK release artifact"
git push gitee main
```

随后服务器常规更新即可自动发布：

```bash
bash deploy/scripts/update-cloud-production.sh api frontend
```

`update-cloud-production.sh` 默认 `ALICIA_PUBLISH_ANDROID_APP_PACKAGE=auto`：发现 `deploy/android-app-package/current.apk` 就发布，没有该文件就跳过。可用 `ALICIA_PUBLISH_ANDROID_APP_PACKAGE=false` 禁用，或用 `ALICIA_PUBLISH_ANDROID_APP_PACKAGE=true` 要求必须存在并发布。服务器已经公开同一 `versionName` 时会跳过重复上传；需要强制覆盖同版本时设置 `ALICIA_ANDROID_APP_PACKAGE_FORCE=true`。

## 12. 推荐执行顺序

大节点上线前后推荐：

```bash
cd ~/aliciaCloudStorage
git fetch gitee main
git pull --ff-only gitee main
git log --oneline -3

bash deploy/scripts/backup-production-data.sh

ALICIA_VERIFY_PRODUCTION_FLOWS_AFTER_UPDATE=true \
ALICIA_COLLECT_STATUS_AFTER_UPDATE=true \
  bash deploy/scripts/update-cloud-production.sh api frontend
```

如果只是拉取新验证脚本，不涉及容器代码更新，则不用重建容器，直接运行对应 `verify-*` 脚本即可。
