#!/usr/bin/env bash
# Bump the version of a single SDK in place. Does NOT commit, tag, or push.
#
# Usage: scripts/bump-version.sh <sdk> <version>
#   <sdk>     one of: node, python, php, dotnet
#   <version> a version string without the 'v' prefix, e.g. 1.0.2
#
# Examples:
#   scripts/bump-version.sh node 1.0.2
#   scripts/bump-version.sh python 0.2.0

set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <sdk> <version>" >&2
  echo "  sdk: node | python | php | dotnet" >&2
  exit 2
fi

SDK="$1"
VERSION="$2"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

case "$SDK" in
  node)
    FILE="$ROOT/sdks/node/package.json"
    # Update only the top-level "version" key (line 3 region, before "description").
    # Use Node to do this safely.
    node -e "
      const fs = require('fs');
      const p = '$FILE';
      const j = JSON.parse(fs.readFileSync(p, 'utf8'));
      j.version = '$VERSION';
      fs.writeFileSync(p, JSON.stringify(j, null, 2) + '\n');
    "
    echo "Updated $FILE → version $VERSION"
    ;;

  python)
    FILE_PYPROJECT="$ROOT/sdks/python/pyproject.toml"
    FILE_INIT="$ROOT/sdks/python/mobiscroll_connect/__init__.py"
    # pyproject.toml: line `version = "X"` under [project]
    sed -i.bak -E "s/^version = \"[^\"]+\"/version = \"$VERSION\"/" "$FILE_PYPROJECT"
    rm -f "$FILE_PYPROJECT.bak"
    # __init__.py: line `__version__ = "X"`
    sed -i.bak -E "s/^__version__ = \"[^\"]+\"/__version__ = \"$VERSION\"/" "$FILE_INIT"
    rm -f "$FILE_INIT.bak"
    echo "Updated $FILE_PYPROJECT → version $VERSION"
    echo "Updated $FILE_INIT → __version__ $VERSION"
    ;;

  php)
    # Packagist resolves PHP versions from git tags only. Nothing to bump in files.
    echo "PHP versions are tag-driven (Packagist reads git tags). Nothing to update in files."
    echo "Tag the release with scripts/release.sh php $VERSION when ready."
    ;;

  dotnet)
    FILE="$ROOT/sdks/dotnet/src/Mobiscroll.Connect/Mobiscroll.Connect.csproj"
    sed -i.bak -E "s|<Version>[^<]+</Version>|<Version>$VERSION</Version>|" "$FILE"
    rm -f "$FILE.bak"
    echo "Updated $FILE → <Version>$VERSION</Version>"
    ;;

  *)
    echo "unknown sdk: $SDK (expected: node | python | php | dotnet)" >&2
    exit 2
    ;;
esac
