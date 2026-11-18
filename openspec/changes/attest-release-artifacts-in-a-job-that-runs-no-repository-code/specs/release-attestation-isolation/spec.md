## Purpose

Garantir que seul un job de release qui n'exécute aucun code du dépôt détient le droit de signer la provenance d'une
release, et qu'il atteste les octets produits par le build. Page durable à créer :
`docs/developer/specifications/release-attestation-isolation.md`.

## ADDED Requirements

### Requirement: Seul un job qui n'exécute aucun code du dépôt DOIT porter les permissions d'attestation

Dans `.github/workflows/release.yml`, un job qui porte `id-token: write` ou `attestations: write` NE DOIT PAS (MUST NOT)
contenir d'étape `run:` ni d'étape `actions/checkout`, et le workflow NE DOIT PAS déclarer ces permissions à son niveau
global. Le build DOIT échouer sinon, en nommant le job et l'étape.

#### Scenario: Les jobs de construction ne peuvent pas obtenir de jeton OIDC

- **GIVEN** le workflow de release après ce changement
- **WHEN** le test de contrat lit les permissions de chaque job
- **THEN** les jobs qui lancent `distribution/build-release.*` ou `scripts/validate-m28.ps1` ne portent ni
  `id-token: write` ni `attestations: write`
- **AND** le test passe

#### Scenario: Une étape de build ajoutée au job d'attestation fait échouer le build

- **GIVEN** un job qui porte `id-token: write`
- **WHEN** une étape `run:` ou `actions/checkout` y est ajoutée
- **THEN** la suite d'architecture échoue et nomme le job et l'étape
- **AND** aucun fichier n'est réécrit par le test

#### Scenario: Une permission déclarée au niveau du workflow fait échouer le build

- **WHEN** `id-token: write` est ajouté au bloc `permissions` de premier niveau de `release.yml`
- **THEN** la suite d'architecture échoue et nomme le bloc de premier niveau

#### Scenario: Le contrôle a trouvé un job d'attestation

- **WHEN** aucun job de `release.yml` ne porte `attestations: write`
- **THEN** le test échoue au lieu de passer, une release devant publier ses attestations

### Requirement: Le job d'attestation DOIT attester les octets téléversés par le build

Le job d'attestation DOIT (MUST) obtenir les artefacts qu'il atteste par `actions/download-artifact` avec
`digest-mismatch: error`, depuis les artefacts téléversés par les jobs de construction de la même exécution, et le
paquet d'attestation qu'il produit DOIT être publié avec la release.

#### Scenario: Une release vérifie publiquement

- **GIVEN** une release publiée par le workflow après ce changement
- **WHEN** `scripts/verify-release-provenance.sh` ou `.ps1` vérifie ses actifs
- **THEN** chaque artefact distribué et l'inventaire CycloneDX vérifient leur attestation pour le dépôt
- **AND** les fichiers `*.attestation.jsonl` sont parmi les actifs publiés

#### Scenario: Un artefact altéré entre deux jobs n'est pas attesté

- **GIVEN** un artefact dont le digest ne correspond plus à celui enregistré au téléversement
- **WHEN** le job d'attestation le télécharge
- **THEN** le téléchargement échoue sur `digest-mismatch: error` et aucune attestation n'est produite
- **AND** le job `publish` ne s'exécute pas

#### Scenario: Un téléchargement sans contrôle de digest fait échouer le build

- **WHEN** l'étape `actions/download-artifact` d'un job d'attestation ne porte pas `digest-mismatch: error`
- **THEN** la suite d'architecture échoue et nomme le job
