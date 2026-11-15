# Conception : dire qu'un refus de budget cumulatif porte sur toute la lecture

<!-- Conception allégée : changement local d'un texte de refus, sans surface ni persistance. -->

## Contexte

- [EXISTANT VÉRIFIÉ] `ProviderIngestionBudget.exceeded(metric, source, value, maximum)` écrit tous les refus sous la
  même forme : `provider ingestion <metric> exceeds budget for <source>: <value> > <maximum>`.
- [EXISTANT VÉRIFIÉ] Grandeurs comptées sur la session (`Session`) : `file count` (`requireFiles(fileCount + 1)`),
  `aggregate bytes`, `line count` (`requireLines(lineCount + lines)`), `block count` (`addBlocks`), `entity count`
  (`addEntities`), `evidence bytes` (`read` en mode évidence et `addEvidenceFragment`). Grandeurs par élément :
  `document bytes`, et `line count` quand `requireUtf8Document` le vérifie sur un seul texte — **le même libellé désigne
  deux grandeurs**.
- [EXISTANT VÉRIFIÉ] `addEvidenceFragment(String)` attribue le fragment à `lastDocument`, le fichier dont la lecture a
  commencé en dernier ; les trois lecteurs OpenSpec l'appellent (`OpenSpecCurrentSpecificationReader`,
  `OpenSpecRequirementDeltaReader`, `OpenSpecChangeMetadataReader`).
- [EXISTANT VÉRIFIÉ] Quand `readUtf8Document` lève `WorkspaceFileTooLargeException`, `Session.read` imprime
  `maximum + 1` : une borne inférieure, présentée comme une mesure.
- [EXISTANT VÉRIFIÉ] `OpenSpecSpecificationContentReader` transforme le refus en diagnostic `INVALID_SOURCE` de source
  `ingestion-budget` ; le nom du fichier n'apparaît que dans le message.

## Exigences couvertes

| Capacité | Exigence (titre exact) | Rubriques de conception qui y répondent |
|---|---|---|
| `provider-ingestion-budget` | A cumulative budget refusal names the whole read, not the file being read | §Décisions D1, D2 |

## Décisions

### D1 : deux formes de refus, choisies par la grandeur
- **Nature** : [DÉCISION]
- **Exigences servies** : `provider-ingestion-budget` › A cumulative budget refusal names the whole read, not the file
  being read
- **Choix** : `exceeded` reçoit la portée de la grandeur. Par élément : forme actuelle. Cumulative :
  `provider ingestion <metric> for the whole read exceeds the budget: <total> > <maximum>, reached while reading <source>`
  (le verbe s'accorde avec le libellé de la grandeur, comme dans la forme par élément).
  La vérification par document du nombre de lignes prend le libellé `document line count`, distinct du `line count`
  de la session.
- **Alternatives écartées** : retirer le fichier du refus cumulatif (l'opérateur perd l'endroit où la lecture s'est
  arrêtée, utile pour estimer ce qui a été lu) ; nommer le plus gros contributeur (exige de tenir un compte par fichier
  pour un diagnostic, et un contributeur n'est pas plus coupable que les autres d'un total).
- **Compromis** : les tests qui lisent le texte changent ; la sortie des notes de version 1.2.1, relevé daté, garde
  l'ancien texte.
- **ADR** : non concerné (filtre de `.claude/rules/adr.md` : pas de coût de retour arrière).

### D2 : une valeur non mesurée n'est pas imprimée comme mesurée
- **Nature** : [DÉCISION]
- **Exigences servies** : idem, scénario « A bounded read that stops before the total is known »
- **Choix** : la branche `WorkspaceFileTooLargeException` écrit `more than <maximum>` au lieu de
  `<maximum + 1> > <maximum>`, dans les deux formes ; il en va de même du refus posé avant la lecture quand le budget
  de l'agrégat ou de l'évidence est déjà plein, dont la valeur n'était aussi qu'un minimum. Le cas « évidence » de la
  troisième branche est inatteignable (une lecture dont la borne d'élément est la borne d'évidence a au plus cette
  évidence de reste, et la deuxième branche la prend) : il est retiré.
- **Conséquence sur le scénario par document** : la lecture d'un document trop gros s'arrête à sa borne, donc sa
  taille n'est imprimée que lorsqu'elle est mesurée (`requireDocumentBytes`, `requireUtf8Document`) ; le scénario le
  dit.
- **Alternatives écartées** : relire le fichier pour mesurer sa taille (lecture non bornée, contraire au budget).
- **Compromis** : aucun.
- **ADR** : non concerné.

## Écarts constatés

| Constat | Source A | Source B | Traitement proposé |
|---|---|---|---|
| L'audit compte quatre grandeurs cumulatives | `AUD-QUA-24` (agrégat, lignes, entités, évidence) | `ProviderIngestionBudget.Session` (aussi `file count` et `block count`) | les six sont traitées ; l'écart est signalé dans la PR pour la revérification |
| `line count` désigne deux grandeurs | `requireUtf8Document` (par document) | `Session.read` (session) | D1 : libellé `document line count` pour la première |

## Stratégie de vérification

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| A cumulative budget refusal names the whole read, not the file being read | Evidence accumulated over many small files | `OpenSpecRefusedFileNameTest#anEvidenceTotalReachedOnASmallFileDoesNotAccuseIt` | écrit |
| idem | One document larger than the per-document budget | `ProviderIngestionBudgetTest` (cas `document bytes`, texte relu) | ajusté |
| idem | A bounded read that stops before the total is known | `ProviderIngestionBudgetPrecisionTest#aBoundedReadThatStopsEarlySaysMoreThanTheMaximum` | écrit |
| idem | (les six grandeurs cumulatives) | `ProviderIngestionBudgetTest#everyCumulativeQuantityIsRefusedForTheWholeRead` (une assertion par grandeur) | écrit |

## Documentation durable et diagrammes à mettre à jour

Page nouvelle `docs/developer/specifications/provider-ingestion-budget.md` (bornes, deux familles de grandeurs, formes
de refus, traçabilité), indexée dans `docs/developer/specifications/README.md`.

## Questions ouvertes

Aucune.
