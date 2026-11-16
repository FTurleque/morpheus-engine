# Proposition : accepter un schéma OpenSpec qui déclare dériver de `spec-driven`

## Why

Le provider OpenSpec n'accepte que `schema: spec-driven` : `OpenSpecSpecificationProvider.SUPPORTED_SCHEMA` est comparé
par égalité stricte, et tout autre schéma rend `UNSUPPORTED` avec le diagnostic `UNSUPPORTED_PROVIDER_SCHEMA`. Les trois
projets du mainteneur (`morpheus-engine`, `nexus-context-engine`, `minos-code-intelligence`) déclarent
`schema: detailed-uml`, un schéma dérivé du schéma officiel qui porte les diagrammes et l'interopérabilité Archgate.
MORPHEUS n'en ingère donc aucun, alors qu'il sait les lire : une copie de `nexus-context-engine/openspec` dont la seule
ligne `schema:` passe à `spec-driven` est publiée en entier (237 sources, 168 exigences, 579 liens, 0 diagnostic).

Preuve : constat `AUD-ARC-22` (Élevée), `docs/project/audits/2026-10-09/findings.md`, revérification du 15/11/2026 sur
`1b5fe93d` (`docs/project/audits/2026-10-09/reverification-2026-11-15.md`).

## What Changes

- Le probe du provider OpenSpec accepte un schéma de projet qui **déclare** dériver de `spec-driven` par une clé
  `derives: spec-driven` dans son `openspec/schemas/<nom>/schema.yaml`. Il le traite alors comme `spec-driven`, avec
  les mêmes capacités, et le rapporte sous son nom déclaré.
- Un schéma qui ne déclare rien, qui déclare un parent inconnu, ou dont le répertoire `openspec/schemas/<nom>/` manque,
  reste refusé : `UNSUPPORTED` et `UNSUPPORTED_PROVIDER_SCHEMA`, avec le motif dans les détails du diagnostic.
- Le schéma `detailed-uml` de ce dépôt gagne la ligne `derives: spec-driven`, que la CLI OpenSpec 1.14.3 accepte
  (`openspec schema validate detailed-uml` reste valide, vérifié le 15/11/2026 dans une copie jetable).
- ADR-0169 fixe ce que « dériver » engage : les catégories que le provider garantit, et le refus de tout le reste. Il
  remplace ADR-0002 (archivé), dont il reprend toute la décision du provider de référence.
- Aucune surface publique (CLI, MCP, HTTP), aucun format persisté ni schéma SQLite ne change : le probe rend la même
  forme de résultat.

## Capabilities

### New Capabilities

### Modified Capabilities
- `openspec-provider-reading` : une exigence ajoutée, qui fixe quand un schéma autre que `spec-driven` est lu ; le refus
  par défaut d'un schéma inconnu est conservé.

## Périmètre et exclusions

**Inclus :** le probe du provider OpenSpec, ses tests, la déclaration `derives:` du schéma `detailed-uml` de ce dépôt,
l'ADR, la page durable `docs/developer/specifications/openspec-provider-reading.md` et le guide `docs/user/` qui dit
quels schémas sont lus.

**Exclus :**
- la lecture des artefacts propres à `detailed-uml` (diagrammes, matrices de traçabilité de `design.md`) : ils restent
  hors des catégories, comme sous `spec-driven` ;
- un parent autre que `spec-driven` et une chaîne de dérivation sur plusieurs niveaux ;
- le volume des corpus de `morpheus-engine` et `minos-code-intelligence`, qui dépasse ensuite le budget d'évidence :
  changement `qualify-the-evidence-budget-on-real-corpora` (`AUD-ARC-23`) ;
- la modification des dépôts `nexus-context-engine` et `minos-code-intelligence`, qui suivront par leur propre PR.

## Acteurs et surfaces concernés

L'utilisateur de la CLI qui enregistre et synchronise un projet OpenSpec ; les clients MCP et HTTP qui lisent le
résultat du probe ; le mainteneur, qui accepte l'ADR.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Le provider n'accepte que `spec-driven`, par égalité stricte | existant vérifié | `OpenSpecSpecificationProvider.SUPPORTED_SCHEMA`, ligne `if (!SUPPORTED_SCHEMA.equals(...))` |
| Les trois projets déclarent `detailed-uml` | existant vérifié | `AUD-ARC-22`, revérification du 15/11/2026 |
| Le contenu de nexus est lu sans diagnostic une fois la ligne changée | existant vérifié | `AUD-ARC-22`, sortie `sync --json` relevée |
| La CLI OpenSpec 1.14.3 tolère `derives:` dans `schema.yaml` et dans `config.yaml` | existant vérifié | copie jetable du 15/11/2026 : `openspec schema validate`, `openspec new change`, `openspec status` |
| La déclaration vit dans `schema.yaml` plutôt que dans `config.yaml` | décision | design.md, D1, tranchée par le mainteneur le 16/11/2026 |
| La décision relève d'un ADR | décision | `.claude/rules/adr.md` : choix entre options, contrainte durable du provider, coût de retour (projets ingérés) |

## Impact

`morpheus-provider-openspec` (`OpenSpecSpecificationProvider`, lecture de `schema.yaml`) ; tests du provider ;
`openspec/schemas/detailed-uml/schema.yaml` ; ADR-0169, qui remplace ADR-0002 (archivé), son index,
`docs/architecture/arc42/09-decisions.md` et `docs/developer/ARCHGATE.md` ;
`docs/developer/specifications/openspec-provider-reading.md` ; le guide utilisateur qui décrit les providers
(`docs/user/QUICKSTART.md` ou `docs/user/CLI.md`, à vérifier en tâche 3.2). Aucun test d'architecture modifié.

## Questions ouvertes

Aucune : l'emplacement de la déclaration (`schema.yaml`) est tranché par le mainteneur, qui accepte ADR-0169 le
16/11/2026 (design.md, D1).
