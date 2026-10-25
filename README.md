# ALET Images & Text - Minecraft 1.21.1

当前版本：**0.1.0-alpha.1**（alpha）。

**[下载可安装 JAR](https://github.com/Sss189/alet-images-text-1.21.1/releases/tag/v0.1.0-alpha.1)** | **[全部版本](https://github.com/Sss189/alet-images-text-1.21.1/releases)** | **[简要使用说明 PDF](docs/quick-start.pdf)**

F9 或 /alet：图片转像素画、文字转小方块。生成蓝图后打开 LT 原生导入，粘贴并导入，取出蓝图放置。

将 Release 中的 JAR 放入客户端 `mods` 并重启游戏。兼容基准：Minecraft 1.21.1、NeoForge 21.1.256、Java 21、LT pre233、CreativeCore 2.13.50。

源码构建：设置 Java 21 的 `JAVA_HOME`，把 `libs/README.md` 中的外部依赖放入 `libs`，执行 `gradlew.bat build`（Windows）或 `./gradlew build`。首次构建需要联网下载 Gradle/Maven 依赖。

构建验证与游戏内验收范围见 `docs/VALIDATION.md`。旧记录中的 `LT_WORKSPACE` / `GAME_DIR` 是本地路径占位；测试用第三方材质包、光影及游戏模型不包含在仓库中。

---

# ALET 字体与像素画 — Minecraft 1.21.1

按用户选定的范围，移植原 A Little Extra Tiles 的 **Photo Importer（图片转像素画）**
与 **Typewriter（字体转小方块）**。这是非官方适配，不是原版 ALET 全功能版本。

目标：Minecraft 1.21.1 / NeoForge 21.1.256 / LittleTiles 1.6.0-pre233 /
CreativeCore 2.13.50 / Java 21。

## 安装与使用

将 `dist/alet-images-text-neoforge-1.21.1-0.1.0-alpha.1.jar` 放进客户端 `mods`，
与当前 LT、CreativeCore 一起使用，然后重启游戏。

进入世界后按 **F9**，或输入 **`/alet`**。按键可在控制设置中修改。
两个功能在同一个界面的两个标签页里；本版本不添加原版的打印机、打字机实体或道具。

### 图片转像素画

1. 输入图片的本地完整路径或 HTTP(S) 图片直链，也可以把图片文件拖进界面。
2. 默认宽度 96 像素，高度 0（根据原图比例自动计算）。宽、高都为 0 时保留原图尺寸。
3. 选择当前 LT 允许的网格。每个像素对应一格小方块；例如 96 像素配 32 网格，宽度为 3 个大方块。
4. 点击 **生成 LT 蓝图**。完成后可以点击 **预览**，查看实际采样后的颜色和大小。
5. 点击 **打开 LT 导入**；蓝图会自动复制到剪贴板。在 LT 导入界面点击 **粘贴**、**导入**，取出蓝图。
6. 使用 LT 蓝图的正常放置方式，把像素画放到世界中。

支持 PNG、JPEG、BMP 和 GIF 首帧，支持中文文件路径。网址必须直接返回图片。
按原版规则，全透明像素留空；“可见像素不透明”仅把非零透明度的像素改为不透明。
默认最近邻缩放，不混合相邻像素颜色。可以水平镜像。

### 字体转小方块

1. 切换到 **字体转小方块** 标签页，输入文字。输入字面值 `\n` 可以换行。
2. 点击 **选择字体**，搜索本机已安装的字体。也可以填写字体名称，或拖入 TTF/OTF 字体文件。
3. 设置字号（像素）、颜色和常规／粗体／斜体／粗斜体。
4. 点击 **生成 LT 蓝图**，随后按图片转换的同样步骤导入和放置。

颜色使用六位 `RRGGBB`，例如 `FF8800`；八位 `AARRGGBB` 可以设置透明度。
选择的字体需包含所输入文字的字形，中文建议用微软雅黑或其他中文字体。

### 更多设置

- XY 墙面、XZ 地面、ZY 侧墙三个方向；放置后仍能使用 LT 自带旋转工具。
- 厚度按小格计，默认 1。
- 颜色步长为 1 时保留原色；增加到 16 等数值可减少照片的颜色种类及蓝图大小。
- 文字旋转与抗锯齿。默认关闭抗锯齿，便于得到清晰像素字。

结果保存在游戏目录的 `alet/prints/image-*/` 或 `alet/prints/text-*/`，包含
`model.struct`（LT 蓝图 SNBT）与 `preview.png`（转换结果预览）。不会覆盖已有输出。
“复制蓝图”可再次复制最近结果；“打开输出”打开结果所在文件夹。

## 性能与限制

文件读取、字体栅格化、像素合并、蓝图编码和写文件均在后台执行，可取消。
采用按行矩形合并和按颜色分组，避免在游戏线程逐像素做 LT 合并。
网络请求设置 10 秒连接／读取超时；取消网络任务可能需等当前读取返回。

- 原图文件最多 32 MiB、最多 1600 万像素；输出最长边 1024 像素，总面积最多 65536 像素。
- 最多 4096 种可见 ARGB 颜色。超出时应增加颜色步长或缩小图片。
- 输出蓝图 SNBT 最多 4 MiB。字号 4–256，文字最多 2048 字符。
- 界面参数在本次游戏会话里保留，关闭游戏后恢复默认。
- 大型蓝图的原生 LT 导入预览和放置仍由 LT 处理，建议先使用默认尺寸测试。

这是客户端工具，转换完成后通过 LT 原生 `/lt-import` 导入，不额外注册服务器网络包。
多人游戏仍需服务器有相容的 LT 与 CreativeCore，且允许玩家使用 LT 导入功能。

## 开发

```powershell
.\tools\build.ps1
.\tools\build.ps1 -Smoke -Packaged
```

使用父目录 `.gradle-user` 的开发缓存。其他机器应修改 `gradle.properties` 中的 Java 21 路径；
首次下载依赖时可去掉脚本中的 `--offline`。

构建产物在 `build/libs/`，交付包在 `dist/`。
开发依赖 JAR 放在 `libs/`，不包含在交付 JAR 中。
`-Smoke -Packaged` 加载实际打包 JAR，在独立客户端主菜单执行验证，生成界面截图后退出。
验证不会加载用户存档，记录见 `docs/VALIDATION.md`。

来源和许可证见 `THIRD-PARTY-NOTICES.md` 与 `LICENSE`。
`upstream/` 保留原始源码与现代 LT/CreativeCore 接口参考，不参与编译。
