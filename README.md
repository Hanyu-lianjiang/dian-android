# 電 Android 应用

版本 `1.0.1`，版本代码 `18`。Kotlin、Jetpack Compose、MVVM、Gradle、Android Jetpack；登录、总览、导入、详情、设置和小组件配置使用官方 Miuix `0.5.1` 组件。最低 Android 8（API 26），编译和目标 SDK 35。

## 使用

使用本系统账号登录，在“添加宿舍”依次选择校区、楼栋、宿舍并确认。管理员与普通用户的日常列表都只显示本人添加的宿舍；全局管理通过设置中的管理后台入口访问。只有管理员可以更新学校授权 JSON。

注册进入独立页面，要求确认密码。个人低电量阈值在设置中保存。会话由 Android Keystore 加密保存，应用与桌面小组件共用；失效后重新登录。学校授权保留在服务端，不保存到客户端。

小组件只从本人已添加的宿舍中选择，点击在应用内打开详情。设置页保留一个更换宿舍入口；存在多个小组件时先选择目标组件。状态标签使用与卡片协调的柔和色底，正常跟随主题、低电量使用琥珀色、异常使用红色；配色变化即时更新已有小组件，并保留当前状态。系统省电策略可能延迟调度，以组件显示的最近成功查询时间为准。

趋势图提供真实采样点与中国标准时间；“5分钟”档查看最近一小时的五分钟采样。单条历史也可显示，不要求至少两个采样。总览隐藏电表总用电量、历史采样数量，详情中保留。

“主题与外观”支持跟随系统、浅色、深色，Android 12 起可使用壁纸动态配色；另提供五组柔和配色及自选 HEX 颜色。可调整纯黑背景、悬浮底栏、液态玻璃、添加入口、紧凑卡片和趋势缩略图。设置即时保存到本机，小组件同步使用所选配色。应用图标采用插头与负形闪电组合标志，并提供系统主题图标资源。菜单页头显示对应图标与名称；关于页展示应用标志、名称、产品口号与版本。

## 导航与反馈

设置切页不发起请求；进入提醒设置才读取个人阈值。添加页保留选择进度，选项按路径缓存五分钟，重复请求合并；服务端在实际导入时重新检查权限与选项。请求失败在对应区域重试。玻璃导航在 Android 12 起复用 Compose 图层做背景模糊，较旧系统使用半透明高光；可关闭效果。滚动列表保留底部安全间距，确认操作单独占位。

关于页从服务端 GET /api/client-config 读取 qqGroup，点击已配置卡片复制群号，空值显示暂未设置。管理后台的客户端设置维护该字段；本客户端仓库不包含服务端实现。内部版本代码保持递增，以便沿用签名覆盖旧安装。

## 正式签名构建

需要 JDK 21、Android SDK 35 与可用的 build-tools。Gradle Wrapper 和 `app/gradle.lockfile` 固定构建依赖。设置本机环境变量，密码只通过当前进程环境提供：

- `JAVA_HOME`：JDK 21 目录
- `ANDROID_HOME`：SDK 目录
- `DORM_RELEASE_KEYSTORE`：正式签名文件路径
- `DORM_RELEASE_KEY_ALIAS`：签名别名
- `DORM_RELEASE_STORE_PASSWORD`、`DORM_RELEASE_KEY_PASSWORD`：签名密码

Windows 在本目录执行：

```powershell
./build-release.ps1
```

脚本在新的英文临时目录构建，执行 `assembleRelease`、`testReleaseUnitTest` 和 `lintRelease`，校验签名后复制正式 APK 至 `app/build/outputs/apk/release/dorm-electric-1.0.1.apk`，检查报告至 `app/build/verification/`。临时目录保留供检查构建诊断。不会生成用于安装的调试 APK。

在英文路径也可直接运行：

```powershell
./gradlew.bat :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease
```

首次需联网下载依赖；常规构建不使用 `--write-locks`。有意升级依赖后才更新锁文件，并复核差异。不要将签名文件、密码或 `local.properties` 提交到仓库。此版本使用新正式签名，后续覆盖安装需沿用同一签名。

自动化覆盖历史缓存、时间范围和趋势数据处理。编译、Lint 与单元测试不能替代真机页面、小组件调度和点击行为验收；交付报告会分别标明。

## 隔离设备检查

使用 `./build-release.ps1 -IncludeDeviceChecks` 额外生成独立的正式签名 instrumentation APK，位于 `app/build/verification/visual-verification.apk`；它不包含在交付应用中。仅在隔离模拟器上安装正式 APK 和该检查 APK，再运行：

```powershell
python ./verify-device.py --adb <ADB路径> --serial <模拟器序列号> --output ./dist/1.0.1
```

该脚本使用本地虚拟数据检查界面，输出截图与 `device-verification.json`。不提供生产会话，不调用真实宿舍接口；不要在日常使用的手机上运行此检查。真实上游连接、厂商系统适配和桌面小组件的系统调度仍需独立验收。

品牌界面专项检查使用相同模拟器和检查 APK：

```powershell
python ./verify-device.py --adb <ADB路径> --serial <模拟器序列号> --output ./dist/brand-1.0.1 --focus brand
```

专项检查覆盖菜单页头、关于页、品牌图标、更新与群号复制，以及 320dp 窄屏大字体和横屏滚动；输出 `brand-verification.json`、界面截图和系统绘制的图标资源预览。

使用 `--focus widget` 检查 132 组原生小组件渲染：全部预设和壁纸配色、自定义极端颜色、深浅色与纯黑背景、正常及告警状态，并验证主题重着色保留文字与状态。输出实际渲染图片和 `widget-verification/checks.json`。

追加 `--focus import-update` 可检查确认添加、小屏大字体、贴边底栏与更新状态。最后一步会对公开更新源执行只读 GET。

## 更新发布

应用启动时自动查询公开仓库 `Hanyu-lianjiang/dian-android` 的最新正式 GitHub Release；关于页也可手动检查。发现较新版本后显示提示，APK 在应用内下载，校验 SHA-256、应用包名、签名及内部版本码后调用 Android 系统安装器。首次安装更新时，系统可能要求允许此应用安装未知来源应用，最终安装仍须由用户确认。发布包附带 `update.json`，用于支持版本名不变时的内部版本码升级。

已安装版本代码 14 的旧版更新器只能通过旧下载流程完成首次升级；升级到版本代码 15 后，后续更新可在应用内下载并安装。

GitHub 仓库和 Releases 只用于客户端分发；学校授权与个人数据由现有服务端管理。签名文件及访问凭据不进入公开仓库。
