# Conception : le commentaire de la borne du mou dit neuf dispersions

Conception allégée : changement local à une classe, sans comportement modifié.

## Contexte

[EXISTANT VÉRIFIÉ] `CoverageQualificationVerifier` porte les deux bornes de la bande de `GEN-0167` :

- `HEADROOM_SPREADS = 3L` est lu par la vérification de marge d'un ratchet (refus « it needs … ») et par celle d'un
  seuil de module qui n'est pas au plancher D2. Elle n'a aucun commentaire ;
- `MAXIMUM_HEADROOM_SPREADS = 9L` est lu par la seule vérification du mou d'un seuil de module (refus « a floor leaves
  at most … -- raise it to at least … »). Son commentaire écrit « three ».

Motivation : voir `proposal.md`, section Why.

## Exigences couvertes

Sans objet : aucune exigence ne change (`skip_specs: true`). La règle documentée est celle de la capacité
`per-module-coverage-floor`, sur la bande de trois à neuf dispersions, qui reste inchangée.

## Composants et frontières

Sans objet : aucune frontière ne bouge.

## Modèle de domaine et invariants

Sans objet : aucun type n'est touché.

## Traitements et interactions

Sans objet : aucun traitement ne change.

## États et transitions

Sans objet : aucun cycle de vie.

## Contrats d'interface

Sans objet : aucune surface publique.

## Persistance et intégrité

Sans objet : aucune donnée persistée.

## Transactions, concurrence et idempotence

Sans objet : aucun traitement concurrent.

## Sécurité et isolation

Sans objet : aucune frontière de confiance.

## Gestion des erreurs et reprise

Sans objet : les messages de refus sont inchangés.

## Observabilité

Sans objet : aucun journal ni compteur.

## Performance et stratégie de mesure

Sans objet : aucun traitement ne change.

## Compatibilité, migration et retour arrière

Sans objet : un commentaire se revient par un `git revert`.

## Décisions

### D1 : chaque constante porte son propre commentaire
- **Nature** : [DÉCISION]
- **Exigences servies** : aucune exigence modifiée ; documente `per-module-coverage-floor` telle qu'elle est.
- **Choix** : corriger « three » en « nine » au-dessus de `MAXIMUM_HEADROOM_SPREADS`, et écrire au-dessus de
  `HEADROOM_SPREADS` ce qu'elle borne (ratchet et seuil de module hors plancher D2, au moins trois dispersions) et
  pourquoi (plus près, une variation ordinaire entre runs ferait échouer le build).
- **Alternatives écartées** : un seul commentaire pour les deux constantes, la forme sous laquelle le chiffre a divergé ;
  un gate qui compare le nombre écrit à la constante, que le constat juge non généralisable.
- **Compromis** : la règle reste écrite à plusieurs endroits, et seule la revue garde le commentaire juste.
- **ADR** : non concerné (`GEN-0167` porte la décision, inchangée).

## Écarts constatés

| Constat | Source A | Source B | Traitement proposé |
|---|---|---|---|
| Le commentaire du maximum annonce trois dispersions | `CoverageQualificationVerifier`, commentaire de `MAXIMUM_HEADROOM_SPREADS` | `GEN-0167`, spécification, règles, constante : neuf | corriger le commentaire (D1) |

## Stratégie de vérification

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| bande de trois à neuf dispersions (inchangée) | les seuils en vigueur restent admis | `CoverageQualificationEvidenceTest` | existant |
| aucun texte de gouvernance ne diverge | cohérence documentaire | contrôles `-Dgroups=coherence` du module d'architecture, `archgate check` | existant |

## Documentation durable et diagrammes à mettre à jour

Aucune : `GEN-0167`, `docs/developer/specifications/per-module-coverage-floor.md` et `.claude/rules/testing.md` disent
déjà neuf.

## Questions ouvertes

Aucune.
