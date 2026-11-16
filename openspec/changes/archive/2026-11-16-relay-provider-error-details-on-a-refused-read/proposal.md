# Proposition : relayer les détails d'une erreur de provider sur une lecture refusée

## Why

Le refus d'un schéma OpenSpec non supporté rend à l'opérateur la phrase
`The OpenSpec schema is not supported by this MORPHEUS provider.`, identique en sortie texte et sous `--json`. Le
diagnostic que `OpenSpecSpecificationProvider.unsupportedSchema` construit porte pourtant le schéma lu, le schéma
supporté, le motif et, s'il y en a un, le parent déclaré. Aucun de ces détails n'atteint l'écran :
`IncompleteProjectReadException` ne relaie que le **message** de chaque erreur, et les lignes `diagnostic=` de la CLI
ne sont imprimées que pour un `sync` réussi.

Un schéma de projet est lu dès que sa définition déclare `derives: spec-driven` (ADR-0169). L'opérateur dont le
workspace est à une ligne d'être lu conclut donc qu'il n'est pas supporté.

Preuve : constat `AUD-QUA-25` (Moyenne), `docs/project/audits/2026-10-09/findings.md`, revérification du 16/11/2026 sur
`a4880f95`, mesuré sur un dépôt réel dans les deux modes de sortie.

## What Changes

- Le message du refus d'un schéma nomme le schéma lu, le fichier de définition attendu et la déclaration
  `derives: spec-driven` qui le rendrait lisible, pour chacun des quatre motifs. Il atteint ainsi toutes les surfaces
  qui relaient déjà le message : CLI texte, CLI `--json`, HTTP `409`.
- `IncompleteProjectReadException` porte les diagnostics d'erreur du provider, pas seulement leurs messages.
- La ligne d'erreur de la CLI relaie ces diagnostics pour une lecture refusée : une ligne `diagnostic=` par diagnostic
  sous la ligne d'erreur en texte, un membre `diagnostics` dans l'objet JSON sous `--json`. Le format est celui d'un
  `sync` réussi.
- Aucun code de diagnostic, aucune clé de détail, aucun code de sortie, aucune route ni aucun format persisté ne
  change. L'enveloppe d'erreur HTTP garde sa forme.

## Capabilities

### New Capabilities

### Modified Capabilities
- `openspec-provider-reading` : le refus d'un schéma dit ce qui le rendrait lisible.
- `project-ingestion-port` : une lecture refusée porte les diagnostics d'erreur du provider.
- `cli-argument-base` : la ligne d'erreur d'une lecture refusée relaie ces diagnostics.

## Périmètre et exclusions

**Inclus :** `OpenSpecSpecificationProvider` (`morpheus-provider-openspec`), `IncompleteProjectReadException` et
`ProjectContentIngestion` (`morpheus-application`), `CliErrorLine`, `CliCommandRun` et l'écriture des lignes
`diagnostic=` (`morpheus-cli`), leurs tests, les trois pages durables des capacités touchées.

**Exclus :** l'enveloppe d'erreur HTTP, qui ne reçoit pas de diagnostics structurés (design.md, D4) ; les codes de
motif (`reason`) et les clés de détail, fixés par ADR-0169 ; les notes de version publiées, relevés datés.

## Acteurs et surfaces concernés

L'opérateur de la CLI qui lit le refus d'un `sync` ou d'un `analyze change` (texte et `--json`) ; le client HTTP qui
reçoit le `409` d'une synchronisation refusée.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Le refus d'un schéma rend une phrase sans schéma, motif ni remède | existant vérifié | `AUD-QUA-25`, sorties relevées mot pour mot |
| Le diagnostic porte `provider`, `schema`, `supportedSchema`, `reason`, `declaredParent` | existant vérifié | `OpenSpecSpecificationProvider.unsupportedSchema` |
| Seuls les messages sont relayés par le refus | existant vérifié | `ProjectContentIngestion.errorsOf`, `IncompleteProjectReadException.message` |
| Un message relayé par HTTP est remplacé en entier au-delà de 512 caractères | existant vérifié | `ServerLocationDisclosure.MAX_RELAYED_LENGTH`, `BoundaryFailureMessage.safe` |
| Aucun ADR n'est requis | décision | `.claude/rules/adr.md` : texte et relais d'un diagnostic, retour arrière sans coût |

## Impact

`morpheus-provider-openspec`, `morpheus-application`, `morpheus-cli` ; tests de ces trois modules et un test
d'intégration de `morpheus-api`. Aucun test d'architecture modifié, aucune surface de `contracts/public-surfaces.tsv`
ni de l'OpenAPI.

## Questions ouvertes

Aucune.
