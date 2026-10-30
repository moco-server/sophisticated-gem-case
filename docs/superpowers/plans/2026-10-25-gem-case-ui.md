# Gem Case中央画面 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 収納・純度確認・加工を、バッグ幅に依存しない中央画面へまとめる。
**Architecture:** 既存StorageScreenBaseのmodal overlayを使い同じmenu/sessionを維持する。サーバーの有界な5出力preview、ネイティブslot操作のbridge、クライアントの描画・入力を責務分割する。
**Tech Stack:** Kotlin 2.0.21、Java 21、MC 1.21.1／NeoForge 21.1.252、固定Apotheosis 8.9.0／Core 1.5.2.2343／Backpacks 3.26.6.2174。
**Spec:** docs/superpowers/specs/2026-10-25-gem-case-ui-design.md（2026-10-25承認）

## Global Constraints

- coreはpure Kotlin/JVM、client専用型をcommonから参照しない。
- 保存schema、32,767上限、失敗時無変更、既存recipe・確定処理を維持する。
- UIは232×230 GUI pxを目標、320×240に収める。独自の描画を使い外部画像を同梱しない。
- .local/build以外に起動物を置かず、既存worldは上書きしない。JEIは開発client限定。
- userは実装を承認済み。追加の実行方法確認はせず、境界を分担して統合する。push・公開は含めない。

## Review Focus

- Escape・外側クリック・resize・menu差替え後に素材許可や背面入力が残らない。
- 空収納・未選択でも素材を回収でき、壊れた保存を上書きしない。
- 種類切替／reload／旧revisionで別強化ボタンを誤って有効化しない。
- 大きい保存個数と未解決IDを表示から落とさず、表示stack個数と混同しない。
- カーソル保持・標準slotクリック・Shift移動で通常menuの予測とサーバー結果が一致する。

### Task 1: 有界な全強化先preview

**Files:** menu/GemMenuSession.kt、GemProcessingActions.kt、network/GemNetworking.kt・加工payload群、client/GemProcessingCache.kt、対応GameTest。
**Interfaces:** clientはselectGem(menu, slot, gemId: String?)、preview(menu,slot,output: GemKey)、canProcess(menu,slot,output)、process(menu,slot,output,many)、canEditMaterials(menu,slot)、useBackpackMaterials(menu,slot)を使う。
- [x] 5出力の世代・種類照合、null選択の素材状態、古いpreview拒否のテストを先行追加して失敗を確認する。
- [x] 一回の要求で最大5強化先を返す有界なpayloadとserver照合を実装する。既存単発要求のテスト契約も維持する。
- [x] 対応GameTest／wire境界を実行して成功を確認する。

### Task 2: modalのslot操作と終了境界

**Files:** client/GemCaseModalSlots.kt、client/GemCaseModalLifecycle.kt、必要最小のclient Mixin、mixins.json、対応GameTest。
**Interfaces:** GemCaseModalSlots(screen,container)にrender(graphics,mouseX,mouseY,x,y,showMaterials)、mouseClicked、mouseReleased、mouseDragged、keyPressed、renderTooltip、closeを用意する。座標はoverlay左上、main左端はx+56。材料はx+12から2列3行、playerはx+64,y+148の9列3行、hotbar y+206。実slotの論理indexを使いnativeクリック処理へ接続する。
- [x] 素材の表示終了／無選択／旧menu拒否のサーバー側テストを先行追加し、不足があれば失敗を確認する。
- [x] 実slotを描画し、左右クリックとShiftをnative処理へ橋渡しする。可能な数字キー・drag・double clickを接続し、未対応は明示する。
- [x] overlayの差替え・Escape・外側クリック・screen終了を同一closeへ接続し、素材許可と座標を復元する。
- [x] 固定JAR APIとのcompile、対応GameTestを確認する。

### Task 3: 中央UIと小さい入口

**Files:** client/GemCaseTab.kt・新規GemCaseModal.kt・描画／一覧責務、GemSettingsPanel.kt、不要加工viewの整理、翻訳provider、JAR必須entry。
**Interfaces:** Task 1のpreviewとTask 2のslot/lifecycleを使う。GemCaseModalはWidgetBase、close()を持ち、中央表示の入口から生成する。
- [x] 選択・検索・スクロール・未解決表示のモデルへ意味のある先行テストを追加し、失敗を確認する。
- [x] 右入口、232×230の中央表示、6列3行、純度6枠、強化5個、検索、補充・投入・一括移動・設定・戻るを実装する。
- [x] 転送設定を中央表示の設定ページへ載せ、画面往復で一覧状態を維持する。
- [x] アイコン／数量／ホバー／カーソル描画を実装し、翻訳をdatagenする。生成diffの追跡・未追跡を確認する。
- [x] ktlintFormat、root checkで成功を確認する。

### Task 4: 統合検収と引き渡し

**Files:** docs/design.md、docs/verification.md、必要なclient受入手順。
- [x] 通常JAR build／verifyModJarと全必須GameTestを実行し、成功件数を確認する。
- [x] 固定JEI入りclientと専用serverを新規／コピー環境で起動し、可能なGUI観測と操作を行う。
- [x] 独立レビューで全diff・境界・実測を検収し、重大指摘は回帰テスト付きで修正する。
- [x] 設計・検証記録へ結果と未実施を反映し、開発worktreeの最終差分を元workspaceへ戻す。元workspaceの並行変更は上書きしない。

検収の詳細と未実施条件はdocs/verification.mdの中央画面節を正とする。直接起動の入口は最終調整のため人手検収未実施、中央modalのnativeイベント検収と区別する。
