# ADR-0087 — Opérabilité local-first et diagnostics sûrs par défaut

Statut : **Acceptée — M19**

Date : 26 juillet 2026

## Contexte

MORPHEUS possède des diagnostics métier mais M19 exige des garanties d'exploitation : logs structurés, codes stables, compteurs/timings, distinction health/readiness et absence de fuite de secrets ou chemins sensibles. Cette observabilité ne doit pas introduire de télémétrie externe obligatoire.

## Décision

1. Les événements opérationnels ont un code machine-readable stable, un niveau et des attributs canoniques.
2. Le port `OperationalEventSink` appartient à l'application ; aucun backend réseau n'est imposé.
3. Le sink local JSON-lines applique la redaction avant l'écriture.
4. Les clés de secret (`token`, `password`, `secret`, `apiKey`, `authorization`, `credential`) sont redacted par défaut.
5. Les chemins absolus associés à des attributs path/workspace/database/root/home sont redacted par défaut.
6. Les secrets inline reconnus sont redacted ; le home utilisateur est remplacé par un marqueur stable.
7. Les compteurs et timings restent process-local, thread-safe et consultables sans export externe.
8. `health` signifie liveness du processus ; `readiness` signifie capacité à accéder aux dépendances locales nécessaires pour servir un état cohérent. Un processus peut être vivant mais non prêt.
9. Les timings sync/provider/composition/intégrations externes doivent être enregistrables localement avec la même infrastructure.
10. Le scan local ignore par défaut les répertoires de métadonnées/build/dépendances et ne suit pas les liens symboliques.
11. Les écritures locales sensibles sont durcies owner-only lorsque POSIX ou ACL le permettent ; un chemin symbolique est refusé.
12. `UNSUPPORTED` pour un mécanisme permissionnel de filesystem reste explicite et ne doit pas être présenté comme `HARDENED`.

## Invariants

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

## Conséquences

### Positives

- diagnostics exploitables par humain et machine ;
- comportement sûr par défaut sur logs et parcours filesystem ;
- aucune dépendance à un SaaS de télémétrie ;
- readiness peut refléter une dégradation locale réelle sans confondre liveness et disponibilité métier.

### Contraintes

- les adapters/composition roots doivent explicitement activer les sinks utiles ;
- les attributs opérationnels doivent rester bornés et ne pas devenir un dump de contenu métier ;
- ACL/POSIX sont dépendants des capacités du filesystem et doivent être testés de manière portable.

## Preuve d'acceptation

Le SHA de code `dca27db969b426ad43941ccb8cee7e926efb931b` a passé séparément les gates Windows et Linux enregistrés dans `docs/validation/VALIDATION_M19.md`, avec preuve de :

- redaction secrets/chemins avant écriture et logs structurés locaux ;
- compteurs et timings process-local à cardinalité bornée ;
- health/readiness distincts, readiness sondant réellement le store local ;
- timings sync/provider/composition/intégrations externes ;
- chemins ignorés, liens non suivis et source root symbolique refusée ;
- hardening owner-only des écritures et sidecars SQLite lorsque supporté ;
- absence de télémétrie externe obligatoire.
