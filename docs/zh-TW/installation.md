# 安裝

## 安裝 App

1. 在手機上開啟 [Releases](https://github.com/naritaroad/EarCue/releases/latest)，下載 `EarCue-<version>.apk`。
2. 開啟檔案。Android 會詢問是否允許從瀏覽器或檔案管理員安裝，允許一次即可。
3. 開啟 EarCue。需要 Android 8.0（API 26）以上。

更新：直接安裝新版 APK 覆蓋舊版，講稿、歷史與設定都會保留。

## 準備講稿

### 用 AI skill（建議）

`skills/earcue-script/` 是一個 agent skill。從 Release 下載 `earcue-script.zip`，加到你的 AI agent 的 skills，然後這樣請它：

> 把 `Green Streets Transcript (Bilingual).docx` 轉成 P2 Mina Park 的 EarCue 講稿。

Agent 會產出含標籤的 `<talk>-<role>.earcue.json`，並用 `scripts/validate.py` 逐字檢查。

### 手動輸入

在「講稿 → 貼上文字」貼上純文字：

```
title: 我的報告
lang: zh-TW
tags: 課堂報告

# 開場 | P1 | 2
> 主持人: 交給你了。
大家好。
// 顯示在螢幕上的備註
```

## 載入到手機

- 「講稿 → 匯入檔案」，選擇 `.json` 或 `.txt`；或
- 從檔案管理員或聊天 App 把檔案分享給 EarCue。

標題與既有講稿相同時，可以選擇存成它的新版本。

## 第一次使用檢查清單

- 接上耳機，右上角的狀態會變成綠色並顯示裝置名稱。
- 在「設定 → 聲音模型」挑一個符合講稿語言的本機聲音。
- 在「按鍵」選一種配置，或拖曳按鍵配合你的手掌。
- Sony Xperia 手機的快門鍵就是「下一段」：短按換下一句，按住會連續往後跳。按住畫面上的「播放」會回到第一段重新開始。

## 從原始碼建置

需要 JDK 17 與 Android SDK Platform 35、Build-tools 35。建立 `local.properties` 並寫入 `sdk.dir=<SDK 路徑>`。

```bash
./gradlew assembleDebug
```

```bash
./gradlew assembleRelease
```

Release 版以 R8 縮減，本 repo 以 debug 金鑰簽署。上架商店前請改用自己的簽署設定。

修改 `tools/i18n/*.py` 之後：

```bash
python tools/i18n/gen_strings.py
```

重新產生 `examples/green-streets/` 的示範檔（需要 `python-docx` 與 `python-pptx`）：

```bash
python tools/demo/build_demo.py
```
