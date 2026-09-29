# EpgTimer-Mobile

EDCB / EpgTimerNW 互換の Android ネイティブクライアント。

EpgTimerSrv へ直接 TCP 接続し、EpgTimerNW で設定済みのカスタム番組表をその設定どおりに表示します。

---

## 特徴

- **EpgTimerSrv に直接 TCP 接続**（HTTP サーバは経由しません）
- **カスタム番組表**を EpgTimerNW 側の設定（`EpgTimerNW.exe.xml`）から取り込み、そのまま忠实に表示（read-only）
- **表示モード 3 種**: 標準（新聞型）/ リスト / 週間
- 番組詳細、録画予約、予約一覧・変更・削除、録画済み一覧、EPG 検索、**キーワード自動予約（自動 EPG 予約）**
- チャンネルロゴの表示（サーバに LogoData がある場合）

---

## 動作環境

| 項目 | 値 |
|---|---|
| 通信 | TCP ソケット（`EpgTimerSrv` の `EnableTCPSrv` が有効である必要があります） |
| 既定ポート | **4510** |
| ポート 5510 について | EpgTimerSrv の **HTTP サーバ**用ポートです。TCP API には 4510 を使用してください |
| 最小 SDK | 26 (Android 8.0) |
| 対象 SDK | 35 |

> **注意**: 4510 と 5510 の両方が listen 状態でも、接続成功だけでは判別できません。本アプリは接続時に応答が EDCB バイナリかどうかを検証し、HTTP 応答であれば明示的なエラーを表示します。

---

## ビルド

```bash
export JAVA_HOME=/path/to/jdk17
./gradlew :app:assembleDebug
```

生成物: `app/build/outputs/apk/debug/app-debug.apk`

必要環境: JDK 17、Android SDK（compileSdk 36）、Gradle ラッパー（9.1.0）

---

## テスト

```bash
# 実サーバに依存しない単体テストのみ
./gradlew :app:testDebugUnitTest

# 実サーバ統合テストも実行（環境変数でホストを指定）
EDCB_TEST_HOST=192.168.0.10 EDCB_TEST_PORT=4510 \
  ./gradlew :app:testDebugUnitTest
```

`EDCB_TEST_HOST` が未設定の場合、実サーバに依存するテストは自動的に skip されます。

統合テストはサーバの予約・自動予約データを一時的に作成し、**テスト後必ず削除して元に戻します**。

---

## 使い方

### 1. 接続設定

起動後「設定」タブで、EpgTimerSrv のアドレスとポートを入力し「接続テスト」を実行します。

### 2. カスタム番組表の取り込み

EpgTimerNW と同じ PC にある `EpgTimerNW.exe.xml`（EpgTimer 設定ファイル）を、
設定画面の「設定XML取込」から取り込みます。

取り込んだ状態では、タブは EpgTimerNW で設定した内容がそのまま反映されます。

| XML ファイルの状態 | タブの挙動 |
|---|---|
| `UseCustomEpgView = true` | XML 内の `CustomEpgTabList` をそのまま使用 |
| `UseCustomEpgView = false` | 既定タブ（地デジ / BS / CS / CS3 / その他） |
| 未取り込み | 既定タブ |

各タブの表示モード（`ViewMode`）は XML の値に従います。
- `0` … 標準（新聞型）
- `1` … 1 週間
- `2` … リスト

.app 内での表示モード切替は、EpgTimer 本体と同じく**セッションのみ有効**（再起動すると XML の値に戻ります）。

### 3. 番組表の操作

- **標準（新聞型）**: 縦軸 = 時間、横軸 = 放送サービス。番組の縦サイズは放送時間に比例
- 2 方向スクロール（ドラッグ）、サービス名ヘッダーと時刻軸は固定
- 番組をタップすると詳細画面が開きます

### 4. 録画予約・自動予約

- 番組詳細画面から録画予約
- 予約タブの「自動予約」タブでキーワード予約（自動 EPG 予約）を管理
- 検索画面の「この条件で自動予約」ボタンから、検索条件をそのまま引き継いで登録可能

---

## 仕様に関する重要な注意

### カスタム番組表はサーバから取得できない

EpgTimerNW のカスタム番組表はクライアント本地の設定ファイル（`EpgTimerNW.exe.xml`）に保存されており、
EpgTimerSrv にはこの設定を取得する API が存在しません。

そのため本アプリは、**ユーザーが XML を取り込む方式**を採用しています。
XML 内の作成・編集・削除は実装対象外です（EpgTimerNW / EDCB 側が正となります）。

### チャンネルロゴについて

ロゴは EpgTimerSrv 側の `Setting/LogoData/` に実ファイルが保存されている場合にのみ取得できます。

`EpgDataCap_Bon.exe`（Windows GUI）で「ロゴデータを保存する」を有効にして EPG 取得を実行すると生成されます。
Linux 版 EpgTimerSrv の EPG 取得経路にはロゴ保存の処理が含まれていないため、
Windows で取得した LogoData を配置するか、別途入手したロゴファイルを配置する必要があります。

---

## プロジェクト構成

```
app/src/main/java/com/starrow/epgtimer/
├─ data/
│  ├─ edcb/          # EDCB TCP プロトコル実装
│  │  ├─ EDCBConnection.kt        # 接続・フレーミング
│  │  ├─ CtrlCmd.kt               # コマンド ID・エラーコード
│  │  ├─ CtrlCmdSerializer.kt     # バイナリ書き込み
│  │  ├─ CtrlCmdDeserializer.kt   # バイナリ読み込み
│  │  └─ EpgTimerTcpClient.kt     # クライアント実装
│  ├─ model/         # EDCB データモデル
│  ├─ guide/         # カスタム番組表の解析・表示条件エンジン
│  └─ repository/    # データリポジトリ層
├─ ui/               # Jetpack Compose UI
│  ├─ guide/         # 番組表（標準・リスト・週間）
│  ├─ program/       # 番組詳細・予約ダイアログ
│  ├─ reservation/   # 予約一覧・自動予約
│  ├─ recording/     # 録画済み
│  ├─ search/        # EPG 検索
│  └─ settings/      # 設定
└─ util/             # 時刻ユーティリティ
```

---

## ライセンス

本リポジトリのコードは自由に使用できます。

EDCB（[xtne6f/EDCB](https://github.com/xtne6f/EDCB)）の作者が著作権を持つ成果物は含んでいません。
本アプリは EDCB のソースを参照してプロトコル互換クライアントとして実装したもので、
`EpgDataCap_Bon.exe` / `EpgTimerSrv.exe` などの EDCB 本体バイナリ、
およびそのソースコードは一切同梱していません。

EDCB 等の外部ソフトウェアの利用は、それぞれのライセンスに従ってください。

---

## テスト用のサンプルデータ

`app/src/test/resources/sample/EpgTimerNW.exe.xml` は、EpgTimerNW の設定ファイル実物（UTF-8 BOM 付き）です。
カスタム番組表タブ（地デジ / BS / BS4K / CS / アニメ / 新アニメ）の設定が含まれているため、
XML 構造の検証と BOM 処理の再現確認に使っています。

テストは既定でこのファイルを参照します。別の設定ファイルで検証したい場合は環境変数で上書きできます。

```bash
EPGTIMER_TEST_XML=/path/to/EpgTimerNW.exe.xml ./gradlew :app:testDebugUnitTest
```

実運用で自分の `EpgTimerNW.exe.xml` を誤ってコミットしないよう、`.gitignore` には
`/EpgTimerNW.exe.xml` を指定済みです。
テストリソースは `app/src/test/resources/` 配下にあるため、このパターンには一致しません。
