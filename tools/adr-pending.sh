#!/usr/bin/env bash
#
# Lists the open follow-ups recorded inside accepted ADRs.
#
# ADR-0002 lets an accepted ADR carry a "Pending verification" task list whose
# items are marked [Pending], [Solved] or [Deprecated] without superseding the
# document. Those lists live one per ADR, so without this there is no way to
# see the open ones short of opening every file — and a follow-up nobody
# surfaces is indistinguishable from one that does not exist.
#
# This is a REPORT, not a gate. It always exits 0. Open follow-ups are the
# normal state of an honest project; a check that is red for weeks teaches
# people to ignore it, and then it is ignored the once it matters.
#
# Usage: ./tools/adr-pending.sh [directory]   (default: docs/adr next to this)

set -uo pipefail

dir="${1:-}"
if [ -z "$dir" ]; then
  dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/docs/adr"
fi

if [ ! -d "$dir" ]; then
  printf 'adr-pending: no such directory: %s\n' "$dir"
  exit 0
fi

# The header is everything before the first "## " heading. Reading the status
# from there matters: ADR-0000 and ADR-0002 both quote example status lines
# inside fenced code blocks further down, and a naive grep picks those up and
# reports the document as superseded by a fictional ADR-0009.
status_of() {
  awk '
    /^## / { exit }
    /^- \*\*Status:\*\*/ {
      sub(/^- \*\*Status:\*\*[[:space:]]*/, "")
      print
      exit
    }
  ' "$1"
}

open_items=0
adrs_with_items=0
suppressed=0
skipped=0
report=""

shopt -s nullglob
for file in "$dir"/[0-9][0-9][0-9][0-9]-*.md; do
  status="$(status_of "$file")"

  # Only Accepted ADRs describe live work. A superseded, obsolete or rejected
  # ADR's follow-ups belong to whatever replaced it, or to nothing.
  case "$status" in
    Accepted) ;;
    *) skipped=$((skipped + 1)); continue ;;
  esac

  # Top-level bullets inside the "Pending verification" section, with their
  # line numbers so the output is navigable. Continuation lines are indented
  # and are folded into the item they belong to.
  items="$(awk '
    /^## / {
      if (inside) exit
      if (tolower($0) ~ /^## pending verification/) { inside = 1; next }
    }
    !inside { next }
    /^- / {
      if (item != "") print start "\t" item
      start = NR; item = $0; next
    }
    /^[[:space:]]+[^[:space:]]/ {
      if (item != "") { sub(/^[[:space:]]+/, " "); item = item $0 }
      next
    }
    END { if (item != "") print start "\t" item }
  ' "$file")"

  [ -z "$items" ] && continue

  name="$(basename "$file")"
  shown=0

  while IFS=$'\t' read -r line text; do
    [ -z "${text:-}" ] && continue
    case "$text" in
      *'**[Solved]**'*|*'**[Deprecated]**'*)
        suppressed=$((suppressed + 1))
        continue
        ;;
    esac

    # Everything else is open: an explicit [Pending] mark, or no mark at all.
    # ADR-0002 makes Pending the default, so an unmarked bullet is open work
    # and must not be filtered out for lacking the newer vocabulary.
    clean="${text#- }"
    clean="${clean#\*\*\[Pending\]\*\* }"

    if [ "$shown" -eq 0 ]; then
      report="${report}
${name}"
      shown=1
      adrs_with_items=$((adrs_with_items + 1))
    fi
    report="${report}
  ${name}:${line}  ${clean}"
    open_items=$((open_items + 1))
  done <<<"$items"
done
shopt -u nullglob

if [ "$open_items" -eq 0 ]; then
  printf 'No open ADR follow-ups.\n'
else
  printf 'Open ADR follow-ups:\n%s\n' "$report"
fi

printf '\n%d open in %d ADR(s); %d marked item(s) suppressed; %d non-accepted ADR(s) skipped.\n' \
  "$open_items" "$adrs_with_items" "$suppressed" "$skipped"

exit 0
