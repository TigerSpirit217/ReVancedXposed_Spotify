<div align="center">
  <h1>ReVanced Xposed Spotify</h1>
  <br>
</div>

[简体中文](README.zh-CN.md)

**A ReVanced LSPosed module by ChsBuffer for Spotify.**

**This fork is maintained by TigerSpirit217 and builds on upstream work by TheWinner02 and pizzaschleppa.**

> [!IMPORTANT]
> -   This is an **unofficial ReVanced project**. Please do not ask the ReVanced developers for support.
> -   **Root access is required** to use this module.

## Patches

### Spotify

The list describes the features implemented in the source code. Results may vary by Spotify version. Features with a default state have a toggle in the module settings.

Open Spotify's sidebar and select **ReVanced Xposed Settings**. After changing settings, select **Restart Spotify** in the top right.

-   **Premium playback adjustments (on by default)**. Covers on-demand playback, playback order, Shuffle and Smart Shuffle, and adding tracks to the queue. This patch also adjusts the Premium UI, includes compatibility changes for Google Assistant playback and legacy Car Thing, and filters ad sections on Home and Browse as well as promotional pop-ups.
-   **Ad blocking (on by default)**. Attempts to disable ads and hide the video ad countdown.
-   **Share link sanitization**. Removes the `si` and `utm_source` tracking parameters when copying links or using the Android share sheet.
-   **Additional lyrics sources (off by default)**. Better Lyrics and NetEase Cloud Music can fill in missing lyrics in Spotify's existing lyrics view. If both are enabled, Better Lyrics is tried first. When NetEase Cloud Music provides a Chinese translation for a non-Chinese song, it appears as an option in Spotify's lyrics view.
-   **Session retention**. Attempts to prevent automatic sign-outs.
-   **Third-party launcher widget compatibility**. Allows third-party Android launchers to bind Spotify widgets.
-   **Monet theme (off by default)**. Uses Android's wallpaper-based colors for Spotify backgrounds and accents.
-   **RoundyUI (off by default)**. Rounds corners on elements such as cards, cover art, buttons, and bottom sheets.
-   **Native HTTP filtering (experimental, currently unregistered)**. Includes a filter for ad and tracking requests.

## Downloads and Release Differences

### Source Repository

-   [Repository](https://github.com/TigerSpirit217/ReVancedXposed_Spotify)
-   [Download](https://github.com/TigerSpirit217/ReVancedXposed_Spotify/releases)

> [!NOTE]
> The package name and signing key for releases from this repository change daily and with each update.
>
> You only need to reinstall when a new version is available.
>
> To update, uninstall the previous version, install the new version, and configure the module settings again.

### Xposed-Modules-Repo

-   [Repository](https://github.com/Xposed-Modules-Repo/io.github.tigerspirit217.rvx.fe.s)
-   [Download](https://github.com/Xposed-Modules-Repo/io.github.tigerspirit217.rvx.fe.s/releases)

> [!NOTE]
> Releases in Xposed-Modules-Repo are updated on an irregular schedule.
>
> These releases always use the package name `io.github.tigerspirit217.rvx.fe.s` and the same signing key.
>
> This version is easier to update and carries a higher risk of an account ban if the module is detected. Use it at your own risk.

## ⭐ Credits

-   High-performance DEX runtime parsing library [DexKit](https://luckypray.org/DexKit/en/)
-   Continuation of the Vanced project [ReVanced](https://revanced.app)
-   Lyrics provider [BetterLyrics](https://betterlyrics.org/)
-   Original developer of ReVanced Xposed (now NexAlloy) [ChsBuffer](https://github.com/chsbuffer)
-   Additional modifications by [TheWinner02](https://github.com/TheWinner02) and [pizzaschleppa](https://github.com/pizzaschleppa)
