# shellcheck shell=bash
# Resolves the Python 3 interpreter used by the shell validators.
#
# The validators used to invoke `python3` by name. The official Windows installer registers
# `python` and `py` but no `python3`, so a machine with Python 3.13 installed still failed every
# Linux-flavoured validator under Git Bash or WSL-less shells, and the failure looked like a
# missing prerequisite rather than a naming difference.
#
# Resolution is by capability, not by name: each candidate is asked whether it is Python 3, so a
# `python` that is still Python 2 is skipped instead of being run and failing obscurely later.
# Set MORPHEUS_PYTHON to force a specific interpreter.

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
