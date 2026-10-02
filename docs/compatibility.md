# 対応構成と版の固定

## 検証の基準

Minecraft 1.21.1／NeoForge 21.1.252／Java 21。Kotlin 2.0.21、KotlinForForge NeoForge 5.5.0、Gradle 8.12.1、ModDevGradle 2.0.148、detekt 1.23.8、ktlint plugin 12.1.2／本体1.5.0、JUnit 5.11.4を維持する。

版の正はGradle properties／catalog。外部配布JARのURL・Modrinth版ID・SHA256・SHA512・sideは[dependency-manifest.json](dependency-manifest.json)に固定する。実取得物のSHA512を配布APIと照合し、SHA256を計算した。動作確認の範囲は[検証記録](verification.md)を参照し、別版への対応を断定しない。

| MOD | 公開成果物の版 | 検証環境への導入 | 取得元 |
| --- | --- | --- | --- |
| apotheosis | 1.21.1-8.9.0 | client／server | [配布版](https://modrinth.com/mod/apotheosis/version/veL1ccTb) |
| apothic-attributes | 1.21.1-2.10.0 | client／server | [配布版](https://modrinth.com/mod/apothic-attributes/version/7r6iuon2) |
| apothic-enchanting | 1.21.1-1.6.2 | client／server | [配布版](https://modrinth.com/mod/apothic-enchanting/version/2vH7csNR) |
| apothic-spawners | 1.21.1-1.4.0 | client／server | [配布版](https://modrinth.com/mod/apothic-spawners/version/pWfxcfO2) |
| jei | 19.51.0.418 | 開発・試遊client限定 | [配布版](https://modrinth.com/mod/jei/version/ufHUqt9b) |
| patchouli | 1.21-87-neoforge | client／server | [配布版](https://modrinth.com/mod/patchouli/version/MY12jUBD) |
| placebo | 1.21.1-9.9.2 | client／server | [配布版](https://modrinth.com/mod/placebo/version/1Ypo4tf4) |
| sophisticated-backpacks | 1.21.1-3.26.6.2174 | client／server | [配布版](https://modrinth.com/mod/sophisticated-backpacks/version/pJxNzk4X) |
| sophisticated-core | 1.21.1-1.5.2.2343 | client／server | [配布版](https://modrinth.com/mod/sophisticated-core/version/blXGSmAb) |
| gateways | 1.21.1-5.1.0 | datagen限定 | [配布版](https://modrinth.com/mod/gateways-to-eternity/version/FNtkt80G) |
| kotlin-for-forge | 5.5.0 | client／server | [配布版](https://modrinth.com/mod/kotlin-for-forge/version/tdvjylfz) |

Apotheosis／Sophisticated Backpacks／Sophisticated Coreを本アドオンの直接必須依存としてmetadataに宣言する。Apotheosisの配布JARはPlacebo 9.9.2以上、Apothic Attributes 2.10.0以上を必須とする。Apothic Spawners／EnchantingはJAR内ではoptionalだが、Modrinth APIと作者の公開依存一覧はrequiredとするため、初期検証では両方を含める。Patchouliも公開依存一覧に従って含める。この差を理由に本アドオンの必須依存を増やさない。

SophisticatedのJAR名にはbuild番号があるが、MOD metadataの版はBackpacks `3.26.6`／Core `1.5.2`。本アドオンのversionRangeはこの宣言版を使用し、build番号までの固定はcatalogと配布JAR hashで行う。対応宣言を未検証の将来版へ広げない。

GradleはModrinth Mavenを`maven.modrinth`グループに限定して使う。公開版番号から取得した本番依存JARはmanifestの配布物と照合する。JEIは同じ`19.51.0.418`でFabric版も存在するため、NeoForge版ID `ufHUqt9b`で取得する。単なる版番号指定ではFabric JARが選ばれ、クライアント起動時にスキップされたことを実測した。

## 実行別の開発依存

- clientDevelopment source setはmainの依存・runtime classpathを継承し、NeoForge版JEIを追加する。runClientだけで使用する。
- dataDevelopment source setは同じ基盤へGatewaysを追加し、runDataだけで使用する。Apotheosis 8.9.0のGatherDataEventは任意連携のGatewayRegistryなどを参照するため、GatewaysなしのdatagenはNoClassDefFoundErrorになる。
- runServer／GameTest／通常JARにはJEIとGatewaysを入れない。通常JARにはcoreと必要resourcesだけを含める。

ModDevGradle 2.0.148のadditionalRuntimeClasspathConfigurationはlegacy classpathへ入れるため、外部MODの検出用には使わない。この経路ではGatewaysがMod Listへ入らず、Placeboの型を解決できなかった。source setの実行用runtimeとmainのMinecraft classpathを両方継承する必要がある。

一次資料: [ModDevGradle 2.0.148 sources](https://maven.neoforged.net/releases/net/neoforged/moddev-gradle/2.0.148/moddev-gradle-2.0.148-sources.jar)、[Apotheosisの固定ソース](https://github.com/Shadows-of-Fire/Apotheosis/blob/7dd7cec26830074f1745529eb8f1f533092733d0/src/main/java/dev/shadowsoffire/apotheosis/Apotheosis.java)、[作者の依存設定](https://github.com/Shadows-of-Fire/Apotheosis/blob/7dd7cec26830074f1745529eb8f1f533092733d0/gradle.properties)。JAR metadataも実物を照合した。

## Kotlinと再現性

KFFのMaven開発用aggregateは配布用JARと異なる。専用サーバーと試遊clientでは作者配布の`kotlinforforge-5.5.0-all.jar`を使う。配布実行時stdlibは2.0.10、開発compilerは2.0.21であり、通常JARの起動を別に確認する。coreはKFFに依存しない。

Wrapperのchecksumと既存のcore／buildSrc lockfileを維持する。NeoForge側の内部生成artifactを含めた全依存のchecksum verificationは未導入。coreは連携MOD・関連MODの禁止prefixを追加し、本番classpathとbytecodeを検査する。

Java 21は既存のEclipse Adoptium 21.0.12.1を読み取り利用した。GRADLE_USER_HOMEはこのrepoの`.local/gradle`で、既存cacheのコピーをseedにした。起動環境・ログ・新規ワールドはこのrepoの`.local`へ置き、親packの環境は変更していない。
