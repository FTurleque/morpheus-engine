# Commande /health

Génère un rapport de santé du projet Morpheus Engine.

## Ce que tu dois faire

Collecter et présenter les indicateurs suivants :

### 1. État du dépôt
```bash
git status
git log --oneline -5
git diff --stat HEAD~1
```
- Changements non commités
- 5 derniers commits
- Fichiers modifiés dans le dernier commit

### 2. Statut des tests (rapide)
```bash
./mvnw test -pl morpheus-architecture-tests -q 2>&1 | tail -20
```
- Tests ArchUnit passants/échouants
- Gate M28 : PASS ou FAIL

### 3. Hygiene des dépendances
```bash
./mvnw dependency:analyze -q 2>&1 | grep -E "WARNING|ERROR"
```
- Dépendances inutilisées déclarées
- Dépendances utilisées non déclarées

### 4. Surface des changements
- Modules touchés par les 3 derniers commits
- Contrats publics modifiés (`contracts/`)
- Fichiers de documentation à mettre à jour (`docs/`)

### 5. Dette technique visible
- TODOs/FIXMEs dans les fichiers récemment modifiés : `grep -r "TODO\|FIXME" --include="*.java" src/`
- Tests ignorés (JUnit `@Disabled`) : `grep -r "@Disabled" --include="*.java" src/test/`

## Format du rapport

```
═══════════════════════════════════════
  MORPHEUS ENGINE — HEALTH REPORT
  <date>
═══════════════════════════════════════

GIT       <branch> | <N> commits ahead | <status>
ARCH      M28 gate ✅/❌ | <N> tests
DEPS      <N> warnings / 0 violations
CONTRACTS <N> fichiers | dernière MAJ: <date>
DEBT      <N> TODOs | <N> @Disabled

Recommandations:
  1. ...
  2. ...
```

Si $ARGUMENTS contient "full", exécuter aussi `./mvnw clean verify -q` pour un rapport de build complet (plus lent).
