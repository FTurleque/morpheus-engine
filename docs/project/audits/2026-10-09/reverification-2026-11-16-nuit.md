# Revérification du 16 novembre 2026 (nuit) — `7d3b012d`

Base : `origin/main` = **`7d3b012d`**, cinq commits et 31 fichiers (+896 / −219) depuis `a4880f95`. Deux PR :
**#653** (relayer les détails d'une erreur de provider sur une lecture refusée) et **#654** (archivage). Le commit
`003199fd` resserre au passage la section `Decision` de trois ADR.

Build : **vert**, `BUILD SUCCESS` en **11 min 06 s** — **5 472 tests** (14 ignorés) dont **1 097 gates**, les deux verdicts d'enforcement à **0**. Les **10 tests de plus** qu'à la passe du soir tombent dans les quatre modules que #653 a touchés, et dans aucun autre : **CLI +4** (le relais sur la surface de sortie), **OpenSpec Provider +3** (la phrase du refus et sa borne de divulgation), **Application +2** (`IncompleteProjectReadException` qui porte les diagnostics), **API +1** (son propre chemin d'échec). Le compte de gates ne bouge pas — 1 097 — ce qui est l'empreinte attendue d'un correctif qui ajoute des tests unitaires et aucune règle d'architecture.

## `AUD-QUA-25` — corrigé, et éprouvé là où un correctif de message est dangereux

Les **deux** formes que l'action laissait au choix sont livrées : le refus compose sa phrase avec ses propres faits,
**et** `IncompleteProjectReadException` porte désormais les diagnostics du provider.

Vérifié sur `nexus-context-engine`, le dépôt réel qui **est** le cas du constat :

```
OpenSpec schema 'detailed-uml' is not supported:
openspec/schemas/detailed-uml/schema.yaml must declare 'derives: spec-driven'.
```

La phrase nomme le schéma lu, le fichier attendu et la ligne à écrire. En `--json`, un bloc `diagnostics` porte en
plus la carte complète (`reason=no-declared-parent`, `schema`, `supportedSchema`) ; en mode texte, la ligne
`diagnostic=ERROR UNSUPPORTED_PROVIDER_SCHEMA {…}` s'imprime — par la surface qui ne servait qu'au succès.

**Le correctif a ajouté ce que le constat n'avait pas demandé, et c'était le bon endroit pour y penser.** Interpoler
dans une phrase une valeur que n'importe quel `config.yaml` fournit, c'est exactement là qu'un correctif de message
crée un risque. La borne existe (`SCHEMA_NAME` refuse `..`, `MAX_NAMED_SCHEMA_LENGTH = 32`, `declaredParent` ne va
jamais dans la phrase), et je l'ai éprouvée sur quatre valeurs déclarées, sur copies de la fixture :

| Valeur déclarée | Ce que la phrase rend | Motif |
|---|---|---|
| `../../../etc/passwd` | gabarit `<schema>`, **valeur non recopiée** | `invalid-schema-name` |
| nom bien formé de 63 caractères | gabarit `<schema>`, **valeur non recopiée** | `schema-definition-missing` |
| `detailed-uml'; DROP TABLE x; --` | refus plus en amont, la valeur n'atteint aucun message | `INVALID_SOURCE` |
| `detailed-uml` | valeur recopiée, chemin complet | `schema-definition-missing` |

La valeur brute reste dans le champ structuré `details`, ce qui est sa place : un champ JSON n'est pas un vecteur de
falsification de log comme l'est une phrase libre.

## `AUD-QUA-26` — ouvert (Moyenne, M), et il vient d'un commit que je n'avais pas à vérifier

`003199fd` resserre la section `Decision` de trois ADR sous les 2 000 caractères que `rules/adr.md` énonce. Vérifié :
`GEN-0134` tient 1 743 caractères, `SEC-0087` en tient 1 712. J'ai voulu savoir si la borne était tenue ailleurs.

**Elle ne l'est pas : 56 des 80 ADR en vigueur la dépassent.** Les cinq plus longs : `ARCH-0124` (10 279),
`DATA-0168` (9 000), `BE-0106` (8 459), `ARCH-0092` (8 416), `SEC-0129` (7 949). `DATA-0168` a été écrit **la veille**.

La règle ne dit pas ce qu'elle compte, et les deux lectures ne se valent pas. Prise jusqu'au `##` suivant, la section
inclut ses sous-sections `###` — ce sont les chiffres ci-dessus. Prise jusqu'au premier `###`, elle vaut
**0 caractère** pour `DATA-0168`, dont la section commence directement par `### 1.` : cette lecture ferait remettre
aux agents un briefing vide, ce qui contredit la raison d'être que la règle énonce. C'est donc la première qui vaut.

Et rien ne surveille cette borne : `archgate` n'est nommé par aucun workflow ni aucun script, et `rules/adr.md` le
dit lui-même — « `archgate check`, hors CI ».

Ce que ça coûte : la section `Decision` est la pièce qu'Archgate sert comme briefing aux agents qui travaillent sur ce
dépôt. À 10 279 caractères elle fait cinq fois la taille prévue, et l'intention de la borne — tenir le briefing court
pour qu'il soit lu — est perdue sur deux tiers du registre. L'écart grandit plutôt qu'il ne se résorbe.

**Limite de ma mesure, dite franchement** : je n'ai pas lu l'implémentation d'Archgate, qui vit hors du dépôt, donc
son compte peut différer du mien. L'écart ne disparaîtrait pas pour autant : la règle est écrite dans le dépôt, en
caractères, sans dire ce qu'elle compte — et cette imprécision est la première chose que l'action demande de corriger.

Trois sorties, et c'est une décision, pas un nettoyage : tenir la borne en déplaçant vers `Context` les raisons que
56 sections `Decision` portent ; requalifier la borne sur ce que le corpus porte, en la mesurant comme la borne
d'évidence vient de l'être ; ou retirer la borne, parce qu'une règle que deux tiers du corpus ignorent et qu'aucun
gate ne lit n'est pas une règle. Dans tous les cas, rendre le contrôle visible — un test de cohérence du dépôt sait
compter des caractères, et `RepositoryDocumentationCoherenceTest` en compte déjà d'autres.

## État

| | |
|---|---|
| Constats | **138** — 110 corrigés, 22 Info qui tiennent, 4 caducs, 1 non vérifiable, **1 toujours valide** |
| Ouvert | `AUD-QUA-26` (Moyenne) seul |
| Axes | Qualité **96**, Architecture / Sécurité / Tests / Performance / Dépendances **100** — moyenne **99,3** |
| Sprints | 16 soldés sur 17 ; le sprint 17 est neuf, **1 à 3 jours** |

Le **sprint 16 est soldé** : les quatre constats nés de l'exécution du produit sur les projets de son auteur sont
fermés en deux jours. Un **sprint 17** s'ouvre, d'un genre différent — une règle que le dépôt énonce, que son corpus
ne tient pas, et qu'aucun gate ne lit.

## Incident de ma part, consigné

En inscrivant le verdict du build j'ai d'abord tapé « 1 093 gates » au lieu de 1 097, puis corrigé par une
substitution globale sur `README.md`. Elle a réécrit **cinq blocs datés** où 1 093 était la mesure juste de leur
passe — les trois du 14 novembre, celle du 15, et celle du 16 matin. C'est exactement ce que la règle du dépôt
interdit : « les mesures historiques datées ne se réécrivent pas. »

Rattrapé en restaurant le fichier depuis la copie de la machine du mainteneur, committée avant la bévue, puis en
réappliquant la seule substitution voulue. Vérifié après coup, bloc par bloc : 1 097 pour les deux passes du 16 au
soir et de la nuit, 1 093 pour les cinq précédentes.

La leçon est mécanique, pas morale : **une substitution globale sur un fichier de relevés datés est dangereuse par
construction**, parce que le même chiffre y est juste à une date et faux à une autre. Une correction dans un relevé
daté se fait sur l'occurrence, jamais sur le motif.

## Note de méthode

Deux passes de suite, le constat neuf est venu d'un geste qui n'était pas demandé : hier, lancer le produit sur les
dépôts réels ; aujourd'hui, vérifier si un commit qui corrige trois cas avait laissé les autres. **Vérifier la
clôture d'un constat et vérifier la règle que ce constat invoque sont deux gestes différents**, et c'est le second
qui trouve. Un correctif qui met trois ADR en règle est une bonne nouvelle pour trois ADR ; il ne dit rien des
soixante-dix-sept autres, et c'est en le demandant qu'on l'apprend.
