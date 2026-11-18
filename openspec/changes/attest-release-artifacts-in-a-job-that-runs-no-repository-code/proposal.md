# Proposition : attester les artefacts de release dans un job qui n'exécute aucun code du dépôt (AUD-DEP-33)

## Why

Les deux jobs de construction de `.github/workflows/release.yml` (`linux-release`, `windows-release`) déclarent
`id-token: write` et `attestations: write` au niveau du job. Dans ces mêmes jobs s'exécutent
`distribution/build-release.sh` ou `build-release.ps1` (Maven et `jpackage`), et sous Windows
`scripts/validate-m28.ps1`, qui lance un `clean verify` complet avec ses dépendances de test ; `actions/setup-java`
restaure `~/.m2` (`cache: maven`). Tout code exécuté par ce build — un plugin Maven, une dépendance de test, un script —
peut donc demander un jeton OIDC au runner et produire une attestation de provenance signée au nom du dépôt pour un
binaire de son choix. L'exploitation suppose la compromission d'un artefact épinglé ou du cache, d'où la sévérité
faible ; mais l'attestation est la garantie que la release vend.

Preuve : constat `AUD-DEP-33` (Faible), `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du 17/11/2026
sur `a04f32bd`, revérifié le 17/11/2026 sur `1a539c22` (`release.yml` relu : permissions des deux jobs, étapes de
construction et `actions/attest` dans le même job).

## What Changes

- Les jobs de construction et de test de `release.yml` ne portent plus `id-token: write` ni `attestations: write` ; ils
  téléversent leurs artefacts sans attestation.
- Un job dédié par plateforme (ou un seul job, design.md D1) télécharge ces artefacts avec `digest-mismatch: error`,
  n'extrait ni n'exécute aucun fichier du dépôt, porte seul `id-token: write` et `attestations: write`, appelle
  `actions/attest` et téléverse les paquets d'attestation (`*.attestation.jsonl`).
- Le job `publish` dépend du ou des jobs d'attestation et garde sa liste d'actifs attendus inchangée.
- Le build refuse un workflow de release dont un job porte une de ces deux permissions et exécute un `run:` ou un
  `actions/checkout`, ou dont un job d'attestation télécharge sans `digest-mismatch: error`.

## Capabilities

### New Capabilities
- `release-attestation-isolation` : seul un job qui n'exécute aucun code du dépôt détient le droit de signer la
  provenance d'une release, et il atteste les octets produits par le build.

### Modified Capabilities

## Périmètre et exclusions

**Inclus :** `.github/workflows/release.yml`, `AuditHardeningWorkflowContractTest` (`morpheus-architecture-tests`,
paquet `m21`, méthode `releaseWorkflowPublishesAttestedNonOverwritableAssetsFromMain`, qui exige aujourd'hui la seule
présence des deux permissions), la nouvelle page durable de la capacité.

**Exclus :** la liste des actifs publiés et leur parité avec les scripts de qualification
(`release-component-disclosure`, inchangée) ; la signature Authenticode (SEC-0164) ; l'intégrité des artefacts Maven
téléchargés par le build (`AUD-DEP-38`, change `verify-the-checksums-of-every-resolved-maven-artifact`, qui touche
aussi `release.yml` : les deux lots se rebasent l'un sur l'autre).

## Acteurs et surfaces concernés

Le mainteneur qui pose un tag `v*` ; l'utilisateur qui vérifie une release par `gh attestation verify` ou par
`scripts/verify-release-provenance.*` ; GitHub Actions, qui délivre le jeton OIDC.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Les deux jobs de construction portent `id-token: write` et `attestations: write` | existant vérifié | `release.yml`, `linux-release` et `windows-release` |
| Ces jobs exécutent Maven, `jpackage` et, sous Windows, `validate-m28.ps1` | existant vérifié | `release.yml`, étapes « Build tagged … » et « Run full M28 gate » |
| Le test de contrat n'exige que la présence des permissions | existant vérifié | `AuditHardeningWorkflowContractTest#releaseWorkflowPublishesAttestedNonOverwritableAssetsFromMain` |
| L'attestation dans un job séparé atteste les mêmes octets | proposition à valider | digest calculé sur l'artefact téléchargé avec `digest-mismatch: error` ; vérifié par `verify-release-provenance` |

## Impact

`.github/workflows/release.yml`, `AuditHardeningWorkflowContractTest` (fichier de gouvernance : justification de PR),
`docs/developer/specifications/` (nouvelle page) et son index, `docs/developer/RELEASE.md` ou page équivalente qui
décrit le pipeline de release s'il en décrit les jobs. Aucun ADR (design.md, D3). Aucune surface publique, aucun code
de production.

## Questions ouvertes

Aucune.
