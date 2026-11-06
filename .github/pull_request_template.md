## Résumé

<!-- Ce que la pull request change, et pourquoi. -->

## Intention de chaque assertion textuelle

<!--
Pour chaque assertion textuelle ajoutée ou modifiée dans la suite d'architecture (`morpheus-architecture-tests/`),
une ligne du tableau. Écrire « aucune » si la pull request n'en ajoute ni n'en modifie.
Critère : `.claude/rules/architecture.md`, section « Règle ArchUnit ou assertion textuelle ».
- Intention : une phrase qui dit ce qui est refusé, la mention interdite ou la dépendance qu'elle représente.
- Mécanisme : assertion textuelle, règle sur le bytecode, ou les deux.
- Violation plantée : pour une règle sur le bytecode, la violation introduite qui l'a fait échouer, puis retirée.
-->

| Fichier | Intention | Mécanisme | Violation plantée |
|---|---|---|---|
| | | | |

## Justification d'une modification de gouvernance

<!--
Pour chaque fichier de gouvernance modifié, la raison du changement. Écrire « aucune » si la pull request n'en touche
aucun. Critère : `.claude/rules/governance.md`.
-->

- `contracts/public-surfaces.tsv` :
- `config/*ratchets*.properties` (une hausse d'une clé de `config/debt-counter-ratchets.properties` cite l'ADR
  consolidé qui la décide) :
- `docs/developer/reference/openapi/*.yaml` :
- un test sous `morpheus-architecture-tests/` :

## Vérification

<!-- Les commandes lancées et leur verdict. -->
