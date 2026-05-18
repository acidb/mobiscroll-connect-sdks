#!/usr/bin/env bash
# Tag and push a release for a single SDK.
#
# Workflow:
#   1. Bump version in the SDK's manifest file(s) (calls bump-version.sh).
#   2. Commit the bump.
#   3. Create tag <sdk>-v<version>.
#   4. Push the commit and tag (when a remote named 'origin' exists).
#
# Usage: scripts/release.sh <sdk> <version>
#   <sdk>     node | python | php | dotnet | java | go
#   <version> a version string without 'v', e.g. 1.0.2
#
# For PHP, no file changes are needed — only the tag.
#
# Tag format: <sdk>-v<version>, except for Go which must use sdks/go/v<version>
# because the Go module proxy requires the module path as the tag prefix.

set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <sdk> <version>" >&2
  exit 2
fi

SDK="$1"
VERSION="$2"
case "$SDK" in
  go) TAG="sdks/go/v${VERSION}" ;;
  *)  TAG="${SDK}-v${VERSION}" ;;
esac
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

cd "$ROOT"

if [[ -n "$(git status --porcelain)" ]]; then
  echo "error: working tree is not clean. Commit or stash changes first." >&2
  exit 1
fi

if git rev-parse "$TAG" >/dev/null 2>&1; then
  echo "error: tag $TAG already exists." >&2
  exit 1
fi

echo "==> Bumping version for $SDK to $VERSION"
"$ROOT/scripts/bump-version.sh" "$SDK" "$VERSION"

if [[ -n "$(git status --porcelain)" ]]; then
  echo "==> Committing version bump"
  git add -A
  git commit -m "chore($SDK): release $TAG"
else
  echo "==> No file changes (PHP is tag-driven); skipping commit"
fi

echo "==> Creating tag $TAG"
git tag -a "$TAG" -m "$SDK $VERSION"

if git remote get-url origin >/dev/null 2>&1; then
  echo "==> Pushing to origin"
  git push origin HEAD
  git push origin "$TAG"
else
  echo "==> No 'origin' remote configured. Push manually with:"
  echo "      git push origin HEAD && git push origin $TAG"
fi

echo "==> Done. Tag $TAG created."
