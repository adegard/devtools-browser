#!/usr/bin/env bash
# Create a NEW GitHub repo from this project and push it.
# Run this inside Termux:
#     cd /sdcard/apps/devtools-browser
#     bash push_to_github.sh [repo-name] [public|private]
# Defaults: repo-name = devtools-browser, visibility = public
set -eu
cd "$(dirname "$0")"

REPO_NAME="${1:-devtools-browser}"
VISIBILITY="${2:-public}"

# /sdcard is a FUSE mount owned by a different uid, so git refuses to operate
# on it unless we mark it as a safe directory.
PROJECT_DIR="$(pwd -P)"
git config --global --add safe.directory "$PROJECT_DIR" 2>/dev/null || true
git config --global --add safe.directory /storage/emulated/0/apps/devtools-browser 2>/dev/null || true

if ! command -v git >/dev/null 2>&1; then
  echo "git is not installed. Run: pkg install git" >&2
  exit 1
fi
if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI is not installed. Run: pkg install gh" >&2
  exit 1
fi
if ! gh auth status >/dev/null 2>&1; then
  echo "Not logged in to GitHub. Run: gh auth login" >&2
  exit 1
fi

if [ ! -d .git ]; then
  git init -b main 2>/dev/null || { git init; git checkout -b main; }
fi

git add -A
if git commit -m "DevTools Browser: WebView browser with on-device Eruda devtools, popup blocker and status-bar insets"; then
  echo "Committed."
else
  echo "(nothing new to commit)"
fi
git branch -M main

if git remote get-url origin >/dev/null 2>&1; then
  echo "Remote origin already set; pushing..."
  git push -u origin main
else
  echo "Creating GitHub repo '${REPO_NAME}' (${VISIBILITY}) and pushing..."
  gh repo create "$REPO_NAME" "--${VISIBILITY}" --source=. --remote=origin --push
fi

echo
echo "Done. Repo: https://github.com/$(gh api user -q .login 2>/dev/null || echo '<user>')/${REPO_NAME}"
