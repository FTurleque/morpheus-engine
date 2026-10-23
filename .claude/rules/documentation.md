# Règles — Organisation de `docs/`

L'arborescence de `docs/` est **figée** depuis le 11/10/2026. Elle a été réorganisée parce que les
pages utilisateur portaient des SHA de release et des compteurs de qualification, que deux dossiers
d'audits et deux registres d'ADR coexistaient, et que l'historique du projet noyait la documentation
active. Ne pas recréer ce désordre : ranger chaque nouveau fichier à sa place, du premier coup.

## L'arborescence

```text
docs/
  README.md          aiguillage, rien d'autre
  user/              utiliser MORPHEUS
    release-notes/   RELEASE_NOTES_<version>.md
  developer/         construire, modifier, étendre MORPHEUS
    reference/       contrats machine
      openapi/       morpheus-v1*.yaml
      contracts/     contrats de ports
      domain/        modèle de domaine
  architecture/      dossier arc42, overview, quality/, risks/
    adr/             registre unique des ADR + template.md, lu par Archgate (docs/developer/ARCHGATE.md)
  project/           piloter et auditer le projet
    governance/      ROADMAP, DOCUMENTATION_STATUS, PLAN
    roadmap/         <jalon>_EXECUTION.md
    validation/      VALIDATION_<jalon>.md, RELEASE_QUALIFICATION.md
    audits/          un relevé par date
    product/         cadrage produit
    research/        études et expériences
    rapport-supervisor/  dossier local du superviseur de code, non versionné
```

`docs/README.md` est le **seul** fichier à la racine de `docs/`, et `user/`, `developer/`,
`architecture/`, `project/` les **seuls** dossiers suivis par Git. Un nouveau dossier de premier
niveau est une décision de structure : la demander, ne pas la prendre en passant.

## Où ranger un nouveau fichier

Se poser la question du **lecteur**, pas du sujet :

| Le lecteur veut… | Emplacement |
|---|---|
| installer, configurer, lancer une commande, migrer de version | `docs/user/` |
| savoir ce qui change dans une version | `docs/user/release-notes/` |
| compiler, tester, qualifier, comprendre un module ou une sous-plateforme | `docs/developer/` |
| lire un contrat machine (OpenAPI, port, modèle, surfaces publiques) | `docs/developer/reference/` |
| comprendre l'architecture d'ensemble, un scénario qualité, un risque | `docs/architecture/` |
| retrouver ou écrire une décision structurelle | `docs/architecture/adr/` |
| connaître l'état, les priorités, l'autorité d'un document | `docs/project/governance/` |
| lire le plan d'un jalon | `docs/project/roadmap/` |
| lire la preuve d'un jalon ou d'une release | `docs/project/validation/` |
| lire un audit daté | `docs/project/audits/` |

Un même sujet a souvent **deux pages** : `user/TEAM_REMOTE_SERVER.md` dit comment exposer le serveur,
`developer/REMOTE_SERVER_PLATFORM.md` dit comment il est construit. Écrire deux pages plutôt qu'une
page mixte.

## TOUJOURS

- Ranger une page dans **un seul** emplacement ; ne jamais la dupliquer dans un second
- Ajouter la page à l'index de son espace dans le même changement : `README.md` du dossier, et
  `docs/README.md` si elle répond à un besoin de premier niveau
- Écrire les liens **relatifs** entre pages de `docs/` ; citer un chemin depuis la racine du dépôt
  (`docs/developer/API.md`) partout ailleurs — tests, scripts, workflows, `.claude/`
- Garder une page de `docs/user/` **lisible sans le dépôt** : elle parle du produit installé, de ses
  commandes et de ses fichiers de configuration
- Ajouter un audit sous `docs/project/audits/`, soit `AUDIT_<objet>_<AAAA-MM-JJ>.md`, soit un dossier
  `<AAAA-MM-JJ>/` quand il porte plusieurs fichiers, et l'indexer dans `audits/README.md`
- Partir de `docs/architecture/adr/template.md` pour une ADR, vérifier le plus haut numéro attribué,
  et ajouter sa ligne à `docs/architecture/adr/README.md` (`AdrIndexCoherenceTest`)
- Livrer plan et preuve d'un jalon dans `docs/project/roadmap/` et `docs/project/validation/`
  (cf. `rules/testing.md`, quadruplet)

## JAMAIS

- Jamais de fichier à la racine de `docs/` autre que `README.md`
- Jamais de nouveau dossier de premier niveau sous `docs/`, ni de retour d'un ancien
  (`docs/adr/`, `docs/openapi/`, `docs/reference/`, `docs/contracts/`, `docs/domain/`, `docs/release/`,
  `docs/governance/`, `docs/roadmap/`, `docs/validation/`, `docs/audit/`, `docs/audits/`,
  `docs/research/`, `docs/product/`)
- Jamais de second registre d'ADR, ni de second dossier d'audits : Archgate lit `docs/architecture/adr/`
  (`.archgate/config.json`, `paths.adrs`), `.archgate/adrs/` ne contient que `.gitkeep`, et `archgate adr create`
  n'est pas utilisé (il numérote par domaine)
- Jamais de document versionné sous `docs/project/rapport-supervisor/` : c'est le dossier local du superviseur de
  code (rapports et état de session, auto-ignoré), fixé par `log_subdir` dans `.claude/supervisor.config.json`.
  Jamais de rapport ailleurs — ni `docs/rapport-supervisor/`, ni un `docs/` imbriqué dans un sous-dossier
- Jamais dans `docs/user/` : un SHA, un compteur de tests ou de couverture, un numéro d'issue ou de PR,
  un nom de gate, une commande `./mvnw`, un chemin `src/` — c'est du contenu `developer/` ou `project/`
  (`UserDocumentationSpeaksOfTheInstalledProductTest` le refuse dans toute page active ; seules les notes de
  version déjà publiées, relevés datés, en sont exemptées par leur nom)
- Jamais de seuil de qualité dans `docs/README.md` : c'est un aiguillage ; les seuils vivent dans
  `docs/developer/README.md` et `docs/developer/BUILD_AND_TEST.md`, que
  `RepositoryDocumentationCoherenceTest` compare à `config/m21-quality-ratchets.properties`
- Jamais de contenu de pilotage (état d'un jalon, priorités, preuve) dans `docs/developer/`

## Déplacer ou renommer un fichier de `docs/`

Un chemin de documentation est **épinglé** : des tests d'architecture le lisent, des validateurs
`.ps1` et `.sh` vérifient sa présence, des workflows et le hook `post-edit.ps1` le citent.

1. Déplacer avec `git mv`, jamais par suppression puis recréation.
2. Chercher l'ancien chemin dans **tout** le dépôt, sous ses deux écritures (`docs/x/` et `docs\x\`),
   et dans les motifs d'expression régulière du hook (`docs[\\/]x[\\/]`).
3. Recalculer les liens relatifs des pages déplacées **et** de celles qui pointent vers elles.
4. Lancer `RepositoryDocumentationCoherenceTest` — il refuse un lien Markdown cassé — puis les tests
   d'architecture qui lisent le fichier.

## Relevés datés — liens oui, prose non

Les plans d'exécution, les `VALIDATION_*`, les audits, les études, les notes de version, les
changements OpenSpec archivés sont des relevés datés. Lors d'un déplacement, leurs
**liens** sont recalculés, leur **prose** n'est pas réécrite : elle cite les chemins de sa date. La
table de correspondance entre anciens et nouveaux chemins est tenue dans `docs/project/README.md` —
la compléter à chaque déplacement plutôt que de retoucher un relevé.

## Hors de `docs/`

`contracts/public-surfaces.tsv`, `distribution/README.md`, `scripts/README.md` et `SECURITY.md`
restent où ils sont : ils sont lus par des outils ou attendus à cet endroit.
`.archgate/` porte la configuration d'Archgate, pas de documentation.
