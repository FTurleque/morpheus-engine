# Revérification du 15 novembre 2026 — `1b5fe93d`

Base : `origin/main` = **`1b5fe93d`** (PR #645, correctif du profil Claude Desktop dans l'installeur).

**Cette passe n'est pas une revérification ordinaire.** Le mainteneur a demandé comment utiliser MORPHEUS et comment y
faire entrer OpenSpec. Y répondre obligeait à faire ce qu'aucune des vingt passes précédentes n'avait fait :
**exécuter MORPHEUS sur de vrais corpus**. Puis il a nommé ses deux autres dépôts, qui partagent le même standard de
rédaction — arc42, OpenSpec dérivé en `detailed-uml` pour porter les diagrammes et l'interopérabilité Archgate. Les
trois ont été mesurés et ingérés.

Trois constats, dont **deux `Élevée`** — les premiers depuis le 28 octobre. Le score passe de **100 à 96**.

Build : **vert**, `BUILD SUCCESS` en **9 min 00 s** — **5 434 tests** (14 ignorés) dont **1 093 gates**, les deux
verdicts d'enforcement à **0**. Totaux par module identiques aux deux passes du 14 : la PR #645 ne touche qu'un script
PowerShell et de la documentation.

## Protocole

Aucun des trois dépôts n'a été modifié. Pour chacun : copie de `openspec/` dans `%TEMP%`, puis dans la copie
seulement, `schema: detailed-uml` → `schema: spec-driven`, puis `projects add` et `sync` avec une base jetable, via
la CLI construite la veille (`morpheus-cli-1.2.2-all.jar`).

| Projet | `openspec/specs/` | corpus complet | `sync` |
|---|---:|---:|---|
| `nexus-context-engine` | 163 850 o / 35 fichiers | 1 162 205 o | **publié** — 237 sources, 168 exigences, 579 liens, 0 diagnostic |
| `morpheus-engine` | 406 550 o / 84 fichiers | 2 966 619 o | **refusé** — `524777 > 524288` |
| `minos-code-intelligence` | 503 209 o / 22 fichiers | 6 002 933 o | **refusé** — `557385 > 524288` |

## `AUD-ARC-22` — le schéma déclaré (Élevée)

Les **trois** projets déclarent `schema: detailed-uml` et portent chacun `openspec/schemas/detailed-uml/`,
`.archgate/` et `docs/architecture/arc42/`. Ce n'est pas la particularité d'un dépôt : c'est le standard de
l'écosystème, et il existe parce qu'il porte ce que le schéma officiel ne couvre pas.

`OpenSpecSpecificationProvider.SUPPORTED_SCHEMA` vaut `"spec-driven"`, comparé par égalité stricte. `detailed-uml`
est inconnu du code de production — zéro occurrence dans les `.java`.

**Ce qui emporte la sévérité** : nexus, une fois cette seule ligne changée, s'ingère **entièrement** — 237 sources,
168 exigences, 579 liens de traçabilité, zéro diagnostic, code de sortie 0. Pas un fichier de spécification, pas un
gabarit, pas un marqueur n'a été touché. Le provider sait lire ces corpus ; il refuse de les regarder.

Ouvert `Moyenne`, **relevé `Élevée` dans la même journée** quand la portée est passée d'un dépôt à trois. Le
comportement reste correct — un moteur fail-closed refuse un schéma inconnu plutôt que de l'interpréter de travers —
mais le mainteneur n'a aucun remède dans le produit : l'alternative serait de renoncer au schéma qui porte ses
diagrammes et Archgate dans trois dépôts.

**L'option que j'avais proposée en premier est écartée.** Déclarer `spec-driven` et porter `detailed-uml` ailleurs
déplacerait le problème dans l'outillage amont au lieu de le résoudre : le schéma pilote le workflow de rédaction et
ses gabarits. Reste à accepter un schéma dérivé, soit par une liste de schémas admis, soit — plus durable — en
laissant le schéma déclarer celui dont il dérive (`derives: spec-driven`), ce qui garde le fail-closed : un schéma
qui ne déclare rien reste refusé.

## `AUD-ARC-23` — le budget d'évidence (Élevée), et une preuve que j'ai dû corriger

`ProviderIngestionBudget.DEFAULT` fixe `maxEvidenceBytes = 512 * 1024`, compteur **cumulatif sur toute la session**.
Les quatre lecteurs de production ouvrent tous `DEFAULT` : aucune option CLI, aucune clé de configuration, rien dans
`docs/user/`. L'opérateur n'a aucun levier.

**J'avais écrit que le corpus du dépôt dépassait le budget « de 5,6 fois ». C'était faux.** Nexus porte 1,16 Mio de
corpus et passe sans difficulté. La grandeur qui décide est le volume de **`openspec/specs/`**, dont les fragments
cités sont tirés : 164 Kio passent, 407 Kio et 503 Kio échouent. Le plafond pratique se situe autour de **400 Kio de
spécifications courantes**.

J'ai tiré un ratio d'un chiffre mesuré au mauvais endroit, et c'est l'élargissement demandé par le mainteneur qui l'a
mis au jour. Sans les deux autres projets, la preuve fausse aurait tenu — et un correctif écrit dessus aurait visé le
mauvais budget. C'est le genre d'erreur que cet audit doit rendre visible plutôt que réparer en silence : la preuve
est corrigée, et le constat garde la trace de ce qu'elle disait avant.

La sévérité ne bouge pas. Deux des trois projets sont inatteignables, et **le troisième est à 40 % du plafond** :
nexus le franchira en grandissant, sans que rien ne l'annonce. L'échec est total — huit catégories en `FAILED`,
aucune publication partielle, puis `REBUILD_REQUIRED` / `BASELINE_INCONSISTENT`. Cette propriété est cohérente avec
la doctrine du produit, mais combinée à un plafond non réglable elle ne laisse aucune issue. MORPHEUS borne déjà ses
traversées **en exposant une raison de troncature** ; ici il n'expose rien et s'arrête.

Deux lectures restent ouvertes, et c'est au mainteneur de trancher : soit le plafond est trop bas, soit
l'accumulation d'évidence est elle-même excessive. **Je ne tranche pas** sans une mesure du volume d'évidence par
exigence — si un fragment cité pèse plusieurs fois le texte de son exigence, la cible du correctif n'est pas le
plafond.

## `AUD-QUA-24` — le refus accuse un innocent (Moyenne)

Deux instances, et la seconde est pire que la première.

- `morpheus-engine` : le message nomme `openspec/specs/operational-runtime-injection/spec.md`, qui pèse
  **2 005 octets**, comme dépassant 524 288.
- `minos-code-intelligence` : il nomme `openspec/specs/frontieres-de-modules/spec.md`, qui pèse **74 122 octets** —
  et qui est réellement **le plus gros fichier du dépôt**. L'accusation paraît donc confirmée : l'opérateur découpe
  le fichier, et le total ne change pas. Il manquait 483 263 octets.

Le chiffre est un cumul de session ; le fichier nommé est celui que le lecteur traitait quand le compteur a basculé
(`addEvidenceFragment` attribue à `lastDocument`). **Preuve d'impact la plus directe que je puisse fournir : ce
message m'a trompé pendant la vérification même qui l'a produit.** J'ai commencé par mesurer la taille du fichier
nommé.

Le dépôt porte déjà la doctrine contraire, écrite à deux fichiers de là : le Javadoc d'`OpenSpecSourceAttribution`
exclut délibérément les refus de budget de l'attribution à un fichier, au motif que « naming a file there would
accuse an innocent one ». Pour une grandeur cumulative, cette source *est* le fichier innocent. Quatre grandeurs de
`ProviderIngestionBudget` sont cumulatives et partagent la même forme de refus.

## État

| | |
|---|---|
| Constats | **136** — 106 corrigés, 22 Info qui tiennent, 4 caducs, 1 non vérifiable, **3 toujours valides** |
| Ouverts | `AUD-ARC-22` (Élevée) · `AUD-ARC-23` (Élevée) · `AUD-QUA-24` (Moyenne) |
| Axes | Architecture **80**, Qualité **96**, Sécurité / Tests / Performance / Dépendances **100** — moyenne **96** |
| Sprints | 15 soldés sur 16 ; le sprint 16 est neuf, **2,25 à 7 jours** |

## Ce que cette passe apprend sur l'audit lui-même

Le 100 d'hier soir a tenu vingt-quatre heures. Il était exact dans ses termes — « aucun écart que je sais démontrer » —
et c'est sa limite que cette passe illustre : il mesurait ce que j'avais cherché, pas ce que le produit fait.

Deux enseignements de méthode, et le second est contre moi :

1. **Lire le code ne produisait pas ces constats.** Chaque ligne de preuve est vraie dans le code et invisible à la
   lecture ; il fallait la combinaison d'un schéma, d'un corpus réel et d'une exécution.
2. **Une seule mesure ne suffit pas à fonder un ratio.** Ma première preuve d'`AUD-ARC-23` comparait deux grandeurs
   qui n'avaient pas de rapport, et seul le troisième projet l'a montré. Un audit qui mesure un cas et généralise
   fait exactement ce qu'il reproche au code.

Sur la cadence, posée depuis le 12 novembre : ce n'est pas la fréquence qui manquait, c'est la **nature** des passes.
Vingt passes de lecture ont trouvé des dérives documentaires de plus en plus fines ; une passe d'exécution a trouvé
deux plafonds fonctionnels. À intervalle plus long, refaire ce que celle-ci a fait : prendre le produit livré et s'en
servir, sur les trois projets.
