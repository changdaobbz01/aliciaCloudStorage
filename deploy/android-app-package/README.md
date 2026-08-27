# Android APK Git Release Artifact

这个目录用于放置需要随服务器 `git pull` 一起发布的 Android 当前正式 APK。

服务器侧 `deploy/scripts/update-cloud-production.sh` 默认使用 `ALICIA_PUBLISH_ANDROID_APP_PACKAGE=auto`：

- 如果这里没有 `current.apk`，服务器更新会跳过 Android APK 发布。
- 如果这里存在 `current.apk`，服务器更新会调用 `deploy/scripts/publish-git-android-app-package.sh`，通过 `/api/admin/app-package` 发布到当前下载入口。
- 如果服务器已经公开同一个 `versionName`，脚本默认跳过重复上传；需要强制重传时设置 `ALICIA_ANDROID_APP_PACKAGE_FORCE=true`。

推荐只保留当前版本，避免把历史 APK 堆进 Git：

```text
deploy/android-app-package/
├─ current.apk
├─ current.apk.sha256
├─ version-name.txt
└─ release-notes.txt
```

`version-name.txt` 只写一行正式版本号，例如：

```text
0.1.9
```

`release-notes.txt` 写本次正式更新说明，不能为空，也不能保留 TODO 文案。
