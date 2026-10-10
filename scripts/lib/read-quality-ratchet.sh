#!/usr/bin/env bash
# Reads one value of config/m21-quality-ratchets.properties, the living source of the quality ratchets.
#
# Usage (sourced): read_quality_ratchet <properties-file> <key> <integer|ratio>
#
# A validator that compares an observed count to a ratchet reads the ratchet here; it never holds a copy. The path of
# the file and the name of the key stay in the calling script, so a reader of that script sees which ratchet it
# enforces, and the architecture tests that pin those names keep finding them there.
#
# There is no default. A missing file, a missing key, or a value that is not what the key is (a positive integer for a
# count, a ratio in (0, 1] for a coverage) refuses with the key's name. The trailing carriage return of a checkout
# with Windows line endings is removed: the properties file is CRLF in such a checkout, and a value that keeps its
# "\r" turns an arithmetic comparison into an error instead of a verdict.
#
# The value is printed on stdout; the refusal goes to stderr and exits non-zero, so call it as
#   VALUE="$(read_quality_ratchet "$RATCHETS" testsMinimum integer)"
# under `set -e`, where a failed command substitution in an assignment ends the script.

read_quality_ratchet() {
  local file="$1" key="$2" kind="$3" value

  if [[ ! -f "$file" ]]; then
    echo "Missing M21 quality ratchet configuration: $file" >&2
    exit 1
  fi
  value="$(sed -n "s/^${key}=//p" "$file" | head -n 1 | tr -d '\r')"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  if [[ -z "$value" ]]; then
    echo "Missing M21 quality ratchet: $key" >&2
    exit 1
  fi

  case "$kind" in
    integer)
      if ! [[ "$value" =~ ^[0-9]+$ ]] || (( 10#$value < 1 )); then
        echo "M21 quality ratchet $key must be a positive integer: '$value'" >&2
        exit 1
      fi
      ;;
    ratio)
      if ! awk -v v="$value" 'BEGIN { exit !(v ~ /^[0-9]*\.?[0-9]+$/ && v + 0 > 0 && v + 0 <= 1) }'; then
        echo "M21 quality ratchet $key must be a ratio in (0, 1]: '$value'" >&2
        exit 1
      fi
      ;;
    *)
      echo "read_quality_ratchet: unknown kind '$kind' for $key" >&2
      exit 2
      ;;
  esac
  printf '%s' "$value"
}
