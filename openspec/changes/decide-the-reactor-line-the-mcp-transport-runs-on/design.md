# Conception : décider de la ligne Reactor sur laquelle tourne le transport MCP

<!-- Conception allégée : version d'une dépendance tenue sur une ligne, un test de cohérence, sans surface ni
     persistance. La décision de version revient au mainteneur. -->

## Contexte

- [EXISTANT VÉRIFIÉ] `pom.xml` déclare `mcp-sdk.version` et `reactor-bom.version` (`2024.0.18` le 17/11/2026) et
  importe `reactor-bom` sous le commentaire « MCP SDK 2.1.0 is built on the Reactor 2024.0.x/3.7.x line. Keep the
  transport on that compatible train. ».
- [EXISTANT VÉRIFIÉ] `DependencyLineCurrencyTest` (`morpheus-architecture-tests`, paquet `d2`, classe de cohérence)
  exige la phrase « Keep the transport on that compatible train. », la ligne `2024.0` écrite en dur et un troisième
  nombre supérieur à zéro ; il exige aussi l'entrée `ignore` de `.github/dependabot.yml` sur les mises à jour majeures
  et mineures de `io.projectreactor:reactor-bom`.
- [EXISTANT VÉRIFIÉ] Avis Spring CVE-2026-47863 (relu le 17/11/2026) : plages 3.5.7–3.7.19 et 3.8.0–3.8.6, correctif
  open source 3.8.7, correctif 3.7.20 réservé au support commercial. CVE-2026-47857 : plage 3.5.0–3.7.19 selon l'audit
  (OSV, CVSS 5,9).
- [EXISTANT VÉRIFIÉ] Aucune source Java de MORPHEUS ne nomme `bufferTimeout`, `windowTimeout` ni `fairBackpressure` ;
  Reactor est appelé directement par `morpheus-mcp-transport` (`BoundedStdioClientTransport`,
  `BoundedStdioServerTransportProvider`, `SchedulerRelease`).
- [EXISTANT VÉRIFIÉ] `mcp-core` 2.1.0 dépend de `reactor-core` 3.7.0 ; `mcp-core` 2.2.0 (publié le 13/11/2026) de
  `reactor-core` 3.9.0-RC1 (POM relus sur Maven Central le 17/11/2026).
- [EXISTANT VÉRIFIÉ] ARCH-0062, conséquences négatives : « Le SDK impose sa ligne Reactor : `reactor-bom` reste sur la
  ligne compatible avec le SDK ». Le registre des risques porte RT-04 (rupture du SDK MCP), sans mention des CVE.

## Exigences couvertes

| Capacité | Exigence (titre exact) | Rubriques de conception qui y répondent |
|---|---|---|
| `dependency-line-currency` | Reactor SHALL be on a patched release of a maintained line the MCP SDK is qualified on | D1, D2 |
| `dependency-line-currency` | The update bot SHALL propose the patches of a held line and nothing else | D3 (inchangée) |

## Composants et frontières

Sans objet : aucune frontière de module ne bouge. `morpheus-mcp-transport` reste le seul module qui appelle Reactor
directement (`theMcpTransportDependsOnNoOtherMorpheusPackage` inchangé).

## Décisions

### D1 : la ligne Reactor tenue
- **Nature** : [QUESTION] pour le mainteneur, avec [PROPOSITION]
- **Exigences servies** : `dependency-line-currency` › Reactor SHALL be on a patched release of a maintained line the MCP SDK is qualified on
- **Options** :
  - **A** — qualifier le SDK MCP 2.1.0 sur `reactor-bom` 2025.0.x (`reactor-core` 3.8.x, à une version hors des plages
    des deux avis). La ligne reçoit des correctifs open source ; le SDK n'y est pas construit mais y est qualifié par les
    tests réels du transport. Coût : une montée de version mineure de Reactor sous un SDK compilé contre 3.7, à prouver
    par les tests réels ; la conséquence d'ARCH-0062 devient inexacte et se clarifie.
  - **B** — garder 2024.0.x et écrire au registre des risques une entrée datée (numéro suivant le plus haut attribué,
    RT-15 le 17/11/2026 — à revérifier) qui nomme les deux CVE, l'absence d'appel des opérateurs par MORPHEUS et par le
    SDK, et la condition de réouverture : une version GA du SDK MCP sur une ligne Reactor maintenue. Coût : la prochaine
    CVE de `reactor-core` reste sans correctif atteignable.
- **[PROPOSITION]** : A, à condition que la lecture des sources du SDK (tâche 1.1) et les tests réels du transport sur
  3.8.x passent ; sinon B. A ferme le constat au lieu de le dater ; B reste le repli écrit si la qualification échoue.
- **Alternatives écartées** : attendre une version GA du SDK sur une ligne maintenue sans rien écrire (un gate CVE
  resté vert n'est pas une décision) ; supprimer les deux CVE dans `config/dependency-check-suppressions.xml` (leurs
  notes sont sous le seuil, une suppression ne décide rien et relèverait la clé de
  `config/dependency-check-suppression-ratchets.properties` sans raison).
- **Compromis** : le spec delta est écrit pour A ; B impose de le réécrire (exigence d'acceptation écrite) avant toute
  tâche d'implémentation.
- **ADR** : ARCH-0062 couvre le SDK et nomme la ligne Reactor dans ses conséquences. Sous A, clarification de cette
  conséquence si la décision sur le SDK ne change pas, sinon ADR à proposer (consolidé, qui remplace ARCH-0062) ; sous
  B, non concerné (entrée du registre des risques).

### D2 : la ligne maintenue se déclare dans le test, pas dans le commentaire seul
- **Nature** : [PROPOSITION]
- **Exigences servies** : `dependency-line-currency` › Reactor SHALL be on a patched release of a maintained line the MCP SDK is qualified on
- **Choix** : `DependencyLineCurrencyTest` lit la ligne dans le commentaire de l'import et la compare à une constante du
  test qui déclare la ligne maintenue retenue ; une ligne non déclarée fait échouer le build en nommant la ligne et
  `reactor-bom.version`. La phrase « Keep the transport on that compatible train. » reste le marqueur de ligne tenue lu
  par la seconde exigence de la capacité.
- **Alternatives écartées** : lire l'état de maintenance d'une ligne sur le réseau (le build est hors ligne) ; garder
  `2024.0` en dur sans dire pourquoi (c'est l'état qui a laissé la ligne vieillir).
- **Compromis** : la constante se met à jour à la main au changement de ligne, dans le même lot que la propriété.
- **ADR** : non concerné.

### D3 : l'entrée `ignore` de Dependabot garde sa portée
- **Nature** : [DÉCISION]
- **Exigences servies** : `dependency-line-currency` › The update bot SHALL propose the patches of a held line and nothing else
- **Choix** : sous A comme sous B, la ligne reste tenue ; l'entrée `ignore` sur majeures et mineures reste en place.
- **Alternatives écartées** : retirer l'entrée (le robot proposerait la ligne suivante, qui suit la ligne pré-version du
  SDK 2.2).
- **ADR** : non concerné.

## Sécurité et isolation

Les deux CVE sont des dénis de service atteignables seulement par les opérateurs visés. Sans appel par MORPHEUS
([EXISTANT VÉRIFIÉ]) ni par le SDK ([QUESTION], tâche 1.1), elles ne sont pas exploitables aujourd'hui ; le risque porté
est l'absence de correctif atteignable pour la suivante.

## Compatibilité, migration et retour arrière

Option A : retour arrière par restauration de la propriété, du commentaire et de la constante du test ; aucune donnée
persistée. Option B : rien à migrer.

## Écarts constatés

| Constat | Source A | Source B | Traitement proposé |
|---|---|---|---|
| Le commentaire dit que le SDK « est construit » sur la ligne tenue ; sous A, il y serait seulement qualifié | `pom.xml`, commentaire de l'import | option A | réécrire le commentaire dans le même lot |
| ARCH-0062 dit que le SDK impose sa ligne Reactor | ARCH-0062, conséquences | option A | clarification ou ADR à proposer (D1) |
| La page durable décrit `2024.0` comme écrite en dur dans le test | `docs/developer/specifications/dependency-line-currency.md` | D2 | mettre la page à jour (tâche 3.1) |

## Stratégie de vérification

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| Reactor SHALL be on a patched release of a maintained line the MCP SDK is qualified on | The shipped Reactor is a patched one | `DependencyLineCurrencyTest#reactorIsOnAPatchedReleaseOfTheLineThePomHolds` | existant, à adapter |
| idem | The transport still works on it | `BoundedStdioClientTransportTest`, `BoundedStdioServerTransportProviderTest`, `MorpheusMcpStdioIntegrationTest`, tests de délai du client MCP | existant |
| idem | The shipped reactor-core is outside the ranges of the two known advisories | `./mvnw dependency:tree -pl morpheus-mcp-transport` et rapport Dependency-Check du profil `d2-security` | manuel |
| idem | A line without open-source fixes fails the build | nouvelle méthode de `DependencyLineCurrencyTest` | à écrire, violation plantée |

## Documentation durable et diagrammes à mettre à jour

`docs/developer/specifications/dependency-line-currency.md` (valeur tenue, ligne déclarée, diagramme) ; ARCH-0062 ;
`docs/architecture/risks/register.md` sous B ; la table des versions de `.claude/rules/build.md`, que
`GovernanceSurfaceCoherenceTest` compare au POM, suit la propriété `reactor-bom.version`.

## Questions ouvertes

Aucune autre que D1, à trancher avant le groupe 2 des tâches.
