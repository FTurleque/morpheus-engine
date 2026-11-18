# Tâches : compter la présence une fois par plateforme, depuis les modules déclarés

## 1. Reproduction

- [ ] 1.1 Écrire dans `scripts/tests/test_validator_verdicts.py` (à étendre) les cas « copie de travail sous
  `.claude/worktrees` » pour `validate-d2` et `validate-m21`, `.sh` et `.ps1` — exigence : `repository-root-location` ›
  A validator SHALL read only the POMs and the Surefire reports of the modules the root POM declares ; vérification :
  rouges avant 2.2 (POM « unexpected » pour D2, refus manquant pour M21)
- [ ] 1.2 Écrire `scripts/tests/test_surefire_totals.py` (à écrire) : liste de cas partagée (déclarées, générées
  `méthode()[N]`, échecs, erreurs, sautées, module sans rapports, copie sous `.claude/worktrees`) rejouée contre les
  deux bibliothèques — exigence : `gate-verdict-currency` › The split between written and generated executions SHALL be
  implemented once per scripting language ; vérification : rouge tant que les bibliothèques n'existent pas

## 2. Bibliothèques et validateurs

- [ ] 2.1 Créer `scripts/lib/Get-SurefireTotals.ps1` et `scripts/lib/surefire-totals.sh` (D1, D2), ASCII pur pour le
  `.ps1` — vérification : tests de 1.2 verts sous Linux et Windows
- [ ] 2.2 Remplacer les comptages de `validate-m19`, `validate-m20` (deux lectures), `validate-m21` à `validate-m27` et
  `validate-d2`, dans les deux langages, par l'appel de la bibliothèque — exigences : les deux du changement ;
  vérification : tests de 1.1 verts, cas existants de `test_validator_verdicts.py` et `test_validator_entry_refusals.py`
  verts sans modification de leurs attentes
- [ ] 2.3 Réécrire le compte des POM de `validate-d2.*`, `validate-r2.*` et `validate-r3.*` sur les modules déclarés
  (D2) — exigence : `repository-root-location` › A validator SHALL read only the POMs and the Surefire reports of the
  modules the root POM declares ; vérification : cas de 1.1 pour D2 vert, cas R2/R3 existants verts
- [ ] 2.4 Déclarer les deux bibliothèques dans l'inventaire de `scripts/tests/test_script_inventory.py` — vérification :
  `python -m pytest scripts/tests/test_script_inventory.py` vert
- [ ] 2.5 Mettre à jour `scripts/README.md` — vérification : `RepositoryDocumentationCoherenceTest` vert

## 3. Gate

- [ ] 3.1 Ajouter à `RepositoryTreeOwnershipTest` les deux interdits textuels de D3 (fichier de gouvernance :
  justification dans la section « Justification d'une modification de gouvernance » de la PR, intention de chaque
  assertion dans sa section) — exigences : les deux du changement, scénarios « A copy of the rule in a validator is
  refused » et « A recursive walk of the root in a script is refused » ; vérification : planter une copie de
  `\[[0-9]+\]` dans `validate-m22.sh` et un `rglob(` dans `validate-d2.sh`, constater l'échec qui nomme script et ligne,
  revenir en arrière, consigner la trace dans la PR
- [ ] 3.2 Mettre à jour `docs/developer/specifications/gate-verdict-currency.md` et
  `docs/developer/specifications/repository-root-location.md` (exigences, diagramme de design.md, traçabilité) —
  vérification : `RepositoryDocumentationCoherenceTest` vert

## 4. Vérification d'ensemble

- [ ] 4.1 Lancer `openspec validate count-presence-once-from-the-declared-modules --strict` — vérification : aucun
  avertissement
- [ ] 4.2 Compléter la matrice de traçabilité des deux pages durables (exigence → scénario → D1/D2/D3 → tâche → preuve)
  — vérification : chaque scénario a une preuve existante ou « preuve manquante »
- [ ] 4.3 Lancer `python -m pytest scripts/tests` sous Linux et sous Windows, `./mvnw test -pl morpheus-architecture-tests
  -Dtest=RepositoryTreeOwnershipTest`, puis `scripts\validate.cmd d2 -Version <version de ProductMetadata>` sur un poste
  où une copie existe sous `.claude/worktrees` — vérification : verts, et le relevé D2 égal à celui d'un poste sans copie

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire les specs principales consolidées et les deux pages durables.
