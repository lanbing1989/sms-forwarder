```markdown
# 短信转发助手 (SmsForwarder)

轻量、开源的 Android 应用，用于将接收到的短信转发到企业微信/企业微信机器人（Webhook）。设计目标是：小、稳、兼容性好，支持在厂商定制系统（如小米澎湃OS/HyperOS）上长期驻留运行并尽量减少被系统杀死的概率。

仓库：https://github.com/lanbing1989/sms-forwarder

一般用户请直接下载构建好的APK 文件 https://github.com/lanbing1989/sms-forwarder/releases/tag/1.0

---

## 主要功能
- 接收短信并按关键字过滤后转发到指定企业微信 Webhook（支持多个关键字，逗号分隔）。
- 常驻前台服务展示状态与最新日志，保证在后台能够持续运行并提升生存率。
- 可选择开机自启。
- 运行日志保存在应用内部文件，便于无 adb 环境下查看运行状态。
- 兼容 Android 14+（targetSdk=34）以及澎湃OS/HyperOS 的前台服务要求（声明 foregroundServiceType 并在运行时处理）。

---

## 适用场景
- 企业/团队需要将收发到某台 Android 手机的短信内容转发到企业微信以便统一处理通知或归档。
- 需要在用户允许的前提下长期驻留并可靠转发短信的场景。

---

## 免责声明
- 应用会处理短信内容，涉及隐私数据。请仅在合规和经用户授权的场景下使用。
- 请在目标国家/地区遵守关于短信、数据隐私与第三方平台（企业微信等）的相关法律法规与服务条款。

---

## 安装与构建（开发者 / 高级用户）
1. 克隆仓库：
   ```bash
   git clone https://github.com/lanbing1989/sms-forwarder.git
   cd sms-forwarder
   ```

2. 本地构建（需要 JDK 17 / Gradle）：
   ```bash
   ./gradlew clean :app:assembleDebug
   ```
   或在 Android Studio 中打开项目并运行 `app` 模块。

3. 生成的 APK 位于：
   - `app/build/outputs/apk/debug/app-debug.apk`

4. 安装到设备（示例）：
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 权限与系统要求（必须/建议）
- Android 版本要求：minSdk 26，targetSdk 34（已针对 Android 14+ 进行适配）。
- 需要在 Manifest 中声明：
  - RECEIVE_SMS
  - INTERNET
  - FOREGROUND_SERVICE
  - RECEIVE_BOOT_COMPLETED
  - POST_NOTIFICATIONS（Android 13+）
  - 推荐/兼容性声明：FOREGROUND_SERVICE_REMOTE_MESSAGING（部分厂商 ROM 可能额外校验）
- 运行时需要用户授权：
  - RECEIVE_SMS（接收短信）
  - POST_NOTIFICATIONS（显示常驻通知，Android 13+）
- 说明：
  - 前台服务（Foreground Service）在 Android 14+ 要求在 manifest 中声明 `android:foregroundServiceType="remoteMessaging"` 并在运行时通过 `startForeground` 指定类型（代码中已做兼容处理）。
  - 在某些厂商（如澎湃OS/MIUI/HyperOS）上，还需在系统设置中允许“自启动 / 后台保活 / 省电豁免”，否则系统可能会拦截 BOOT 启动或在后台杀死进程。

---

## 如何使用（用户指南）
1. 打开应用，填写企业微信 Webhook（例如企业微信机器人的 webhook URL）。
2. 在“关键词”一栏填写需要匹配的关键词（逗号分隔），留空则转发全部短信。
3. 打开“启用转发服务”开关（首次开启会请求必要权限）。
4. 如果需要开机启动，开启“开机启动”并在系统设置中允许自启动/省电豁免。
5. 查看运行日志（最新在上）以确认转发是否成功。

---

## 澎湃OS（HyperOS）/厂商 ROM 注意事项
澎湃OS 与其他厂商定制系统相比，对后台与前台服务策略通常更严格。为保证在这些系统上稳定运行，请注意：

- Manifest 中声明前台服务类型：
  ```xml
  <service
      android:name=".SmsForegroundService"
      android:exported="false"
      android:foregroundServiceType="remoteMessaging" />
  ```
- 运行时调用 `startForeground` 时需要传入类型（针对 API>=34）。本项目在运行时采用了兼容性处理：优先尝试读取系统常量 `ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING`（通过反射以避免编译期依赖问题），若可用则调用带类型的重载；否则在运行时回退到不带 type 的调用并在日志中记录。
- 在澎湃OS 上，请务必在系统设置中为应用开启：
  - 通知权限与常驻通知
  - 自启动 / 后台保活 / 省电豁免
- 若仍出现问题，请抓取完整 logcat 并上传以便诊断（见下文“问题排查”）。

---

## 关键实现说明（供开发者参考）
- SmsForegroundService：负责创建通知通道、构建通知并启动前台服务。为兼容不同编译环境与厂商实现，使用了运行时反射读取 `ServiceInfo` 的常量值。
- SmsReceiver：通过 Telephony 的 `SMS_RECEIVED_ACTION` 接收短信，异步（goAsync + 单线程 executor）发送 HTTP 请求到 webhook，支持重试策略。
- BootReceiver：在设备开机后根据用户设置启动前台服务，使用 `ContextCompat.startForegroundService`（Android O+ 推荐用法）。

---

## 调试与常见问题排查

1. 构建失败
   - 常见原因：本地未同步改动到远程导致 CI 使用旧代码；或 Kotlin/AGP 与 compileSdk/targetSdk 不匹配。
   - 建议操作：
     ```bash
     ./gradlew clean :app:assembleDebug --stacktrace
     ```
     查看错误信息并修复。

2. 启动前台服务报错：MissingForegroundServiceTypeException
   - 原因：targetSdk >= 34 时，启动前台服务必须在 manifest 和运行时都声明前台服务类型。
   - 检查：
     - Manifest 中是否有 `android:foregroundServiceType="remoteMessaging"`。
     - Service.onStartCommand 是否在 API>=34 分支调用了带 type 的 startForeground（本项目使用反射进行兼容）。
   - 若仍报错，请抓取完整 logcat：
     ```bash
     adb logcat -v threadtime > all.log
     grep -E "MissingForegroundServiceTypeException|Starting FGS|SmsForegroundService" all.log
     ```
     把完整堆栈贴上来（不要只截图），我会继续协助诊断。

3. 在澎湃OS 上开机自启失败或服务被系统杀死
   - 检查系统设置是否允许“自启动 / 后台保活 / 省电豁免”。
   - 确认应用的通知已开启（常驻通知必须允许）。
   - 如果手动启动时正常但 BOOT 场景失败，说明厂商对 BOOT_COMPLETED 的广播或后台启动做了限制，需要用户在系统设置中手动允许自启动。

4. 转发失败（Webhook 无响应）
   - 检查配置的 webhook URL 是否正确且可访问。
   - 查看应用内日志（“运行日志”）或使用 adb logcat 查看网络请求异常。
   - 本项目对网络调用使用 OkHttp，并设有重试策略（最多两次，指数退避）。

---

## 日志（LogStore）
- 应用会将运行信息写入内部文件 `files/sms_forwarder_logs.txt`（最新日志在上）。
- 在 App 界面有“运行日志（最新在上）”用于查看；也可以通过 adb 拉取：
  ```bash
  adb shell run-as com.lanbing.smsforwarder cat files/sms_forwarder_logs.txt
  ```

---

## 贡献与开发
- 欢迎提交 issue 或 PR。提交前请确保：
  - 新增功能符合隐私与合规要求；
  - 与我沟通并列出复现步骤与日志；
  - 若是与厂商 ROM 兼容性相关，请给出尽量完整的 logcat 堆栈。

---

## 变更记录（简要）
- v1.0.0 — 初始实现：短信接收、转发、前台服务、开机自启、日志保存。
- v1.0.1 — 修复 Android 14+/HyperOS 兼容：在 manifest 声明 foregroundServiceType，并在运行时对 startForeground 做兼容性处理（使用反射读取 ServiceInfo 类型常量，回退逻辑及增强的异常处理）。

（发布时请根据实际提交调整版本号与说明）

---

## 许可证
- 本仓库默认 MIT 许可（如无 LICENSE 文件，请在发布前确认并添加所需许可证声明）。

---

## 联系方式
- 仓库 issues：https://github.com/lanbing1989/sms-forwarder/issues
- 如果在澎湃OS/HyperOS 设备上遇到兼容性问题，请在 issue 中附上：
  - 设备型号与系统版本
  - 完整的 adb logcat（包含异常堆栈）
  - 复现步骤

---

感谢使用 & 贡献！
``` 
