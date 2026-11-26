#!/usr/bin/env bash
# Counts how much of Raven's C API compose-raven calls, for docs/api-coverage.md.
#
#   compose/tools/api-coverage.sh [-u HEADER] [REV]
#
# REV is a commit (default: the working tree). It prints the totals, the coverage of each header as
# Markdown table rows, and the widget types; with -u, only HEADER's unbound functions
# (-u raven_window.h), for the reasons in the table.
#
# - Declared: every function declared `RAVEN_API` (or `RAVEN_MCP_API`, libraven_mcp's) in
#   include/raven/*.h and include/Raven.h, under its header. A header-only file's `static inline`
#   helpers (raven_test.h) aren't Raven's API, and a name only called or mentioned in a header
#   isn't counted there.
# - Used: a declared name that appears as an identifier in compose-raven's main sources
#   (compose/*/src/*Main/**/*.kt), samples and spikes left out. Appearing once counts, so a
#   function bound for one case counts as used.
# - Widget types: the R_WIDGET_* constants of raven_widget.h, R_WIDGET_LAST_ left out, against
#   those the main sources name.
set -euo pipefail

unbound=
if [[ ${1:-} == -u ]]; then
    unbound=$2
    shift 2
fi

root=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

if [[ $# -gt 0 ]]; then
    git -C "$root" archive "$1" include compose | tar -x -C "$work"
    tree=$work
else
    tree=$root
fi

# A declaration can span lines, so each header is joined into one line and cut at its semicolons.
for header in "$tree"/include/raven/*.h "$tree"/include/Raven.h; do
    tr '\n' ' ' < "$header" | tr ';' '\n' | grep -oE 'RAVEN_(MCP_)?API[^(]*\(' |
        grep -oE '\bR[a-z][A-Za-z0-9_]*[[:space:]]*\($' | sed -E 's/[[:space:]]*\($//' | sort -u |
        sed "s|^|$(basename "$header") |" || true # a header-only file declares none
done | awk '!seen[$2]++' > "$work/declared"

find "$tree/compose" -path '*/src/*Main/*' -name '*.kt' \
    -not -path "$tree/compose/samples/*" -not -path "$tree/compose/spikes/*" -not -path '*/build/*' -print0 |
    xargs -0 cat > "$work/sources"
grep -ohE '\bR[a-z][A-Za-z0-9_]*\b' "$work/sources" | sort -u > "$work/identifiers"
awk 'NR == FNR { used[$1] = 1; next } ($2 in used)' "$work/identifiers" "$work/declared" > "$work/used"

if [[ -n $unbound ]]; then
    comm -23 <(awk -v h="$unbound" '$1 == h { print $2 }' "$work/declared" | sort) \
        <(awk -v h="$unbound" '$1 == h { print $2 }' "$work/used" | sort)
    exit 0
fi

declared=$(wc -l < "$work/declared" | tr -d ' ')
used=$(wc -l < "$work/used" | tr -d ' ')
echo "Functions: $used of $declared used ($((100 * used / declared))%)"
echo
echo "| Header | Used | Declared | Coverage |"
echo "|---|---:|---:|---:|"
awk 'NR == FNR { u[$1]++; next } { d[$1]++ } END {
    for (h in d) printf "%s %d %d\n", h, u[h], d[h]
}' "$work/used" "$work/declared" | sort -k3,3nr -k1,1 |
    awk '{ printf "| `%s` | %d | %d | %d%% |\n", $1, $2, $3, int(100 * $2 / $3 + 0.5) }'
echo

grep -ohE '\bR_WIDGET_[A-Z0-9_]+\b' "$tree/include/raven/raven_widget.h" | grep -v '^R_WIDGET_LAST_$' | sort -u > "$work/types"
grep -ohE '\bR_WIDGET_[A-Z0-9_]+\b' "$work/sources" | sort -u > "$work/types_used"
echo "Widget types: $(comm -12 "$work/types" "$work/types_used" | wc -l | tr -d ' ') of $(wc -l < "$work/types" | tr -d ' ') bound"
echo "Unbound: $(comm -23 "$work/types" "$work/types_used" | sed 's/^R_WIDGET_//' | paste -sd ' ' -)"
