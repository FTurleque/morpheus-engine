# E12 — Diagnostics

Statut : **PASS**

Date : 22 juillet 2026

## Objectif

Valider un contrat de diagnostics structuré, stable et consommable par les futures couches CLI, MCP et API sans dépendre du texte libre d'un provider.

## Spike

```text
experiments/m0/spikes/e12_diagnostics_python/
├── diagnostics.py
└── test_diagnostics.py
```

## Résultat

```text
Ran 7 tests
7 PASS
0 FAIL
```

## Catalogue exercé

Le spike définit notamment :

```text
NO_PROVIDER_FOUND
UNSUPPORTED_SOURCE
UNSUPPORTED_PROVIDER_SCHEMA
UNSUPPORTED_FORMAT_VERSION
MISSING_REQUIRED_CAPABILITY
OPTIONAL_CAPABILITY_UNAVAILABLE
MULTIPLE_PROVIDER_MATCHES
EXPLICIT_PROVIDER_INCOMPATIBLE
REMOTE_PROVIDER_REQUIRES_OPT_IN
IDENTITY_COLLISION
UNRESOLVED_REFERENCE
INVALID_SOURCE
PARTIAL_INGESTION
SNAPSHOT_CONFLICT
INVALID_SNAPSHOT
```

Chaque diagnostic possède :

```text
code
severity
message
source?
details{}
```

## Invariants validés

- [x] codes contrôlés, pas de chaîne arbitraire ;
- [x] sévérité normative par code ;
- [x] impossible de contredire silencieusement la sévérité du catalogue ;
- [x] provenance/source optionnelle ;
- [x] détails structurés ;
- [x] sérialisation JSON déterministe ;
- [x] codes demandés par la matrice M0 représentables.

## Principe retenu

Le texte humain :

```text
message
```

peut évoluer ou être localisé plus tard.

Les consommateurs automatiques doivent s'appuyer principalement sur :

```text
code
severity
details
```

et non sur une comparaison de chaînes de message.

## Décision

```text
E12 = PASS
STRUCTURED_DIAGNOSTICS = RETAIN
FREE_FORM_ERROR_CONTRACT = REJECT
```
