# Proposition : nommer la propriété plutôt qu'un identifiant de constat dans les commentaires de production (AUD-QUA-39)

## Why

`.claude/rules/code-style.md` interdit tout commentaire « référençant la tâche courante, un ticket » : l'identifiant
n'a de sens que pour qui a l'audit sous les yeux. La Javadoc de `PageArguments` (`morpheus-mcp`, paquet
`com.morpheus.mcp`) en cite deux, revérifié le 17/11/2026 sur `1a539c22` :

- ligne 17 : « so the three tools of the MCP-2 finding (the policy audit journal, the policy pack versions, the
  saved-view versions) » ;
- ligne 39 : « the defect class of the export finding (MCP-3), not of this one ».

Une recherche des formes `<préfixe>-<nombre>` (`MCP`, `CLI`, `SEC`, `DT`, `RT`, `PRF`, `QUA`, `TRV`, `DEP`, `ARC`,
`TST`, `AUD-…`) dans les sources principales des modules de production ne trouve aucune autre occurrence ; la seule
autre correspondance, `SEC-0129` dans `ProviderPluginActivator`, est un identifiant d'ADR en vigueur, permis.

Preuve : constat `AUD-QUA-39` (Faible), `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du 17/11/2026
sur `a04f32bd`, confirmé à la ligne près.

## What Changes

- Ligne 17 : « the three tools of the MCP-2 finding » devient la propriété désignée, les outils de lecture paginés que
  le conseil de réponse trop volumineuse vise.
- Ligne 39 : « the export finding (MCP-3) » devient la propriété désignée, le désaccord entre le schéma publié et la
  borne appliquée par le code des outils d'export et de requête.
- Aucun code, aucun comportement, aucun gate, aucune surface.

## Capabilities

### New Capabilities

### Modified Capabilities

Aucune : aucune exigence ne change. Le changement pose `skip_specs: true`.

## Périmètre et exclusions

**Inclus :** la Javadoc de `PageArguments`.

**Exclus :** un gate qui refuserait un identifiant de constat dans un commentaire de production (question ouverte de
design.md, non retenue par défaut) ; les identifiants de constat dans les sources de test, les relevés datés et les
changements OpenSpec, où ils sont la traçabilité attendue.

## Acteurs et surfaces concernés

Le lecteur du code de `morpheus-mcp`. Aucun acteur externe.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Deux identifiants de constat dans une Javadoc de production | existant vérifié | `PageArguments.java` l. 17 et 39 |
| Aucune autre occurrence dans les sources principales | existant vérifié | recherche des préfixes de constat, `1a539c22` |
| La règle de style interdit la référence à un ticket | existant vérifié | `.claude/rules/code-style.md`, section JAMAIS |
| Aucun ADR n'est requis | décision | correction de commentaire |

## Impact

`morpheus-mcp`, un fichier, commentaire seulement. Aucun test modifié. Aucune dépendance avec les autres constats du
sprint 9.

## Questions ouvertes

- Faut-il un gate textuel contre les identifiants de constat dans les commentaires de production ? Non proposé par
  l'audit ; peut attendre (design.md).
