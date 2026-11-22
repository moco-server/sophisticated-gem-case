#!/usr/bin/env bash
set -euo pipefail

jar=".local/artifact/sophisticated_gem_case-$RELEASE_VERSION.jar"
test -f "$jar"
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT

node scripts/release-version.mjs notes > "$scratch/notes.md"

if gh api "repos/$GITHUB_REPOSITORY/git/ref/tags/$RELEASE_TAG" --silent 2>"$scratch/gh-error"; then
  target=$(gh api "repos/$GITHUB_REPOSITORY/commits/$RELEASE_TAG" --jq .sha)
  if [[ "$target" != "$GITHUB_SHA" ]]; then
    echo "Tag $RELEASE_TAG points to $target instead of $GITHUB_SHA" >&2
    exit 1
  fi
elif [[ $(cat "$scratch/gh-error") != 'gh: Not Found (HTTP 404)' ]]; then
  cat "$scratch/gh-error" >&2
  exit 1
fi

if draft=$(gh release view "$RELEASE_TAG" --json isDraft --jq .isDraft 2>"$scratch/gh-error"); then
  if [[ "$draft" == true ]]; then
    target=$(gh release view "$RELEASE_TAG" --json targetCommitish --jq .targetCommitish)
    if [[ "$target" != "$GITHUB_SHA" ]]; then
      echo "Draft $RELEASE_TAG targets $target instead of $GITHUB_SHA" >&2
      exit 1
    fi
  fi
elif [[ $(cat "$scratch/gh-error") == 'release not found' ]]; then
  gh release create "$RELEASE_TAG" --target "$GITHUB_SHA" --draft \
    --title "$RELEASE_TAG" --notes-file "$scratch/notes.md"
  draft=true
else
  cat "$scratch/gh-error" >&2
  exit 1
fi

if [[ "$draft" == true ]]; then
  gh release upload "$RELEASE_TAG" "$jar" --clobber
else
  gh release download "$RELEASE_TAG" --pattern "${jar##*/}" --dir "$scratch"
  cmp "$jar" "$scratch/${jar##*/}"
fi

# Maven の公開後に Release の公開だけが失敗しても、同じ版を再登録せずに再実行できる。
package_url="https://maven.pkg.github.com/${GITHUB_REPOSITORY,,}/org/mocoserver/sophisticated-gem-case/$RELEASE_VERSION/sophisticated-gem-case-$RELEASE_VERSION"
jar_status=$(curl --silent --show-error --location --retry 3 --user "$GITHUB_ACTOR:$GITHUB_TOKEN" \
  --output "$scratch/package.jar" --write-out '%{http_code}' "$package_url.jar")
pom_status=$(curl --silent --show-error --location --retry 3 --user "$GITHUB_ACTOR:$GITHUB_TOKEN" \
  --output "$scratch/package.pom" --write-out '%{http_code}' "$package_url.pom")

case "$jar_status:$pom_status" in
  200:200) cmp "$jar" "$scratch/package.jar" ;;
  404:404) ./gradlew :neoforge:publishModPublicationToGitHubPackagesRepository "-PreleaseJar=$jar" ;;
  *) echo "Unexpected GitHub Packages response (JAR $jar_status, POM $pom_status)" >&2; exit 1 ;;
esac

if [[ "$draft" == true ]]; then
  gh release edit "$RELEASE_TAG" --draft=false
fi
