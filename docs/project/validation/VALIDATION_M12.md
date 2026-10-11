# VALIDATION M12 — Intégration optionnelle MINOS

Date : **24 juillet 2026**

Statut : **✅ VALIDÉ**

## Candidat validé

```text
branch = m12/minos-integration
head   = ca0073a875bcf28114a2945b141fc8c45f88930e
PR     = #62
issue  = #61
```

Baseline intégrée :

```text
M11 merge = e30ed4095700b445fedc4517c22ff447c22238f4
M11 gate  = 314/314 PASS
```

## Gate Maven Windows

Commande autoritative :

```powershell
.\mvnw.cmd clean test
```

Résultat :

```text
MORPHEUS Domain               21/21 PASS
MORPHEUS Application          84/84 PASS
OpenSpec Provider             26/26 PASS
Synthetic Provider             7/7 PASS
SQLite Store                   7/7 PASS
MINOS Integration              8/8 PASS
MORPHEUS MCP                   5/5 PASS
MORPHEUS API                   5/5 PASS
MORPHEUS CLI                  15/15 PASS
Architecture Tests           153/153 PASS
-----------------------------------------
TOTAL                        331/331 PASS
Failures                        0
Errors                          0
Skipped                         0
BUILD SUCCESS
```

Temps observé : `44.970 s`.

## Preuves M12 exécutées

### Transport MINOS

`MinosMcpTransportIntegrationTest` : **1/1 PASS**.

Le test démarre un vrai subprocess MCP STDIO fixture, négocie MCP et appelle :

```text
minos_index_status
minos_find_symbols
```

### Résolution externe

`MinosMcpExternalReferenceResolverTest` : **4/4 PASS**.

Contrat validé :

```text
system       = MINOS
resourceType = SYMBOL
project      = obligatoire
externalId   = symbolKey exact
revision     = activeSnapshotId attendu optionnel
```

Sémantiques prouvées : exact match, non-exact rejeté, ambiguïté explicite, revision mismatch explicite, indisponibilité non fatale.

### Optionalité

`MinosIntegrationSettingsTest` : **3/3 PASS**.

Sans configuration MINOS :

```text
state       = DISABLED
configured  = false
NO_RESOLVER = résultat normal
```

MORPHEUS reste utilisable via CLI, MCP et API.

### Résolution live non destructive

Les tests application/architecture prouvent que la résolution retourne une observation transitionnée sans réécrire la `ExternalReference` du snapshot publié.

Preuves :

```text
Memory         -> stored unchanged
SQLite         -> stored unchanged
SQLite reopen  -> stored unchanged
persisted      -> false
```

### Surfaces

CLI M12 : **15/15 PASS** au total pour le module CLI, dont les commandes `minos-status` et `external-references` ainsi que le vrai serveur MCP STDIO M12.

API M12 : **5/5 PASS** au total pour le module API, dont `MorpheusExternalReferenceApiContractTest`.

MCP : les 14 tools M10 restent disponibles et 2 tools read-only sont ajoutés :

```text
list_external_references
resolve_external_reference
```

Serveur MORPHEUS M12 : **16 tools read-only**.

## Architecture

`Architecture Tests` : **153/153 PASS**.

Guards M12 validés :

```text
domain/application -X-> integration-minos
domain/application -X-> com.minos..
api                -X-> integration-minos
integration-minos  -X-> cli/api/mcp/store
integration-minos  -X-> com.minos..
CLI = composition root
```

Aucune dépendance compile-time à l'implémentation MINOS.

## Packaging Windows

Commande :

```powershell
.\distribution\build-portable.ps1
```

Résultat :

```text
BUILD SUCCESS
MCP/API/MINOS adapter packaging proof: PASS
MORPHEUS 0.1.0-SNAPSHOT
{"version":"0.1.0-SNAPSHOT"}
{"system":"MINOS","state":"DISABLED","configured":false,...}
Packaged standalone MINOS-optional smoke: PASS
Packaged API health smoke: PASS
Portable archive creation: PASS
```

Archive :

```text
N:\workspace-dev\morpheus-engine\dist\morpheus-0.1.0-windows-x64.zip
size = 33,587,925 bytes
```

La distribution contient MORPHEUS, son runtime Java, MCP/API et le client/adaptateur MINOS optionnel. Elle n'embarque pas MINOS et ne l'exige pas pour démarrer.

## Warnings non bloquants

Observés sans échec :

```text
Xerial SQLite restricted native-access warning
SLF4J NOP provider warning
MCP SDK deprecated API warnings
Maven Shade overlapping resources / module-info warnings
```

Aucun warning n'a produit de failure, error ou skipped test.

## Décision

La question de sortie M12 reçoit la réponse **OUI** : MORPHEUS peut porter et résoudre live une `ExternalReference` MINOS via MCP STDIO, conserver l'identité/révision explicites et enrichir la traçabilité intention → code sans intégrer le domaine MINOS et sans réécrire les snapshots publiés.

MORPHEUS reste pleinement fonctionnel lorsque MINOS n'est pas configuré.

Les ADR suivantes sont donc acceptables sur la base des preuves exécutées :

```text
ADR-0069 — Intégration MINOS par MCP STDIO inter-processus
ADR-0070 — Référence MINOS par symbolKey exact et révision explicite
ADR-0071 — Résolution externe live sans mutation d'un snapshot publié
ADR-0072 — Configuration runtime et surfaces MINOS optionnelles
```

Le smoke cross-repo contre un vrai JAR MINOS reste une preuve de compatibilité additionnelle utile, mais n'est pas requis pour le gate autonome M12 validé ci-dessus.

**M12 est VALIDÉ. La fusion de PR #62 reste soumise à une autorisation explicite distincte.**
