# Conception : le guide de build énonce toute la fenêtre d'un seuil de module

Conception allégée : changement local à une page de documentation, sans comportement modifié.

## Contexte

[EXISTANT VÉRIFIÉ] `CoverageQualificationVerifier` borne la fenêtre d'un seuil de module par deux constantes :
`HEADROOM_SPREADS = 3L` (marge minimale) et `MAXIMUM_HEADROOM_SPREADS = 9L` (mou maximal). La dispersion retenue d'un
module est la plus grande de son écart entre résumés conservés et de sa référence, celle de l'échelle multipliée par la
part du module dans la population de la grandeur, arrondie à l'unité supérieure. Le plancher D2 est admis quand aucun
seuil plus haut ne tient dans la bande ; un module que la preuve ne porte pas reste au plancher.

[EXISTANT VÉRIFIÉ] `docs/developer/BUILD_AND_TEST.md` n'énonce que la marge minimale et donne pour référence celle de
l'échelle, entière.

Motivation : voir `proposal.md`, section Why.

## Exigences couvertes

Sans objet : aucune exigence ne change (`skip_specs: true`). La règle documentée est l'exigence « Le seuil d'un module
reste dans sa fenêtre qualifiée » de la capacité `per-module-coverage-floor`, inchangée.

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

Sans objet : un paragraphe se revient par un `git revert`.

## Décisions

### D1 : la page énonce la fenêtre entière, dans l'unité du vérificateur
- **Nature** : [DÉCISION]
- **Exigences servies** : aucune exigence modifiée ; documente `per-module-coverage-floor` telle qu'elle est.
- **Choix** : réécrire en place la phrase de la fenêtre pour dire les deux bornes (trois et neuf dispersions retenues),
  le sort du plancher D2 et d'un module absent de la preuve, et la référence proportionnelle à la part du module. Les
  bornes s'écrivent en marge sous la mesure, l'unité du vérificateur et du refus, pas en « haute » et « basse », qui
  s'inversent selon qu'on parle du seuil ou de la marge.
- **Alternatives écartées** : renvoyer seulement vers la spécification `per-module-coverage-floor`, alors que la page
  décrit déjà la règle et que ce lecteur pose le seuil ; un gate qui compare les nombres écrits aux constantes, que le
  constat ne demande pas.
- **Compromis** : la règle reste écrite à plusieurs endroits, et seule la revue garde cette prose juste.
- **ADR** : non concerné (`GEN-0167` porte la décision, inchangée).

## Écarts constatés

| Constat | Source A | Source B | Traitement proposé |
|---|---|---|---|
| Le guide omet le mou maximal de neuf dispersions (`AUD-TST-28`) | `BUILD_AND_TEST.md`, section « Le seuil propre à chaque module » | `GEN-0167`, spécification, vérificateur : trois à neuf | énoncer les deux bornes (D1) |
| Le guide donne pour référence d'un module celle de l'échelle, entière | même phrase | `GEN-0167`, Javadoc du vérificateur : référence multipliée par la part du module | corriger la définition (D1) |

## Stratégie de vérification

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| fenêtre de trois à neuf dispersions (inchangée) | les seuils en vigueur restent admis | `CoverageQualificationEvidenceTest` | existant |
| aucun texte de gouvernance ne diverge, aucun lien cassé | cohérence documentaire | contrôles `-Dgroups=coherence` du module d'architecture, `archgate check` | existant |

## Documentation durable et diagrammes à mettre à jour

`docs/developer/BUILD_AND_TEST.md`. `GEN-0167`, `docs/developer/specifications/per-module-coverage-floor.md` et
`.claude/rules/testing.md` disent déjà la fenêtre entière.

## Questions ouvertes

Aucune.
