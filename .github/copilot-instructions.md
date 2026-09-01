# Morpheus Engine — instructions Copilot (dépôt entier)

Ce fichier est chargé automatiquement par GitHub Copilot (chat, revue de PR, coding agent)
pour toute demande touchant ce dépôt. Il est le pendant `.github/` de `.claude/CLAUDE.md` —
les deux doivent rester cohérents ; en cas de divergence constatée, signaler et corriger les
deux dans le même changement.

## Identité

`io.github.fturleque:morpheus-engine` — Specification & Intent Intelligence Engine.
Java 21 · Maven 3.9.16 · architecture **ports & adapters** (hexagonale) · local-first ·
**sans framework applicatif** (pas de Spring/Micronaut) · **sans Docker**.
Nombre de modules, de packages, d'ADR, de tests : **ne jamais citer de mémoire**, voir
"Principe anti-dérive" ci-dessous.

## Principe directeur

**Les règles de ce projet sont exécutables, pas déclaratives.** Une suite de tests
d'architecture dans `morpheus-architecture-tests/` (ArchUnit + tests textuels sur fichiers
source) fait foi sur la structure, la sécurité et les contrats. `docs/adr/` documente le
*pourquoi*. Avant toute décision structurelle : lire le test concerné, puis l'ADR associé.
Ne jamais deviner une règle — si elle existe, elle est vérifiable par un test ou un script.

## Principe anti-dérive (obligatoire)

Un audit de cette configuration a détecté que plusieurs fichiers de règles citaient des
seuils différents pour le **même** ratchet de coverage (47 %, "711 et 253", 820/258, et la
vraie valeur dans `config/m21-quality-ratchets.properties`). Conséquence : **tout chiffre
mentionné dans ce fichier ou dans `.github/instructions/*.instructions.md` est une copie,
jamais l'original.**

- Avant de citer un seuil, un compte de tests, un nombre d'ADR ou de modules : relire la
  source vivante, pas cette page.
- Sources vivantes de référence :
  - `config/m21-quality-ratchets.properties` — minimums tests/architecture/coverage
  - `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m21/CoverageQualityGateTest.java`
    et `.../d2/D2RepositoryHardeningArchitectureTest.java` — planchers D2, gate de non-régression
  - `pom.xml` (racine) — version produit, liste des `<module>`, versions pinnées
  - `docs/adr/` — compter les fichiers réels (un doublon de numérotation `0095-*` existe déjà :
    ne jamais recopier un total mémorisé)
  - `contracts/public-surfaces.tsv` — convergence CLI/MCP/HTTP, compter les lignes réelles
- Si un chiffre trouvé dans le code diverge d'un chiffre écrit dans ces instructions,
  **le signaler explicitement** dans la réponse et proposer la correction du fichier
  d'instructions dans le même changement — ne jamais trancher silencieusement.
- Ne jamais supposer qu'un fichier de documentation (y compris celui-ci) est plus récent
  que le code : en cas de doute, le code et les scripts de validation (`scripts/validate-m*`)
  font foi.

## Garde-fous non négociables

- **Aucune** exécution de `git push --force`/`-f`, `git reset --hard`, `git clean -fdx`,
  `git branch -D` ou `git push --delete` visant `main`, `develop` ou `master` sans
  confirmation explicite de l'utilisateur humain dans la conversation.
- **Jamais** de commande destructrice non bornée (`rm -rf /`, `rm -rf ~`, suppression de
  répertoire sans chemin explicite et relatif au dépôt).
- **Jamais** de secret, clé API, token ou mot de passe en dur dans le code, les logs ou les
  messages de commit. `NVD_API_KEY` et `MORPHEUS_SERVER_TLS_PASSWORD` sont des variables
  d'environnement, jamais des flags CLI ni des littéraux.
- **Jamais** de commentaire de code qui répète ce que fait le code — le nom (méthode,
  variable, classe) porte cette responsabilité. Un commentaire n'est légitime que pour
  expliquer un *pourquoi* non déductible du code (contrainte externe, contournement,
  référence à un ADR/ticket).
- Toute modification de `contracts/public-surfaces.tsv`, `config/*ratchets*.properties`,
  `docs/openapi/*.yaml` ou d'un test sous `morpheus-architecture-tests/` doit être justifiée
  dans la description de la PR — ce sont des fichiers de gouvernance, pas de simples fichiers
  de config.
- Ne jamais modifier un ADR existant après fusion pour changer son contenu historique :
  créer un nouvel ADR qui supersede l'ancien (statut `Superseded by ADR-xxxx`).

## Architecture : ports & adapters

`morpheus-application` définit les **ports** (interfaces) et n'a aucune dépendance vers un
adaptateur concret. Les adaptateurs (`provider-*`, `store-*`, `api`, `mcp`, `integration-*`)
dépendent vers l'intérieur (domain ← application ← adapters) et sont **frères entre eux** :
un adaptateur ne doit jamais appeler directement un autre adaptateur. Seul `morpheus-cli`
fait le câblage explicite de toutes les dépendances (composition root).
Ces règles sont vérifiées par `morpheus-architecture-tests/.../LayerDependencyTest.java` —
la lire avant toute nouvelle dépendance inter-module.

## Commandes de validation

```bash
./mvnw clean verify                                       # reactor complet
./mvnw test -pl morpheus-architecture-tests               # tous les gates d'architecture
./mvnw dependency:analyze                                 # 0 warning exigé
```

Scripts de validation par milestone : `scripts/validate-m<N>.ps1` / `.sh`.

## Style de code

- Records/value objects immuables pour le domaine, factories nommées (`identifier()`,
  `generate()`) plutôt que des constructeurs publics multiples.
- JUnit 5 (Jupiter) exclusivement. `assertThrows` + message explicite pour les cas d'échec.
- Pas de champ `static` mutable partagé entre tests.

## Sécurité — invariants non négociables

Ces invariants sont assertés **textuellement** par des tests d'architecture — les affaiblir
casse le build, pas seulement une revue humaine :

- Aucune désérialisation JSON via `activateDefaultTyping`/`enableDefaultTyping`.
- Tout serveur HTTP local est loopback-only par défaut (`LoopbackHostPolicy`).
- Le serveur remote (M26) exige TLS 1.2/1.3, tokens `SecureRandom` 32 octets stockés en
  `sha256`, comparaison par `MessageDigest.isEqual`, jamais de CORS, jamais de forward du
  header `Authorization` vers l'amont.
- L'activation d'un plugin provider exige un pin SHA-256 de confiance ; découverte en
  métadonnées uniquement (jamais de classloading au scan).
- Toutes les actions GitHub sont pinnées par SHA 40 caractères, jamais par tag mutable.
- Détail complet et exigences exactes : `.claude/rules/security.md` (source partagée avec
  Claude Code) — le relire avant toute revue de sécurité, ne pas se fier uniquement à ce
  résumé.

## Où trouver le détail

Ce fichier reste un résumé volontairement court pour Copilot. Le détail exhaustif
(exemples de code réels, invariants complets, procédures pas-à-pas) vit dans `.claude/rules/`
et s'applique **également** aux agents Copilot : `architecture.md`, `testing.md`,
`security.md`, `governance.md`, `build.md`, `code-style.md`, `meta.md`.

## Paramétrage IA complet du dépôt

La cartographie exhaustive de tout le paramétrage IA (instructions, prompts, skill, hooks,
correspondance Claude ↔ Copilot) vit dans `.github/AI_GOVERNANCE.md` — la lire avant de
créer un nouvel artefact IA, pour éviter de dupliquer ce qui existe déjà.

- **Instructions ciblées par chemin** : `.github/instructions/*.instructions.md`
  (`architecture`, `security`, `testing`, `governance`, `build`, `code-style`) — chacune
  porte un `applyTo` et renvoie vers la règle Claude détaillée correspondante.
- **Prompts invocables** : `.github/prompts/*.prompt.md` — point d'entrée
  `morpheus-orchestrator.prompt.md`, puis prompts spécialisés (`morpheus-governance-audit`,
  `morpheus-security-audit`, `morpheus-test-gate`, `morpheus-validate`,
  `morpheus-milestone`, `morpheus-health`, `morpheus-bug-fix`) — miroir fonctionnel des
  commandes `.claude/commands/*.md`.
- **Skill projet** : `.github/skills/morpheus-engine/SKILL.md` — carte de navigation du
  savoir Morpheus (où trouver quel seuil, quel test, quel ADR), pas un recueil de chiffres.
- **Agents spécialisés** : `.claude/agents/*.md` (`architect`, `bug-investigator`,
  `contract-guardian`, `security-reviewer`) — **partagés nativement**, sans miroir dans
  `.github/` : un outil Copilot capable de lancer des sub-agents les lit directement depuis
  `.claude/agents/`. Ne jamais dupliquer un agent dans `.github/agents/`.

## RTK — hook de réécriture des commandes

`.github/hooks/rtk-rewrite.json` déclare un hook `PreToolUse` (`rtk hook copilot`) au
niveau repo — miroir du hook utilisateur `~/.copilot/hooks/rtk-rewrite.json`, mais
**portable** : il s'applique à quiconque clone ce dépôt, sans dépendre du profil machine.
Toujours préférer `rtk <commande>` à la commande brute pour les invocations shell
(`git`, `mvn`/`mvnw`, etc.) quand l'outil `rtk` est disponible.

<!-- rtk-instructions v2 -->
# RTK — Token-Optimized CLI

**rtk** is a CLI proxy that filters and compresses command outputs, saving 60-90% tokens.

## Rule

Always prefix shell commands with `rtk`:

```bash
# Instead of:              Use:
git status                 rtk git status
git log -10                rtk git log -10
cargo test                 rtk cargo test
docker ps                  rtk docker ps
kubectl get pods           rtk kubectl get pods
```

## Meta commands (use directly)

```bash
rtk gain              # Token savings dashboard
rtk gain --history    # Per-command savings history
rtk discover          # Find missed rtk opportunities
rtk proxy <cmd>       # Run raw (no filtering) but track usage
```
<!-- /rtk-instructions -->