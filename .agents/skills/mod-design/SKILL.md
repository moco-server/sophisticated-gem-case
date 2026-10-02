---
name: mod-design
description: 独立MODの目的、core境界、loader、依存版、保存・通信・失敗条件を設計する時に使う。packの構成選定だけには使わない。
---

# mod-design

入力は目的、対象Minecraft／loader、既存designと依存構成。出力は対象MODの[design.md](../../../docs/design.md)の現在の設計と採用理由・未決事項。

ゲームから独立する判断をcoreへ置き、登録・保存変換・外部MOD連携をloaderへ置く。未使用の抽象基盤を作らない。素材不足・出力満杯・連続要求・保存互換・サーバー権限を設計し、失敗時の無変更を契約にする。

[対応構成](../../../docs/compatibility.md)と対象版の一次資料で依存・API・sideを確認する。pack固有の判断を対象MODへコピーしない。検証条件は[verification](../../../docs/verification.md)に対応させ、未確認の移植性を断定しない。
