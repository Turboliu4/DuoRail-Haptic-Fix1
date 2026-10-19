# DuoRail Haptic Fix 0.2.0（测试版）

适配目标：用户上传的 **MiDuo 0.78**、Root + KernelSU + LSPosed。此项目是独立 LSPosed APK 源码，不是 KernelSU ZIP 模块，也不会修改 MiDuo 或系统桌面的原始 APK。

**重要说明：这是尚未经过手机实测的修复版本，无法保证一定恢复震动或保证零重启风险。**

## 修复范围

- 应用界面的 DuoRail：直接监听 MiDuo 0.78 中真正存在的 `Entry$NavView$$ExternalSyntheticLambda1.onTouch(View, MotionEvent)`（导航触摸）与 `Entry$$ExternalSyntheticLambda76/107/108.onClick(View)`（侧栏按钮 / 最近任务），并兼顾 `buildModePanels` 的 16/17 两个点击监听器。
- 桌面界面的官方 Dock 图标：额外对 `com.miui.home` 安装受限点击监听。只有点击 View 的 tag（或近层父 View 的 tag）属于小米桌面类，且 `ItemInfo.container == -102` 时才请求震动。这个容器值取自该设备的 `DuoOfficialDock OUTER_LAUNCH ... container=-102` 日志，**不是所有小米机型通用的 Dock ID**。
- 避免重复震动：全进程 120ms 最小间隔。

## 0.1.0 → 0.2.0 的变化

1. **SystemUI 删除**全局 `View.performClick()` Hook 与 `android.util.Log.w()` Hook，改成在 `setOnClickListener()` / `setOnTouchListener()` 中发现准确的 MiDuo 专属监听器类，然后只 Hook 该类 `onClick` / `onTouch` 方法。
2. 增加 `com.miui.home`：尽量直接 Hook `Launcher.onClick(View)`；同时在桌面进程安装 `View.performClick()` 的兼容处理，只有确认 `container == -102` 时才触发触觉反馈。这项兼容 Hook **仍属于 Android View 全局方法 Hook，但只运行于 `com.miui.home` 进程，且带严格容器过滤**。
3. 点击结果不被修改；反射失败时直接跳过，不会“强制恢复”图标点击。
4. Version code 2，版本名 `0.2.0-test`，包名与 0.1.0 相同：`io.github.duorail.haptic`。

## 风险与前置条件

- 此补丁需要 Hook 核心的 `com.android.systemui`，存在系统界面重启或卡顿可能；新增了 `com.miui.home`，如果出现异常也可能影响桌面启动。**不能承诺绝对不会死机或卡开机**。
- 安装前备份资料；确认 KernelSU / LSPosed 的恢复方案已经掌握；不应在不能接受重启风险的场景下测试。
- 如果旧版 0.1.0 仍在使用，**先在 LSPosed 禁用旧版本并重启**。本项目包名与旧版相同，GitHub Actions 每次生成的调试签名可能不同，升级安装可能提示签名不一致：备份设置后卸载旧补丁，再安装新版。不要卸载 MiDuo 本体。
- 新版启用时只勾选 **系统界面 (`com.android.systemui`)** 和 **系统桌面 (`com.miui.home`)** 两个作用域。不要勾选 `system`、其他应用、全部作用域。
- 首次测试推荐先测试应用内 Dock 的 Home/Back/Recents 是否仍正常，然后测试桌面 Dock **应用图标**，再测试普通桌面图标是否维持原有触感行为。

## 构建 APK：GitHub Actions（Mac 无需 Android SDK）

1. 解压源码压缩包，把压缩包中的项目 **内容** 放入你的仓库根目录，注意保留隐藏的 `.github/workflows/build-apk.yml`；仅上传 ZIP 文件或上传一个额外外层文件夹都无法自动构建。
2. 将旧版工程中的 `app/`、`xposed-stubs/`、`README.md`、`.github/workflows/build-apk.yml` 一并替换，提交到默认分支。Mac Finder 按 `Command + Shift + .` 显示隐藏文件。
3. 如果网页上传总是漏掉 `.github`，可用 GitHub **Add file → Create new file**，文件名输入 `.github/workflows/build-apk.yml`，粘贴压缩包同名文件的内容并 Commit。
4. 在仓库的 **Actions → Build DuoRail Haptic APK → Run workflow** 运行；或者更新文件后等 push 工作流触发。
5. 构建成功（绿色勾）后，在运行页面 **Artifacts** 下载 `DuoRail-Haptic-Fix-0.2.0-APK`，解压安装其中的 `app-debug.apk`。
6. 禁用旧版 LSPosed 模块 → 安装新版 → 作用域勾选 SystemUI + 系统桌面 → 重启 → 测试。

> 目前只进行了 Java 语法编译检查（使用测试用 Android API 占位类），**未进行 Android SDK/Gradle 的正式 APK 构建，也没有经过实机验证**。如果 Actions 编译失败请提供失败步骤和日志。

## 验证日志

在 Mac 终端执行：

```bash
adb logcat -c
adb logcat -v time -s DuoRailHapticFix:I '*:S'
```

然后分别点击 **应用内 Dock 按钮**和**桌面 Dock 应用图标**，观察 `Haptic requested: MiDuo-NavView / MiDuo-click / Launcher.onClick / Dock.performClick`。

若无任何记录，可能是系统桌面图标走了不同的回调，或其 tag 不符合 `container=-102`。此时需要提供 `com.miui.home` 桌面 APK 或运行时 View/tag 信息，不能仅凭当前 MiDuo APK 保证适配。

## 卸载/救援

能进入系统时，直接在 LSPosed **禁用 DuoRail Haptic Fix**，重启；不要删除或清除系统桌面数据。严重异常时，参考当前 KernelSU 版本对应的安全模式/模块救援说明操作，恢复方法可能随版本、加载器而变化。

## 版本与技术详情

- `app/src/main/java/io/github/duorail/haptic/DuoRailHook.java`：所有 Hook 和触感逻辑。
- `app/src/main/assets/xposed_init`：Xposed 入口。
- `xposed-stubs/`：仅用于编译，**不应被打包到最终 APK**。
- `.github/workflows/build-apk.yml`：GitHub Actions 自动编译配置。

源码用于个人实验，未经 MiDuo 作者背书。请不要将“Java 编译成功”当作“实机安全或功能已验证”。
