# Tâches : qualifier le budget d'évidence sur des corpus réels

## 1. Mesure et décision

- [x] 1.1 Écrire l'outil de mesure de test (design.md, §Performance) et mesurer les trois projets en lecture seule —
  exigence : `provider-ingestion-budget` › The default evidence budget is qualified on measured corpora ;
  vérification : tableau par corpus dans la PR (specs, évidence actuelle, évidence comptée une fois par ligne,
  distribution par exigence)
- [x] 1.2 Faire trancher D1 par le mainteneur sur cette mesure, et consigner le choix dans design.md — vérification :
  D1 passe de [PROPOSITION] à [DÉCISION]

## 2. Qualification

- [x] 2.1 Écrire `OpenSpecEvidenceBudgetQualificationTest` (corpus généré au-delà du plus gros projet mesuré, corpus
  au-delà de la borne) (à écrire) — même exigence ; vérification : le premier cas échoue avant 2.2
- [x] 2.2 Appliquer D1 (comptage par plages fusionnées et/ou borne qualifiée, marge écrite à côté de la borne avec sa
  mesure et sa date) — même exigence ; vérification : tests de 2.1 verts, tests existants du budget inchangés ou
  renforcés
- [x] 2.3 Écrire le test qui lit `openspec/` de ce dépôt avec le budget par défaut, après l'archivage de
  `accept-a-declared-derived-openspec-schema` (à écrire) — même exigence, scénario « The specification corpus of this
  repository » ; vérification : vert

## 3. Documentation durable

- [x] 3.1 Compléter `docs/developer/specifications/provider-ingestion-budget.md` (borne, mesure, marge, organigramme) —
  vérification : `RepositoryDocumentationCoherenceTest`, diagramme rendu
- [x] 3.2 Dire dans `docs/user/CLI.md` que `sync` est borné en volume et comment se lit le refus — vérification :
  `UserDocumentationSpeaksOfTheInstalledProductTest`

## 4. Vérification d'ensemble

- [x] 4.1 Lancer `openspec validate qualify-the-evidence-budget-on-real-corpora --strict` — vérification : aucun
  avertissement
- [x] 4.2 Relancer `sync` sur des copies jetables de `morpheus-engine` et `minos-code-intelligence` — vérification :
  `published: true` (relevé manuel, dans la PR)
- [x] 4.3 Lancer `./mvnw test -pl morpheus-architecture-tests` et `archgate check` — vérification : verts

## Workflow follow-up

- Archiver après `name-a-cumulative-budget-refusal-as-a-session-total`, qui crée la capacité.
- Si le mainteneur retient D2 ou D3, ouvrir un changement et un ADR pour chacun.
