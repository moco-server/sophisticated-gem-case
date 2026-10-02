# テンプレートの更新とサンプル削除

template-version.txtは元テンプレート版、.template-initialized.jsonは生成先の識別情報。初版は0.1.0。テンプレートは各MODの上流資料として扱い、自動同期や共有Gradle pluginを必須にしない。

更新時はテンプレートrepoの変更目的と対象ファイル、固定版と互換性記録を読み、必要な差分だけ各MODへ取り込む。独立repoの履歴を上書きしない。生成先のMOD ID、package、保存形式、依存MODを維持してCIとクリーン起動を再検証する。template-version.txtの更新だけで適用済みとは扱わず、取り込んだ差分・判断を各MODの設計／検証記録に残す。

サンプルを削除する時は次を一つの変更として扱う。

- ModTemplateのitem登録・creative tab・datagen listenerとModItemsを、実MODの登録へ置き換える。
- TemplateDataGeneratorsのrecipe／翻訳／model、対応するgenerated resources、GameTestを実MODの内容へ置き換える。数量サンプルが不要ならcoreのQuantityRulesと対応testも削除する。
- neoforge/build.gradle.ktsのverifyModJar requiredEntriesを実際のcore class・item class・必要resourceへ変更する。検査を無効化して通さない。
- テスト用structureが不要なら削除する。GameTestには実機能の登録・recipe・失敗条件等を検査するテストを用意する。
- datagenを再生成して古いnamespaceやrecipeの残存を確認し、check、通常JAR検査、GameTest、クリーン起動を実施する。README・design・verificationも更新する。

ゲーム版／loader／Kotlin／KFFの更新は一度に目的を絞る。catalog、Wrapper checksum、依存lock・検証記録を更新し、型解決付きdetektが正常コードを解析できることも確認する。Fabricを追加する場合も、実際のcore利用と両loaderでの検証に基づいて移植性を判断する。
