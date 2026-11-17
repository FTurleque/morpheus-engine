# Revérification du 16 novembre 2026 (soir) — `a4880f95`

Base : `origin/main` = **`a4880f95`**, **23 commits et 66 fichiers** (+2 234 / −212) depuis `968431af`. Cinq PR :
**#648** (Maven 3.10.1), **#649** (schéma dérivé **et** budget d'évidence), **#650** et **#651** (propagation d'un
refus de budget), **#652** (archivage des trois changements livrés).

**Les deux constats `Élevée` sont soldés, et démontrés en lançant le produit sur les dépôts réels, sans rien y
modifier.** Un constat neuf s'ouvre, de la même famille que celui d'hier.

Build : **vert**, `BUILD SUCCESS` en **9 min 51 s** — **5 462 tests** (14 ignorés) dont **1 097 gates**, les deux verdicts d'enforcement à **0**. Les **25 tests de plus** qu'à la passe du matin tombent tous dans les modules que la vague a touchés, et dans aucun autre : **OpenSpec Provider +12** (schéma dérivé, outil de mesure, test de qualification, propagation du refus pendant la sonde), **Application +4** (comptage unique et qualification de la borne), **Architecture Tests +4** (le gate de la clause propre d'un refus de budget), **Synthetic +2** et **Markdown +1** (#650, le refus rendu sous le groupe `ingestion-budget`), **API +2** (#651, le budget de la poignée de main TLS). Les douze autres modules rendent des totaux identiques.

## Le résultat qui résume la passe

```
projects add --workspace N:\workspace-dev\morpheus-engine     (dépôt réel, non modifié)
sync  →  {"mode":"FULL_REBUILD","sourceCount":636,"requirementCount":363,
          "traceabilityLinkCount":1304,"diagnosticCount":0,"published":true}   exit=0
```

**MORPHEUS ingère le projet de MORPHEUS.** Hier la lecture s'arrêtait à 52 % ; avant-hier le schéma était refusé
d'entrée. La boucle de retour que le sprint 16 réclamait est fermée.

## `AUD-ARC-22` — corrigé

Le provider lit désormais un schéma de projet qui **déclare** dériver : un schéma autre que `spec-driven` est lu comme
tel seulement si `openspec/schemas/<nom>/schema.yaml` porte `derives: spec-driven`, « one level, one parent ». Un
schéma muet ou au parent inconnu reste refusé sous `UNSUPPORTED_PROVIDER_SCHEMA` — **le fail-closed est conservé, et
c'est la déclaration qui ouvre la porte**, exactement l'option que le constat recommandait. `ADR-0169` consolide la
décision.

Vérifié sur le dépôt réel (ci-dessus), puis sur le plus gros corpus : une copie de `minos-code-intelligence` à
laquelle la seule ligne `derives: spec-driven` est ajoutée s'ingère entièrement — **921 sources, 441 exigences,
2 290 liens, 0 diagnostic**.

**Un point pratique, qui n'est pas un défaut du produit** : `nexus-context-engine` et `minos-code-intelligence` n'ont
pas encore cette ligne dans leur propre `schema.yaml`, et restent donc refusés. C'est ce que la conception prévoit. Une
ligne par dépôt les débloque, et je l'ai prouvé sur minos.

## `AUD-ARC-23` — corrigé, et les deux moitiés de l'action sont là

C'était le point sur lequel j'avais insisté après avoir corrigé mes propres chiffres : compter une fois aurait laissé
minos à 209 octets du plafond, donc la borne devait être requalifiée **dans le même changement**.

- **Le comptage** : chaque ligne citée est comptée **une fois par fichier lu** (`addEvidenceLines`). Le
  multiplicateur de 2,50× à 2,61× que j'avais mesuré disparaît.
- **La borne** : `maxEvidenceBytes` passe de 512 Kio à **4 Mio**, et sa qualification est écrite à côté d'elle —
  mesurée le 16/11 sur les trois workspaces réels, lus en entier avec chaque ligne comptée une fois, le plus gros
  cite **1 241 544 octets** (531 513 de spécifications courantes, le reste de 33 changements actifs), sous le tiers de
  la borne. La **marge déclarée est la moitié de la borne** : un corpus qui cite plus que la moitié sort de ce qui a
  été qualifié. Un outil de mesure et un test de qualification sont livrés avec — la qualification est exécutable,
  pas seulement documentée.

Vérifié sur le pire corpus, celui qui dépassait l'ancienne borne de 11,4 fois : minos s'ingère entièrement.

La borne reste **non réglable** par une option ou une configuration. Ce n'était pas l'objet du constat, dont l'action
portait sur le comptage et la qualification, et avec le plus gros corpus à 30 % de la borne le besoin n'est plus
actuel. Si un corpus approche la moitié, c'est la qualification qui se refait.

## `AUD-QUA-25` — ouvert (Moyenne, S)

`OpenSpecSpecificationProvider.unsupportedSchema` construit un diagnostic riche : `provider`, `schema`,
`supportedSchema`, `reason`, et `declaredParent` quand un parent est déclaré. **Aucun de ces détails n'atteint
l'opérateur sur le chemin d'échec.** Exécuté sur `nexus-context-engine`, les deux modes de sortie rendent la même
phrase nue :

```
--json : … errors: The OpenSpec schema is not supported by this MORPHEUS provider.
texte  : MORPHEUS error [4]: … errors: The OpenSpec schema is not supported by this MORPHEUS provider.
```

`IncompleteProjectReadException` ne relaie que les **messages** des erreurs, pas leurs détails. Et la surface qui sait
les imprimer, `MorpheusProjectCli.diagnosticLines`, n'est appelée que sur la vue d'un `sync` **réussi** : un refus de
probe n'y passe jamais.

Le refus ne nomme donc ni le schéma lu, ni celui qui est supporté, ni la raison, ni le remède — une ligne
`derives: spec-driven`. **Le cas n'est pas hypothétique : deux des trois projets du mainteneur sont dans cet état ce
soir**, et la phrase qu'ils rendent ne permet pas de le découvrir. Un opérateur conclut que son workspace n'est pas
supporté, alors qu'il l'est à une ligne près.

Ce constat n'existe que depuis la livraison du schéma dérivé : avant, « non supporté » était la vérité entière ;
maintenant c'est une vérité incomplète, et l'écart entre les deux est exactement ce qui manque à l'écran.

## État

| | |
|---|---|
| Constats | **137** — 109 corrigés, 22 Info qui tiennent, 4 caducs, 1 non vérifiable, **1 toujours valide** |
| Ouvert | `AUD-QUA-25` (Moyenne) seul |
| Axes | Qualité **96**, Architecture / Sécurité / Tests / Performance / Dépendances **100** — moyenne **99,3** |
| Sprints | 15 soldés sur 16 ; reste **0,25 à 1 jour** |

## Note de méthode

Trois passes consécutives l'ont confirmé dans les deux sens : **ce qui se prouve en exécutant ne se prouve pas en
lisant.** Les deux `Élevée` d'avant-hier ont été trouvés en lançant le produit ; ils sont soldés aujourd'hui en le
relançant sur les dépôts réels, non modifiés — et le constat neuf vient du même geste, sur le chemin d'échec cette
fois. Un diff lu aurait montré que le schéma dérivé fonctionne ; il n'aurait pas montré que son refus est devenu muet.

Un incident à consigner, sans constat : `mvnw.cmd` a d'abord échoué dans mon shell avec « `powershell` n'est pas
reconnu », parce que Maven 3.10.1 doit être téléchargé par le wrapper et que mon `PATH` n'avait pas PowerShell. C'est
mon environnement, pas le dépôt — le build passe dès que le répertoire de PowerShell est ajouté. Je le note parce que
le même symptôme, sur une machine de contributeur, se lirait comme un défaut du wrapper.
