# Spécifications consolidées par capacité

Ce dossier donne, pour chaque capacité OpenSpec, la vue durable qu'une spec ne porte pas : le comportement en
français, les diagrammes maintenus, les écarts connus et la traçabilité jusqu'aux tests. La spec de référence reste
la seule source normative : une page de ce dossier la résume et la relie, elle ne la remplace pas.

| Document | Rôle | Mis à jour par |
|---|---|---|
| `openspec/specs/<capacité>/spec.md` | exigences et scénarios normatifs, en vigueur | l'archivage d'un changement |
| `openspec/changes/<changement>/` | évolution proposée, conception propre au changement | le changement, jusqu'à son archivage |
| `docs/developer/specifications/<capacité>.md` | vue consolidée, diagrammes, écarts, traçabilité | le groupe de tâches qui change la capacité |
| `docs/architecture/arc42/` | vues d'ensemble, frontières, scénarios transverses | les changements d'architecture |
| `docs/architecture/adr/` | décisions durables et leurs conséquences | un ADR, selon `.claude/rules/adr.md` |

Le mode d'emploi du schéma qui produit les specs est dans [`../OPENSPEC_SCHEMA.md`](../OPENSPEC_SCHEMA.md).

## Pages disponibles

Une capacité absente de cette table n'a pas encore de page durable ; l'état de couverture de toutes les specs est
relevé dans [`AUDIT_SPECIFICATIONS_OPENSPEC_2026-11-03.md`](../../project/audits/AUDIT_SPECIFICATIONS_OPENSPEC_2026-11-03.md).

### Frontière HTTP locale et distante

Vue commune : la séquence de la façade distante, dans [`remote-access-journal.md`](remote-access-journal.md#séquence-de-la-façade-distante).

| Capacité | Page | Spec de référence |
|---|---|---|
| `http-path-decoding` | [lecture du chemin d'une requête](http-path-decoding.md) | [spec](../../../openspec/specs/http-path-decoding/spec.md) |
| `http-response-headers` | [en-têtes de sécurité des réponses](http-response-headers.md) | [spec](../../../openspec/specs/http-response-headers/spec.md) |
| `http-failure-disclosure` | [texte d'un échec rendu à un appelant HTTP](http-failure-disclosure.md) | [spec](../../../openspec/specs/http-failure-disclosure/spec.md) |
| `remote-access-journal` | [journal d'accès du serveur distant](remote-access-journal.md) | [spec](../../../openspec/specs/remote-access-journal/spec.md) |

### Surfaces publiques et adaptateurs CLI et MCP

| Capacité | Page | Spec de référence |
|---|---|---|
| `public-surface-convergence` | [convergence des surfaces publiques](public-surface-convergence.md) | [spec](../../../openspec/specs/public-surface-convergence/spec.md) |
| `adapter-capability-decomposition` | [commandes et outils servis par des classes de capacité](adapter-capability-decomposition.md) | [spec](../../../openspec/specs/adapter-capability-decomposition/spec.md) |
| `cli-argument-base` | [base d'arguments de la CLI](cli-argument-base.md) | [spec](../../../openspec/specs/cli-argument-base/spec.md) |
| `payload-map-construction` | [construction des cartes de réponse](payload-map-construction.md) | [spec](../../../openspec/specs/payload-map-construction/spec.md) |
| `mcp-tool-failure-contract` | [échec d'un outil MCP et borne d'un appel](mcp-tool-failure-contract.md) | [spec](../../../openspec/specs/mcp-tool-failure-contract/spec.md) |
| `mcp-peer-launch-command` | [commande de lancement d'un pair MCP](mcp-peer-launch-command.md) | [spec](../../../openspec/specs/mcp-peer-launch-command/spec.md) |
| `launch-environment-disclosure` | [variables d'environnement documentées](launch-environment-disclosure.md) | [spec](../../../openspec/specs/launch-environment-disclosure/spec.md) |

## Conventions

- **Exigences citées par leur titre exact**, dans la langue de la spec. Le titre est la clé de synchronisation
  d'OpenSpec : il n'est ni traduit ni renuméroté ici. Les alias `R1`, `R2`… d'une page ne valent que dans cette page.
- **Nature de chaque affirmation** : `[EXIGENCE]` (dit par la spec), `[EXISTANT VÉRIFIÉ]` (lu dans le code ou un test,
  avec sa référence), `[DÉCISION]` (ADR ou décision écrite), `[PROPOSITION]` (à valider, portée par un changement),
  `[QUESTION]` (à trancher). Un comportement vérifié dans le code mais absent de la spec n'est pas une exigence : il
  est signalé comme tel.
- **Diagrammes** : Mermaid. Chaque diagramme suit la question à laquelle il répond, dit s'il montre l'existant ou une
  cible, et nomme les exigences concernées. Un `flowchart` est un organigramme, pas un diagramme UML.
- **Preuve** : un test cité par chemin de fichier et nom de méthode. « Preuve manquante » est écrit en clair.
- **Changements** : un changement actif est cité par son nom en code, sans lien (l'archivage déplace son dossier) ;
  un changement archivé par son dossier daté sous `openspec/changes/archive/`.
- **Chiffres** : une borne citée renvoie à la constante ou au fichier qui la porte.

## Gabarit d'une page de capacité

```markdown
# <Titre en français> (`<capability-path>`)

Spec de référence : lien vers openspec/specs/<capability-path>/spec.md · Relevé du <date> sur <SHA>

## Objet et périmètre
Acteurs, déclencheurs, ce qui est inclus, ce qui est exclu.

## Exigences
| Alias | Exigence (titre exact) | En bref | Nature |

## Comportement détaillé
Entrées, validations, sorties, refus (code, message), effets persistés ou leur absence, bornes.

## Diagrammes
Question → diagramme → explication → exigences concernées.

## Écarts et questions ouvertes
Divergences code / tests / spec / documentation, et décisions attendues.

## Traçabilité
| Exigence | Scénario | Conception ou diagramme | Changement ou tâche | Preuve |

## Sources
ADR, archives OpenSpec, pages développeur liées.
```
