# Spec delta : mcp-peer-gateway-lifecycle

## Purpose
Définir le cycle de vie d'un client de pair MCP commun aux passerelles MINOS et NEXUS, ce que chaque passerelle garde
en propre, et ce qui refuse une copie de ce cycle, pour le mainteneur et la CI. Documentation durable :
`docs/developer/specifications/mcp-peer-gateway-lifecycle.md`.

## ADDED Requirements

### Requirement: Le cycle de vie d'un client de pair MCP existe une fois

Le lancement du client borné d'un pair, l'armement de son échéance, l'initialisation et la vérification des outils
requis, l'appel d'un outil (réponse bornée, un seul contenu texte, refus d'un résultat en erreur ou vide), la fermeture
et la suppression de la copie vérifiée du JAR DOIVENT (MUST) être implémentés une seule fois, dans
`morpheus-mcp-transport`, paramétrés par le pair.

#### Scenario: Les deux passerelles ouvrent leur pair par le même socle

- **GIVEN** une passerelle MINOS et une passerelle NEXUS configurées
- **WHEN** chacune ouvre son pair, appelle un outil et se ferme
- **THEN** les deux traversent la même implémentation du cycle de vie, avec les outils requis et le nom de leur pair
- **AND** la copie vérifiée du JAR de chacune est supprimée à la fermeture

#### Scenario: Un pair auquel il manque un outil requis

- **GIVEN** un pair qui ne déclare pas l'un des outils requis de sa passerelle
- **WHEN** la passerelle l'ouvre
- **THEN** elle échoue avec `<PAIR> MCP server is incompatible; required tools missing: [<outils>]`, dans son type
  d'exception
- **AND** le client est fermé et la copie vérifiée du JAR est supprimée

### Requirement: Le socle du cycle de vie ne connaît aucun type MORPHEUS

Le socle NE DOIT PAS (MUST NOT) dépendre d'un autre paquet MORPHEUS que `com.morpheus.transport.mcp` : la passerelle
lui remet le nom de son pair, la fabrique de son exception et les observateurs qui comptent un abandon, un résidu de
copie et un processus du pair qui n'a pu être signalé ; la vérification de la copie du JAR reste dans la passerelle.

#### Scenario: Un abandon qui échoue est compté par la passerelle qui l'a remis

- **GIVEN** une opération dont l'échéance expire et dont l'abandon échoue
- **WHEN** le socle exécute l'abandon
- **THEN** l'échec est compté une fois dans la famille `MCP_PEER_SHUTDOWN` de l'enregistreur de la passerelle, puis
  propagé
- **AND** le socle ne nomme aucun type de `morpheus-application`

#### Scenario: Une copie vérifiée qui ne peut être supprimée

- **GIVEN** une copie du JAR que le système refuse de supprimer à la fermeture
- **WHEN** la passerelle se ferme
- **THEN** le résidu est compté dans la famille `STAGED_ARTIFACT_CLEANUP` et la fermeture se termine sans échouer
- **AND** la copie reste sur le disque

### Requirement: Le comportement observable des deux passerelles est conservé

Le passage par le socle NE DOIT PAS (MUST NOT) changer, pour MINOS comme pour NEXUS, le texte ni le type d'une
exception levée, les familles comptées, l'échéance d'une opération, les outils requis, ni la commande de lancement.

#### Scenario: Une opération qui dépasse son échéance

- **GIVEN** un pair qui ne répond pas avant l'échéance de l'opération
- **WHEN** la passerelle appelle un outil
- **THEN** elle échoue avec `<PAIR> MCP operation exceeded its <n> ms deadline`, dans son type d'exception, `<n>` étant
  l'échéance dérivée de son nombre de requêtes par opération

#### Scenario: Un outil qui rend une erreur ou une charge vide

- **GIVEN** un pair dont l'outil rend un résultat marqué en erreur, puis une charge vide
- **WHEN** la passerelle l'appelle
- **THEN** elle échoue avec `<PAIR> tool failed: <outil>: <contenu>`, puis `<PAIR> tool returned an empty payload:
  <outil>`, comme avant ce changement

### Requirement: Une passerelle ne refait pas le cycle de vie

La suite d'architecture DOIT (MUST) échouer, en nommant la classe, quand une classe de `com.morpheus.integration..`
dépend du client MCP du SDK ou de `BoundedStdioClientTransport`, ou appelle `PeerOperationDeadline.arm`,
`Files.deleteIfExists` ou `Throwable.addSuppressed`, et quand une classe qui décrit un lancement de pair
(`McpPeerLaunch`) ne passe pas par le socle.

#### Scenario: Une passerelle qui reconstruit son client

- **GIVEN** une classe d'intégration qui appelle `McpClient.sync(...)` elle-même
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme la classe

#### Scenario: Une passerelle qui nettoie sa copie elle-même

- **GIVEN** une classe d'intégration qui appelle `Files.deleteIfExists` sur sa copie du JAR
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme la classe
