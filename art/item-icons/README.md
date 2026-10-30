# Itemアイコン原稿

16×16の自作pixel原稿とpaletteはrender.pyに保存する。採用済みの幅広い縦長六角形を3種の共通マークにする。結晶のbboxは(4,3)〜(11,12)、中心は(7.5,7.5)。上下左右に対称で、下位は濃茶の単色シルエットだけを描く。

基板は三item modelのlayer0から`sophisticatedbackpacks:item/upgrade_base`を参照し、配布PNGをlayer1へ重ねる。元MODの基板画像は本アドオンへ複製しない。上位には他の上位アップグレードに合わせた金色の四隅、変換itemには右下の小さな上向き印を添える。結晶マークの位置と画素は共通のままにする。

repo rootから `python3 art/item-icons/render.py` で本番の透過PNG三枚と.local/verification/item-iconsの原寸／8倍プレビューを再生成する。このプレビューは記号レイヤーのみ。基板との合成とゲーム内表示は別に確認する。

Python標準ライブラリだけを使い、通常Gradle buildは本scriptへ依存しない。原稿とscriptは通常JARへ同梱しない。PNGはRGBA、透過あり、16×16。検証結果はdocs/verification.mdへ記録する。
