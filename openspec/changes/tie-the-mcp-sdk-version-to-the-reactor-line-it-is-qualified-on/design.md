# Conception : lier la version du SDK MCP à la ligne Reactor sur laquelle il est qualifié

<!-- Conception allégée : un test de cohérence et, en option, une entrée de configuration de Dependabot. -->

## Contexte

- [EXISTANT VÉRIFIÉ] `pom.xml` importe `mcp-bom` (`${mcp-sdk.version}`) puis `reactor-bom` (`${reactor-bom.version}`)
  dans `<dependencyManagement>` ; l'import de `reactor-bom` fixe la version de `reactor-core` résolue, quelle que soit
  celle que le SDK déclare.
- [EXISTANT VÉRIFIÉ] `DependencyLineCurrencyTest` lit `reactor-bom.version` (expression `REACTOR_VERSION`), le
  commentaire de ligne et l'entrée `ignore` de `.github/dependabot.yml` ; il ne lit pas `mcp-sdk.version`.
- [EXISTANT VÉRIFIÉ] `<dependencyConvergence/>` (Enforcer, `pom.xml`) ne voit pas un écart que la gestion de versions
  d'un BOM importé résout avant lui.
- [EXISTANT VÉRIFIÉ] `.github/dependabot.yml`, écosystème `maven` : aucun groupe ; seule une entrée `ignore` sur
  `io.projectreactor:reactor-bom`.
- [EXISTANT VÉRIFIÉ] `mcp-core` 2.1.0 → `reactor-core` 3.7.0 ; `mcp-core` 2.2.0 → `reactor-core` 3.9.0-RC1 (Maven
  Central, 17/11/2026).

## Exigences couvertes

| Capacité | Exigence (titre exact) | Rubriques de conception qui y répondent |
|---|---|---|
| `dependency-line-currency` | Le build DOIT refuser une version du SDK MCP qu'aucune entrée ne lie à la ligne Reactor tenue | D1 |

## Décisions

### D1 : une table de faits dans le test de ligne
- **Nature** : [PROPOSITION]
- **Exigences servies** : `dependency-line-currency` › Le build DOIT refuser une version du SDK MCP qu'aucune entrée ne lie à la ligne Reactor tenue
- **Choix** : `DependencyLineCurrencyTest` porte une table `version du SDK → ligne Reactor qualifiée` (une entrée par
  version qualifiée, aujourd'hui celle de `mcp-sdk.version` sur la ligne retenue par `AUD-DEP-30`). Le test lit
  `mcp-sdk.version`, cherche l'entrée et la compare aux deux premiers nombres de `reactor-bom.version`. La table ne
  nomme aucune classe de production : elle n'entre pas dans `ProductionClassListsArchitectureTest`.
- **Alternatives écartées** : lire le POM de `mcp-core` dans le dépôt Maven local pour en déduire la ligne (le build ne
  doit pas dépendre de l'état du cache, et le SDK peut être qualifié sur une autre ligne que la sienne, option A
  d'`AUD-DEP-30`) ; une règle `requireUpperBoundDeps` de l'Enforcer (elle voit les versions déclarées, pas la ligne de
  qualification, et refuserait la ligne choisie sous l'option A).
- **Compromis** : qualifier une version du SDK impose d'ajouter sa ligne à la table dans le même lot que la montée.
- **ADR** : non concerné (ARCH-0062 décide du SDK ; la table est un contrôle de cohérence).

### D2 : grouper les deux BOM dans Dependabot
- **Nature** : [QUESTION] pour le mainteneur, avec [PROPOSITION]
- **Exigences servies** : aucune exigence propre ; réduit le bruit des pull requests que D1 refuse.
- **Options** : (a) un groupe `mcp-and-reactor` qui propose `io.modelcontextprotocol.sdk:*` et
  `io.projectreactor:reactor-bom` ensemble ; (b) rien, la pull request du robot échoue sur le gate et le mainteneur la
  reprend à la main.
- **[PROPOSITION]** : (b). L'entrée `ignore` de Reactor sur les mineures empêcherait de toute façon le groupe de proposer
  un changement de ligne ; un groupe ne ferait donc que masquer la nature de la montée.
- **ADR** : non concerné.

## Écarts constatés

| Constat | Source A | Source B | Traitement proposé |
|---|---|---|---|
| Le commentaire de l'import nomme la version du SDK en clair, sans lien avec `mcp-sdk.version` | `pom.xml`, commentaire | `mcp-sdk.version` | le test peut exiger que le commentaire nomme la version courante ; laissé hors exigence, à décider en revue |

## Stratégie de vérification

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| Le build DOIT refuser une version du SDK MCP qu'aucune entrée ne lie à la ligne Reactor tenue | La version courante du SDK est liée à la ligne tenue | nouvelle méthode de `DependencyLineCurrencyTest` | à écrire |
| idem | Une montée du SDK proposée seule fait échouer le build | même méthode, violation plantée (`mcp-sdk.version` porté à 2.2.0 en local) consignée dans la Javadoc | à écrire |
| idem | Une version déclarée sur une autre ligne fait échouer le build | même méthode, entrée de table modifiée en local | à écrire |
| idem | Le contrôle a trouvé la propriété à lire | assertion de présence en tête de méthode | à écrire |

## Documentation durable et diagrammes à mettre à jour

`docs/developer/specifications/dependency-line-currency.md` : nouvelle exigence R3, son comportement et la
traçabilité ; `docs/architecture/risks/register.md`, RT-04 (rupture du SDK MCP), peut citer le gate dans ses mesures.

## Questions ouvertes

D2, sans effet sur l'exigence.
