# ADR-0116 — Les journaux sont conservés pour la vie de la base et lus par pages

> **Archivée.** Cette décision est consolidée dans [ADR-0131](../BE-0131-pagination-des-collections-et-journaux.md). Ce texte n'est plus en vigueur et n'est pas tenu à jour.

- Statut : **Remplacée par ADR-0131**
- Date : 17 octobre 2026
- Dépend de : ADR-0107 (vocabulaire de page, amendement du 17 octobre 2026), ADR-0108 (une réponse dit ce qu'elle n'a pas
  observé), ADR-0110 (une copie est gardée avant que le schéma avance)
- Portée : `policy_audit`, `change_lifecycle_mutation_audit`, `sync_source_archives` ; `PolicyPackStore.listAudit`,
  `ChangeLifecycleMutationStore.listAudit`, `SyncStateStore.listArchives`

## Contexte

Trois tables ne font que croître : l'audit de configuration des policies (un enregistrement par écriture de
configuration), l'audit des mutations de cycle de vie (un par transition) et les archives de synchronisation (une ligne
par fichier source qui disparaît ou se déplace). Aucune instruction du produit ne supprime d'aucune des trois. Les trois
étaient lues sans borne ; une seule a un lecteur — l'audit des policies, atteint par HTTP, MCP et la CLI. Le serveur
distant refuse une réponse amont au-delà de `MAX_PROXY_RESPONSE_BYTES` (`502 UPSTREAM_RESPONSE_TOO_LARGE`) : un journal
devenu trop long devenait illisible à distance, sans recours.

Les tailles n'ont jamais été mesurées : ni par l'audit, ni ici.

## Décision

1. **Un journal est lu par pages, bornées au store.** L'audit des policies est servi page par page sur les trois
   transports (ADR-0107, amendement du même jour). Le port n'offre plus de lecture complète de celui-là.
2. **Les deux journaux sans lecteur sont barrés, pas paginés.** `ChangeLifecycleMutationStore.listAudit` et
   `SyncStateStore.listArchives` rendent toutes les lignes d'un change ou d'un projet et n'ont aucun appelant de
   production. Les paginer demanderait de concevoir un ordre — pour les archives, de résoudre une seconde fois le problème
   du texte d'instant — pour aucun appelant. `AppendOnlyJournalReadsArchitectureTest` refuse qu'une classe de production
   les appelle ; la surface qui en aura besoin les borne d'abord.
3. **Un journal est conservé pour la vie de la base.** Aucune instruction du produit ne supprime d'une de ces tables ; une
   assertion textuelle le tient (`noStatementOfTheProductDeletesFromAJournal`). Ce qu'un opérateur peut faire de leur
   taille : la sauvegarde et la restauration hors ligne qu'il a déjà. Ce qui rouvrirait la question : une taille mesurée
   qui pose un problème réel, ou une obligation de purge écrite. Une purge est une décision sur ce que le produit garde
   comme preuve, pas un correctif de performance.
4. **Les archives de synchronisation n'ont pas de lecteur, et on continue de les écrire.** C'est la trace qu'une source a
   disparu ; un lecteur est une capacité à proposer, pas un effet de bord de cette décision.

## Conséquences

- La page d'un journal profond coûte plus que celle d'un journal court : `OFFSET` parcourt l'index jusqu'au décalage. Il ne
  matérialise que la page, ce qui est le tas dont parlait le constat ; le parcours est noté, pas caché.
- Une page lue pendant qu'un enregistrement est ajouté peut glisser ; `totalMatches` le montre (ADR-0107).
- Aucune purge n'est spécifiée, aucune n'est possible sans amender cette décision.
