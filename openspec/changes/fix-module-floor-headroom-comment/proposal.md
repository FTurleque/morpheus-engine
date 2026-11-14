# Proposition : le commentaire de la borne du mou dit neuf dispersions

## Why

`CoverageQualificationVerifier` (`morpheus-architecture-tests/.../m21/`) déclare deux constantes de marge :
`HEADROOM_SPREADS = 3L`, sans commentaire, et `MAXIMUM_HEADROOM_SPREADS = 9L`, sous un commentaire qui annonce « at
most three times its margin ». La justification de ce commentaire, un module qui perdrait l'essentiel de sa couverture
avant que son seuil ne morde, est celle du maximum : il documente donc la bonne constante et en annonce le tiers. Les
autres énoncés de la règle disent neuf : `GEN-0167` (décision 5 et Do's and Don'ts), la spécification
`per-module-coverage-floor` et ses scénarios, `.claude/rules/testing.md`, et le message de refus du vérificateur, qui
calcule avec la constante. C'est le constat `AUD-TST-27` de l'audit du 2026-10-09, ouvert le 14/11/2026 sur `d35e7466`.

Le gate n'est pas faux à l'exécution, puisque c'est la constante qui décide. Mais le lecteur de ce commentaire est
celui qui viendra modifier la borne : il pourrait « rétablir l'accord » en abaissant la constante.

## What Changes

- Correction du commentaire de `MAXIMUM_HEADROOM_SPREADS` : « at most nine times ».
- Ajout d'un commentaire à `HEADROOM_SPREADS`, qui dit ce que cette constante borne (la marge minimale d'un ratchet
  et d'un seuil de module au-dessus du plancher D2) et pourquoi.
- Aucun changement de valeur, de comportement de gate ni de message de refus.

## Capabilities

### New Capabilities

### Modified Capabilities

Aucune : aucune exigence ne change. Le changement pose `skip_specs: true`.

## Périmètre et exclusions

**Inclus :** les deux commentaires des constantes de marge de `CoverageQualificationVerifier`.

**Exclus :** un gate qui comparerait un nombre écrit en toutes lettres dans un commentaire à la valeur de la constante
documentée. L'action du constat ne le demande pas et sa généralisation est douteuse ; la revue suffit ici. La fiche
d'audit elle-même est mise à jour par son auteur, lors de la revérification suivante.

## Acteurs et surfaces concernés

Le mainteneur qui lit ou modifie les bornes de couverture. Aucune surface publique.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Le commentaire annonce trois là où la constante vaut neuf | existant vérifié | `CoverageQualificationVerifier.java`, déclaration des constantes de marge |
| La règle en vigueur est « au moins trois, au plus neuf dispersions » | existant vérifié | `GEN-0167`, décision 5 ; `openspec/specs/per-module-coverage-floor/spec.md` |
| `HEADROOM_SPREADS` sert le ratchet et le seuil de module hors plancher D2 | existant vérifié | ses deux usages dans `CoverageQualificationVerifier` |
| Aucun comportement ne change | décision | seuls des commentaires sont modifiés |

## Impact

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m21/CoverageQualificationVerifier.java`
  (commentaires seulement).
- Aucun ADR, aucun contrat, aucune spécification, aucun ratchet.

## Questions ouvertes

Aucune.
