#!/usr/bin/env bash
# Brings duckietm/Polaris-Emulator dev into this renamed tree.
#
# Every file upstream changed since the last sync is exported twice (as it was at BASE and as it
# is now), both copies go through the whole rename chain in maps/, and the result is merged
# three-way into the current file; new upstream files are translated and added. Import-order
# and formatting conflicts are resolved automatically; anything else stops with the conflict
# marked for review. Then the tree is formatted, compiled and checked by the gates that guard
# plugins and packets. Nothing is committed: review the diff, commit, then run with --done to
# record the new base.
#
# usage: scripts/upstream-sync/sync.sh [--remote origin] [--branch dev] [--skip-tests] [--done]
set -u
export MSYS_NO_PATHCONV=1   # Git Bash would rewrite "<rev>:.github/..." into a Windows path

here=$(cd "$(dirname "$0")" && (pwd -W 2>/dev/null || pwd))
repo=$(cd "$here/../.." && (pwd -W 2>/dev/null || pwd))
maps="$here/maps"
remote=origin
branch=dev
skip_tests=0
done_only=0
while [ $# -gt 0 ]; do
    case $1 in
        --remote) remote=$2; shift 2 ;;
        --branch) branch=$2; shift 2 ;;
        --skip-tests) skip_tests=1; shift ;;
        --done) done_only=1; shift ;;
        *) echo "unknown option $1" >&2; exit 2 ;;
    esac
done

git -C "$repo" fetch -q "$remote" "$branch" || { echo "fetch of $remote/$branch failed" >&2; exit 1; }
base=$(cat "$here/BASE")
up=$(git -C "$repo" rev-parse "$remote/$branch")

if [ $done_only = 1 ]; then
    [ -f "$here/PENDING" ] || { echo "No sync is pending." >&2; exit 1; }
    mv "$here/PENDING" "$here/BASE"
    echo "BASE is now $(cat "$here/BASE"). Commit scripts/upstream-sync/BASE with the sync."
    exit 0
fi
if [ "$base" = "$up" ]; then
    echo "Up to date with $remote/$branch ($up)."
    exit 0
fi
if [ -n "$(git -C "$repo" status --porcelain --untracked-files=no)" ]; then
    echo "Commit or set aside the tracked changes first: the merge writes into the working tree." >&2
    exit 1
fi

echo "$up" > "$here/PENDING"
echo "Upstream commits since the last sync:"
git -C "$repo" log --oneline --no-merges "$base..$up" | sed 's/^/  /'

work=$(cd "$(mktemp -d)" && (pwd -W 2>/dev/null || pwd))
mkdir -p "$work/base" "$work/up"

# Upstream path -> path in this tree: packages, renamed types and test classes, moved files.
newpath() {
    printf '%s\n' "$1" |
        sed -E 's#/com/eu/habbo/habbohotel/#/polaris/hotel/#; s#/com/eu/habbo/#/polaris/#; s#/habboway/#/hotelway/#' |
        perl -e 'my %m; for my $f (@ARGV) { open my $h, "<", $f or next; while (<$h>) { s/\r?\n$//; my ($o, $n) = split /\t/; $m{$o} = $n if $n } }
                 while (my $p = <STDIN>) { chomp $p; if ($p =~ m{^(.*/)([A-Za-z0-9]+)\.java$} && exists $m{$2}) { $p = "$1$m{$2}.java" } print "$p\n" }' \
            "$maps/types.tsv" "$maps/test-types.tsv" "$maps/composers.tsv" |
        perl -e 'my %mv; open my $h, "<", $ARGV[0] or die; while (<$h>) { s/\r?\n$//; my ($o, $n) = split /\t/; $mv{$o} = $n }
                 while (my $p = <STDIN>) { chomp $p; $p = $mv{$p} if exists $mv{$p}; print "$p\n" }' "$maps/moves.tsv"
}

packages() {
    perl -0pi -e '
        s#com([./])eu([./])habbo([./])habbohotel([./;"\$ )\*`]|$)#polaris$3hotel$4#mg;
        s#com([./])eu([./])habbo([./;"\$ )\*`]|$)#polaris$3#mg;
        s#"com",(\s*)"eu",(\s*)"habbo",(\s*)"habbohotel"#"polaris",$3"hotel"#g;
        s#"com",(\s*)"eu",(\s*)"habbo"#"polaris"#g;
        s#\bhabbohotel([/.])#hotel$1#g;' "$@"
}

: > "$work/map.txt"
git -C "$repo" diff --name-status --no-renames "$base" "$up" | while IFS=$'\t' read -r status path; do
    [ -z "$path" ] && continue
    case $path in Emulator/*) rel=${path#Emulator/}; prefix=Emulator/ ;; *) rel=$path; prefix= ;; esac
    new=$(newpath "$rel")
    for side in base up; do
        commit=$base; [ $side = up ] && commit=$up
        if git -C "$repo" cat-file -e "$commit:$path" 2>/dev/null; then
            mkdir -p "$work/$side/$(dirname "$new")"
            git -C "$repo" show "$commit:$path" > "$work/$side/$new"
        fi
    done
    printf '%s\t%s\t%s\n' "$status" "$prefix" "$new" >> "$work/map.txt"
done

empty="$work/empty.tsv"; : > "$empty"
contract=protocol/packet-field-contracts.json
for side in base up; do
    (
        cd "$work/$side" || exit 1
        java=$(find . -name '*.java' | sed 's#^\./##')
        other=$(find . -type f ! -name '*.java' ! -path '*/db/migration/*' | sed 's#^\./##')
        [ -n "$java$other" ] && packages $java $other
        # The packet contract also holds the renderer's names: only its Java side is renamed, on
        # exact className/symbol values and on Emulator/ paths.
        if [ -f "$contract" ]; then
            perl -i -e '
                my %m; for my $f (splice @ARGV, 0, 3) { open my $h, "<", $f or die; while (<$h>) { s/\r?\n$//; my ($o, $n) = split /\t/; $m{$o} = $n if $n && $o ne $n } }
                open my $mv, "<", shift @ARGV or die; my %moves;
                while (<$mv>) { s/\r?\n$//; my ($o, $n) = split /\t/; $moves{"Emulator/$o"} = "Emulator/$n" }
                my $re = join "|", sort { length $b <=> length $a } map { quotemeta } keys %m;
                while (<>) {
                    s{("(?:className|symbol)": ")($re)(")}{$1$m{$2}$3}g;
                    s{("path": "Emulator/[^"]*/)($re)(\.java")}{$1$m{$2}$3}g;
                    s{("path": ")(Emulator/[^"]+)(")}{$1 . ($moves{$2} // $2) . $3}ge;
                    print }' "$maps/types.tsv" "$maps/composers.tsv" "$maps/constants.tsv" "$maps/moves.tsv" "$contract"
        fi
        [ -n "$java" ] || exit 0
        perl "$here/rename.pl" "$maps/types.tsv" "$maps/methods.tsv" $java >/dev/null 2>&1
        main=$(printf '%s\n' $java | grep '^src/main/' || true)
        test=$(printf '%s\n' $java | grep '^src/test/' || true)
        [ -n "$main" ] && perl "$here/fields.pl" "$maps/field-owners.txt" $main >/dev/null
        [ -n "$test" ] && FIELD_RECEIVERS='[a-z][A-Za-z0-9]*Event|event|rolled|login' perl "$here/fields.pl" "$empty" $test >/dev/null
        perl "$here/rename.pl" "$empty" "$maps/constants.tsv" $java >/dev/null 2>&1
        CODE_ONLY=1 perl "$here/rename.pl" "$empty" "$maps/identifiers.tsv" $java >/dev/null 2>&1
        perl "$here/rename.pl" "$maps/composers.tsv" "$empty" $java >/dev/null 2>&1
        # Files moved to another package: their imports and qualified names follow them.
        perl -i -e '
            open my $m, "<", shift @ARGV or die; my @moves;
            while (<$m>) { s/\r?\n$//; my ($o, $n) = split /\t/;
                for ($o, $n) { s#^src/(main|test)/java/##; s#\.java$## }
                push @moves, [$o, $n] if $o ne $n }
            while (<>) { for my $mv (@moves) { my ($o, $n) = @$mv; (my $od = $o) =~ s#/#.#g; (my $nd = $n) =~ s#/#.#g;
                s/\b\Q$od\E\b/$nd/g; s/\b\Q$o\E\b/$n/g } print }' "$maps/moves.tsv" $java $other
    )
done

conflicts=()
added_tests=()
while IFS=$'\t' read -r status prefix new; do
    target="$repo/$prefix$new"
    case $status in
        A)
            mkdir -p "$(dirname "$target")"
            cp "$work/up/$new" "$target"
            echo "added    $prefix$new"
            case $new in src/test/java/*Test.java) added_tests+=("$(basename "$new" .java)") ;; esac
            ;;
        D)
            rm -f "$target"
            echo "deleted  $prefix$new"
            ;;
        M)
            if [ ! -f "$target" ]; then echo "MISSING  $prefix$new (moved here without a moves.tsv line?)"; conflicts+=("$prefix$new"); continue; fi
            if [ "$prefix$new" = "$contract" ]; then
                # Generated from the code: take upstream's, translated; PacketContractCatalogTest decides.
                cp "$work/up/$new" "$target"
                echo "taken    $prefix$new (translated upstream copy; the contract tests check it)"
                continue
            fi
            git merge-file -q "$target" "$work/base/$new" "$work/up/$new"
            rc=$?
            if [ $rc -eq 0 ]; then
                echo "merged   $prefix$new"
            elif ! grep -q '^<<<<<<<' "$target"; then
                # merge-file reports an error (not a conflict) without writing markers: never guess.
                echo "CHECK    $prefix$new (git merge-file exit $rc, no conflict markers)"; conflicts+=("$prefix$new")
            else
                [ "${new##*.}" = java ] && perl "$here/resolve.pl" "$target" >/dev/null
                if grep -q '^<<<<<<<' "$target"; then
                    echo "CONFLICT $prefix$new"; conflicts+=("$prefix$new")
                else
                    echo "resolved $prefix$new (imports/formatting)"
                fi
            fi
            ;;
    esac
done < "$work/map.txt"
rm -rf "$work"

# The packet contract has a copy in the renderer that must stay identical.
if git -C "$repo" diff --quiet -- protocol/packet-field-contracts.json; then :; else
    echo "protocol/packet-field-contracts.json changed: copy it to the renderer (sync-renderer.sh does)."
fi

if [ ${#conflicts[@]} -gt 0 ]; then
    echo
    echo "Stopped: resolve these by hand, then rerun the checks below:"
    printf '  %s\n' "${conflicts[@]}"
    exit 3
fi

cd "$repo/Emulator" || exit 1
[ -f "$repo/../.workspace/scripts/use-jdk25.sh" ] && source "$repo/../.workspace/scripts/use-jdk25.sh" >/dev/null 2>&1
./mvnw -B -q spotless:apply -Dspotless.ratchetFrom="$remote/$branch" || { echo "spotless:apply failed" >&2; exit 4; }
./mvnw -B -q test-compile -Dspotless.check.skip=true || { echo "compilation failed: see above" >&2; exit 4; }
[ $skip_tests = 1 ] && { echo "Merged and compiled; tests skipped."; exit 0; }

gates=LegacyRemapperTest,PluginAbiCompatibilityTest,FrozenArchitectureBaselineTest,WiredPublicSurfaceCompatibilityTest
gates=$gates,WiredInteractionProtocolContractTest,WiredRegisteredExecutionCompatibilityTest,WiredPersistenceCompatibilityTest
gates=$gates,PacketContractCatalogTest,PacketContractCoverageTest,PacketRegistryPolicyContractTest,PacketWireMismatchBaselineTest
for t in "${added_tests[@]}"; do gates="$gates,$t"; done
if ./mvnw -B verify "-Dtest=$gates" -Dit.test=PackagedJarContractIT -Dsurefire.failIfNoSpecifiedTests=false \
        -Dfailsafe.failIfNoSpecifiedTests=false "-Dspotless.ratchetFrom=$remote/$branch"; then
    echo
    echo "Synced with $remote/$branch $up: gates, packaged plugin fixtures and the ported tests pass."
    echo "Run the full suite (./mvnw -B test), review the diff, commit, then: scripts/upstream-sync/sync.sh --done"
else
    echo "Gates failed: see the report above." >&2
    exit 5
fi
