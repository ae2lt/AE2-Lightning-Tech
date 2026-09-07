# 苍穹织雷物品贴图

九张 32×32 RGBA 静态贴图，已接入 resources 的原物品资源路径。四件盔甲采用原版可辨认轮廓、灰蓝半透明底色、银白雷纹与少量紫色节点。五种灵核沿用深蓝框体、银白边框，以眼、心、分支、双阶和熄灭纹样区分。

使用内置 ImageGen 逐张生成；完整提示词与来源记录见 selected.json，选用原图位于 source/。prepare_textures.py 仅裁剪、保持比例并用最近邻采样缩至 32×32，保留原 alpha。16×16 试样会丢失眼纹和分叉雷纹，因此使用 32×32。textures/ 为实际运行贴图，index.html 可对照深浅底色。

验证：assemble 和 compileJdbJava 成功；构建 JAR 的九张 PNG 与选用贴图一致，无旧动画 mcmeta，无开发预览类。items-ingame.png 来自运行中 Minecraft 的 GuiGraphics.renderItem，显示九个实际注册物品模型。开发预览仅放在 src/jdb，不进入发行包。

本轮调整物品图标；穿戴状态的雷电场渲染沿用已有实现。
