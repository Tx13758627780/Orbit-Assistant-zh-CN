# 简体中文初版验证记录

日期：2026-10-23。

基线：上游 v0.8.4.1，提交 `32655b8cd9dc6149b4264b476c8db0d6aab9cff2`。

## 检查范围

`tools/check_localization.py` 检查中英文资源配对、格式参数、Java 引用和英文回退文字。新增 `ChineseLocalizationTest` 在 Android API 29 与 35 的 Robolectric 环境中检查中文界面标签、中文设置搜索、语言切换、真实附件菜单的文件选择操作，以及模型和样式标识的稳定性。

原有的一批结构测试直接匹配 Java 源码中的英文字符串。测试用的 `LocalizationSource` 仅剥离透明的 `OrbitText.get` 资源调用，保留控件类型、操作绑定和原断言；资源检查另行验证英文回退与默认资源一致。

## 验证结果

- 资源检查：2,619 对中英文字符串、3,541 处英文回退、所有 Java 引用和格式参数通过。
- 汉化及受影响的回归测试：523 项通过，0 失败。
- 全量复核：9,975 项测试，42 项跳过，5 项失败。其中 4 项已在未修改上游复现，详见下表。
- 另外 1 项是收藏库上限测试对同一毫秒保存顺序的假设不稳定。改为校验全部已接受条目的集合仍完整保留，未改收藏库业务代码。
- 最终汉化与收藏库复测：76 项通过，0 失败、0 跳过。
- 最终 Debug APK 编译成功，开发签名校验通过；APK 包含 `zh-CN` 资源，中文应用名称为「Orbit 助手」。

机器可读的结果见 [localization-validation.json](localization-validation.json)。全量测试不作“全部通过”声明；原版已有的 4 项失败仍保留。

## 已在未修改上游复现的失败

在独立、未修改的基线工作区中运行两个测试类，共 84 项测试，其中 4 项失败。修改后的完整测试结果须与下列基线失败逐项比较。

| 测试方法 | API 29、35 的原版结果 |
| --- | --- |
| `OpenRouterConnectTest.theCatalogKeepsChatModelsAndOnlyStructuredCapabilities` | 预期 `deprecated`，实际为 `unavailable`。 |
| `ComponentInstallHandoffTest.aVerifiedComponentPassesEveryCheckOrbitControls` | 预期分享组件失败提示，实际为没有可用的系统安装程序。 |

这两个方法各在两个 API 环境下失败，因此合计 4 项。它们在汉化前即存在，本次保留原始测试与原始业务行为。

## 实机范围

尚未进行 OnePlus 实机验证。默认助手设置、系统唤起入口、后台限制、语音引擎和通知权限需要结合具体机型、ColorOS/OxygenOS 与 Android 版本继续验证。

主应用的包名、版本号、上游固定签名验证、发行签名配置和更新渠道没有在本次汉化中重设。Debug APK 仅作为开发构建验证。
