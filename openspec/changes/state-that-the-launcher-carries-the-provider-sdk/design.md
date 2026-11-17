# Conception : écrire que le launcher porte le SDK provider, et le tenir par une règle

## Contexte

- [EXISTANT VÉRIFIÉ] `SEC-0129`, Decision §1 : « Ni `morpheus-domain`, ni `morpheus-application` (POM compris), ni le
  launcher ne dépendent du SDK ou de `morpheus-provider-reference`. » Do's and Don'ts : « Ne pas faire dépendre
  `morpheus-domain` ou `morpheus-application` du SDK ou du plugin de référence » et « Ne pas embarquer
  `morpheus-provider-reference` dans `morpheus-cli` » — ces deux puces sont vraies ; seule la mention du launcher dans
  le §1 est fausse.
- [EXISTANT VÉRIFIÉ] `GEN-0127` §1 : `MorpheusMain` est le launcher unique (CLI, `mcp --stdio`, `api`).
- [EXISTANT VÉRIFIÉ] Modules dont le POM déclare `morpheus-provider-sdk` en compilation, relevés le 17/11/2026 sur
  `1a539c22` : `morpheus-api`, `morpheus-cli`, `morpheus-mcp`, `morpheus-provider-reference`,
  `morpheus-provider-testkit`, `morpheus-coverage-report` (agrégateur de rapports). `morpheus-architecture-tests` le
  déclare en portée de test.
- [EXISTANT VÉRIFIÉ] `ProviderPluginProbeProcess.command` (`:253-264`) passe à la JVM enfant le classpath de l'hôte
  pour lancer `ProviderPluginProbeWorker` : le launcher doit porter le SDK pour que le probe fonctionne.
- [EXISTANT VÉRIFIÉ] Contrôles existants : `ProviderPluginPlatformContractTest#domainAndApplicationDoNotDependOnSdkOrReferencePlugin`
  (POM de `domain` et `application`), `#referenceProviderIsNotEmbeddedAndExecutableSurfacesAreFailClosed` (référence
  absente du POM de la CLI), `AdapterSiblingArchitectureTest#builtInProvidersDoNotDependOnTheProviderSdk` (bytecode).
- [EXISTANT VÉRIFIÉ] ADR-0090 (archivé), §« plugin de référence » : « n'est pas une dépendance runtime du launcher ».

## Exigences couvertes

| Capacité | Exigence (titre exact) | Rubriques de conception qui y répondent |
|---|---|---|
| `provider-sdk-dependency-boundary` | Les modules qui compilent contre le SDK provider DOIVENT être exactement ceux que le build nomme | D1, D2 |

## Composants et frontières

`morpheus-architecture-tests` : une méthode de test lit les `<module>` du POM racine, puis le POM de chaque module, et
compare l'ensemble de ceux qui déclarent `morpheus-provider-sdk` sans portée ou en portée `compile` à une constante de la
classe ; un ensemble interdit (`morpheus-domain`, `morpheus-application`, `morpheus-provider-openspec`,
`morpheus-provider-markdown`, `morpheus-provider-synthetic`) est refusé quel que soit le contenu de la constante. Place
proposée : `ProviderPluginPlatformContractTest` (paquet `m22`, qui lit déjà les POM du SDK). La constante nomme des
modules, pas des classes de production : elle n'entre pas dans `ProductionClassListsArchitectureTest`.

## Modèle de domaine et invariants

Sans objet.

## Traitements et interactions

Sans objet : contrôle de build.

## États et transitions

Sans objet.

## Contrats d'interface

Sans objet.

## Persistance et intégrité

Sans objet.

## Transactions, concurrence et idempotence

Sans objet.

## Sécurité et isolation

Le texte corrigé garde l'invariant de sécurité réel : le plugin de référence, code tiers par nature, n'est jamais
embarqué dans le launcher.

## Gestion des erreurs et reprise

Sans objet.

## Observabilité

Sans objet.

## Performance et stratégie de mesure

Sans objet.

## Compatibilité, migration et retour arrière

Aucune donnée, aucune surface. Retour arrière : restaurer le texte, retirer la méthode.

## Décisions

### D1 : forme du changement de la décision
- **Nature** : [QUESTION] décision du mainteneur ; [PROPOSITION] clarification de `SEC-0129`.
- **Exigences servies** : `provider-sdk-dependency-boundary` › Les modules qui compilent contre le SDK provider DOIVENT être exactement ceux que le build nomme
- **Options** :
  - **Clarification** de `SEC-0129` §1 : « Ni `morpheus-domain`, ni `morpheus-application` (POM compris) ne dépendent
    du SDK ; le launcher porte le SDK, dont il exécute la découverte, le probe et le worker, et n'embarque jamais
    `morpheus-provider-reference`. Les modules qui compilent contre le SDK sont la liste que tient
    `ProviderPluginPlatformContractTest` (méthode à écrire). » Recevable si le mainteneur lit le §1 comme une
    formulation fautive de l'intention d'ADR-0090 (archivé) : le sens visé ne change pas (`.claude/rules/adr.md`,
    « clarifier son texte si son sens ne change pas »).
  - **ADR consolidé** qui remplace `SEC-0129` : exigé si le mainteneur lit le §1 tel qu'écrit comme la décision en
    vigueur, dont le changement serait alors partiel. **ADR à proposer.**
- **Proposition** : clarification si `AUD-ARC-30` n'est pas engagé dans la même PR ; s'il l'est, un seul ADR consolidé
  qui porte les deux changements (la séparation SPI / hôte modifie de nouveau le §1 et la conséquence « Le SDK est une
  surface publique »), pour ne pas faire deux remplacements successifs.
- **Compromis** : la clarification ne laisse pas de trace d'un changement de sens dans le registre ; l'historique est
  dans Git et dans ce change.
- **ADR** : `SEC-0129` ; ADR à proposer selon l'option retenue. Budget de la section Decision : 2 000 caractères
  (`AdrBriefingBudgetTest`) ; mesurer après réécriture.

### D2 : une liste exacte de modules plutôt qu'une phrase
- **Nature** : [PROPOSITION]
- **Exigences servies** : `provider-sdk-dependency-boundary` › Les modules qui compilent contre le SDK provider DOIVENT être exactement ceux que le build nomme
- **Choix** : la décision cite la règle, la règle tient la liste des consommateurs du SDK lue dans les POM, dans les deux
  sens, plus un ensemble interdit.
- **Alternatives écartées** : énumérer les modules dans l'ADR — une copie de plus, que rien ne compare ; une règle
  ArchUnit seule — elle juge le bytecode du classpath de la suite, pas les POM, et ne voit pas un module hors de
  l'ensemble importé.
- **Compromis** : la liste change avec `AUD-ARC-27` (retrait de `morpheus-mcp`) et `AUD-ARC-30` (module hôte éventuel).
- **ADR** : couvert par D1.

## Écarts constatés

| Constat | Source A | Source B | Traitement proposé |
|---|---|---|---|
| Le launcher est dit sans dépendance au SDK | `SEC-0129` Decision §1 | `morpheus-cli/pom.xml:29`, `MorpheusProviderPluginCli` | D1 |
| « sans modifier … ni le launcher » | `docs/developer/PROVIDER_SDK.md:5` | vrai au sens « sans recompiler le launcher », ambigu | préciser la phrase (tâche 3.2) |
| Lien libellé `0090-…` vers `SEC-0129` | `docs/developer/PROVIDER_SDK.md:385` | `.claude/rules/documentation.md` (libellé d'un lien redirigé) | corriger le libellé (tâche 3.2) |

## Stratégie de vérification

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| Les modules qui compilent contre le SDK provider DOIVENT être exactement ceux que le build nomme | Le launcher porte le SDK et pas le plugin de référence | nouvelle méthode + `#referenceProviderIsNotEmbeddedAndExecutableSurfacesAreFailClosed` | à écrire / existant |
| idem | Un module ajoute le SDK sans être nommé | nouvelle méthode, violation plantée | à écrire |
| idem | Un module nommé ne déclare plus le SDK | nouvelle méthode | à écrire |
| idem | Un provider intégré nommé est refusé | nouvelle méthode | à écrire |

## Documentation durable et diagrammes à mettre à jour

`SEC-0129` ou son remplaçant (et l'index des ADR, `09-decisions.md` si un ADR est créé) ;
`docs/developer/specifications/provider-sdk-dependency-boundary.md` ; `docs/developer/PROVIDER_SDK.md`.

## Questions ouvertes

D1, qui conditionne la tâche 1.1 et la forme de la tâche 3.1.
