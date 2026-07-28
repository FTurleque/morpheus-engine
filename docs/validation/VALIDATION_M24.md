# VALIDATION M24 — Query DSL, Saved Views & Export/Reporting

Statut : **PASS — qualification Windows + Linux exact-head acquise sur le même SHA exécutable**

Date : 28 juillet 2026

Issue : #105  
PR : #106  
Branche : `m24/query-dsl-saved-views-reporting`

Baseline : `main@f70eaa1ad58633ee59874ab44f70963ab51152c6`.

Head exécutable qualifié Windows + Linux :

```text
be69e47da0ae209d2246df9c67bc08caeafb2bb0
```

Les commits postérieurs à ce SHA sont exclusivement documentaires. Toute modification de code produit, POM, contrat runtime, manifeste public, OpenAPI contractuel, packaging ou validateur invaliderait cette preuve et imposerait une nouvelle qualification Windows + Linux.

## Question de sortie

> Les utilisateurs peuvent-ils exprimer, sauvegarder et exporter des vues métier complexes sans dépendre d’un transport ou d’un format provider particulier ?

**Réponse : oui.** M24 introduit un DSL métier provider-neutral borné, des scopes projet/portfolio explicites, un moteur déterministe de filter/sort/projection/pagination, des saved views versionnées avec CAS, une persistance Memory + SQLite V014 et des exports JSON canonique, CSV et Markdown read-only. CLI, MCP et HTTP réutilisent les mêmes services applicatifs.

## Contrats prouvés

```text
DSL != SQL passthrough                       PASS
provider-specific types excluded             PASS
project scope explicit                       PASS
portfolio scope explicit                     PASS
portfolio ProjectSpecificationId preserved   PASS
filter/sort/projection/pagination             PASS
stable sort + deterministic identity tie-break PASS
absent/null != empty string                   PASS
saved view != materialized truth              PASS
SavedViewId independent from name             PASS
saved-view CAS / stale revision rejection     PASS
Memory persistence                            PASS
SQLite persistence V014                       PASS
canonical JSON export                         PASS
CSV export                                    PASS
Markdown export                               PASS
export != mutation                            PASS
query/export budgets                          PASS
CLI/MCP/HTTP convergence                      PASS
SBOM/provenance                               PASS
Windows portable                              PASS
Linux portable                                PASS
post-gate executable delta                    NONE
```

## Budgets qualifiés

```text
encoded query expression   <= 16 KiB
AST nodes                  <= 128
boolean nesting depth      <= 8
leaf predicates            <= 64
sort fields                <= 8
projection fields          <= 32
page size                  <= 500
export rows                <= 10,000
export bytes               <= 10 MiB
saved views per scope      <= 250
saved-view name            <= 160 chars
```

Un dépassement est une erreur explicite ; M24 ne transforme pas silencieusement un dépassement en résultat présenté comme complet.

## Gate Windows

Commande canonique :

```powershell
.\validate-m24.cmd 1.0.0
```

Preuve machine-readable :

```text
M24 VALIDATION PASS
sha=be69e47da0ae209d2246df9c67bc08caeafb2bb0
baseRef=origin/main
version=1.0.0
tests=543
architectureTests=221
lineCoverage=0.442936
branchCoverage=0.381166
queryDsl=PASS
savedViews=PASS
canonicalJsonExport=PASS
csvExport=PASS
markdownExport=PASS
queryBudgets=PASS
surfaceConvergence=PASS
sqliteV014=PASS
sbom=PASS
provenance=PASS
portable=True
postGateExecutableDelta=NONE
```

Preuves complémentaires Windows :

- `git diff --check` PASS ;
- reactor 17/17 SUCCESS ;
- 543 tests PASS contre baseline M23 >= 507 ;
- 221 tests d’architecture PASS contre baseline M23 >= 195 ;
- JaCoCo au-dessus des floors 25% lignes / 20% branches ;
- CycloneDX JSON/XML + provenance PASS ;
- runtime autonome Windows + `jdk.httpserver` + `java.sql` + `java.net.http` PASS ;
- classes M24 + migration V014 présentes dans le package ;
- Query DSL + budget page PASS ;
- saved views versionnées + stale CAS rejection PASS ;
- JSON canonique + CSV + Markdown read-only PASS ;
- convergence CLI/MCP/HTTP M24 PASS ;
- archive `morpheus-1.0.0-windows-x64.zip` créée ;
- `postGateExecutableDelta=NONE`.

Le répertoire local non suivi `dist-r1/` est hors delta tracké et n’entre pas dans la preuve M24.

## Gate Linux / WSL2

Le gate a été exécuté dans un clone propre WSL, avec checkout détaché du même SHA exécutable.

Environnement observé : OpenJDK `21.0.11`.

Commande dans le clone :

```bash
bash ./scripts/validate-m24.sh 1.0.0
```

Preuve machine-readable :

```text
M24 VALIDATION PASS
sha=be69e47da0ae209d2246df9c67bc08caeafb2bb0
baseRef=origin/main
version=1.0.0
tests=543
architectureTests=221
lineCoverage=0.443037
branchCoverage=0.381166
queryDsl=PASS
savedViews=PASS
canonicalJsonExport=PASS
csvExport=PASS
markdownExport=PASS
queryBudgets=PASS
surfaceConvergence=PASS
sqliteV014=PASS
sbom=PASS
provenance=PASS
portable=true
postGateExecutableDelta=NONE
```

Le wrapper a enregistré :

```text
M24 LINUX EXIT CODE: 0
```

Un message `: numeric argument required` est apparu après ce bloc, au retour du here-string PowerShell contenant des fins de ligne Windows. Il est extérieur à `scripts/validate-m24.sh` : le validateur avait déjà terminé avec code 0 et produit son bloc PASS complet.

Preuves complémentaires Linux :

- clone propre puis checkout détaché exact ;
- SHA vérifié avant le gate ;
- reactor 17/17 SUCCESS ;
- 543 tests PASS ;
- 221 architecture PASS ;
- CycloneDX JSON/XML + provenance PASS ;
- runtime Linux autonome créé ;
- classes M24 + V014 présentes dans le package ;
- Query DSL, saved views, exports et convergence publique PASS ;
- archive `morpheus-1.0.0-linux-x64.tar.gz` créée ;
- `postGateExecutableDelta=NONE`.

## Surfaces qualifiées

M24 expose les mêmes intentions métier sur les trois surfaces, sans imposer la même forme transport :

```text
query.execute
saved-view.create
saved-view.list
saved-view.get
saved-view.versions
saved-view.update
saved-view.archive
saved-view.execute
query.export
saved-view.export
```

Les surfaces publiques réutilisent `QueryDslParser`, validation/budgets, `QueryExecutionService`, `SavedViewService` et `QueryExportService` dans la couche application.

## Persistence

SQLite ajoute uniquement la migration additive :

```text
V014__saved_views.sql
```

Aucune migration historique n’a été modifiée. Le store SQLite conserve définition courante et historique immuable des révisions ; le store Memory implémente le même contrat observable.

## Conclusion

```text
Windows exact-head    PASS
Linux exact-head      PASS
Executable SHA        be69e47da0ae209d2246df9c67bc08caeafb2bb0
Tests                 543 PASS Windows + Linux
Architecture          221 PASS Windows + Linux
Windows coverage      44.2936% line / 38.1166% branch
Linux coverage        44.3037% line / 38.1166% branch
Query DSL             PASS
Saved views           PASS
SQLite V014           PASS
JSON/CSV/Markdown     PASS
Query/export budgets  PASS
CLI/MCP/HTTP          convergence PASS
SBOM/provenance       PASS Windows + Linux
Portable              PASS Windows + Linux
Executable delta      NONE Windows + Linux
ADR-0092              ACCEPTED — M24
```

Cette preuve qualifie le SHA exécutable ci-dessus. Les consolidations documentaires post-gate et post-merge doivent rester distinctes et ne peuvent pas être utilisées pour prétendre qu’un code non testé a été qualifié.