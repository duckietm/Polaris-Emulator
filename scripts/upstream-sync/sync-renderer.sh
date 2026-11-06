#!/usr/bin/env bash
# Brings duckietm/Octane-Renderer Dev into the renderer that pairs with this tree.
#
# The renderer is not renamed: only its copy of protocol/packet-field-contracts.json follows this
# emulator, whose Java side names polaris.* paths. So upstream is merged as it is, the contract
# is set to this repository's copy (a conflict there is expected and resolved that way), and the
# contract tests plus the cross-check against this emulator decide. Nothing is pushed.
#
# usage: scripts/upstream-sync/sync-renderer.sh <renderer repo> [--remote origin] [--branch Dev]
set -u
here=$(cd "$(dirname "$0")" && pwd)
emulator=$(cd "$here/../.." && pwd)
renderer=${1:?usage: sync-renderer.sh <renderer repo> [--remote origin] [--branch Dev]}
shift
remote=origin
branch=Dev
while [ $# -gt 0 ]; do
    case $1 in
        --remote) remote=$2; shift 2 ;;
        --branch) branch=$2; shift 2 ;;
        *) echo "unknown option $1" >&2; exit 2 ;;
    esac
done
renderer=$(cd "$renderer" && pwd)
contract=protocol/packet-field-contracts.json

git -C "$renderer" fetch -q "$remote" "$branch" || { echo "fetch of $remote/$branch failed" >&2; exit 1; }
if [ -n "$(git -C "$renderer" status --porcelain --untracked-files=no)" ]; then
    echo "Commit or set aside the renderer's tracked changes first." >&2
    exit 1
fi

behind=$(git -C "$renderer" rev-list --count "HEAD..$remote/$branch")
if [ "$behind" -gt 0 ]; then
    echo "Merging $behind upstream commit(s):"
    git -C "$renderer" log --oneline --no-merges "HEAD..$remote/$branch" | sed 's/^/  /'
    git -C "$renderer" merge --no-edit --no-commit "$remote/$branch" >/dev/null 2>&1
    cp "$emulator/$contract" "$renderer/$contract"
    git -C "$renderer" add "$contract"
    if git -C "$renderer" diff --name-only --diff-filter=U | grep -q .; then
        echo "Stopped: conflicts outside the packet contract:" >&2
        git -C "$renderer" diff --name-only --diff-filter=U | sed 's/^/  /' >&2
        exit 3
    fi
    git -C "$renderer" commit -q --no-edit
    echo "Merged as $(git -C "$renderer" rev-parse --short HEAD)."
else
    cp "$emulator/$contract" "$renderer/$contract"
    echo "Up to date with $remote/$branch; contract copied from the emulator."
fi

cd "$renderer" || exit 1
yarn vitest run packages/communication/src/messages/contracts --testTimeout=180000 || { echo "contract tests failed" >&2; exit 5; }
node scripts/verify-packet-contract-manifest.mjs --emulator "$emulator" || { echo "the two contracts differ" >&2; exit 5; }
git status --short -- "$contract"
echo "Done. Commit the contract if it changed, run the renderer's own checks, then push."
