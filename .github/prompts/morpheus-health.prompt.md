---
mode: agent
description: "Génère un rapport de santé du dépôt Morpheus Engine (git, tests, dépendances, dette technique)."
---

# Health report Morpheus Engine

Miroir du prompt Claude `.claude/commands/health.md`.

## Collecte

### 1. État du dépôt
```bash
git status
git log --oneline -5
git diff --stat HEAD~1
```

### 2. Statut des tests (rapide)
```bash
./mvnw test -pl morpheus-architecture-tests -q 2>&1 | tail -20
```

### 3. Hygiène des dépendances
```bash
./mvnw dependency:analyze -q 2>&1 | grep -E "WARNING|ERROR"
```

### 4. Surface des changements
Modules touchés par les 3 derniers commits, contrats publics modifiés (`contracts/`),
fichiers de documentation à mettre à jour (`docs/`).

### 5. Dette technique visible
```bash
grep -r "TODO\|FIXME" --include="*.java" src/
grep -r "@Disabled" --include="*.java" src/test/
```

## Rapport

```
═══════════════════════════════════════
  MORPHEUS ENGINE — HEALTH REPORT
  <date du jour>
═══════════════════════════════════════

GIT       <branch> | <N> commits ahead | <status>
ARCH      <milestone courant lu en live> gate ✅/❌ | <N> tests
DEPS      <N> warnings / 0 violation attendue
CONTRACTS <N> fichiers | dernière MAJ: <date>
DEBT      <N> TODOs | <N> @Disabled

Recommandations:
  1. ...
```

Si l'utilisateur demande un rapport "complet", exécuter aussi
`./mvnw clean verify -q` (plus lent).
