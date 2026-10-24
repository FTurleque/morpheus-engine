# ADR-0111 — Le produit embarque un seul binding de journalisation, le sien

> **Archivée.** Cette décision est consolidée dans [ADR-0146](../BE-0146-journal-operationnel-slf4j-et-fichier-borne.md). Ce texte n'est plus en vigueur et n'est pas tenu à jour.

- Statut : **Remplacée par ADR-0146**
- Date : 2026-10-13

## Context

Le SDK MCP et ses dépendances journalisent par SLF4J, et par rien d'autre : une poignée de main qui expire, un flux
rompu, une trame qui ne se décode pas ne sont dits nulle part ailleurs. Un binding qui jette tout (`slf4j-nop`) perd
ces informations, que l'opérateur ait demandé un journal opérationnel ou non.

Les classes du produit qui journalisent par `System.Logger` (dans `morpheus-mcp-transport` et la persistance SQLite)
écrivent leurs enregistrements de niveau `INFO` et au-dessus sur la sortie d'erreur, en texte, dans tous les modes de
lancement, et jamais sur la sortie standard. Elles ne sont pas perdues ; elles ne produisent simplement pas
d'événements opérationnels.

Options considérées :

- **Garder `slf4j-nop`** : ce que les bibliothèques embarquées rapportent reste jeté.
- **Embarquer `slf4j-simple` ou `slf4j-jdk14`** : ils écrivent du texte libre sur la sortie d'erreur, sans rédaction
  et quel que soit le mode ; un opérateur qui n'a rien demandé recevrait des lignes, et celui qui a demandé du JSON
  par ligne recevrait un flux qui n'en est plus.
- **Embarquer Logback ou Log4j** : une dépendance de plus, sa configuration et sa surface de vulnérabilités, pour
  router deux niveaux vers un puits qui existe déjà.
- **Reprendre le message d'un échec joint** : écarté. Un message d'exception peut nommer un emplacement du serveur ;
  comme pour `FAILURE_SUPPRESSED`, seul le type de l'échec est gardé.

## Decision

1. **Le binding est celui de MORPHEUS.** `morpheus-cli` déclare `slf4j-api` en portée de compilation (version gérée
   par le POM racine) et fournit `OperationalSlf4jServiceProvider`, enregistré par
   `META-INF/services/org.slf4j.spi.SLF4JServiceProvider`. Le produit n'embarque aucun autre fournisseur SLF4J,
   `slf4j-nop` compris.
2. **Avertissements et erreurs seulement.** Un enregistrement de niveau `WARN` ou `ERROR` devient un événement
   `THIRD_PARTY_LOG` portant le nom du logger, le message formaté et, s'il y en a un, le **type** de l'échec joint.
   Les niveaux inférieurs sont refusés à la source (`isInfoEnabled()` rend faux) : la bibliothèque ne formate rien.
3. **Journal coupé, silence identique.** Quand le journal opérationnel est coupé, le fournisseur n'accepte aucun
   niveau et n'écrit rien : le comportement de `slf4j-nop`.
4. **Même puits, même rédaction.** L'événement passe par le puits opérationnel du processus, donc par
   `SensitiveValueRedactor` : un secret qu'une bibliothèque aurait placé dans son message y est masqué comme dans tout
   autre événement. Le message est borné à `OperationalSlf4jServiceProvider.MAX_MESSAGE_CHARACTERS`. Rien n'est
   écrit sur la sortie standard, qui est le flux de protocole du serveur MCP.
5. **Les utilisateurs de `System.Logger` ne passent pas par ce canal.** Ils restent sur la sortie d'erreur ; la
   destination du journal opérationnel est décidée par ADR-0112.

## Do's and Don'ts

### Do

- Déclarer `slf4j-api` sans version dans `morpheus-cli/pom.xml` et laisser le POM racine la gérer.
- Convertir un enregistrement SLF4J `WARN` ou `ERROR` en événement `THIRD_PARTY_LOG` passé par le puits opérationnel.
- Ne garder d'un échec joint que son type.

### Don't

- Ne pas embarquer un second fournisseur SLF4J (`slf4j-nop`, `slf4j-simple`, `slf4j-jdk14`, Logback, Log4j).
- Ne pas accepter les niveaux sous `WARN`, ni aucun niveau quand le journal opérationnel est coupé.
- Ne rien écrire sur la sortie standard depuis le binding.

## Consequences

### Positives

- Ce que les bibliothèques embarquées signalent rejoint le journal opérationnel, rédigé et borné.
- Un opérateur qui n'a pas demandé de journal ne voit aucun changement.

### Négatives

- `slf4j-api` est une dépendance de compilation de `morpheus-cli`, et non seulement transitive.

### Risques

- Un second fournisseur sur le chemin de classes est une régression : SLF4J en choisirait un des deux, l'annoncerait
  sur la sortie d'erreur, et ce que les bibliothèques journalisent serait de nouveau perdu.
- Un message de bibliothèque peut nommer un chemin : le rédacteur remplace le répertoire personnel, et le journal
  opérationnel reste local ; aucun transport ne le sert à distance.

## Compliance and Enforcement

- Règle Archgate `BE-0111-the-product-ships-one-logging-binding-its-own.rules.ts` : `no-other-slf4j-provider`.
- `morpheus-cli/src/test/java/com/morpheus/cli/OperationalSlf4jServiceProviderTest.java` : un avertissement et une
  erreur donnent chacun un événement ; rien sous l'avertissement ; rien quand le journal est coupé ; message borné ;
  secret rédigé ; un seul fournisseur sur le chemin de classes du produit, celui-ci ; dans un processus réel, une
  ligne JSON sur la sortie d'erreur et une sortie standard vide avec le journal actif, les deux sorties vides avec le
  journal coupé.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m20/ProductReleaseContractTest.java` : le POM
  de `morpheus-cli` déclare `slf4j-api` et ne nomme pas `slf4j-nop`.

## References

- [ADR-0062](../ARCH-0062-official-java-mcp-sdk-and-native-stdio.md) — SDK MCP officiel.
- [ADR-0087](../SEC-0087-local-first-operability-and-secure-diagnostic-defaults.md) — opérabilité local-first.
- [ADR-0105](../SEC-0105-no-secret-vault-no-reusable-secret-at-rest.md) — aucun secret réutilisable persisté.
- [ADR-0112](0112-the-logs-directory-holds-a-bounded-operational-log.md) — journal opérationnel borné dans le
  répertoire de logs.
