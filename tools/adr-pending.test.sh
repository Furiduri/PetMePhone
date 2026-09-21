#!/usr/bin/env bash
#
# Tests for adr-pending.sh.
#
# Every fixture is written into a temporary directory. Writing fake ADRs into
# docs/adr/ would make the tool under test report its own test data, and would
# leave the repository dirty when a test fails partway through.
#
# Run: ./tools/adr-pending.test.sh

set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOL="$HERE/adr-pending.sh"

passed=0
failed=0

fail() {
  printf 'FAIL: %s\n' "$1"
  printf '  %s\n' "$2"
  failed=$((failed + 1))
}

pass() {
  printf 'ok: %s\n' "$1"
  passed=$((passed + 1))
}

# adr <dir> <number> <status> <body...> — writes one fixture ADR.
adr() {
  local dir="$1" num="$2" status="$3"
  shift 3
  {
    printf '# ADR-%s: Fixture\n\n' "$num"
    printf -- '- **Status:** %s\n' "$status"
    printf -- '- **Date:** 2026-01-01\n\n'
    printf '## Decision\n\nWe do the thing.\n\n'
    printf '%s\n' "$@"
  } >"$dir/$num-fixture.md"
}

newdir() { mktemp -d "${TMPDIR:-/tmp}/adr-pending-test.XXXXXX"; }

# ---------------------------------------------------------------------------
# An accepted ADR has an item with no marker.
#
# ADR-0002 makes Pending the default state, so an unmarked bullet is open work.
# This is the rule most easily implemented backwards — by reporting only what
# is explicitly marked [Pending] — which would silently hide every item written
# before the marker vocabulary existed.
# ---------------------------------------------------------------------------
d=$(newdir)
adr "$d" 0001 'Accepted' '## Pending verification' '' '- Extract the token check into a script.'
out=$("$TOOL" "$d" 2>&1); rc=$?
if [ "$rc" -ne 0 ]; then
  fail 'unmarked item is reported' "exited $rc, expected 0"
elif ! printf '%s' "$out" | grep -q 'Extract the token check'; then
  fail 'unmarked item is reported' "not in output: $out"
else
  pass 'unmarked item is reported'
fi
rm -rf "$d"

# ---------------------------------------------------------------------------
# A solved item is suppressed, and its ADR does not appear empty-handed.
# ---------------------------------------------------------------------------
d=$(newdir)
adr "$d" 0001 'Accepted' '## Pending verification' '' \
  '- **[Solved]** Add the CI check. — pr-checks.yml, #53.' '' \
  '- **[Deprecated]** Split the sprite cache. — the cache was removed in #142.'
out=$("$TOOL" "$d" 2>&1); rc=$?
if [ "$rc" -ne 0 ]; then
  fail 'solved and deprecated items are suppressed' "exited $rc, expected 0"
elif printf '%s' "$out" | grep -qE 'Add the CI check|Split the sprite cache'; then
  fail 'solved and deprecated items are suppressed' "leaked into output: $out"
else
  pass 'solved and deprecated items are suppressed'
fi
rm -rf "$d"

# ---------------------------------------------------------------------------
# A superseded ADR's open items are not live work.
#
# They are skipped — but the skip is counted out loud, because an ADR that
# vanishes from a report silently is indistinguishable from one the tool failed
# to parse.
# ---------------------------------------------------------------------------
d=$(newdir)
adr "$d" 0001 'Superseded by [ADR-0003](0003-fixture.md)' '## Pending verification' '' \
  '- Something nobody will ever do now.'
out=$("$TOOL" "$d" 2>&1); rc=$?
if [ "$rc" -ne 0 ]; then
  fail 'superseded ADR is skipped and counted' "exited $rc, expected 0"
elif printf '%s' "$out" | grep -q 'Something nobody'; then
  fail 'superseded ADR is skipped and counted' "reported a dead item: $out"
elif ! printf '%s' "$out" | grep -qi 'skipped'; then
  fail 'superseded ADR is skipped and counted' "skip not reported: $out"
else
  pass 'superseded ADR is skipped and counted'
fi
rm -rf "$d"

# ---------------------------------------------------------------------------
# An ADR with no task list at all. ADR-0002 and ADR-0003 both look like this.
# ---------------------------------------------------------------------------
d=$(newdir)
adr "$d" 0001 'Accepted' '## Consequences' '' '- It works.'
out=$("$TOOL" "$d" 2>&1); rc=$?
if [ "$rc" -ne 0 ]; then
  fail 'ADR without a task list is skipped cleanly' "exited $rc, expected 0"
elif printf '%s' "$out" | grep -q 'It works'; then
  fail 'ADR without a task list is skipped cleanly' "read the wrong section: $out"
else
  pass 'ADR without a task list is skipped cleanly'
fi
rm -rf "$d"

# ---------------------------------------------------------------------------
# Nothing is open.
#
# The tool must say so. Printing nothing leaves the reader unable to tell a
# clean result from a tool that failed to run.
# ---------------------------------------------------------------------------
d=$(newdir)
adr "$d" 0001 'Accepted' '## Pending verification' '' '- **[Solved]** All done. — #1.'
out=$("$TOOL" "$d" 2>&1); rc=$?
if [ "$rc" -ne 0 ]; then
  fail 'an empty result is stated, not implied' "exited $rc, expected 0"
elif [ -z "$(printf '%s' "$out" | tr -d '[:space:]')" ]; then
  fail 'an empty result is stated, not implied' 'printed nothing at all'
elif ! printf '%s' "$out" | grep -qiE 'no open|nothing'; then
  fail 'an empty result is stated, not implied' "no explicit all-clear: $out"
else
  pass 'an empty result is stated, not implied'
fi
rm -rf "$d"

# ---------------------------------------------------------------------------
# The report is navigable: every item carries its file and line.
# ---------------------------------------------------------------------------
d=$(newdir)
adr "$d" 0007 'Accepted' '## Pending verification' '' '- Wire the emulator job.'
out=$("$TOOL" "$d" 2>&1); rc=$?
if [ "$rc" -ne 0 ]; then
  fail 'each item carries file and line' "exited $rc, expected 0"
elif ! printf '%s' "$out" | grep -qE '0007-fixture\.md:[0-9]+'; then
  fail 'each item carries file and line' "no file:line reference: $out"
else
  pass 'each item carries file and line'
fi
rm -rf "$d"

printf '\n%d passed, %d failed\n' "$passed" "$failed"
[ "$failed" -eq 0 ]
