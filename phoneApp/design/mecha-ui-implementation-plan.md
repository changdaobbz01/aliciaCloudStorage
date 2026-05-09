# Alicia 云盘机甲风首页落地计划

## 目标

把参考图里的“机甲头图 + 白色装甲卡片 + 蓝橙机械角标 + 驾驶舱底栏”落到 Android Compose 客户端中，同时保留现有业务能力。

## 角色规则

- 普通用户首页快捷入口只显示：全部文件、上传文件、回收站。
- 管理员首页快捷入口显示：全部文件、上传文件、回收站、账号管理。
- 普通用户底部不出现“账号管理”字样，可使用“我的”进入个人账号信息。
- 管理员底部保留“账号管理”入口。

## 执行阶段

1. 视觉基准

产物：原创机甲头图 `app/src/main/res/drawable-nodpi/alicia_mecha_header.png`。

状态：已完成。该素材用于替代简化几何图，让程序效果接近参考图的视觉冲击。

2. 基础组件

产物：`AliciaMechaBackdrop`、`AliciaMechaPanel`、`AliciaMechaQuickActionGrid`、`AliciaMechaQuotaBanner`、`AliciaMechaMetricCard`、`AliciaMechaTrendCard`。

状态：已完成第一版。后续根据截图继续调角标、阴影、间距。

3. 首页接入

产物：首页使用机甲头图、深色网格背景、装甲卡片、角色态快捷入口。

状态：已完成第一版。管理员态已截图校验，普通用户态已在代码层过滤账号管理入口。

4. 视觉校验

产物：`debug-mecha-home-v2-wait.png`。

状态：已完成一次设备截图。下一轮重点检查普通用户态、低高度屏幕和滚动到底部后的趋势/最近文件区域。

5. 精修迭代

待办：

- 普通用户账号登录后截图，确认三快捷入口布局不会留空。
- 调整底部栏选中状态，让管理员首页的“首页”和“账号管理”层级更清楚。
- 根据真实设备截图微调头图裁切，确保标题和头像不遮挡机体关键部位。
- 视情况把文件页、账号页也换成同一套装甲卡片语言。

## 验证命令

```powershell
.\gradlew.bat :app:assembleDebug
& 'F:\CCache\Android\Sdk\platform-tools\adb.exe' install -r app\build\outputs\apk\debug\app-debug.apk
& 'F:\CCache\Android\Sdk\platform-tools\adb.exe' shell screencap -p /sdcard/alicia_mecha_home.png
& 'F:\CCache\Android\Sdk\platform-tools\adb.exe' pull /sdcard/alicia_mecha_home.png debug-mecha-home.png
```
