# qq-zone

QQ 空间相册下载项目，现阶段以 **Android App** 为主。

项目当前的主要目标，是让用户在手机端完成 QQ 登录、相册选择、导出目录选择和下载导出，不再以早期的 Windows 终端程序为核心使用方式。

## 当前定位

这已经不是一个单纯的 Go 命令行爬虫仓库，而是一个：

- Android 前端：`Kotlin + Jetpack Compose`
- Go 核心：负责 QQ 空间登录会话导入、相册列表读取、媒体下载
- Go/Android 桥接：通过 `gomobile bind` 生成 `AAR` 供 Android 调用

当前 App 主要流程为：

1. 在 App 内打开 QQ 官方网页登录页
2. 登录成功后导入网页 Cookie 会话
3. 拉取当前账号可访问的相册列表
4. 勾选要下载的相册
5. 选择导出文件夹
6. 先下载到应用工作目录，再导出到用户选择的目录

## 当前功能

- QQ 官方网页登录
- 拉取当前账号可访问相册
- 相册多选下载
- 自定义导出文件夹
- 下载进度显示
- 导出进度显示
- 生成 Android Debug APK

## 当前限制

- 目前以 Android 为主，不再优先维护桌面终端体验
- 目前只支持“当前登录账号自己可访问的相册”
- 私密相册、需要密码的相册不会出现在可下载列表中
- 下载阶段需要保持 App 存活；被系统杀掉后不保证续传
- 文件会先落到应用工作目录，再导出到目标目录，因此下载过程中会临时占用额外空间
- 当前默认生成的是 `Debug APK`，不是正式发布包

## 目录结构

```text
android/               Android Studio 工程
mobile/core/           Go 核心下载逻辑
mobile/bridge/         gomobile 对外桥接层
utils/qzone/           QQ 空间接口与登录相关底层逻辑
utils/net/http/        下载和请求封装
scripts/               构建 AAR / APK 的脚本
tools/                 构建工具依赖声明
```

## 开发环境

- Go `1.25+`
- Android Studio
- Android SDK `33`
- Android NDK `27.3.13750724`
- `gomobile`

如果本机还没有准备好 NDK，可以先运行：

```powershell
.\scripts\install_android_ndk.ps1
```

## 构建 Android APK

在仓库根目录执行：

```powershell
.\scripts\build_android_debug.ps1
```

构建完成后，产物位于：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

如果只想重新生成 Go Bridge 的 AAR，可以执行：

```powershell
.\scripts\bind_android_bridge.ps1
```

生成结果位于：

```text
android/app/libs/qqzone-mobile.aar
```

## 在模拟器或手机上运行

### 方式一：Android Studio

1. 用 Android Studio 打开 `android/`
2. 等待 Gradle 同步完成
3. 选择模拟器或真机
4. 点击运行，或直接 `Build > Build APK(s)`

### 方式二：命令行

先构建 APK：

```powershell
.\scripts\build_android_debug.ps1
```

再安装到设备：

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r ".\android\app\build\outputs\apk\debug\app-debug.apk"
```

## 项目分层说明

### `android/app`

负责界面、WebView 登录、相册选择、目录选择和导出流程。

关键文件：

- `android/app/src/main/java/com/qzone/android/MainActivity.kt`
- `android/app/src/main/java/com/qzone/android/MainViewModel.kt`
- `android/app/src/main/java/com/qzone/android/QzoneWebLogin.kt`
- `android/app/src/main/java/com/qzone/android/StorageExport.kt`

### `mobile/core`

负责登录会话导入、相册拉取、任务下载、任务状态查询。

关键文件：

- `mobile/core/client.go`
- `mobile/core/albums.go`
- `mobile/core/download.go`

### `mobile/bridge`

负责把 Go 能力暴露给 Android 调用。

关键文件：

- `mobile/bridge/bridge.go`

## 当前推荐的维护方向

如果你现在只把这个仓库当作 Android 项目维护，建议把后续开发重点放在：

- Web 登录稳定性
- Cookie 会话导入
- 相册选择体验
- 下载任务状态
- 文件导出和失败重试

而不是继续扩展旧的终端交互入口。

## 如果你不再需要终端版程序

如果你已经决定以后只保留 Android 版本，下面这些文件可以优先考虑清理：

### 可以直接删除的旧终端入口 / Windows 打包遗留

- `main.go`
- `app/controllers/BaseController.go`
- `app/controllers/QzoneController.go`
- `app.ico`
- `app.syso`
- `ico.manifest`
- `qq-zone.exe`
- `运行截图.png`

### 可以继续清理的旧辅助代码

这部分不是 Android 当前主流程所需，但删除前最好顺手做一次 `go mod tidy`：

- `utils/logger/logger.go`
- `utils/office/office.go`

删除这部分后，通常还可以进一步从 `go.mod` 中清掉一些仅旧逻辑使用的依赖，例如：

- `github.com/Unknwon/goconfig`
- `github.com/360EntSecGroup-Skylar/excelize/v2`

## 建议的清理顺序

1. 先删除旧 CLI 入口和 Windows 资源文件
2. 执行一次 `go build ./mobile/... ./utils/...`
3. 执行 `.\scripts\build_android_debug.ps1`
4. 确认 Android 正常构建后，再删除 `utils/logger` / `utils/office`
5. 最后执行 `go mod tidy`

## 说明

仓库里仍然保留了一些历史结构，是为了让 Android 改造过程能渐进完成，而不是一次性大拆。

如果你准备彻底转成“纯 Android + Go Core”项目，下一步最合适的动作通常是：

- 去掉旧 CLI 主入口
- 只保留 `android/ + mobile/ + utils/` 中 Android 实际使用到的部分
- 重新整理一次目录和依赖

