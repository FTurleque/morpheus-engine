# VALIDATION M25 — Policy Packs & Governance Automation

Statut : **PASS — qualification Windows + Linux/WSL exact-head acquise sur le même SHA**

Date : 29 juillet 2026

Issue : #107  
PR : #108  
Branche : `m25/policy-packs-governance-automation`  
Target : `develop`

Baseline M25 :

```text
develop@5cdb26405fb9ae768964a24016fef89bdca97e88
```

Head exact qualifié Windows + Linux/WSL :

```text
a392604fc9e8d00f4021351ab5ba53f8488ab920
```

Le dernier changement de code produit avant la qualification finale est :

```text
71e6eb7caeb17dca0a4e3bc822213dac09a5daac
fix(m25): register MCP tools before server startup
```

Le commit suivant ne touche que le harness Linux M25 et permet de dériver `JAVA_HOME` depuis le `java` résolu par `PATH` sous WSL :

```text
a392604fc9e8d00f4021351ab5ba53f8488ab920
fix(m25): resolve WSL JDK for Linux gate
```

Les deux gates ont été rejoués intégralement sur ce dernier SHA exact.

## Question de sortie

> Les règles de qualité, contraintes et lifecycle peuvent-elles être distribuées comme politiques versionnées, explicables et auditables sans transformer recommandations, texte libre ou dry-run en mutation silencieuse ?

**Réponse : oui.** M25 fournit des policy packs provider-neutral versionnés, des règles déclaratives typées, des activations et overrides explicites avec CAS, un dry-run strictement read-only, une provenance conservée, un audit append-only et des surfaces CLI/MCP/HTTP convergentes. Aucun résultat de policy n'applique silencieusement une mutation lifecycle.

## Contrats prouvés

```text
constraint text != executable policy             PASS
UNKNOWN != BLOCKED                               PASS
severity != blocking policy                      PASS
policy recommendation != applied mutation        PASS
policy version != mutable latest                  PASS
policy override != provenance erasure             PASS
dry-run != mutation                              PASS
policy evaluation != lifecycle mutation           PASS
pack activation != domain truth mutation          PASS
stable pack/version/rule identity                 PASS
immutable policy versions                         PASS
explicit activation + CAS                         PASS
explicit overrides + CAS                          PASS
override removal restores source decision         PASS
audit append-only                                 PASS
Memory / SQLite parity                            PASS
SQLite reopen                                     PASS
SQLite V015                                       PASS
codec deterministic / bounded                     PASS
CLI/MCP/HTTP convergence                          PASS
SBOM / provenance                                 PASS
Windows portable                                  PASS
Linux portable                                    PASS
post-gate executable delta                        NONE
```

## Budgets qualifiés

```text
rules per pack              <= 128
active packs per scope      <= 32
overrides per scope         <= 256
pack name                   <= 160 chars
rule description            <= 512 chars
dry-run evaluations         <= 4096 rules
```

Tout dépassement est rejeté explicitement ; aucun dépassement n'est transformé silencieusement en résultat partiel présenté comme complet.

## Gate Windows

Commande canonique :

```powershell
.\validate-m25.cmd 1.0.0
```

Preuve machine-readable :

```text
M25 VALIDATION PASS
sha=a392604fc9e8d00f4021351ab5ba53f8488ab920
baseRef=origin/develop
version=1.0.0
tests=565
architectureTests=231
lineCoverage=0.429925
branchCoverage=0.363983
policyPacks=PASS
policyVersioning=PASS
policyOverrides=PASS
policyDryRun=PASS
policyExplainability=PASS
surfaceConvergence=PASS
sqliteV015=PASS
sbom=PASS
provenance=PASS
portable=True
postGateExecutableDelta=NONE
```

Preuves complémentaires Windows :

- `git diff --check` PASS ;
- reactor Maven 17/17 SUCCESS ;
- 565 tests PASS contre baseline M24 >= 543 ;
- 231 tests d'architecture PASS contre baseline M24 >= 221 ;
- JaCoCo au-dessus des floors 25% lignes / 20% branches ;
- CycloneDX JSON/XML + provenance PASS ;
- runtime Windows autonome et modules `jdk.httpserver`, `java.sql`, `java.net.http` PASS ;
- classes M25 + migration V015 présentes dans le package ;
- versioning + stale CAS rejection PASS ;
- dry-run read-only + UNKNOWN preservation PASS ;
- override provenance / originalDecision / effectiveDecision PASS ;
- convergence CLI/MCP/HTTP M25 PASS ;
- archive Windows portable créée ;
- `postGateExecutableDelta=NONE`.

## Gate Linux / WSL

Commande canonique :

```bash
bash ./scripts/validate-m25.sh 1.0.0
```

Le harness qualifié découvre sous WSL :

```text
M25 discovered JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

Preuve machine-readable :

```text
M25 VALIDATION PASS
sha=a392604fc9e8d00f4021351ab5ba53f8488ab920
baseRef=origin/develop
version=1.0.0
tests=565
architectureTests=231
lineCoverage=0.429945
branchCoverage=0.363983
policyPacks=PASS
policyVersioning=PASS
policyOverrides=PASS
policyDryRun=PASS
policyExplainability=PASS
surfaceConvergence=PASS
sqliteV015=PASS
sbom=PASS
provenance=PASS
portable=true
postGateExecutableDelta=NONE
```

Preuves complémentaires Linux/WSL :

- SHA exact vérifié avant le gate ;
- reactor Maven 17/17 SUCCESS ;
- 565 tests PASS ;
- 231 architecture PASS ;
- le test `MorpheusM17McpStdioIntegrationTest` PASS, prouvant le correctif de la race de démarrage MCP ;
- CycloneDX JSON/XML + provenance PASS ;
- runtime Linux autonome créé ;
- classes M25 + V015 présentes dans le package ;
- versioning, CAS, dry-run et override explainability PASS ;
- API packagée et convergence CLI/MCP/HTTP PASS ;
- archive `morpheus-1.0.0-linux-x64.tar.gz` créée ;
- `postGateExecutableDelta=NONE`.

## Incident Linux découvert pendant la qualification

Le premier gate Linux sur `f15391c5...` a exposé une race réelle : le serveur MCP pouvait devenir observable avant l'enregistrement complet de toutes les familles de tools, provoquant ponctuellement `Tool not found: apply_change_lifecycle_transition`.

Le correctif `71e6eb7c...` assemble toutes les `SyncToolSpecification` avant `.build()`. Le replay Windows puis Linux l'a validé.

Le run Linux suivant sur `71e6eb7c...` a passé tout le reactor puis s'est arrêté uniquement parce que `JAVA_HOME` n'était pas exporté sous WSL alors que le JDK était présent. Le commit `a392604f...` corrige ce harness sans modifier le code produit. Les deux gates ont ensuite été rejoués sur `a392604f...`.

## Persistance

M25 ajoute uniquement la migration additive :

```text
V015__policy_packs.sql
```

Tables :

```text
policy_packs
policy_pack_versions
policy_pack_activations
policy_overrides
policy_audit
```

Les versions et l'audit sont append-only ; activations et overrides utilisent des révisions CAS explicites.

## Surfaces qualifiées

Intentions publiques M25 :

```text
policy.pack.create
policy.pack.list
policy.pack.get
policy.pack.versions
policy.pack.update
policy.pack.activate
policy.pack.deactivate
policy.activation.list
policy.override.put
policy.override.list
policy.override.remove
policy.evaluate
policy.dry-run
policy.audit
```

La convergence signifie identité d'intention et de services applicatifs, pas identité de forme transport.

## Conclusion

```text
Windows exact-head       PASS
Linux/WSL exact-head     PASS
Qualified SHA            a392604fc9e8d00f4021351ab5ba53f8488ab920
Tests                    565 PASS Windows + Linux
Architecture             231 PASS Windows + Linux
Windows coverage         42.9925% line / 36.3983% branch
Linux coverage           42.9945% line / 36.3983% branch
Policy packs             PASS
Policy versioning        PASS
Overrides/provenance     PASS
Dry-run                  PASS
Explainability           PASS
SQLite V015              PASS
CLI/MCP/HTTP             convergence PASS
SBOM/provenance          PASS Windows + Linux
Portable                 PASS Windows + Linux
Executable delta         NONE Windows + Linux
CI / GitHub Actions      NOT USED — July 2026
ADR-0093                 ACCEPTED — M25
```

Cette preuve qualifie le SHA exact ci-dessus. Toute consolidation post-gate doit rester documentaire uniquement ; elle ne peut pas être utilisée pour qualifier un code différent.