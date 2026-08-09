# Alicia Cloud Android Add

`phoneAppAdd` 是 Alicia 云盘移动端的新视觉版本，使用 Kotlin + Jetpack Compose 构建。业务层、API、ViewModel 状态机复用当前 `phoneApp`，UI 改为轻量白底蓝色体系，便于和机甲风版本并行维护。

## 当前能力

- 手机号 + 密码登录
- 首页概览、空间使用进度、最近文件、文件分类入口
- 文件页：目录浏览、全盘分类、关键字搜索、文件/文件夹筛选
- 文件操作：上传文件、新建文件夹、预览、下载、分享、移动、删除到回收站
- 批量操作：多选下载 ZIP、移动、删除、回收站恢复和彻底删除
- 传输管理：底部导航独立页面、下载/上传进度、失败态、下载失败重试
- 分享链接：剪贴板/深链识别、提取码校验、选中内容保存到网盘
- 管理员页：创建账号、调整额度、重置密码
- 账号面板：头像、昵称、密码、环境切换、退出登录

这个工程使用独立 `applicationId = "com.alicia.cloudstorage.phone.add"`，可以和当前 `phoneApp` 同时安装，便于对比两套 UI。源码 `namespace` 仍保持 `com.alicia.cloudstorage.phone`，避免为了并行安装而移动业务代码目录。

并行体验版保留内置 APP 更新检测，便于验证新 UI 版本的完整启动流程。

## 默认联调地址

当前默认连线上服务：

- `https://windwindwind-alicia.cn`

如需覆盖默认地址，可通过 Gradle 属性或 `local.properties` 配置 `ALICIA_API_BASE_URL`。

## 本地运行

1. 在 Android Studio 中打开 `phoneAppAdd`
2. 如需覆盖默认后端地址，可参考 `local.properties.example`
3. 同步 Gradle 并运行 `app`

## 可选本地配置

可以在 `phoneAppAdd/local.properties` 里追加：

```properties
ALICIA_API_BASE_URL=https://windwindwind-alicia.cn
```

如果你要临时切回本地开发环境，也可以改成例如：

```properties
ALICIA_API_BASE_URL=http://10.0.2.2:8090
```

## 后续建议

- 正式发版前接入与当前 `phoneApp` release 包一致的签名流程
- 按新 UI 风格继续细化 PDF / 音视频内嵌预览
- 增加大文件分片上传
- 引入更完整的分页、离线缓存和分类统计
