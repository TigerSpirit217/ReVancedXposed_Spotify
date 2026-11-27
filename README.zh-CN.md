<div align="center">
  <h1>ReVanced Xposed Spotify</h1>
  <br>
</div>

[English](README.md)

**由 ChsBuffer 开发的 ReVanced LSPosed 模块，仅适用于 Spotify。**

> [!IMPORTANT]
> -   本项目为**非官方 ReVanced 项目**。请勿向 ReVanced 开发者寻求支持。
> -   使用本模块**必须拥有 Root 权限**。

## 补丁

### Spotify

以下按源码中的实现列出，实际效果可能因 Spotify 版本而异。标注了默认状态的功能可在模块设置中开关。

打开 Spotify 侧栏，点击 **ReVanced Xposed Settings**。修改设置后，点击右上角的 **Restart Spotify**。

-   **Premium 播放调整（默认开启）**。涵盖按需点播、播放顺序、随机播放、智能随机播放和添加到播放队列。此补丁还调整 Premium 界面，包含 Google Assistant 播放和旧版 Car Thing 的兼容性调整，并过滤首页和浏览页的广告区块及应用内推广弹窗。
-   **广告拦截（默认开启）**。尝试关闭广告并隐藏视频广告倒计时。
-   **分享链接净化**。复制链接或使用 Android 系统分享菜单时，移除 `si` 和 `utm_source` 追踪参数。
-   **第三方歌词源（默认关闭）**。Better Lyrics 和网易云音乐可为 Spotify 原生歌词界面补齐缺失歌词。两个来源同时开启时优先查询 Better Lyrics。
-   **登录会话保留**。尝试避免自动退出登录。
-   **第三方桌面小组件兼容**。允许第三方 Android 启动器绑定 Spotify 小组件。
-   **TheWinner02 开发的 Monet 主题（默认关闭）**。使用 Android 根据壁纸生成的配色调整 Spotify 的背景色和强调色。
-   **TheWinner02 开发的 RoundyUI（默认关闭）**。为卡片、封面、按钮和底部弹层等元素添加圆角。
-   **原生 HTTP 过滤（实验性，尚未接入加载流程）**。实现了广告和追踪请求过滤。

## 下载

-   [下载发布版](https://github.com/TigerSpirit217/ReVancedXposed_Spotify/releases)

> [!NOTE]
> 发布版的包名和签名每天都会变化。已安装的用户无需每天重新安装。

## ⭐ 致谢

-   高性能 DEX 运行时解析库 [DexKit](https://luckypray.org/DexKit/en/)
-   延续 Vanced 项目的 [ReVanced](https://revanced.app)
-   最初开发 ReVanced Xposed（现名 NexAlloy）的 [ChsBuffer](https://github.com/chsbuffer)
-   提供其他修改的 [TheWinner02](https://github.com/TheWinner02) 和 [pizzaschleppa](https://github.com/pizzaschleppa)
