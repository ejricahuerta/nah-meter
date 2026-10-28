#!/usr/bin/env bash
# Claude Code PreToolUse hook (Bash): main and master only change through merged pull requests.
# Blocks `git push` that targets main/master, or a bare `git push` while on main/master.
# Exit 2 = block, and the message on stderr goes back to Claude.
input=$(cat)

# Only look at git push commands.
echo "$input" | grep -Eq 'git[^"]*[[:space:]]push([[:space:]]|")' || exit 0

block() {
  echo "Blocked: main and master only change by merging a pull request. Push a branch and open a PR instead ($1)." >&2
  exit 2
}

# The command text, roughly: everything after "command":"
cmd=$(echo "$input" | sed -n 's/.*"command"[[:space:]]*:[[:space:]]*"\(\([^"\\]\|\\.\)*\)".*/\1/p')
[ -z "$cmd" ] && cmd="$input"

# Explicit target: `git push origin main`, `HEAD:main`, `:refs/heads/master`, `--all`, `--mirror`.
if echo "$cmd" | grep -Eq 'push.*([[:space:]:/]|^)(main|master)([[:space:]]|$|")'; then block "pushes to main/master"; fi
if echo "$cmd" | grep -Eq 'push.*--(all|mirror)'; then block "--all/--mirror would push main"; fi

# Bare push while on main/master.
dir=$(echo "$input" | sed -n 's/.*"cwd"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
branch=$(git -C "${dir:-.}" rev-parse --abbrev-ref HEAD 2>/dev/null)
if [ "$branch" = "main" ] || [ "$branch" = "master" ]; then
  # Allowed only if it names another branch explicitly, e.g. `git push -u origin my-branch`.
  if ! echo "$cmd" | grep -Eq 'push.*[[:space:]](origin|upstream)[[:space:]]+[^-[:space:]]'; then
    block "you are on $branch"
  fi
fi
exit 0
