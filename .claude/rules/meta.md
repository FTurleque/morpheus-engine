# Règles — Fraîcheur des règles elles-mêmes

Ce fichier existe parce qu'une dérive réelle a été constatée le 31/08/2026 :
`rules/testing.md` citait un ratchet coverage de 47%/40%, `rules/governance.md` citait
"711 et 253", `docs/README.md` citait 820/258/50.6%/43.0% — **trois chiffres différents
pour le même seuil**, alors que la source réelle (`config/m21-quality-ratchets.properties`)
donnait une quatrième valeur. Un agent qui aurait suivi `.claude/rules/` tel quel aurait
raisonné sur des seuils faux.

## Principe

**Tout chiffre présent dans `.claude/rules/*.md` est une copie, jamais l'original.**
Les fichiers de ce dossier décrivent des règles *stables* (ce qui est interdit, ce qui
est obligatoire, où chercher). Dès qu'une règle s'appuie sur un **nombre qui évolue avec
le projet** (seuil de coverage, nombre de tests, nombre d'ADR, nombre de gates, numéro de
version, compteurs `dependency:analyze`), ce nombre doit être traité comme périssable.

## TOUJOURS

- Avant un audit de gouvernance, de coverage, ou toute réponse qui cite un seuil chiffré :
  relire la source vivante, pas cette page. Sources vivantes connues :
  - `config/m21-quality-ratchets.properties` — tests/architecture/coverage minimums
  - `morpheus-architecture-tests/.../d2/CoverageQualityGateTest.java` et
    `D2RepositoryHardeningArchitectureTest.java` — planchers D2 asserts textuellement
  - `pom.xml` (racine) — version produit, versions pinnées
  - `docs/adr/` — compter les fichiers, ne pas répéter un total mémorisé ("N ADRs" doit
    toujours être vérifié par un `glob`/`ls`, jamais recopié tel quel ; un doublon de
    numérotation `0095-*` a existé jusqu'au 01/09/2026, corrigé en renumérotant le second
    fichier en `0097-*` — la numérotation elle-même n'est donc pas à l'abri d'un futur
    doublon et mérite d'être revérifiée avant d'assigner un nouveau numéro)
  - `contracts/public-surfaces.tsv` — compter les lignes et les sentinelles réellement
    présentes plutôt que de citer un total figé
- Si un chiffre trouvé dans les sources ci-dessus diverge d'un chiffre écrit dans
  `.claude/rules/*.md` ou `CLAUDE.md`, **le signaler explicitement** dans la réponse et
  proposer la correction de la règle dans la même session — ne pas trancher silencieusement
  en faveur de l'un ou l'autre
- Quand un ratchet (`config/m21-quality-ratchets.properties` ou équivalent futur) change :
  répercuter la nouvelle valeur **dans le même changement** partout. Cette page annonçait
  trois destinations ; le 04/09/2026 une hausse de ratchet en a fait échouer **quatre tests
  d'architecture** sur des fichiers absents de cette liste. Ne pas se fier à la liste : lancer
  `RepositoryDocumentationCoherenceTest` + `ProductionIntegrityContractTest` et laisser les
  échecs énumérer les destinations réelles. Constatées ce jour-là :

  ```text
  config/m21-quality-ratchets.properties        (source normative)
  README.md                                     (phrase « Le ratchet global est … »)
  docs/README.md
  docs/developer/BUILD_AND_TEST.md              (tableau + « verrouillée à » + règle de baisse)
  docs/developer/PRODUCTION_INTEGRITY.md
  docs/developer/README.md
  docs/governance/DOCUMENTATION_STATUS.md
  docs/governance/ROADMAP.md
  docs/architecture/arc42/11-risques-dette.md
  docs/architecture/risks/register.md           (état CI actif + DT-10)
  scripts/README.md
  distribution/README.md
  .claude/rules/testing.md
  .claude/rules/governance.md
  RepositoryDocumentationCoherenceTest          (valeurs épinglées, hausse délibérée)
  ```

  Les **mesures historiques** datées ne se réécrivent pas : seule une valeur active se met à jour
- Traiter `post-edit.ps1` (hook) comme un filet de sécurité, pas une garantie : il avertit
  quand un fichier de gouvernance change, mais ne vérifie pas la cohérence des valeurs

## JAMAIS

- Jamais citer un seuil de coverage, un nombre de tests ou un nombre d'ADR de mémoire sans
  l'avoir revérifié dans la session courante si la réponse a un impact décisionnel
  (passer/casser un gate, autoriser une PR, juger une régression)
- Jamais supposer qu'une règle `.claude/rules/*.md` est plus récente que le code — en cas
  de doute, le code et les scripts de validation font foi, pas la documentation
