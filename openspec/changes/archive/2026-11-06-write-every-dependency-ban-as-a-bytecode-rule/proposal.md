# Proposition : écrire en règle sur le bytecode chaque interdit de dépendance dont la cible le permet

## Why

Le constat `AUD-TRV-03` de l'audit du 09/10/2026 (`docs/project/audits/2026-10-09/findings.md`, revérifié le
2026-11-06 sur `30520bd3`, statut PARTIEL) demande, en deuxième clause de son action, de « migrer en priorité vers une
règle ArchUnit les assertions qui expriment une absence de dépendance, en commençant par les classes sans constante
inlinable ». La revérification du 06/11 constate qu'aucune migration n'a eu lieu dans la vague. La convention existe :
`.claude/rules/architecture.md` (« Un interdit textuel de type écrit son intention ») écrit qu'« un interdit de type dont
la cible ne déclare aucune constante inlinable s'écrit en règle ArchUnit », et la Javadoc de
`EnforcementMechanismAccountingTest.UNEXPLAINED_TYPE_BAN_CEILING` le répète. Mais **aucun test ne la tient** : le plafond
de la famille « sans intention » oblige un interdit à porter un message, jamais à avoir une règle. Une dépendance
indirecte ou non épelée continue d'échapper à un interdit textuel dont la cible aurait permis une règle.

## What Changes

- La suite d'architecture mesure une population nouvelle : les interdits textuels d'un nom de type dont la cible est un
  type du dépôt, dans un module qu'elle importe, sans constante de compilation non privée, et qu'aucune classe de règles
  ne nomme. Son compte égale un plafond déclaré, qui ne fait que baisser.
- Les membres présents à l'implémentation reçoivent leur règle sur le bytecode, découpée par intention, prouvée par une
  violation plantée ; le plafond descend avec eux.
- Toute règle qui remplace ou double un interdit textuel est livrée avec la trace de sa violation plantée.
- La part textuelle globale de l'enforcement **n'est pas plafonnée** : ni le nombre d'assertions textuelles, ni leur
  rapport aux règles (refus délibéré de `.claude/rules/architecture.md`, « La part textuelle de l'enforcement n'est pas
  plafonnée »).

## Capabilities

### New Capabilities

Aucune.

### Modified Capabilities
- `enforcement-mechanism-accounting` : deux exigences ajoutées, sur la population des interdits de type qui attendent
  une règle et sur la preuve d'une règle migrée.

## Périmètre et exclusions

**Inclus :** les interdits textuels d'un **nom de type** (littéral qui commence par une majuscule), avec ou sans
message, quelle que soit l'expression qui précède `.contains(`, dans les sources de test de
`morpheus-architecture-tests`.

**Exclus :**
- les interdits d'un nom de **paquet** (`com.morpheus.sdk.provider`, `tools.jackson`, `java.sql`…) : la constante
  inlinable s'y apprécie sur tous les types du paquet, et plusieurs sont textuels par nature (`.claude/rules/architecture.md`,
  « Cœurs purs — interdits textuels ») ; laissés en `[QUESTION]` dans `design.md` ;
- les cibles hors des sources principales du dépôt (types du JDK ou d'une bibliothèque) ;
- les interdits textuels par nature : chaîne scannée sur tout le dépôt, câblage explicite, artefact non compilé ;
- tout plafond de la part textuelle globale ;
- le gabarit de pull request (changement `ask-for-the-intention-of-every-textual-assertion-in-the-pull-request`) et le
  verdict distinct des contrôles de cohérence (changement
  `report-documentation-drift-apart-from-architecture-violations`).

## Acteurs et surfaces concernés

Mainteneur qui écrit ou relit un test d'architecture ; CI qui exécute la suite. Aucune surface publique ne change.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| La convention « cible sans constante inlinable → règle ArchUnit » n'est tenue par aucun test | existant vérifié | `.claude/rules/architecture.md` ; Javadoc d'`UNEXPLAINED_TYPE_BAN_CEILING` ; aucune méthode de la suite ne relie un interdit de type à la constante de sa cible (recherche de `inlin` dans `morpheus-architecture-tests/src/test/java`) |
| Les deux familles d'interdits de type ne voient qu'un récepteur identifiant | existant vérifié | expressions `UNEXPLAINED_TYPE_BAN` et `EXPLAINED_TYPE_BAN` d'`EnforcementMechanismAccountingTest` (`[A-Za-z0-9_]+\.contains`) |
| Rapport assertions `contains` / règles `noClasses()` | mesure de l'audit du 06/11 sur `30520bd3` | `findings.json`, `AUD-TRV-03`, champ `verif_preuve` (méthode de comptage qui y est inscrite) ; ce n'est pas un seuil |
| Une population mesurée qui ne fait que baisser, sans chiffre cible | décision | `design.md`, D1 et D3 |
| Pas d'ADR | décision | `design.md`, D1 (filtre de `.claude/rules/adr.md`) |

## Impact

- Tests : une classe ajoutée sous `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/` (elle lit des
  sources de production, donc ne porte pas `@CoherenceCheck`) ; règles ajoutées dans les `*ArchitectureTest` de chaque
  intention ; plafond déclaré dans la classe ajoutée.
- Aucun code de production.
- Règles et skill : `.claude/rules/architecture.md` (la convention renvoie à son test), `.claude/skills/enforcement-choice/SKILL.md`.
- Documentation durable : `docs/developer/specifications/enforcement-mechanism-accounting.md`.
- ADR : aucun.

## Questions ouvertes

- Faut-il, dans un changement ultérieur, étendre la population aux interdits d'un nom de paquet ? Voir `design.md`.
