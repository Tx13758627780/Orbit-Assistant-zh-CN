# Orbit Assistant 简体中文版

面向 Android 的社区简体中文版，包含本地 AI 修复和 OnePlus 底部悬浮输入条。基于原项目稳定版 **v0.8.4.1**，当前发布版本为 **OnePlus Dock fix2**，主程序与本地组件的安装版本号均为 **819**。

本仓库由 **Tx13758627780** 独立维护，保留原项目的 MPL-2.0 许可证。它是社区修改版，不是原作者或 OnePlus 的官方发行版。

**[下载正式发行版](https://github.com/Tx13758627780/Orbit-Assistant-zh-CN/releases/latest)** · **[仓库内 APK](releases/v0.8.4.1-zh-CN-OnePlus-Dock-fix2/Orbit-Assistant-v0.8.4.1-zh-CN-OnePlus-Dock-fix2.apk)** · **[本次更新说明](releases/v0.8.4.1-zh-CN-OnePlus-Dock-fix2/RELEASE_NOTES.zh-CN.md)**

## 功能与汉化

- 将 Orbit 设为系统默认数字助理，通过系统手势或设备支持的按键入口唤起；也可使用完整聊天界面。
- 支持文字、语音、图片、文件、剪贴板、文档及经你选择的屏幕内容。
- 保留模型切换、历史、新建聊天、设备操作、自动流程、快捷面板、收藏库、主题与小组件。
- 提供简体中文界面资源、中文设置搜索及搜索别名，包含 **2619 对中英文字符串**。英文仍作为回退语言；模型回复和服务端信息由相应模型或服务决定。
- 保留模型 ID、文件名、资源 key、包名、网络协议与校验常量，不对用户内容执行全局替换。
- 支持可选的 Orbit 本地 AI 组件。组件与模型分别安装，模型不会随主程序一起捆绑。

## OnePlus Dock fix2 的变化

- 默认“现代”样式改为底部圆角输入条，保留加号、输入框、麦克风和语音对话按钮；可用时在上方显示“使用屏幕”胶囊。桌面其余区域透明。
- 开始对话后向上平滑展开，内容较短时按内容取高，较长时滚动显示；支持下滑收起、点击或上滑把手展开。收起保留草稿、聊天和正在生成的回复。
- 加号菜单保留附件、模型、历史、新建、屏幕、区域选择及关闭入口；展开后也可使用标题栏快捷按钮。
- 处理键盘、系统栏、刘海及全面屏手势区域；适配横屏、较大字体、系统深浅色和自定义主题。经典、Float 等原有样式继续保留。
- 继承 fix1 的本地 AI 修复：主程序内置同签名组件，保留证书及哈希校验；检查失败显示原因与重试入口，避免无限“正在检查”。

目标设备为 **OnePlus 13s / 骁龙 8 至尊版 / 16 GB 内存 / OxygenOS 15.0 / Android 15**。已完成自动化测试及 Android 15 原生视图渲染检查，**尚未完成实体 OnePlus 13s 验证**，实际系统手势、麦克风、朗读和本地模型推理仍需真机确认。

## 下载与安装

1. 打开发行版页面，在附件中下载 `Orbit-Assistant-v0.8.4.1-zh-CN-OnePlus-Dock-fix2.apk`；也可从上方仓库内 APK 链接下载原始文件。
2. 使用下载该文件的浏览器或文件管理器打开 APK，按 Android 提示授予该来源的安装权限并安装。
3. 按应用引导将 Orbit 设为默认数字助理。OxygenOS 的入口名称可能随系统语言或维护版本变化，可在系统设置中搜索“数字助理”或“默认应用”。
4. 在设置的外观选项中选择“现代”悬浮样式，即可使用底部输入条。Android 13 及以上系统可在应用语言设置中选择简体中文；较旧系统跟随系统语言。
5. 语音、屏幕和设备操作按实际使用需求授予对应权限。

### 从 fix1 覆盖升级

- 本 APK 与之前交付的汉化 **fix1 使用同一签名**，安装版本号从 818 增加到 819，直接选择更新即可。**不要先卸载主程序或本地组件。**
- 本地 AI 页面若提示更新组件，点击“更新组件”，安装主程序内置的同签名配套组件；覆盖更新保留既有应用数据及已安装完整的模型。
- 上游官方版、旧调试签名汉化版或其他人重新签名的版本不能保证覆盖安装。遇到签名冲突时先核对版本和来源、导出重要数据，勿为排错直接卸载。
- 本包保留原有更新逻辑；应用内上游更新提示不代表可覆盖本社区签名。后续社区更新请以本仓库发行版为准。

### 启用本地 AI

在设置中进入 Orbit 本地 AI 页面，按提示安装或更新内置组件，再分别下载聊天模型与操作模型。组件约 35 MB，不包含模型；Qwen 聊天模型约 1.60 GB，操作模型约 0.55 GB，下载与校验需额外空闲空间，并需要能连接模型下载站点。模型完整就绪后可离线使用。

请使用本主程序提供的配套组件；官方签名组件与本社区签名主程序不能配对。历史问题的根因及修复见 [本地 AI 修复记录](docs/LOCAL_AI_FIX.zh-CN.md)，当前安装步骤以上文及本次发行说明为准。

### 校验安装包

本次 APK 的 SHA-256：

```text
7fc7f1bdfd5a0b624f1dd90749dfd166b2d431790c33ba3bab07ee918f6eadee
```

公开签名证书 SHA-256：

```text
E5E6F7745D4E088EE36511F762491E7BAB7EA887D4DFD4A41FF993EE06CA4F0F
```

校验文件位于 [SHA256SUMS](releases/v0.8.4.1-zh-CN-OnePlus-Dock-fix2/SHA256SUMS)。仓库内 APK 与发行附件必须使用相同字节，不重新签名、不重新打包。

## 验证范围

- fix2 构建时通过主程序 **998 项**、组件 **122 项**相关测试，共 **1120 项**，零失败、零错误；不是完整项目测试套件。
- 检查覆盖浮层布局与动画状态、菜单、草稿和请求保留、语音按钮、键盘和手势区域、横屏、较大字体及深浅色。
- Release 编译与发布 Lint、主程序和组件证书一致性、内置组件哈希与版本检查通过；实际比对 fix1 和 fix2 的签名与安装版本号。
- 汉化资源配对、格式参数及引用检查通过。

详见 [界面改动与验证说明](docs/ONEPLUS_DOCK_FIX2.zh-CN.md) 和 [验证数据](docs/ONEPLUS_DOCK_VALIDATION.json)。本仓库发布流程只校验并分发已经构建签名的 fix2 APK，不会接触或生成私钥。

## 从源码构建

需要 Git、JDK 17、Android SDK 36 和 Build Tools 35.0.0。使用仓库内 Gradle Wrapper：

```bash
git clone https://github.com/Tx13758627780/Orbit-Assistant-zh-CN.git
cd Orbit-Assistant-zh-CN
python3 tools/check_localization.py
./gradlew :app:assembleDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/`，使用开发环境的调试签名，不能作为本发行包的覆盖更新。

同签名 Release 构建需要维护者在本机恢复已有私有签名备份，并在被忽略的 `orbit-signing.properties` 中提供签名参数，再执行：

```bash
./gradlew -PorbitBundledLocal=true \
  -PorbitLocalizedCertificateSha256=E5E6F7745D4E088EE36511F762491E7BAB7EA887D4DFD4A41FF993EE06CA4F0F \
  :app:assembleRelease
```

此方式先构建同签名本地组件，再将组件与校验元数据放入主 APK。公开源码不含私钥、密码、账号令牌或本机 SDK 配置。更多结构与构建说明见 [构建文档](docs/BUILDING.md) 和 [签名修复记录](docs/LOCAL_AI_FIX.zh-CN.md)。

## 目录与反馈

| 目录 | 内容 |
| --- | --- |
| `app/` | 主程序与悬浮助手 |
| `local/` | 本地 AI 组件 |
| `ipc/` | 主程序和组件间的 AIDL 协议 |
| `docs/` | 技术说明及验证记录，保留上游文档 |
| `releases/` | 已签名安装包、中文发行说明与校验值 |
| `tools/` | 构建、检查与校验工具 |

问题请在[本仓库 Issues](https://github.com/Tx13758627780/Orbit-Assistant-zh-CN/issues)中反馈，注明机型、系统版本、安装包来源与复现步骤。提交日志或截图前移除个人内容与凭证。

## 开源许可证

项目沿用原项目的 [Mozilla Public License 2.0（MPL-2.0）](LICENSE)。`LICENSE` 原文完整保留，源码中的原有版权及许可证声明继续保留。第三方组件与模型分别遵守其自身许可证。

## 参考来源与致谢

- 原项目：[lpnovi/Orbit-Assistant](https://github.com/lpnovi/Orbit-Assistant)。原作者：**[lpnovi](https://github.com/lpnovi)**。感谢原作者及贡献者提供 Orbit Assistant 的应用、组件、文档和测试基础。
- 上游基线：稳定版 **v0.8.4.1**，提交 `32655b8cd9dc6149b4264b476c8db0d6aab9cff2`。
- 本次完整源码继承已交付的 fix2，来源提交 `c7a69ef7caad0056f36735141806394028088d36`；由 **Tx13758627780** 账号发布和维护简体中文、本地 AI 修复与 OnePlus Dock 改动。
- 界面交互参考 Gemini 的紧凑底部唤起方式；本项目与 Google、OnePlus、OpenAI 及相关服务提供方均无官方隶属或背书关系。
