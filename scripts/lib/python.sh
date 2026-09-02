# shellcheck shell=bash
# Resolves and invokes the Python 3 interpreter used by the shell validators.
#
# Two Windows-specific problems made these validators unusable, or worse, quietly wrong.
#
# 1. Name. The validators invoked `python3`. The official Windows installer registers `python` and
#    `py` but no `python3`, so a machine with Python 3.13 installed still failed every validator
#    under Git Bash, and the failure read as a missing prerequisite rather than a naming
#    difference. Resolution here is by capability: each candidate is asked whether it is Python 3,
#    so a `python` that is still Python 2 is skipped instead of failing obscurely later.
#    Set MORPHEUS_PYTHON to force a specific interpreter.
#
# 2. Line endings. Python's stdout translates "\n" to "\r\n" on Windows. The trailing "\r" lands in
#    the last variable of `read -r A B C D < <(...)`, so `(( D < MINIMUM ))` raised
#    "invalid arithmetic operator" -- which returns non-zero, which makes the enclosing `if` false,
#    which SKIPS the check. The architecture-test ratchet was not being enforced at all on that
#    platform while the script still printed PASS. morpheus_python strips carriage returns so a
#    numeric gate compares numbers instead of silently disappearing.

morpheus_resolve_python() {
    local candidate
    for candidate in "${MORPHEUS_PYTHON:-}" python3 python py; do
        [ -n "$candidate" ] || continue
        command -v "$candidate" >/dev/null 2>&1 || continue
        if "$candidate" -c 'import sys; sys.exit(0 if sys.version_info[0] == 3 else 1)' >/dev/null 2>&1; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done
    echo "error: no Python 3 interpreter found (tried MORPHEUS_PYTHON, python3, python, py)" >&2
    return 1
}

PYTHON="$(morpheus_resolve_python)"

# `set -o pipefail` is active in every validator, so the interpreter's exit status still propagates
# through the pipe and a failing check still fails the gate.
morpheus_python() {
    "$PYTHON" "$@" | tr -d '\r'
}
