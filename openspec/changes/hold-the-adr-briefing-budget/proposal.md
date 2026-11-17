# Proposition : les sections remises aux agents tiennent dans le briefing d'Archgate

## Why

`.claude/rules/adr.md` borne la section `Decision` d'un ADR à 2 000 caractères, parce que c'est elle qu'Archgate remet
aux agents. C'est le constat `AUD-QUA-26` de l'audit du 2026-10-09, ouvert le 16/11/2026 sur `7d3b012d` : **56 des
80 ADR en vigueur** dépassent cette borne, le plus long à 10 279 caractères ; la règle ne dit pas ce qu'elle compte,
et aucun gate ne la lit (`archgate check` vit hors CI).

Relu dans la CLI Archgate installée (0.61.0), le compte est précis : `review-context` remet **deux** sections,
`Decision` et `Do's and Don'ts`, et coupe chacune à 2 000 caractères ; une section court de sa ligne `## ` à la
suivante, sous-sections `###` comprises, mesurée après `trim()`. Sur une copie du registre en LF, `archgate check`
rend 60 `briefingWarnings` (56 `Decision`, 4 `Do's and Don'ts`) : exactement les chiffres de l'audit, plus quatre.

Sur ce poste, le même `archgate check` en rend **zéro** : Archgate reconnaît un titre par `/^## (.+)$/` sur des lignes
découpées au seul `\n`, et le `\r` d'une copie de travail CRLF (`core.autocrlf=true`) fait échouer chaque titre. Sous
Windows, l'outil censé tenir la borne ne voit aucune section. C'est ce qui a laissé la borne dériver sans témoin.

Au-delà de la borne, la fin de la décision n'est jamais lue par l'agent qu'elle cadre.

## What Changes

- La règle de `.claude/rules/adr.md` dit ce qu'elle compte : les deux sections remises, 2 000 caractères chacune,
  sous-sections comprises.
- Un test de cohérence du module d'architecture, `AdrBriefingBudgetTest`, mesure les deux sections de chaque ADR du
  registre, indépendamment des fins de ligne, et refuse un dépassement ou une section vide.
- Les 56 ADR hors borne sont ramenés sous elle **sans changer aucune décision** : chaque règle reste dans `Decision`,
  condensée ; raisons, options, diagrammes, exemples et mesures vont dans `Context` — le patron que le commit
  `003199fd` a appliqué à trois ADR.
- `docs/developer/ARCHGATE.md` et le gabarit d'ADR disent la borne et la cécité d'Archgate sur un arbre CRLF.

## Capabilities

### New Capabilities

- `adr-briefing-budget` : les sections d'un ADR que le briefing des agents remet tiennent dans ce que le briefing
  garde.

### Modified Capabilities

Aucune.

## Périmètre et exclusions

**Inclus :** les ADR en vigueur de `docs/architecture/adr/` ; la règle, le test, la page Archgate et le gabarit.

**Exclus :** les ADR archivés, qu'Archgate ne lit pas ; un changement de décision dans un ADR ; l'ajout d'Archgate à
la CI (le test tient la borne dans le build, sur toutes les plateformes) ; un correctif d'Archgate, outil tiers. La
fiche d'audit est mise à jour par son auteur, lors de la revérification suivante.

## Acteurs et surfaces concernés

Les agents qui reçoivent le briefing d'Archgate ; le mainteneur et les contributeurs qui écrivent un ADR. Aucune
surface publique du produit.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| 56 ADR sur 80 dépassent 2 000 caractères en `Decision`, 4 en `Do's and Don'ts` | existant vérifié | mesure du 17/11/2026 sur `7d3b012d`, algorithme d'Archgate ; `archgate check` sur une copie LF : 60 avertissements |
| `review-context` remet `Decision` et `Do's and Don'ts`, coupés à 2 000 chacun | existant vérifié | CLI Archgate 0.61.0 : `o_=["Decision","Do's and Don'ts"]`, `s_=2000`, découpe sur `/^## (.+)$/u` |
| `archgate check` ne voit aucune section sur un arbre CRLF | existant vérifié | 0 avertissement sur la copie de travail Windows, 60 sur la même copie convertie en LF |
| Aucune décision ne change | décision | seule la place du texte dans chaque ADR change |

## Impact

- `.claude/rules/adr.md`, `docs/developer/ARCHGATE.md`, `docs/architecture/adr/gabarit/template.md`.
- 56 ADR de `docs/architecture/adr/` (sections `Context`, `Decision`, `Do's and Don'ts`).
- `morpheus-architecture-tests` : une classe de test `@CoherenceCheck`.
- `docs/developer/specifications/adr-briefing-budget.md` et l'index du dossier.
- Aucun ADR nouveau : la borne est une convention de rédaction, tenue par `.claude/rules/adr.md` (tableau « Ce n'est
  pas un ADR »).

## Questions ouvertes

Aucune.
