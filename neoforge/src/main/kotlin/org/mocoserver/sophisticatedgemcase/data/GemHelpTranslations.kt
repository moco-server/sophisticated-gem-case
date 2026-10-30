package org.mocoserver.sophisticatedgemcase.data

/** 操作の前提・条件の具体例を、行数を制限しない段落として登録する。 */
internal fun addGemHelpTranslations(
    japanese: Boolean,
    add: (String, String) -> Unit,
) {
    helpDescriptions.forEach { (key, text) ->
        add("sophisticated_gem_case.gui.${key}_description", if (japanese) text.first else text.second)
    }
}

private fun paragraphs(vararg text: String): String = text.joinToString("\n")

private val helpDescriptions =
    mapOf(
        "back" to (
            paragraphs(
                "Gem Caseを閉じて、リュックの画面に戻ります。",
                "ケースにしまったジェムや、転送条件・素材補充の設定はそのまま保持されます。",
            ) to
                paragraphs(
                    "Close Gem Case and return to the backpack screen.",
                    "Stored gems, transfer filters and material settings are retained.",
                )
        ),
        "settings" to (
            paragraphs(
                "ケースのジェムを、接続先へ搬出したり接続先から補充したりするための設定画面を開きます。",
                "搬出と補充は、それぞれON/OFFと対象のジェムを別々に設定できます。種類とランクを組み合わせた条件も指定できます。",
                "この設定だけでは転送は始まりません。実際の操作には、同じリュックの対応するDeposit／Restockアップグレードが必要です。",
                "「ジェムをしまう」「リュックのジェムを移動」の手動操作には、この転送条件は適用されません。",
            ) to
                paragraphs(
                    "Open settings for sending case gems to external storage or collecting gems from it.",
                    (
                        "Deposit and Restock have separate switches and filters. Choose gem types, purities, or a " +
                            "combination of both."
                    ),
                    (
                        "These settings do not start a transfer. The matching Deposit or Restock upgrade in this " +
                            "backpack performs it."
                    ),
                    "These filters do not affect Store held gems or Move backpack gems.",
                )
        ),
        "transfer_back" to (
            paragraphs(
                "ジェムの種類と在庫を表示する画面に戻ります。",
                "変更した転送条件は、そのまま保持されます。戻る前に別の保存ボタンを押す必要はありません。",
            ) to
                paragraphs(
                    "Return to the gem browser and stored counts.",
                    "Filter changes are retained. There is no separate Save button to press before leaving.",
                )
        ),
        "insert" to (
            paragraphs(
                "マウスで持っているジェムを、Gem Caseにしまいます。先にリュックの中身へ置く必要はありません。",
                "同じ種類・同じランクの在庫に加算します。ケースの上限に達した分や、ケースで扱えないジェムは、マウスで持ったまま残ります。",
                "このボタンは手動の収納操作です。転送設定のON/OFFや、選択した種類・ランクの条件には影響されません。",
            ) to
                paragraphs(
                    (
                        "Store the gems held on your mouse cursor directly in Gem Case. You do not " +
                            "need to place them " +
                            "in the backpack first."
                    ),
                    (
                        "Gems are added to the matching type and purity. Excess gems and gems the case cannot handle " +
                            "remain on your cursor."
                    ),
                    "This is a manual action. Transfer switches and type or purity filters do not restrict it.",
                )
        ),
        "move" to (
            paragraphs(
                "リュックの中身を調べ、ケースで扱えるジェムをまとめてGem Caseへ移します。プレイヤーの持ち物や接続先のチェストからは移動しません。",
                "移したジェムは、同じ種類・同じランクの在庫に加算します。ジェム以外のアイテム、扱えないジェム、上限に達して入らない分は元の場所に残ります。",
                "転送設定をOFFにしていても使えます。転送条件で選んだ種類・ランクにかかわらず、収納できるジェムを移動します。",
            ) to
                paragraphs(
                    (
                        "Move all supported gems from the backpack contents into Gem Case. This does not take items " +
                            "from your player inventory or external chests."
                    ),
                    (
                        "Gems are added to the matching type and purity. Other items, unsupported gems and excess " +
                            "amounts stay where they are."
                    ),
                    (
                        "This manual action works even when transfer is OFF and ignores the transfer type and purity " +
                            "filters."
                    ),
                )
        ),
        "unresolved" to (
            paragraphs(
                "ケースに記録は残っているものの、今のMOD構成ではアイテムとして読み込めないジェムを一覧で確認します。",
                "例えば、追加元のMODやデータパックを外した場合や、ジェムの種類・ランクの定義が変わった場合に該当します。",
                "この一覧には種類・ランクのIDと個数を表示します。名前が収まらない項目は、ホバーするとIDを確認できます。",
                "一覧を開いても記録は削除しません。読み込めない状態のジェムは、取り出しや強化には使えません。クリックすると通常のジェム一覧と切り替わります。",
            ) to
                paragraphs(
                    (
                        "Inspect gems whose records remain in the case but cannot be recreated as " +
                            "items in the current " +
                            "mod setup."
                    ),
                    (
                        "This can happen after removing a mod or datapack, or after gem types or purity definitions " +
                            "change."
                    ),
                    (
                        "The list shows type and purity IDs and their stored counts. Hover an entry" +
                            " to read IDs that do" +
                            " not fit on screen."
                    ),
                    (
                        "Opening this list does not delete records. Unavailable gems cannot be " +
                            "withdrawn or upgraded. " +
                            "Click again to return to the gem browser."
                    ),
                )
        ),
        "processing_supplement" to (
            paragraphs(
                "ジェムの強化に必要な素材が、左側の素材枠だけでは足りない場合に、リュックの中身の素材も使用するかを切り替えます。",
                "ON：まず左側の素材枠だけで強化できるかを確認し、足りない場合はリュックの中身も候補に加えます。",
                "OFF：左側の素材枠だけを使用します。必要な素材がリュックにあっても、この枠だけでそろわなければ強化できません。",
                "このボタンを押しただけでは素材を消費しません。実際に強化を実行したときに、必要な分だけ消費します。",
            ) to
                paragraphs(
                    (
                        "Choose whether upgrading may also use materials from the backpack contents" +
                            " when the material " +
                            "slots on the left are insufficient."
                    ),
                    (
                        "ON: first check whether the left material slots are sufficient. If not, also consider " +
                            "materials in the backpack."
                    ),
                    "OFF: use only the left material slots. Materials elsewhere in the backpack will not be used.",
                    (
                        "Toggling this option does not consume materials. Required materials are consumed when an " +
                            "upgrade is actually performed."
                    ),
                )
        ),
        "transfer_deposit" to (
            paragraphs(
                "Gem Caseの在庫から、Depositアップグレードが転送先にしている収納へ、ジェムを送り出す条件を編集します。",
                "この方向の「自動転送」をONにし、転送したい種類・ランクの条件を設定してください。補充側の条件は変更されません。",
                "実際の転送は、同じリュックの有効なDepositアップグレードを使って行います。この画面を開く・設定を変えるだけではジェムは移動しません。",
                "条件に合っても、転送先に入らない分はケースに残ります。",
            ) to
                paragraphs(
                    "Edit which gems may leave Gem Case for storage targeted by the Deposit upgrade.",
                    (
                        "Turn Auto transfer ON for this direction and set the desired type and purity rules. Restock " +
                            "settings are independent."
                    ),
                    (
                        "An enabled Deposit upgrade in this backpack performs the actual transfer. " +
                            "Opening or editing " +
                            "this screen does not move gems."
                    ),
                    "Gems that the destination cannot accept remain in the case.",
                )
        ),
        "transfer_restock" to (
            paragraphs(
                "Restockアップグレードが補充元にしている収納から、Gem Caseへ取り込むジェムの条件を編集します。",
                "この方向の「自動転送」をONにし、集めたい種類・ランクの条件を設定してください。搬出側の条件は変更されません。",
                "実際の転送は、同じリュックの有効なRestockアップグレードを使って行います。この画面を開く・設定を変えるだけではジェムは移動しません。",
                "ケースで扱えないジェムや、在庫の上限に達して入らない分は補充元に残ります。",
            ) to
                paragraphs(
                    "Edit which gems may enter Gem Case from storage used by the Restock upgrade.",
                    (
                        "Turn Auto transfer ON for this direction and set the desired type and purity rules. Deposit " +
                            "settings are independent."
                    ),
                    (
                        "An enabled Restock upgrade in this backpack performs the actual transfer. " +
                            "Opening or editing " +
                            "this screen does not move gems."
                    ),
                    "Unsupported gems and amounts beyond the case capacity remain in the source storage.",
                )
        ),
        "transfer_enabled" to (
            paragraphs(
                "今選んでいる方向の転送で、Gem Caseの在庫も対象にするかを切り替えます。",
                "ON：対応するDeposit／Restockアップグレードの転送時に、この画面の条件に合うジェムを扱います。",
                "OFF：その方向の転送では、ケースのジェムを扱いません。選んだ種類・ランクは消さずに保持するため、ONに戻すと同じ条件を使えます。",
                "搬出と補充のON/OFFは独立しています。リュック自体の転送機能や、ジェムをしまう手動ボタンを停止する設定ではありません。",
            ) to
                paragraphs(
                    "Choose whether Gem Case participates in transfers in the currently selected direction.",
                    "ON: the matching Deposit or Restock upgrade may transfer case gems that match this filter.",
                    (
                        "OFF: case gems are not transferred in this direction. Selections are " +
                            "retained and apply again " +
                            "when switched ON."
                    ),
                    (
                        "Deposit and Restock switches are independent. This does not disable the backpack upgrade " +
                            "itself or the manual gem buttons."
                    ),
                )
        ),
        "transfer_mode" to (
            paragraphs(
                "クリックするたびに、今選んでいる方向の転送対象を3つの方式で切り替えます。",
                "「すべてのジェム」：種類・ランクを制限しません。下の選択は保持しますが、転送条件には使いません。",
                "「選んだジェムだけ」：下で選んだ条件に合うジェムだけを転送します。種類もランクも未選択なら、転送対象はありません。",
                "「選んだジェムを除く」：下で選んだ条件に合うジェムを転送せず、それ以外を対象にします。種類もランクも未選択なら、すべてが対象です。",
                "種類とランクを両方選んだ場合は、その組み合わせで判定します。例えば種類Aと「無傷の」を選ぶと、Aの無傷ランクが条件に合います。",
                "片方だけを選んだ場合、もう片方は制限しません。種類AだけならAの全ランク、無傷ランクだけなら全種類の無傷ジェムが条件に合います。",
            ) to
                paragraphs(
                    "Click to cycle between three filters for the currently selected transfer direction.",
                    "All gems: do not restrict type or purity. The selections below are retained but ignored.",
                    (
                        "Only selected gems: transfer gems matching the selections below. If " +
                            "neither types nor purities" +
                            " are selected, nothing matches."
                    ),
                    (
                        "Except selected gems: keep matching gems out of the transfer and allow the" +
                            " others. If neither " +
                            "axis is selected, all gems are allowed."
                    ),
                    (
                        "When both axes have selections, a gem must match both. For example, selecting type A and " +
                            "Flawless matches only Flawless gems of type A."
                    ),
                    (
                        "An empty axis imposes no restriction. Type A alone matches all purities of" +
                            " A; Flawless alone " +
                            "matches Flawless gems of every type."
                    ),
                )
        ),
        "transfer_types" to (
            paragraphs(
                "下のアイコン一覧をジェムの種類に切り替えます。括弧内は、この方向で選択している種類の数です。",
                "アイコンをクリックすると選択・解除できます。金枠とチェックが選択済みを表します。在庫の有無や個数とは関係なく選べます。",
                "ランクを選んでいなければ、選んだ種類の全ランクが条件に合います。ランクも選んでいれば、種類とランクの両方が一致するジェムが条件に合います。",
                "選んだジェムを転送するか、逆に除外するかは、上の「条件」で決まります。「すべてのジェム」の間は、選択を編集できず、転送にも適用しません。",
            ) to
                paragraphs(
                    (
                        "Show gem types in the icon grid. The number in brackets counts selected types for this " +
                            "direction."
                    ),
                    (
                        "Click an icon to select or deselect it. Gold frames and checkmarks show " +
                            "selections, not owned " +
                            "amounts. Unowned types can also be selected."
                    ),
                    (
                        "With no purity selected, every purity of a selected type matches. If purities are selected " +
                            "too, a gem must match both axes."
                    ),
                    (
                        "The Match option determines whether matching gems are transferred or excluded. All gems " +
                            "ignores the selections and disables editing them."
                    ),
                )
        ),
        "transfer_ranks" to (
            paragraphs(
                "下のアイコン一覧をランクに切り替えます。低いランクから順に並び、名前も表示します。括弧内は選択中のランク数です。",
                "アイコンのジェムはランクを示すための見本です。その種類だけを指定するものではありません。金枠とチェックが選択済みを表します。",
                "種類を選んでいなければ、全種類のうち選んだランクが条件に合います。種類も選んでいれば、その種類の、選んだランクだけが条件に合います。",
                "選んだランクを転送するか、逆に除外するかは、上の「条件」で決まります。「すべてのジェム」の間は、選択を編集できず、転送にも適用しません。",
            ) to
                paragraphs(
                    (
                        "Show purities from lowest to highest, with their names. The number in " +
                            "brackets counts selected" +
                            " purities."
                    ),
                    (
                        "The displayed gem is only a sample for the purity. It does not limit the filter to that gem " +
                            "type. Gold frames and checkmarks mark selected purities."
                    ),
                    (
                        "With no type selected, the selected purities match every gem type. If " +
                            "types are selected too, " +
                            "only the selected purities of those types match."
                    ),
                    (
                        "The Match option determines whether matching gems are transferred or excluded. All gems " +
                            "ignores the selections and disables editing them."
                    ),
                )
        ),
        "transfer_search" to (
            paragraphs(
                "名前またはIDの一部を入力すると、今表示している種類／ランクの候補を絞り込めます。",
                "これは候補を探すための検索欄です。入力した文字自体が転送条件になるわけではありません。条件へ加えるには、候補のアイコンを選んでください。",
                "検索で見えなくなった項目も、選択は保持されて転送条件に使われます。検索文字を消すと、隠れていた候補を再び確認できます。",
                "種類とランクを切り替えると検索欄は空になりますが、両方の選択内容は保持されます。",
            ) to
                paragraphs(
                    "Type part of a name or ID to narrow the currently displayed type or purity choices.",
                    (
                        "This search only helps find choices. The typed text is not a transfer rule: select the " +
                            "matching icons to add them to the filter."
                    ),
                    (
                        "Selections hidden by the search remain selected and continue to apply. " +
                            "Clear the text to see " +
                            "all choices again."
                    ),
                    (
                        "Switching between types and purities clears the search text but retains selections on both " +
                            "axes."
                    ),
                )
        ),
        "transfer_pages" to (
            paragraphs(
                "矢印をクリックするか、アイコン一覧の上でマウスホイールを動かすと、画面に収まらない候補を表示できます。",
                "矢印は前後のページへ移動し、ホイールは一覧を行単位で移動します。続きがない側の矢印は押せません。",
                "ページの外にある選択済みの項目も、転送条件に含まれたままです。下の選択数は今のページだけでなく、この方向での選択全体を数えます。",
            ) to
                paragraphs(
                    (
                        "Click the arrows, or scroll the mouse wheel over the icon grid, to browse " +
                            "choices that do not " +
                            "fit on screen."
                    ),
                    (
                        "Arrows move by pages; the wheel moves by rows. An arrow is disabled when there are no more " +
                            "choices in that direction."
                    ),
                    (
                        "Selections on other pages remain part of the filter. The totals count all " +
                            "selections for this " +
                            "direction, not just the current page."
                    ),
                )
        ),
        "selection_rules" to (
            paragraphs(
                "クリックすると、この種類またはランクの選択を切り替えます。金枠とチェックは選択状態であり、在庫があるという意味ではありません。",
                "「選んだジェムだけ」なら条件に合うジェムを転送し、「選んだジェムを除く」なら条件に合うジェムを残して、それ以外を転送します。",
                "種類とランクを両方選ぶと、両方に合う組み合わせが条件です。例：種類Aと「無傷の」なら、Aの無傷ランクが条件に合います。",
                "片方が未選択なら、その軸は制限しません。種類AだけならAの全ランク、無傷ランクだけなら全種類の無傷ジェムが条件に合います。",
                "両方が未選択の場合は、「選んだジェムだけ」では対象なし、「選んだジェムを除く」ではすべてが対象になります。",
                "選択は、今編集している搬出／補充の方向に保存されます。検索やページ移動で見えなくなっても、解除するまで条件に残ります。",
            ) to
                paragraphs(
                    (
                        "Click to toggle this type or purity. Gold frames and checkmarks show selection, not whether " +
                            "any gems are owned."
                    ),
                    (
                        "Only selected gems transfers matches. Except selected gems keeps matches " +
                            "out of the transfer " +
                            "and allows all others."
                    ),
                    (
                        "If both types and purities are selected, a gem must match both. Type A " +
                            "plus Flawless matches " +
                            "only Flawless gems of A."
                    ),
                    (
                        "An empty axis is unrestricted. Type A alone matches all purities of A; " +
                            "Flawless alone matches " +
                            "Flawless gems of every type."
                    ),
                    (
                        "If both axes are empty, Only selected gems matches none, while Except " +
                            "selected gems allows all" +
                            " gems."
                    ),
                    (
                        "Selections belong to the current Deposit or Restock direction. Searching or changing pages " +
                            "does not deselect hidden entries."
                    ),
                )
        ),
        "selection_unknown" to (
            paragraphs(
                "この項目は保存された条件に残っていますが、今のMOD構成では種類またはランクの定義を読み込めません。",
                "選択を解除することはできます。定義のない項目を、新たに選択することはできません。",
            ) to
                paragraphs(
                    "This saved filter entry has no available type or purity definition in the current mod setup.",
                    "It can be deselected, but an undefined entry cannot be selected again.",
                )
        ),
    )
