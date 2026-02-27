#!/usr/bin/env bash
set -euo pipefail

echo "Starting pre-release checks..."
echo ""

echo "1. Formatting code..."
npm run format
echo "Code formatted"
echo ""

echo "2.  Running linter..."
npm run lint
echo "Linting passed"
echo ""

echo "3. Running tests..."
npm test
echo "All tests passed"
echo ""

echo "4. Building project..."
npm run build
echo "Build successful"
echo ""

echo "All pre-release checks passed!"
