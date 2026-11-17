---
name: local-build-and-shell-traps
description: Two traps of this machine: Bash heredocs with apostrophes fail to parse, and editing a source during clean verify voids the coverage gates
metadata:
  type: feedback
---

Write multi-line text (Python scripts, commit messages, Markdown) with the Write tool into the scratchpad, then run or pass the file. Do not use a Bash heredoc for it.

**Why:** in this environment a quoted heredoc whose body holds apostrophes fails with "unexpected EOF while looking for matching `'`", and the whole command is lost, earlier steps included.

**How to apply:** keep Bash commands single-purpose. Long text goes through Write, then `python file.py` or `git commit -F file`.

Never edit a source file, not even a Javadoc, while `./mvnw clean verify` runs in the background.

**Why:** `CoverageQualityGateTest` refuses a module whose `jacoco.xml` is older than its sources, and `AggregateCoverageGateTest` only concludes over the whole reactor. One edit during the build costs a second full `clean verify`.

**How to apply:** finish every edit, then launch the full verify once. A partial `-pl` verify cannot satisfy the aggregate gate.
