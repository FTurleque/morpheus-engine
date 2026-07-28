# VALIDATION M23 — Multi-project / Portfolio Specification Intelligence

Statut : **PASS — qualification Windows + Linux exact-head acquise sur le même SHA exécutable**

Date : 28 juillet 2026

Issue : #103
PR : #104
Branche : `m23/portfolio-specification-intelligence`

Baseline : `main@67c587057e287d57b0733f9e425a57b26cc38ae4` après merge M22.

Head exécutable qualifié Windows + Linux :

```text
04a906e9d5858292ed0f0f1bec65246fef91ed63
```

Les commits postérieurs à ce SHA sont exclusivement documentaires et ne modifient ni code produit, ni POM, ni contrat runtime, ni packaging, ni validateur.

## Question de sortie

> MORPHEUS peut-il raisonner sur plusieurs projets sans confondre identité métier, workspace, repository et source provider ?

**Réponse : oui.** M23 introduit une identité de portfolio provider-neutral, des adhésions par identité projet stable, des références inter-projets conservant provenance et evidence, des requêtes project-scoped et portfolio-scoped, une traversal BFS déterministe et bornée, une fraîcheur incrémentale et une persistance Memory + SQLite V013. CLI, MCP et HTTP convergent sur les mêmes intentions métier.

## Contrats prouvés

```text
portfolio identity                  indépendante des chemins/repository/provider
missing project                     non destructif
portfolio membership                != source ownership
cross-project reference             provenance/evidence conservées
conflict                            pas de silent last-write-wins
project-scoped queries              PASS
portfolio-scoped queries            PASS
traversal                           BFS déterministe, bornée et explicable
traversal node order                ordre de découverte BFS préservé
freshness                           incrémentale, projet-scoped
Memory store                        PASS
SQLite store                        PASS
migration                           V013
CLI/MCP/HTTP                        convergence PASS
baseline tests                      >= 494
architecture                        >= 190
JaCoCo line/branch                  >= 25% / 20%
SBOM/provenance                     PASS
Windows portable                    PASS
Linux portable                      PASS
post-gate executable delta          NONE
```

## Gates canoniques exécutés

Windows :

```powershell
.\validate-m23.cmd
```

Linux WSL2, dans un clone propre détaché sur le SHA qualifié :

```bash
bash ./scripts/validate-m23.sh 1.0.0
```

Les deux gates ont démarré et terminé sur :

```text
04a906e9d5858292ed0f0f1bec65246fef91ed63
```

## Preuve Windows

```text
M23 VALIDATION PASS
sha=04a906e9d5858292ed0f0f1bec65246fef91ed63
baseRef=origin/main
version=1.0.0
tests=507
architectureTests=195
lineCoverage=0.467034
branchCoverage=0.409099
portfolioIdentity=PASS
crossProjectReferences=PASS
boundedTraversal=PASS
sqliteV013=PASS
surfaceConvergence=PASS
sbom=PASS
provenance=PASS
portable=True
postGateExecutableDelta=NONE
```

Preuves complémentaires Windows :

- reactor 17/17 SUCCESS ;
- `git diff --check` PASS ;
- application 129 tests PASS ;
- MCP 11 tests PASS ;
- API 18 tests PASS ;
- CLI 37 tests PASS ;
- architecture 195 tests PASS, dont `PortfolioIntelligenceContractTest` 5/5 ;
- classes M23 et migration V013 présentes dans le runtime packagé ;
- portfolio CLI create/register/overview PASS ;
- HTTP portfolio overview PASS ;
- convergence CLI/MCP/HTTP portfolio PASS ;
- archive Windows portable créée ;
- `postGateExecutableDelta=NONE` ;
- le répertoire local non suivi `dist-r1/` est hors delta exécutable tracké et n'entre pas dans la preuve M23.

## Preuve Linux WSL2

Environnement observé : OpenJDK `21.0.11`.

```text
M23 VALIDATION PASS
sha=04a906e9d5858292ed0f0f1bec65246fef91ed63
baseRef=origin/main
version=1.0.0
tests=507
architectureTests=195
lineCoverage=0.466979
branchCoverage=0.409099
portfolioIdentity=PASS
crossProjectReferences=PASS
boundedTraversal=PASS
sqliteV013=PASS
surfaceConvergence=PASS
sbom=PASS
provenance=PASS
portable=true
postGateExecutableDelta=NONE
```

Le wrapper de qualification a confirmé :

```text
M23 LINUX EXIT CODE: 0
```

Preuves complémentaires Linux :

- clone WSL propre puis checkout détaché du SHA exact ;
- `git status --short` vide avant le gate ;
- reactor 17/17 SUCCESS ;
- tests 507 PASS ;
- architecture 195 PASS ;
- SBOM CycloneDX JSON/XML et provenance PASS ;
- runtime Linux autonome créé avec `jdk.httpserver + java.sql + java.net.http` ;
- classes M23 et V013 présentes dans le package ;
- portfolio CLI create/register/overview PASS ;
- convergence CLI/MCP/HTTP portfolio PASS ;
- archive `morpheus-1.0.0-linux-x64.tar.gz` créée ;
- HEAD exact conservé jusqu'à la fin du validateur ;
- `postGateExecutableDelta=NONE`.

## Correction finale avant qualification

Le premier gate Windows avait révélé un défaut de contrat dans l'ordre des nœuds de traversal : la BFS construisait correctement l'ordre de découverte dans un `LinkedHashMap`, puis cet ordre était perdu par une copie `TreeMap` triée par identité UUID.

La correction qualifiée :

1. conserve l'ordre d'insertion BFS dans `PortfolioTraversalService` ;
2. le préserve dans `PortfolioTraversalResult` via copie immuable `LinkedHashMap` ;
3. verrouille le contrat avec des UUIDv7 volontairement hors ordre lexical ;
4. vérifie que `PortfolioPublicViews` expose le même ordre BFS.

Aucun autre changement de sémantique de traversal n'a été introduit ; l'ordre déterministe des liens reste inchangé.

## Invariants de sortie

```text
cross-project identity != source path
project identity != workspace path
project identity != repository URL
project identity != provider identifier
absence of one project != identity deletion
portfolio membership != source ownership
cross-project reference != traceability proof
conflict != silent last-write-wins
precedence != provenance erasure
traversal is bounded and explainable
freshness != full destructive rescan
local-first remains default
```

## Conclusion

```text
Windows exact-head    PASS
Linux exact-head      PASS
Executable SHA        04a906e9d5858292ed0f0f1bec65246fef91ed63
Tests                 507 PASS Windows + Linux
Architecture          195 PASS Windows + Linux
Windows coverage      46.7034% line / 40.9099% branch
Linux coverage        46.6979% line / 40.9099% branch
Portfolio identity    PASS
Cross-project refs    PASS
Bounded traversal     PASS
SQLite V013           PASS
CLI/MCP/HTTP          convergence PASS
SBOM/provenance       PASS Windows + Linux
Portable              PASS Windows + Linux
Executable delta      NONE Windows + Linux
ADR-0091              ACCEPTED — M23
```

Toute modification ultérieure de code produit, POM, contrat runtime, packaging ou validateur invaliderait cette preuve et imposerait une nouvelle qualification Windows + Linux. Les consolidations post-gate doivent rester strictement documentaires et conserver explicitement le SHA exécutable qualifié ci-dessus.
