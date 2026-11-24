# カスタムビルド

`.github/workflows/custom-build.yml` が毎朝 steeben/Dial の最新バージョンを確認し、
新しければ `patches/` のパッチをすべて当てて APK を作り、このリポジトリの Releases に
`<バージョン>-custom` として公開する。

## 入っている変更
- `01-remove-home-toppicks-mood.patch` — ホーム画面から「Top Picks for You」と「Find Your Mood」を消す

## パッチが当たらなくなったら
元アプリ側で同じ箇所が書き換わると、ビルドが失敗して GitHub からメールが来る。
その時はパッチを作り直す。
