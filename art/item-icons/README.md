# Itemアイコン原稿

16×16の自作pixel原稿とpaletteはrender.pyに保存する。下位はガラス蓋のケース、上位は同じケース＋輪郭外の工具、変換は強化部品。色だけに頼らず輪郭でも区別する。外部画像素材は使用していない。

repo rootから `python3 art/item-icons/render.py` で本番texture三PNGと.local/verification/acquisitionの原寸／8倍プレビューを再生成する。Python標準ライブラリだけを使い、通常Gradle buildは本scriptへ依存しない。原稿とscriptは通常JARへ同梱しない。

PNGはRGBA、透過あり、16×16。実clientのGUI scale 2／3／4、ja_jp／en_usでの視認性はdocs/client-acceptance.mdの別検収項目であり、原稿プレビューの確認だけでは合格にしない。
