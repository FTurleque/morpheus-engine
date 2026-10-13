# ADR-0111 — Le produit embarque un seul binding de journalisation, le sien

- Statut : **Acceptée — post-audit 1.2.1**
- Date : 13 octobre 2026
- Dépend de : ADR-0062 (SDK MCP officiel), ADR-0087 (opérabilité local-first), ADR-0105 (aucun secret réutilisable persisté)
- Portée : `morpheus-cli` (`OperationalSlf4jServiceProvider`, son descripteur de service, le POM),
  `morpheus-application` (`OperationalEventCode.THIRD_PARTY_LOG`, `LocalOperationalRuntime.writesEvents`)

## Contexte

Le SDK MCP et ses dépendances journalisent par SLF4J, et par rien d'autre : une poignée de main qui expire, un flux
rompu, une trame qui ne se décode pas ne sont dits nulle part ailleurs. Le produit embarquait `slf4j-nop`. Tout ce que
ces bibliothèques rapportaient était donc jeté, que l'opérateur ait demandé un journal ou non : avec
`MORPHEUS_OPERATIONAL_LOGS=json` il recevait les événements du produit et aucun de ceux des bibliothèques qu'il
embarque (constat AUD-TRV-04 de l'audit du 9 octobre 2026).

Trois classes du produit journalisent par `System.Logger` (`BoundedStdioClientTransport`,
`BoundedStdioServerTransportProvider`, `SqliteTransactionRunner`). Mesuré le 13 octobre 2026 sur un JDK nu, avec et
sans le module `java.logging` : leurs enregistrements de niveau `INFO` et au-dessus sortent sur la sortie d'erreur, en
texte de deux lignes, dans tous les modes de lancement, et jamais sur la sortie standard. Ils ne sont donc pas perdus ;
ils ne sont simplement pas des événements.

## Décision

1. **Le binding est celui de MORPHEUS.** `morpheus-cli` déclare `slf4j-api` en portée de compilation et fournit
   `OperationalSlf4jServiceProvider`, enregistré par `META-INF/services/org.slf4j.spi.SLF4JServiceProvider`.
   `slf4j-nop` quitte le produit.

2. **Avertissements et erreurs seulement.** Un enregistrement de niveau `WARN` ou `ERROR` devient un événement
   `THIRD_PARTY_LOG` portant le nom du logger, le message formaté et, s'il y en a un, le **type** de l'échec joint. Les
   niveaux inférieurs sont refusés à la source (`isInfoEnabled()` rend faux) : la bibliothèque ne formate rien.

3. **Journal coupé, silence identique.** Quand le mode de journal est `off`, le fournisseur n'accepte aucun niveau et
   n'écrit rien : exactement le comportement de `slf4j-nop`. L'opérateur qui n'a rien demandé ne voit rien changer.

4. **Même puits, même rédaction.** L'événement passe par le puits opérationnel du processus, donc par
   `SensitiveValueRedactor` : un secret qu'une bibliothèque aurait placé dans son message y est masqué comme dans tout
   autre événement. Le message est borné à 2 048 caractères. Rien n'est écrit sur la sortie standard, qui est le flux
   de protocole du serveur MCP.

5. **Les trois utilisateurs de `System.Logger` ne bougent pas dans ce changement.** Ils sont entendus aujourd'hui. Les
   faire passer par le même canal change leur destination et leur forme ; cela se décide avec le mode de journal qui
   écrit ailleurs que sur la sortie d'erreur, où la question « où va cet enregistrement » se pose pour tous à la fois
   (tranché par ADR-0112 : ils restent sur la sortie d'erreur).

Cette décision a été prise sur la recommandation de la spécification
(`openspec/changes/leave-a-trace-when-a-failure-is-swallowed`, décision 4), le mainteneur ayant lancé le lot sans
l'amender.

## Alternatives écartées

- **Garder `slf4j-nop`.** C'est le défaut constaté.
- **Embarquer `slf4j-simple` ou `slf4j-jdk14`.** Ils écrivent du texte libre sur la sortie d'erreur, sans rédaction et
  quel que soit le mode : un opérateur qui n'a rien demandé recevrait des lignes, et celui qui a demandé du JSON par
  ligne recevrait un flux qui n'en est plus.
- **Embarquer Logback ou Log4j.** Une dépendance de plus, sa configuration, sa surface de vulnérabilités, pour router
  deux niveaux vers un puits qui existe déjà.
- **Reprendre le message d'un échec joint.** `FAILURE_SUPPRESSED` ne porte que le type, parce qu'un message d'exception
  peut nommer un emplacement du serveur ; la même règle vaut ici pour l'exception. Le message du logger, lui, est ce que
  la bibliothèque a choisi de dire, et il est gardé, rédigé et borné.

## Conséquences

- `slf4j-api` devient une dépendance de compilation de `morpheus-cli` au lieu d'être seulement transitive. Sa version
  reste gérée par le POM racine.
- Un second fournisseur sur le chemin de classes est une régression : SLF4J en choisirait un des deux et l'annoncerait
  sur la sortie d'erreur. Trois preuves la refusent (ci-dessous).
- Le journal opérationnel reste local : la sortie d'erreur du processus. Un message de bibliothèque peut nommer un
  chemin ; le rédacteur remplace le répertoire personnel, et aucun transport ne sert ce journal à distance.
- La liste des composants tiers perd `org.slf4j:slf4j-nop`.

## Preuves exécutables

- `OperationalSlf4jServiceProviderTest` : un avertissement et une erreur donnent chacun un événement ; rien sous
  l'avertissement ; rien quand le journal est coupé ; message borné ; secret rédigé par le puits ; un seul fournisseur
  sur le chemin de classes du produit, celui-ci ; dans un processus réel, une ligne JSON sur la sortie d'erreur et une
  sortie standard vide avec le journal actif, les deux sorties vides avec le journal coupé.
- `ProductReleaseContractTest` : le POM de `morpheus-cli` déclare `slf4j-api` et ne nomme plus `slf4j-nop`.
- Rupture volontaire du 13 octobre 2026 : `slf4j-nop` remis dans le POM fait échouer trois tests du premier et
  l'assertion du second, puis est retiré.
