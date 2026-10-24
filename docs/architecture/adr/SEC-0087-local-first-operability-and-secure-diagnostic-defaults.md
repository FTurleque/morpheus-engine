---
id: SEC-0087
title: "Opérabilité local-first et diagnostics sûrs par défaut"
domain: security
rules: false
---

# Opérabilité local-first et diagnostics sûrs par défaut

- Statut : **Acceptée**
- Date : 2026-07-26

## Context

MORPHEUS possède des diagnostics métier, mais l'exploitation exige des garanties : logs structurés, codes stables,
compteurs et timings, distinction health / readiness, et absence de fuite de secrets ou de chemins sensibles. Cette
observabilité ne doit pas introduire de télémétrie externe obligatoire.

## Decision

1. Les événements opérationnels ont un code machine-readable stable, un niveau et des attributs canoniques.
2. Le port `OperationalEventSink` appartient à l'application (`com.morpheus.application.operability`) ; aucun backend
   réseau n'est imposé.
3. Le sink local JSON-lines applique la redaction avant l'écriture.
4. Une clé d'attribut qui contient `token`, `password`, `secret`, `apiKey`, `authorization` ou `credential` (casse,
   `-` et `_` ignorés) voit sa valeur redacted par défaut.
5. Un chemin absolu porté par un attribut de chemin (clé finissant par `path`, `file`, `directory` ou `root`, ou
   contenant `workspace`, `database` ou `home`) est redacted par défaut.
6. Les secrets inline reconnus (dont un en-tête `Authorization` en texte libre) sont redacted ; le home utilisateur
   est remplacé par un marqueur stable.
7. Les compteurs et timings restent locaux au processus, thread-safe et consultables sans export externe.
8. `health` signifie liveness du processus ; `readiness` signifie capacité à accéder aux dépendances locales
   nécessaires pour servir un état cohérent. Un processus peut être vivant mais non prêt.
9. Les timings de synchronisation, des providers, de la composition et des intégrations externes sont enregistrables
   localement avec la même infrastructure.
10. Le scan local ignore par défaut les répertoires de métadonnées, de build et de dépendances, et ne suit pas les
    liens symboliques.
11. Les écritures locales sensibles sont durcies owner-only lorsque POSIX ou les ACL le permettent ; un chemin
    symbolique est refusé.
12. `UNSUPPORTED` pour un mécanisme de permissions du système de fichiers reste explicite et n'est jamais présenté
    comme `HARDENED`.

Invariants :

```text
local observability != mandatory external telemetry
structured event != unredacted secret/path dump
health != readiness
UNKNOWN/UNAVAILABLE != false success
external link/symlink != followed by default
ignored path policy != implicit incidental behavior
write hardening unsupported != hardened
MORPHEUS operability != JARVIS orchestration
```

## Do's and Don'ts

### Do

- Émettre chaque événement opérationnel avec un code stable, un niveau et des attributs canoniques.
- Redacter secrets, chemins absolus et home utilisateur avant toute écriture.
- Garder compteurs et timings locaux au processus.
- Répondre `health` et `readiness` séparément ; faire sonder les dépendances locales par `readiness`.
- Refuser un chemin symbolique pour une écriture locale sensible.
- Rapporter `UNSUPPORTED` quand le durcissement des permissions n'est pas possible.
- Activer explicitement les sinks utiles dans les racines de composition.
- Garder les attributs opérationnels bornés.

### Don't

- Ne pas imposer de télémétrie externe ni de backend réseau d'observabilité.
- Ne pas écrire un attribut sensible avant redaction.
- Ne pas déduire `readiness` de la seule liveness du processus.
- Ne pas suivre un lien symbolique lors du scan local par défaut.
- Ne pas présenter un durcissement non supporté comme `HARDENED`.
- Ne pas faire des attributs opérationnels un dump de contenu métier.

## Consequences

### Positives

- Diagnostics exploitables par un humain et par une machine.
- Comportement sûr par défaut sur les logs et le parcours du système de fichiers.
- Aucune dépendance à un SaaS de télémétrie.
- `readiness` peut refléter une dégradation locale réelle sans confondre liveness et disponibilité métier.

### Négatives

- Les adaptateurs et racines de composition doivent activer explicitement les sinks utiles.
- Les attributs opérationnels doivent rester bornés.

### Risques

- Les ACL et permissions POSIX dépendent des capacités du système de fichiers ; elles doivent être testées de manière
  portable, et un mécanisme absent est rapporté `UNSUPPORTED`.

## Compliance and Enforcement

- `morpheus-application/src/test/java/com/morpheus/application/operability/OperationalObservabilityContractTest.java` :
  redaction des secrets et chemins absolus avant l'écriture JSON-lines, en-têtes d'autorisation en texte libre,
  compteurs et timings locaux, attributs canoniques et immuables.
- `morpheus-application/src/test/java/com/morpheus/application/operability/SensitiveValueRedactorCrossPlatformTest.java` :
  redaction des chemins sur les formes Windows, UNC et POSIX.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m19/LocalOperabilityContractTest.java` :
  liveness et readiness distincts, readiness qui sonde réellement le store local.
- `morpheus-application/src/test/java/com/morpheus/application/security/LocalWritePermissionHardenerTest.java` :
  durcissement owner-only là où le système de fichiers le permet, refus d'une cible symbolique.
- `LocalSourceInventorySecurityTest`, `IntermediateSymlinkBoundaryTest`, `ScanWorkspaceBoundaryTest`
  (`morpheus-application`) : répertoires générés et de métadonnées ignorés, liens symboliques non suivis, racine
  symbolique refusée, frontière du scan.
- Revue : un durcissement non supporté reste rapporté `UNSUPPORTED` (`LocalWritePermissionHardener.Result`).

## References

- [ADR-0105](SEC-0105-no-secret-vault-no-reusable-secret-at-rest.md) — aucun secret réutilisable au repos.
- [ADR-0106](BE-0106-inbound-bound-is-defensive-outbound-bound-is-a-defect.md) — bornes entrantes et sortantes.
- [ADR-0111](BE-0111-the-product-ships-one-logging-binding-its-own.md) — une seule liaison de journalisation.
- [ADR-0112](BE-0112-the-logs-directory-holds-a-bounded-operational-log.md) — journal opérationnel borné.
- [ADR-0114](BE-0114-database-work-runs-on-a-bounded-pool-of-platform-threads.md) — pool borné de threads base de
  données.
- [VALIDATION_M19](../../project/validation/VALIDATION_M19.md) — preuve du jalon M19.
