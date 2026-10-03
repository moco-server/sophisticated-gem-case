# 上位Gem Case・材料枠・加工・装着中の上位化 設計案

状態: 工程3をローカルmainへ統合後に作成したレビュー用spec。工程4の本番実装は未開始。ユーザーの設計レビュー後に実装計画を作成する。

## 目的と範囲

Issue #1の合意を工程4として実現する。上位版は下位収納・連携設定をそのまま引き継ぎ、対象版Apotheosisの通常Gem Caseの純度強化を携帯する。新しい依存版へ更新せず、通常入手レシピと最終見た目・pack導入は工程5へ残す。創造モード／コマンドで上位版と変換アイテムを入手して検証する。

対象構成はMC 1.21.1／NeoForge 21.1.252／Java 21／Kotlin 2.0.21／KFF 5.5.0／Apotheosis 8.9.0／Core 1.5.2.2343／Backpacks 3.26.6.2174。工程3のFML 4.0.44固定を維持する。

## 方針と比較

推奨は、作者の実行時recipeをloader側で解決し、既存のGem個数保存と新しい6材料枠に対して一つの変更計画を作る方式。作者のGemCaseTile／GemUpgradeMatchを加工主体として流用すると、即時の部分変更、package-private境界、同バッグ通常収納からの不足補充を本MODの保存と整合させにくい。既定材料表を複製する方式は小さいが、datapack変更に追従しない。公開RecipeManagerのデータを使う方式は実装が少し増えるが、加工コストと失敗時の無変更を両立できる。

## 登録と共有収納

上位itemは`advanced_gem_case_upgrade`、変換itemは`gem_case_upgrade_conversion`とする。下位と上位を同じGem Caseグループに属させ、バッグ全体で一個に制限する。共通wrapper境界でGem counts/settings・投入・Pickup／Deposit／Restockを共有し、上位だけに材料と加工を追加する。通常inventoryの仮想slotやStack倍率を増やさない。

上下どちらもGem保存が空でない、個数がLocked／ClientSummary、exclusive操作中なら通常remove/swap不可。上位はさらに材料が残るか材料保存がLocked／ClientSummaryなら不可。設定だけが残る空のupgradeは通常どおり取り外せ、設定componentをitemに保持する。

## 加工材料と保存

上位専用材料枠は6枠、各slotの上限は作者と同じ通常stack上限で、バッグtier／Stack upgradeの倍率を受けない。公開IO capability／Pickup／Deposit／Restockへ材料枠を公開しない。menuの通常slot同期を使って中身を表示・操作する。現在recipeの左右Ingredientのいずれかに合う材料だけを新規投入できる。recipe変更で材料ではなくなった既存stackも保持し、取り出せる。

Gem countsとtransfer settingsのschema 1は変更しない。材料と通常収納補充設定は独立したdata componentへ保存する。補充は初期OFF。材料componentはschema 1、slot 0..5の重複しない保存entry、registry-aware ItemStack codecで解決する。NBT budget／深さ／slot数／stack数をcopy前に検査し、未知schema、壊れたstack、不明item、超過payloadはraw保持・材料編集／加工停止とする。正常なGem個数の手動操作と工程3の連携は維持する。通常ItemStack networkは材料の中身あり／lockedと補充状態の要約だけ、材料詳細はmenuの6slot同期で送る。未知componentを空材料へ読み替えない。材料保存の上限はNBT 1MiB／深さ16、entry最大6、各材料ItemStackのregistry-aware永続encode結果32KiB、個数は1..min(64, item.maxStackSize)とする。新規投入とmenu同期に同じstack上限を使う。許容byte／深さを超える既存rawは保持してロックする。通常の素材・名前・componentの往復をこの予算でテストし、上限を黙って緩めない。材料は`gem_processing_materials`へ`schema_version: Int=1`と`entries: [{slot: Int, stack: Compound}]`だけを保存し、slot重複・未知fieldを拒否する。補充は`gem_processing_settings`へ`schema_version: Int=1`／`use_backpack_materials: byte(0/1)`を保存する。補充componentが不明・破損ならraw保持・補充設定変更と加工を止める。材料保存と補充設定が正常なら、転送設定だけのLockedは加工の拒否理由にしない。

## Recipeと候補の選択

公開`GemCuttingMenu.getRecipes(level)`から実行時の`PurityUpgradeRecipe`だけを取得する。recipe ID・種類・Purity・左右`SizedIngredient`の候補順をloaderで扱い、固定の材料個数や新serializerは作らない。

加工要求は種類IDと出力純度を指定する。出力CRACKEDは拒否し、CHIPPED..PERFECTだけを許す。入力は同じ種類の一段階前の標準Gem 2個、出力は指定純度の標準Gem 1個。入力は専用countsだけ、出力も専用countsだけへ置き、出力側32,767の空きを確認する。未知Gem／純度、標準Gemを再構成できない種類、入出力不足、codec予算超過では無変更。PERFECTをさらに上げる操作は設けない。

補充OFF時は作者の選択順を維持する。RecipeManagerの返却順、材料slot 0..5順、各slotに合うleft候補順で選び、rightはleftと異なるslotから選ぶ。最初に左右が揃うrecipeを使う。作者の選択は実装契約を固定sourceで再照合し、ゲームテストで比較する。

補充ON時は、まず同じ6枠だけで成立する作者どおりの候補を探す。それがない場合だけ同じrecipe順で不足補充案を探す。各左右候補の要求数に対して専用slot順で使える材料を先に予約し、不足を同じバッグの通常slot昇順から補う。一つの要求は同じ候補Ingredientに合う複数stackへ分割でき、left/rightの予約は残数を共有して二重消費しない。両Ingredientに適合するstackではright全体の必要数を残せる範囲だけleftへ予約し、その後rightを同じslot順で配分する。leftを単純に先取りして、材料が揃うのにright不足と判定することを避ける。左右候補の組合せを順に検査し、最初に全必要数が揃う案を選ぶ。プレイヤー・別バッグ・外部handlerを材料候補へ入れない。一回の計画は最大512recipe照会、4,096左右候補組合せ、16,384 Ingredient判定、先頭8,192通常slot走査までとし、いずれかの予算を使い切ると加工せず無変更。先頭走査制限を超える巨大inventoryの後半材料を見つける保証はない。previewもこの予算内で選ばれた案を使い、32KiBを超える情報をtruncateして加工可能に見せない。

この補充ONの拡張は、専用枠だけで加工できる場合の選択を変えず、小stackへ分かれた不足材料も使える。代わりに補充用の配分計画と複数候補テストが必要となる。

## coreと確定境界

coreにはID・純度段階・GemCountsと整数の材料消費計画だけを渡す。Recipe／Ingredient／ItemStack／Minecraft／loader型を入れない。coreは2→1の数量変化、容量、解決済みのslot個数と左右適合booleanから上記の二要求の予約量を不変計画として判定する。loaderがIngredient判定を済ませ、coreはItemStackや外部callbackを読まない。未使用の汎用transaction基盤は作らない。

loaderはserver threadでplayer、menu stillValid、バッグUUID、現在upgrade、session/revision、補充設定、recipe、全対象slotを確認する。同バッグexclusive文脈を共有し、加工中のGem保存・設定変更・変換・removeへの再入を拒否する。選んだItemStackのitem/component/countを再照合し、Gem保存・材料・通常slotの全変更値をcopyとして準備する。全事前検証成功後だけ自身の保存と同バッグの実inventoryを確定する。途中callback失敗時は変更前snapshotへ復元し、復元できない契約違反を握り潰さない。外部handlerへの搬送を加工の一部にしない。

通常クリックは一回、Shift加工は最大64回。各回recipe／counts／材料を再計画し、次の不成立で止める。64回を一括成功扱いにせず、既に成功した回だけ消費・出力を保持する。

## 装着中の上位化

Gem専用変換itemをcursorに持ち、装着した下位upgrade slotを右クリックする操作を使う。native Stack conversionがsource componentをコピーしないため、その分岐を無条件に流用しない。固定版menuに対してGem変換itemと現在の下位slotだけを対象にする最小のserver接続を設ける。通常remove/swap禁止を緩めない。

現在のmenu、距離、バッグUUID、slot、下位現物、cursor変換item、exclusive状態、上位装着条件を確認する。旧itemの全data componentを上位itemへ継承し、Gem countsとtransfer settingsはrawも含め保持する。通常の下位は加工用componentを持たず、新材料枠は空、通常収納補充はOFF。例外的に下位へ上位専用の材料／補充componentが既に付いている場合は変換を拒否し、その原文を保持する。移行用の汎用clearやschema書換えをしない。未知／Lockedの旧保存もrawを失わず移行できる場合だけ成功させ、上位で同じロックを保つ。上位itemとcomponentの再読取・装着条件を確認後に一つのupgrade slotを置換し、保存確認後に変換itemを一個消費する。creativeでの消費はnativeと同じ扱い。失敗時は旧item、保存、cursorを保つ。

上位wrapper生成後もバッグ内で上下合わせて一個の制限と既存連携を検査する。通常クラフトによる下位→上位化はこの工程で併設しない。

## menuと通信

既存タブへ上位限定の加工viewを追加し、6材料slot、対象種類と出力純度、必要材料、加工可否、通常収納補充ON／OFF、単発／Shift加工を表示する。必要材料と可否は現在のserver選択結果を同期し、clientが材料表を決めて消費させない。登録Gem／純度の選択、未同期／Locked表示、日本語／英語は既存方式とdatagenを使う。通常Gem一覧・転送設定は下位と上位で共有する。

加工と補充編集は既存session nonce/revisionと16要求/tickのquotaを共有する。Gem個数・転送設定・材料・補充設定の変更で同じrevisionを更新し、同期途中や旧世代の要求を拒否する。recipe reload後は加工計画とpreviewを再取得する。変換成功後はupgrade差替えとして新しいsessionを発行する。材料slotのクリック／Shiftはserver側の現在menuで処理し、複合操作でpartial変更を起こさない。

## 受け入れ条件と未実施を区別する検証

- core: 入力2→出力1、入力不足、出力上限、材料不足、旧状態不変、64回境界。
- 保存: 6枠往復、slot重複／範囲外／不明item／不正schema／巨大・深いNBTのrawロック、設定初期OFF、取り外し制限、通常JARの新規world→コピー再起動。
- recipe: 既定5段階の必要量を実行時recipeから比較、tag／代替材料／複数recipe／reload、native6枠だけの選択順、左右の競合、専用優先・不足だけ通常補充・他inventory不使用。
- 確定: source不足・出力満杯・slot差替え・stale/closed/distance/UUIDの無変更、callback再入／例外の復元と次操作、単発／Shiftの実消費と数量保存。
- 変換: 中身入り下位の上位化、全component保持、cursor一個消費、制限拒否／旧世代／再入／失敗時の全不変、上位でもPickup／Deposit／Restockが維持される。
- 配布: root check／datagen差分／positive GameTest件数／通常JAR内容検査、固定依存だけのserverとJEI開発client起動、独立した安価なreviewer。GUI／認証接続／pack／Windowsは実際に確認した項目だけ検収とする。

## コストと実行方針

実装はcore計画、材料保存、加工確定、装着中変換、menu・通信の単位へ分ける。各単位の先行テストと影響する検証を行い、共有wrapper／Gradleは一担当が持つ。独立可能な作業は安価なサブエージェントへ委譲できるが、常時並列やモデルを規約へ固定しない。統括者がdiffと実測結果を検収し、実装担当とは別の履歴なしの安価なサブエージェントでレビューする。成功済みの広範囲検証は、新しい変更や失敗がない限り重複しない。

## 固定一次資料

- [Apotheosis GemCaseTile](https://github.com/Shadows-of-Fire/Apotheosis/blob/7dd7cec26830074f1745529eb8f1f533092733d0/src/main/java/dev/shadowsoffire/apotheosis/socket/gem/storage/GemCaseTile.java)
- [GemUpgradeMatch](https://github.com/Shadows-of-Fire/Apotheosis/blob/7dd7cec26830074f1745529eb8f1f533092733d0/src/main/java/dev/shadowsoffire/apotheosis/socket/gem/storage/GemUpgradeMatch.java)
- [GemCaseMenu](https://github.com/Shadows-of-Fire/Apotheosis/blob/7dd7cec26830074f1745529eb8f1f533092733d0/src/main/java/dev/shadowsoffire/apotheosis/socket/gem/storage/GemCaseMenu.java)
- [PurityUpgradeRecipe](https://github.com/Shadows-of-Fire/Apotheosis/blob/7dd7cec26830074f1745529eb8f1f533092733d0/src/main/java/dev/shadowsoffire/apotheosis/socket/gem/cutting/PurityUpgradeRecipe.java)
- [Core StorageContainerMenuBase](https://github.com/P3pp3rF1y/SophisticatedCore/blob/f5ec6fb7636a867f87e37cc388077be20221abfe/src/main/java/net/p3pp3rf1y/sophisticatedcore/common/gui/StorageContainerMenuBase.java)
