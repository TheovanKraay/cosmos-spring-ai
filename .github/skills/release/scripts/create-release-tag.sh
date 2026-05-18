#!/usr/bin/env bash
# create-release-tag.sh — Create and push a single annotated release tag.
# Hardened: refuses to tag unless validate-release.sh --phase tag passes.
#
# Usage: create-release-tag.sh --module <name> --version <version> [--no-push]
set -euo pipefail

MODULE=""
VERSION=""
PUSH="true"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --module)  MODULE="$2"; shift 2 ;;
        --version) VERSION="$2"; shift 2 ;;
        --no-push) PUSH="false"; shift ;;
        *) echo "Unknown argument: $1" >&2; exit 1 ;;
    esac
done

if [[ -z "$MODULE" || -z "$VERSION" ]]; then
    echo "Usage: create-release-tag.sh --module <name> --version <version> [--no-push]" >&2
    exit 1
fi

REPO_ROOT="$(git rev-parse --show-toplevel)"
TAG="${MODULE}-v${VERSION}"

echo "=== Pre-tag validation ==="
"$REPO_ROOT/.github/skills/release/scripts/validate-release.sh" \
    --phase tag --module "$MODULE" --version "$VERSION"

echo ""
echo "=== Creating annotated tag $TAG ==="
git tag -a "$TAG" -m "Release ${MODULE} ${VERSION}"
echo "✅ Created $TAG at $(git rev-parse HEAD)"

if [[ "$PUSH" != "true" ]]; then
    echo ""
    echo "ℹ️  --no-push specified. Push manually with:"
    echo "    git push origin $TAG"
    exit 0
fi

echo ""
echo "=== Pushing $TAG to origin ==="
echo "    (Single-tag push — pushing multiple tags in one command can silently"
echo "     fail to trigger the GitHub Actions release workflow.)"
git push origin "$TAG"
echo "✅ Pushed $TAG"

# Build the Actions URL for the release workflow.
ORIGIN_URL=$(git remote get-url origin 2>/dev/null || echo "")
if [[ "$ORIGIN_URL" =~ github\.com[:/](.+)/(.+?)(\.git)?$ ]]; then
    OWNER="${BASH_REMATCH[1]}"
    REPO="${BASH_REMATCH[2]}"
    ACTIONS_URL="https://github.com/${OWNER}/${REPO}/actions/workflows/release.yml"
else
    ACTIONS_URL="<check the Actions tab on GitHub>"
fi

cat <<EOF

=== Release Tag Summary ===
  Tag:     $TAG
  Commit:  $(git rev-parse HEAD)
  Module:  $MODULE
  Version: $VERSION
  Actions: $ACTIONS_URL

Next steps:
  1. Watch the 'parse-tag' and 'unit-tests' jobs in the Actions tab.
  2. The 'publish' job pauses on the 'production' environment.
     Approve at: Actions → Release → (your run) → Review deployments → Approve.
  3. After approval, a GitHub Release is created at:
     https://github.com/${OWNER:-AzureCosmosDB}/${REPO:-spring-ai}/releases/tag/$TAG
EOF
