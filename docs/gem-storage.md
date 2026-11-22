# Gem専用収納の仕様

下位 `sophisticated_gem_case:gem_case_upgrade` と上位 `sophisticated_gem_case:advanced_gem_case_upgrade` は、同じ専用収納を提供する。装着先はバックパックで、上下合わせて一つまで。容量はGemの種類と純度の組ごとに32,767個で、バッグのtierやStackアップグレードの倍率から独立している。

## 収納対象と経路

- 作者APIで種類と純度を解決し、その種類の最低純度以上であることを確認する。同じ種類・純度から再生成した標準Gemとアイテムおよび全componentが一致するstackを、個数を除いて受け入れる。名前変更などの追加component付きGemは、取出時の再生成で付加データを失わないよう通常収納に保持する。無効なGemと非Gemも通常収納の経路へ渡す。
- 通常のバッグ投入では専用収納を先に使い、入らなかった分を通常収納へ回す。両方に入らない分は投入元へ返す。画面の専用投入では、受け入れた分だけカーソルから減らす。
- 設置バッグのblock capabilityと携帯バッグのitem capabilityは公開IO handlerを使う。slot指定の投入は元handlerの範囲と投入可否を守り、通常slotの容量に空きがなくても、投入が許可されるslotなら専用の空きを使える。simulateは保存、投入元、通常収納、保存callbackを変更しない。
- 一括移動は同じバッグの通常収納をslot順に処理し、専用に入る分だけ移す。専用収納は通常slotの列挙・抽出から隔離する。自動回収と外部収納との転送は[回収・転送仕様](gem-transfer.md)に従う。

## 永続保存と互換性

アップグレードのItemStackに `sophisticated_gem_case:gem_storage` を保存する。schema 1は `schema_version`（Int、値1）と `entries`（List）を持ち、各entryは `gem_id` と `purity`（String）、`count`（Int、1〜32,767）で構成する。種類と純度の組は一意で、保存順は種類ID、次に純度文字列。個数ゼロの組は保存しない。変更時は新しいpayloadを作成して差し替え、wrapperの保存callbackを呼ぶ。

処理上限は4,096件、IDと純度はそれぞれUTF-8で256 byte、NBTは見積もりで1 MiB・深さ8とする。新しい組や個数を保存できない場合は未受入分を投入元に残す。componentがなければ空収納として読み込む。未知schema、重複、不正な型・値、処理上限超過は原文を保持し、収納変更をロックする。

形式が正しい未登録ID・未知純度・現在の最低純度より低いGemは個数を保存し続ける。該当entryの投入・取出・加工・転送を止め、再び解決できれば操作を再開できる。こうしたentryも中身として数える。Gemや上位の素材が残る、保存がロックされている、同じバッグで排他的な処理が進行中のいずれかなら、通常の取り外し・交換を拒否する。空のアップグレードは設定componentを持ったまま取り外せる。

## 画面とサーバー確定

純度欄の左クリックはカーソルへ1個、右クリックはカーソルへ最大1 stack、Shift＋左クリックはプレイヤーの通常inventoryへ最大1 stackを取り出す。上限は再生成したGemの実際のstack上限と受取先の空きで決める。受取先が満杯なら数量を変更しない。未解決entryは画面でIDと個数を確認できる。[中央Gem Case画面](design.md#中央gem-case画面)を参照。

通常ItemStack同期で送る収納情報は、中身の有無とロック状態の要約に限る。要約のフラグはNBT Byteの0または1で、`client_summary` は1を要求する。不正な要約はロックし、サーバーは要約を永続データとして受理しない。種類・純度・個数の詳細は、サーバーのsnapshotから最大64 entryかつ32 KiBのページに分けて送る。クライアントは同一session・revisionのページを結合する。

操作要求には操作種別と対象キーなどの識別情報を渡す。サーバーは現在のmenu、バッグ、装着slot、アップグレードの現物、アクセス権、session・revision、入力と受取先を確かめてから数量を確定する。古い要求や不成立では変更せず、表示は現在の状態に同期する。

対象版と実測は[対応構成](compatibility.md)・[連携調査](integration-research.md)・[検証記録](verification.md)を参照。永続codecと通常ItemStack同期の分離は[Minecraft 1.21.1向けNeoForge Data Components](https://docs.neoforged.net/docs/1.21.1/items/datacomponents/)に基づく。
