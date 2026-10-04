# 通常JARと次工程への引き渡し

工程5の実装・自動検証済み成果物。実player検収とpackへの導入は未実施で、この文書は公開済み／pack適用済みを意味しない。

## 成果物と構成

- 通常JAR: `neoforge/build/libs/sophisticated_gem_case-0.1.0-dev.jar`
- SHA256: `fda04d1283cd6858c76b75fc30a9ba156b37c39f93c09fad664075cd7b711853`
- Minecraft 1.21.1／NeoForge 21.1.252／Java 21。通常JARをclientと専用serverの双方へ配置する。
- 固定runtime依存9 JARの版・取得元・hash・sideは[依存manifest](dependency-manifest.json)と[対応構成](compatibility.md)。KFFは5.5.0の配布all JARを使い、Maven開発aggregateと取り違えない。
- JEI 19.51.0.418は試遊client限定。MOD本体の必須依存ではなく、専用serverへ追加しない。GatewaysとGameTest fixtureは通常構成へ追加しない。
- 試遊用の同一JAR配置済み: `.local/client-trial/mods` と `.local/acquisition-server/mods`。保存worldは新規／コピーを使う。

下位はglass_blocks 3＋Gem Dust 4＋Upgrade Base 1＋Chest 1、変換はglass_blocks 3＋Basalt 3＋Gem Cutting Table 1＋Ender Chest 1＋Upgrade Base 1で各一個。中身入り下位は装着中変換で上位化する。三itemのID・日英名称、schema、加工コストは維持した。

## 次に実行する検収

1. [client-acceptance](client-acceptance.md)工程5のJEI二recipe表示と通常craft、三アイコンのja_jp／en_us・GUI scale 2／3／4を実操作する。
2. 工程2〜4の収納・Pickup／Deposit／Restock・材料click・加工・補充・装着中変換を実playerで操作し、不足・満杯・権限拒否時の無変更を確認する。
3. 認証済みclientで通常JAR専用serverへ接続し、装着バッグの四保存領域を再起動・設置回収・tier変更で照合する。今回のitem entity保存probeと区別する。
4. 実施構成、同一JAR hash、操作結果と残件を検証記録へ追記する。空欄項目をGameTestや起動だけで合格にしない。
5. pack側の変更計画を別に作成し、導入対象・side・manifest更新・共存／再起動／Windowsを検収する。既存packの保存worldはコピーする。公開／pushはその工程のユーザー指示に従う。

現在の証拠は[verification](verification.md)工程5と`.local/verification/acquisition/`。独立最終レビューの未解決指摘はなく、check／JAR検査／必須147 GameTest、生成再現性、通常server保存往復、JEI dev client起動を確認した。実player・認証・packの検収は残っている。
