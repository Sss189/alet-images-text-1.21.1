# 1.21.1 字体与像素画验证记录

验证日期：2026-10-09。交付版本：`0.1.0-alpha.1`。

## 环境

- Windows / Java 21
- Minecraft 1.21.1 / NeoForge 21.1.256
- LittleTiles 1.6.0-pre233 / CreativeCore 2.13.50
- 使用实际构建出的 JAR 启动独立测试客户端，未加载用户存档。

## 已通过

执行 `tools/build.ps1` 与 `tools/build.ps1 -Smoke -Packaged`。

- 编译及打包成功；15 项 JUnit 测试，0 失败、0 错误、0 跳过。
- 打包 JAR 与上述 LT、CreativeCore 一起加载成功，F9 按键注册成功。
- 通过实际工具界面的“生成 LT 蓝图”按钮完成图片与中文文字转换，后台处理与文件输出成功。
- 最近邻缩放、中文路径、透明像素、半透明 RGB 保留、颜色量化、矩形合并及输入限额检查通过。
- XY、XZ、ZY 三个方向的坐标和厚度检查通过。
- 生成的 SNBT 由原生 `LittleGroup.load` 读取成功；保存、重读及 `ItemLittleBlueprint` 数据组件往返检查通过。
- 图片工具、文字工具与预览界面生成截图；文字预览显示“小方块 LT”，像素清晰，布局无裁切。

日志中的完成标记：

```text
ALET_SMOKE_PASS image + Chinese text; async pipeline; XY/XZ/ZY geometry; alpha; native LT SNBT/data-component roundtrip; GUI/preview screenshots
```

证据文件在 [validation](validation/)：JUnit XML、转换与完成日志片段、三张界面截图。
文字工具截图捕获时任务正在保存，随后输出成功并进入预览，详见日志。

## 验证边界

本次没有进入世界，没有操作原生 LT 导入界面，也没有实测蓝图在世界中的放置。
这些仍需在用户整合包中确认；独立客户端验证证明 JAR 能加载、两个工具能生成原生 LT 可读取的蓝图。
本次也未覆盖多人服务器、其他 LT 版本、全部网络图片服务或全部第三方字体。

## 在整合包中验证

1. 重启游戏，进入世界后按 F9 或输入 `/alet`。
2. 先用默认宽度 96 导入一张小图片，再生成一行中文文字。
3. 点击“打开 LT 导入”，在原生界面粘贴、导入并取出蓝图，然后使用 LT 放置。

JAR、源码归档与 SHA256 在项目 `dist/` 中。交付时将同一 JAR 复制到用户 1.21 整合包的 `mods/`。
