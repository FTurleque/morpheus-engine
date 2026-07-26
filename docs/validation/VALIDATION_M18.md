# Validation M18 — Real Providers & Multi-Provider Composition

Statut : **✅ VALIDÉ TECHNIQUEMENT — PR #86 prête à intégrer**

Date : 26 juillet 2026

Issue : #85  
PR : #86  
Head de code validé : `7e8caacff567f51354fcb88bd7505a6d135071c0`

## Question de sortie

> MORPHEUS peut-il construire une vue cohérente à partir de plusieurs providers réels en conservant identité, provenance, priorité et conflits sans devenir dépendant d'un format particulier ?

**Réponse : OUI.**

M18 introduit un deuxième provider réel de production, une composition multi-provider déterministe et provider-neutral, ainsi qu'une persistance snapshot-scoped des providers observés et conflits explicites.

## Invariants validés

```text
provider identifier != DomainIdentity
source path != identity
provider ownership is explicit
same logical entity may have multiple provider observations
precedence != provenance erasure
ambiguous continuity must be surfaced
conflict != silent last-write-wins
provider absence != project failure when optional
composition must be deterministic and explainable
provider-specific types never leak into domain/application contracts
```

## Deux providers réels

Validés dans le même projet :

```text
OpenSpec
Structured Markdown
```

Le module `morpheus-provider-markdown` fournit un vrai adapter read-only : discovery/probe, requirements, changes, constraints, decisions, tasks, acceptance criteria, provenance fichier/plage de lignes et diagnostics d'entrée.

Synthetic reste un provider de test et n'est pas utilisé comme deuxième preuve de production M18.

## Composition et conflits

Le chemin validé est :

```text
provider adapters
   -> normalized ProviderContribution
   -> MultiProviderCompositionService
   -> composed content + CompositionConflict*
   -> snapshot-scoped composition state
```

Les contrats prouvent :

- ordre de priorité stable et déterministe ;
- rapprochement uniquement par clé logique provider-neutral explicite ;
- conflit de contenu explicite ;
- conflit d'ownership explicite ;
- conflit de type/identité explicite ;
- absence vs valeur présente représentable sans exception ;
- candidats non sélectionnés conservés ;
- source et evidence ID conservés pour chaque candidat ;
- provider optionnel absent non fatal ;
- provider requis absent = échec explicite ;
- aucun last-write-wins silencieux.

## Persistance

Validé :

```text
MemoryCompositionStateStore
SqliteCompositionStateStore
SQLite migration V012
```

L'état de composition est lié au `KnowledgeSnapshotId` et séparé du contenu provider.

Les contrats prouvent :

- Memory == SQLite ;
- providers/priorités/conflits persistés ;
- SQLite close/reopen exact ;
- candidats et provenance conservés ;
- restauration du mode auto-commit même après erreur de sauvegarde.

## Surfaces validées

CLI :

```text
composition sync --project ID [--revision REV]
composition status --project ID
composition conflicts --project ID
```

MCP read-only :

```text
get_composition_status
list_composition_conflicts
```

HTTP :

```text
GET /api/v1/projects/{projectId}/composition
GET /api/v1/projects/{projectId}/composition/conflicts
```

OpenAPI : **1.7.0**.

Les surfaces exposent uniquement des projections provider-neutral et JSON-safe.

## Gate Maven autoritatif

Commande exécutée par le validateur M18 :

```powershell
.\validate-m18.cmd -SkipUpdate
```

Head testé :

```text
7e8caacff567f51354fcb88bd7505a6d135071c0
```

Résultats :

```text
Domain                         40/40 PASS
Application                  104/104 PASS
OpenSpec                       26/26 PASS
Structured Markdown             2/2 PASS
Synthetic                        7/7 PASS
SQLite                           7/7 PASS
MINOS Integration                8/8 PASS
NEXUS Integration                7/7 PASS
MCP                              6/6 PASS
API                            12/12 PASS
CLI                            29/29 PASS
Architecture                 170/170 PASS
---------------------------------------
TOTAL                        418/418 PASS
Failures                           0
Errors                             0
Skipped                            0
BUILD SUCCESS
```

Le module Memory Store ne contient pas de tests propres et n'ajoute donc aucun cas au total.

## Packaging Windows

Le même validateur a ensuite exécuté le packaging portable et les smokes.

Preuves :

```text
MCP/API/MINOS/NEXUS/M14-M18 classes + provider Markdown + V012 embedded: PASS
Packaged standalone optional-engines + M14 read-only + M17 controlled-write + M18 composition surface smoke: PASS
Packaged API health smoke: PASS
Portable archive creation: PASS
```

Archive :

```text
dist/morpheus-0.1.0-windows-x64.zip
33,919,431 bytes
```

L'archive contient MORPHEUS, son runtime Java, CLI/MCP/API, le provider Structured Markdown, la persistance V012 et les surfaces de composition M18. MINOS, NEXUS et JARVIS ne sont pas embarqués ni requis.

## Environnement du gate

```text
Windows 10 amd64
OpenJDK 24.0.1
Apache Maven 3.9.16 via Maven Wrapper
Java compilation target: release 21
```

## Avertissements non bloquants observés

Le gate a produit des avertissements connus concernant :

- accès natif SQLite sous Java 24 ;
- absence de provider SLF4J ;
- APIs dépréciées dans certaines fixtures MCP ;
- ressources/classes chevauchantes lors du shading.

Ils ne provoquent ni failure ni error et les gates Maven, packaging et smokes se terminent tous avec succès.

## Validateur reproductible

```powershell
.\validate-m18.cmd
```

Le validateur met à jour la branche, contrôle la toolchain, exécute le reactor complet, puis le packaging/smokes et produit un résumé automatique du premier échec éventuel.

## ADR

ADR-0084 — **Acceptée — M18** après preuve du présent gate.

## Conclusion

M18 est **VALIDÉ TECHNIQUEMENT** sur le head de code `7e8caacff567f51354fcb88bd7505a6d135071c0`.

Les commits de clôture postérieurs à ce SHA sont documentaires uniquement et ne modifient ni runtime, ni tests, ni migration, ni build. La PR #86 peut être intégrée.