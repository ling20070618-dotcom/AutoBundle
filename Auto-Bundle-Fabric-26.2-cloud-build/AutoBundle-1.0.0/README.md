# Auto Bundle (Minecraft 26.2 / Fabric)

純客戶端模組：偵測玩家物品欄中新增的物品，並嘗試透過原版收納袋（Bundle）的正常點擊互動收入。

## 功能與限制

- 只支援 Minecraft Java 26.2、Fabric。
- 僅客戶端安裝；不新增物品、不改變收納袋容量、不寫入自訂物品資料。
- 只在玩家的正常物品欄選單生效；開啟箱子、工作台、村民等其他容器時暫停。
- 優先搜尋快捷列，其次主背包，最後副手的原版收納袋。
- 每次只透過原版 `ContainerInput.PICKUP` 點擊流程嘗試移動物品。
- 收納袋滿載或不接受物品時會延遲重試；不會丟棄物品。
- 這是初版原型，尚未完成實際多人伺服器遊戲測試。伺服器可拒絕或回滾任何操作；不同伺服器的反作弊或同步修改可能影響行為。

## 建置需求

- JDK 25
- Gradle 9.5.1 或更新的 9.x 版本
- Fabric Loom 1.19-SNAPSHOT（由 Gradle 自動下載）
- Fabric Loader 0.20.1+
- Fabric API 0.161.0+26.2

在專案根目錄執行：

```bash
gradle build
```

Windows 也可以在安裝 Gradle 後執行：

```bat
gradle.bat build
```

成功後 JAR 位於：

```text
build/libs/auto-bundle-1.0.0.jar
```

把 JAR 放入 `.minecraft/mods`，並確保啟動器使用 Java 25 與 Fabric 26.2。

## 重要

目前交付的是可建置的原始碼專案，不是已編譯、已測試的成品 JAR。本工作環境只有 Java 21，且沒有 Gradle，因此無法在此環境完成 26.2 編譯與遊戲內驗證。


## GitHub Actions 雲端編譯

專案已附 `.github/workflows/build.yml`。把整個專案上傳到 GitHub repository 的 `main` 或 `master` 分支後，GitHub Actions 會自動使用 Java 25 與 Gradle 9.5.1 執行 `gradle build`，並把 JAR 上傳為名為 `auto-bundle-minecraft-26.2` 的 workflow artifact（保留 14 天）。

也可以在 GitHub 專案頁面的 Actions → Build Auto Bundle → Run workflow 手動啟動。若編譯失敗，請查看該次 workflow 的 Build mod 步驟日誌；這代表程式碼仍需依照 26.2 實際 API 修正，不能把失敗的流程視為已完成。
