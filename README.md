# ColorSafe Trim

Androidで**色味(HDR/SDR・ガンマ・カラープロファイル)を一切変えずに動画をトリミングする**ことだけに特化したアプリ。

## 特徴

- 画面は1つだけ(動画選択 → プレビュー → 開始/終了範囲 → 保存)
- 最優先は `ffmpeg -c copy`(再エンコードなし)。画質・色味・HDRを完全維持し高速
- 指定位置がキーフレームでない場合のみ、ユーザーに「高速(色味完全維持)」か「正確(再エンコード)」かを選ばせる
- 再エンコードが必要な場合も、`ffprobe`で取得した元動画の色空間・Transfer・Primaries・Rangeをそのまま`ffmpeg`へ渡し、色味の変化を最小化
- 保存後に元動画と出力動画の色メタデータを再比較し、「色空間を維持しました」を表示
- MediaStore経由でギャラリーに保存
- 広告・課金・設定なし

## 技術構成

- Kotlin + Jetpack Compose + Material3
- MVVM (`TrimViewModel` + `StateFlow<TrimUiState>`)
- Media3 ExoPlayer でプレビュー
- FFmpeg実行: `com.moizhassan.ffmpeg:ffmpeg-kit-16kb`

## 重要: FFmpegライブラリについて

公式の **Arthenica FFmpegKit は2025年4月にネイティブバイナリの配布が終了(廃止)** しています。
本プロジェクトでは、同じJava/Kotlin API(`com.arthenica.ffmpegkit.*`)を保ったままMaven Centralで配布されている
コミュニティ後継フォーク `com.moizhassan.ffmpeg:ffmpeg-kit-16kb`(Android 16KBページサイズ対応ビルド)を採用しています。

初回ビルド時に以下を確認してください。

1. 依存関係が解決できるか(Maven Centralから取得可能か)
2. 同梱されているエンコーダに `libx264` / `libx265` が含まれているか(再エンコード経路で使用)
   含まれていない場合は `VideoTrimmer.kt` の `videoEncoder` 選択ロジックを、実際に同梱されているエンコーダ名に合わせて調整してください。
3. `FFmpegKit` / `FFprobeKit` のメソッド名(`executeWithArgumentsAsync` など)がフォークでも同一であるか

このフォークが今後利用できなくなった場合は、`VideoProbe.kt` / `VideoTrimmer.kt` の呼び出し部分のみを
別のFFmpegラッパー(例: 自前でNDKビルドしたFFmpeg)に差し替えれば、UI/ViewModel層は変更不要です。

## 開発環境について(重要)

このプロジェクトは **Android SDK / Java / Gradle がインストールされていない環境で作成** されました。
そのため、このセッション内では実際のビルド・コンパイル・実機/エミュレータでの動作確認は行えていません。
**Android Studio(最新版)でこのフォルダを開き、Gradle Sync → 実機またはエミュレータでビルド確認** をお願いします。

なお `gradlew` / `gradlew.bat` ラッパー実行ファイル(バイナリの `gradle-wrapper.jar` を含む)は
このセッションでは生成していません。Android Studioでこのフォルダを開けば自動生成されます
(手動で作る場合は `gradle wrapper --gradle-version 9.5.1` を実行してください)。

Gradle Sync時によくある調整ポイント:

- AGP / Kotlin / Compose BOM / Media3 のバージョンをAndroid Studioの提案に合わせて更新
- `ffmpeg-kit-16kb` の依存解決に失敗する場合は、READMEのFFmpegセクションを参照して別フォークに差し替え

## 権限

- Android 13+: `READ_MEDIA_VIDEO`
- Android 12以下: `READ_EXTERNAL_STORAGE`

## 対応形式

mp4 / mov / mkv (トリム後も元と同じコンテナ拡張子で保存)

## トリムの仕組み

1. `ffprobe`で開始位置付近のキーフレーム位置を解析
2. 開始位置がキーフレームとほぼ一致 → 自動的に `-c copy` で高速トリム
3. 一致しない場合 → ダイアログでユーザーに選択させる
   - **高速(色味完全維持)**: `-c copy`。開始位置が最寄りキーフレームにスナップされる
   - **正確(再エンコード)**: 元の色空間情報を引き継いで再エンコード(CRF18・medium preset)
4. 保存後、出力の色メタデータを再検証し「色空間を維持しました」を表示
