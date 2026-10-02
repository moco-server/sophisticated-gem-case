# 対応構成と版の固定

初期検証はMinecraft 1.21.1／NeoForge 21.1.252／Java 21。対応範囲は検証結果から判断する。

| ツール | 固定版 |
| --- | --- |
| Gradle | 8.12.1 |
| ModDevGradle | 2.0.148 |
| Kotlin | 2.0.21 |
| KotlinForForge NeoForge | 5.5.0 |
| detekt | 1.23.8 |
| ktlint plugin／本体 | 12.1.2／1.5.0 |
| JUnit | 5.11.4 |

KFF 5.5.0の公開module metadataはKotlin 2.0.10を要求する。開発コンパイラ・stdlibは同世代の2.0.21に揃え、detekt 1.23.8のcompiler版と一致させた。KFFが提供するcoroutines等は実行環境の構成要素であり、サンプルでは使用しない。coreはKFFに依存しない。

一次資料: [KFF metadata](https://thedarkcolour.github.io/KotlinForForge/thedarkcolour/kfflang-neoforge/5.5.0/kfflang-neoforge-5.5.0.module)、[detekt互換表](https://detekt.dev/docs/1.23.8/introduction/compatibility/)、[NeoForge 1.21.1](https://docs.neoforged.net/docs/1.21.1/gettingstarted/)。

Kotlinコンパイルはin-processとし、独立Kotlin daemonのユーザー共通書き込み先を使わない。core単体テスト、型解決付きdetekt、通常JARビルドは実行成功。VarCouldBeValの故意違反も検出した。起動・配布用環境・クライアント検収は後続の記録を参照。

Wrapperは配布SHA256を固定する。初版では動的版指定を避け、coreとbuildSrcのcompile/runtimeおよびtest compile/runtime classpathをGradle lockfileで固定する。変更時は両buildのcheck --write-locksを明示実行する。loaderのModDevGradle内部configurationとOS別の実行用artifactはlocking対象にしていない。ModDevGradleが生成するMinecraft artifactまで一律checksum検証に入れると再生成artifactが混ざるため、追加のverificationは手書きせず適用範囲を確認して導入する。

追加の全artifact checksum verificationは初版では未導入。Wrapper checksumと固定依存、core／buildSrc lock、実取得物のhash記録を使う。Minecraft生成artifactとOS別nativeの検証範囲を確認してから追加する。

配布環境では[作者のKotlinForForge 5.5.0配布JAR](https://modrinth.com/mod/kotlin-for-forge/version/tdvjylfz)を使う。Mavenのkotlinforforge-neoforge-5.5.0-all.jarはstdlib等のみの開発用aggregateであり、単体ではlanguage providerを含まない。Gradle側では推移依存のkfflang／kffmodも解決されるためdev起動が成功する。配布用kotlinforforge-5.5.0-all.jarとは区別する。配布実行時stdlibは2.0.10、開発compiler／core stdlibは2.0.21で、サンプルの通常JAR起動も別に検証する。
