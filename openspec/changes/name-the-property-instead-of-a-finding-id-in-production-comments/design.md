# Conception : nommer la propriété plutôt qu'un identifiant de constat dans les commentaires de production (AUD-QUA-39)

<!-- Conception allégée : correction de deux phrases de Javadoc, sans comportement. -->

## Contexte

- [EXISTANT VÉRIFIÉ] La Javadoc de `PageArguments` (`com.morpheus.mcp`) explique pourquoi trois outils de lecture
  (journal d'audit des policies, versions de policy pack, versions de saved view) prennent `offset` et `limit`
  (BE-0151), et pourquoi le désaccord entre schéma et code des outils de requête n'est pas gardé ici. Elle désigne ces
  deux sujets par `MCP-2` et `MCP-3`.
- [EXISTANT VÉRIFIÉ] Aucune autre source principale ne cite d'identifiant de constat ; `SEC-0129`
  (`ProviderPluginActivator`) est un ADR en vigueur.

## Exigences couvertes

Sans objet : `skip_specs: true`, aucune exigence ne change.

Sans objet : composants, modèle, traitements, états, contrats, persistance, sécurité, performance — commentaire seul.

## Décisions

### D1 : chaque identifiant devient la propriété qu'il désigne
- **Nature** : [DÉCISION]
- **Exigences servies** : aucune.
- **Choix** : ligne 17, « the three paginated read tools that the oversized-response guidance points to » ; ligne 39,
  « the disagreement between a published schema and the bound the code enforces, which concerns the export and query
  tools and is not guarded here ». Le reste de la Javadoc ne change pas.
- **Alternatives écartées** : supprimer les deux phrases (perd la raison de la pagination et la limite assumée du
  contrôle) ; remplacer par un lien vers l'audit (même défaut, et les fichiers d'audit datés ne sont pas une référence
  durable).
- **Compromis** : aucun.
- **ADR** : non concerné.

### D2 : pas de gate
- **Nature** : [DÉCISION]
- **Exigences servies** : aucune.
- **Choix** : la règle reste tenue par la revue (`.claude/rules/code-review.md`, « pas de commentaire … ni de
  ticket »).
- **Alternatives écartées** : un scan textuel des préfixes de constat dans les commentaires de production (les
  préfixes d'ADR `SEC-`, `ARCH-`, `GEN-`… partagent la forme et devraient être exemptés par le registre ; coût de
  maintien supérieur à deux occurrences en un an).
- **ADR** : non concerné.

## Écarts constatés

| Constat | Source A | Source B | Traitement proposé |
|---|---|---|---|
| Référence à un constat dans un commentaire | `.claude/rules/code-style.md` | `PageArguments.java` l. 17 et 39 | D1 |

## Stratégie de vérification

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| aucune | absence d'identifiant de constat | recherche des préfixes de constat dans `*/src/main/java` | manuel |
| aucune | aucun effet de compilation ou de test | `./mvnw test -pl morpheus-mcp` | existant |

## Documentation durable et diagrammes à mettre à jour

Aucune.

## Questions ouvertes

Un gate textuel reste possible si le mainteneur le demande (D2).
