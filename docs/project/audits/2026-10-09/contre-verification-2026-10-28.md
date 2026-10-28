# Contre-vérification du 28 octobre 2026 — sprint 9, build, et relecture des sept verdicts

Base : `origin/develop` à **`5c3ce4dd`** (PR #577).
Point de départ : `4694b8b2`, état de la passe du 26 octobre.
Écart : **107 commits, 13 pull requests (#565 à #577), 349 fichiers, +12 862 / −5 043.**

Ce document complète `reverification-2026-10-28.md`, écrit le même jour par une autre session sur les sept
constats des sprints 1, 5, 7 et 8. Il couvre ce que cette note déclarait ne pas couvrir — **le sprint 9** —
ajoute **un build de contrôle**, et **relit ses sept verdicts dans le code**.

---

## 1. Le build

```
JAVA_HOME=~/.jdks/ms-21.0.12.1   ./mvnw -B -o clean verify
BUILD SUCCESS — 19/19 modules — 9 min 41
4 669 tests, 0 échec, 0 erreur, 13 skips
```

Dont **825 exécutions de tests d'architecture**, contre un ratchet fixé à 813. Par module : domain 57,
application 459, provider-sdk 90, testkit 8, reference 2, openspec 81, markdown 11, synthetic 41,
store-memory 24, store-sqlite 238, mcp-transport 87, minos 39, nexus 31, mcp 211, api 415, cli 1 983,
architecture-tests 825, coverage-report 10.

C'est ce qui donne leur valeur aux verdicts ci-dessous : toutes les règles ArchUnit citées **passent
réellement**, elles ne sont pas seulement écrites.

**Couverture mesurée**, par module et par ses *propres* tests :

| Module | Lignes | Branches | Lignes instrumentées |
|---|---|---|---|
| `morpheus-application` | **49,14 %** | 45,82 % | 11 339 |
| `morpheus-store-memory` | 52,87 % | 33,58 % | 732 |
| `morpheus-domain` | 59,78 % | 50,39 % | 930 |
| `morpheus-store-sqlite` | 79,60 % | 69,69 % | 3 902 |
| `morpheus-cli` | 88,49 % | 80,39 % | 2 902 |
| `morpheus-mcp` | 91,34 % | 74,27 % | 1 582 |
| `morpheus-api` | 94,36 % | 84,16 % | 4 096 |
| **agrégat** | **91,55 %** | **77,76 %** | 29 753 |

---

## 2. Le sprint 9 est terminé

Six constats de duplication que rien n'avait entamés depuis le 9 octobre se ferment d'un coup. Ce n'est pas
un déplacement de fichiers : un socle partagé a été extrait, et **cinq règles ArchUnit par signature**
refusent désormais une copie, y compris renommée.

| Constat | Ce qui a disparu | Où vit le socle | Ce qui ferme |
|---|---|---|---|
| `AUD-QUA-02` | 71 copies privées du localisateur de racine → **0** ; 16 empreintes → **1** ; 5 critères → **1** | `RepositoryRoot`, 6 copies gouvernées (une par module de test), 342 appels dans 125 fichiers | `RepositoryRootOwnershipTest` : scan **par forme**, pas par nom, exemptions vides |
| `AUD-QUA-08` | `sha256(String)` 7 → **1** ; `slug` 4 → **0** ; `readAllLines` et `joinContent` 3 → **1** | `application.foundation.TextDigest`, `StableKey`, et `OpenSpecSourceText` | `TextDigestOwnershipArchitectureTest`, ArchUnit + jumeau textuel |
| `AUD-QUA-07` | 4 builders → **1** ; 4 casts `(String)` non vérifiés → **0** | `application.foundation.PayloadMap`, 15 fichiers, 52 références | `PayloadMapConstructionArchitectureTest`, **par signature** `Object[]→Map` |
| `AUD-ARC-10` | 4 projections `requirement`, 3 `change`, 2 pour six autres → **1 chacune** | `application.payload.PublicPayloads`, 11 fichiers, les deux transports | `SharedDomainProjectionArchitectureTest`, inventaire croissant + exceptions décroissantes |
| `AUD-QUA-09` | diff normalisé Minos↔Nexus 41 lignes → **0** ; les deux fichiers passent de 224 et 212 lignes à **82** | `application.security.PeerLaunchSettings(+Resolver)` | `McpPeerSettingsResolutionArchitectureTest`, dont un vrai **test de parité** sur les mêmes entrées |
| `AUD-QUA-06` | `safeMessage` 11 → **0** ; `command(String[])` 12 → **1** ; 6 classes `Options` → **0** ; blocs dupliqués du CLI 97 → **15** | `GlobalArgs`, `CliErrorLine`, `CommandOptions`, `CliCommandRun`, `CliFailure` | `CliArgumentBaseArchitectureTest`, trois règles **par signature** |

La différence avec les passes précédentes mérite d'être nommée : ces règles ne cherchent pas un nom de
classe, elles cherchent une **forme** — une méthode `Throwable → String`, un `Object[] → Map`, un fichier de
test qui part du répertoire courant et remonte. Une copie renommée est refusée. C'est la première fois que
l'enforcement du dépôt ferme une porte plutôt que d'en inventorier le passage.

---

## 3. Les sept verdicts du 28 octobre tiennent

Chaque fichier cité par la note et par `findings.json` a été rouvert. **Aucun verdict n'est infirmé.**
`AUD-TST-10` méritait l'examen le plus serré : les trois bases sont réellement dans le dépôt
(552 960, 692 224 et 712 704 octets), suivies par git, avec leurs `provenance.properties`, leurs SHA-256
recalculés conformes, et un contenu lu en SQLite qui porte les bons paliers — 39, 50 et 51 tables,
horodatages réels et séquentiels, aucune table postérieure au tag.

Six écarts de citation ont été relevés et inscrits. Aucun ne change un statut :

1. `AUD-TST-10` — `validate-r2.sh` conserve le contrôle `tests < 1` à `:63` ; le contrôle par palier
   s'y **ajoute** (`:66-88`, pas `:66-76`). Et `:155` écrit encore `sqliteV012ToV015Upgrade=PASS`,
   libellé périmé depuis que trois paliers sont couverts.
2. `AUD-PRF-09` — la citation `MorpheusRemoteRoutePolicyTest:122-138` désigne un autre test ; celui qui
   épingle la répartition par rôle est `:141-157`.
3. `AUD-PRF-14` — `OpenSpecRequirementDeltaReader:82` est la surcharge auto-énumérante, pas celle qui
   reçoit la liste (`:85-91`).
4. `AUD-TST-19` — `testsMinimum` et `generatedTestsMinimum` **partagent** un bloc de commentaire.
5. `AUD-TST-09` — les 32 champs de plafond des fichiers de preuve portent les anciennes valeurs ; ce n'est
   pas un oubli, c'est l'enregistrement fidèle de l'état au commit du run, et aucun test ne les lit.
6. `AUD-TST-09` — l'intitulé du constat reste littéralement vrai : les écarts sont de 0,264 / 0,356 /
   0,284 / 0,420 point. Le « corrigé » porte sur la péremption des plafonds et sur l'exécution de la règle
   de marge, pas sur la suppression de l'écart, qui est désormais un invariant voulu.

**Une réserve de périmètre, inscrite dans `reste`** : `AUD-TST-10` portait `reste: "Rien"` hors paliers 21-22.
Les deux fabriques itèrent `distinctLevels() = {12, 15, 20}`, donc **six paliers — 13, 14, 16, 17, 18, 19 —
ne sont le point de départ d'aucun test d'upgrade**. Le constat d'origine nommait 13, 14, 15 et 18 : 15 est
désormais couvert, 13, 14 et 18 ne le sont pas. C'est défendable — aucune release n'a livré ces paliers —
mais ce n'est pas « rien ».

---

## 4. Un erratum sur mon propre travail

**`AUD-ARC-16` passe à CADUC.** Je l'avais ouvert le 26 octobre en prescrivant, comme première action,
d'ajouter au gate sqlite une garde d'exactitude sur la liste des points d'entrée. **Cette garde existait
déjà ce jour-là** : `SqliteStorePackagesArchitectureTest:81` l'assertionne sur le bytecode et `:109` sur les
noms lus dans les sources des autres modules. `git show 4694b8b2` rend les mêmes lignes. Elle est
bilatérale et elle passe au build.

Le fait brut du constat restait exact — trois types et onze membres sont bien passés de package-private à
`public`. Mais selon la doctrine du dépôt, ce qu'ArchUnit vérifie existe : la frontière est tenue, et ce qui
reste est une préférence de conception, pas un défaut. Le constat tombe.

---

## 5. Trois constats naissent

**`AUD-SEC-18` — Moyenne.** Deux frontières d'espace de travail coexistent désormais dans
`morpheus-application` avec des garanties opposées. Le `SafeWorkspaceFileResolver` étoffé par la vague
refuse tout composant symbolique sur **tous** les segments avant lecture et revalide identité et contenu
après, sans cache d'ancêtres. `ScanWorkspaceBoundary` garde son chemin rapide, celui où `AUD-SEC-17` décrit
deux fenêtres de course ouvertes. Rien n'écrit laquelle fait foi.

**`AUD-QUA-19` — Faible.** La règle qui vient d'éradiquer `safeMessage` du CLI n'importe que
`com.morpheus.cli`. Sept déclarations survivent ailleurs, sous trois signatures.

**`AUD-QUA-20` — Faible.** Maintenant que les réglages ont convergé, la paire de passerelles
`MinosMcpCodeGateway` / `NexusMcpContextGateway` porte **78 des 89 blocs dupliqués** restants de chaque
module d'intégration. Elle était hors périmètre d'`AUD-QUA-09` au motif qu'elle « diverge réellement » —
ce qui était aussi vrai des réglages, jusqu'à ce qu'ils convergent.

---

## 6. Aggravations

| Mesure | 26/10 | 28/10 |
|---|---|---|
| Assertions textuelles dans les gates | 1 826 | **1 848** |
| `Files.readString` dans les gates | 425 | **430** |
| Listes nominatives ne déclarant pas leur sorte | 10 | **13** |
| Rapport de lignes application / domaine | 9,75× | **9,99×** |
| Classes à accès fichier nommées par ADR-0150 | 11 | **12** |
| Fichiers de test dépendant de `RepositoryRoot.locate()` | 0 | **80** |

Le ratio règle/texte s'améliore pour la deuxième passe consécutive — 31,5:1 → **29,8:1** — mais aucun
compte absolu ne baisse : il s'améliore par ajout de règles, pas par retrait de texte.

Deux points méritent d'être lus ensemble. La liste de classes à accès fichier d'ADR-0150 **a grandi**, alors
que l'ADR la veut décroissante : `PeerLaunchSettingsResolver`, né de l'extraction du sprint 9, y est entré.
À la décharge du dépôt, la clause « un nom ajouté exige un ADR consolidé » a été respectée à la lettre — le
0148 a été remplacé par le 0150. C'est la procédure qui fonctionne, pas la liste qui rétrécit. Et
`RepositoryRoot.locate()` est un point de passage nouveau pour 80 fichiers de test, avec un critère de
racine différent de l'ancien (un `pom.xml` déclarant `<modules>`, au lieu de `pom.xml` + `public-surfaces.tsv`).

---

## 7. Score

| Axe | 26 oct. | 28 oct. | |
|---|---|---|---|
| Sécurité | 96 | **92** | −4 |
| Qualité | 70 | **88** | **+18** |
| Dépendances | 83 | **87** | +4 |
| Tests | 70 | **83** | +13 |
| Performance | 77 | **78** | +1 |
| Architecture | 55 | **59** | +4 |
| **Moyenne** | **75** | **81** | **+6** |

La plus forte progression de la mission. Qualité gagne dix-huit points parce que le sprint 9 ferme six
constats d'un coup. Sécurité perd quatre points : `AUD-SEC-18` est le prix de la divergence créée par une
correction de performance.

**Deux constats Élevée restent** : `AUD-ARC-01` (partiel — le réseau n'est pas sorti du cœur) et
`AUD-PRF-04` (partiel — deux requêtes sur trois non bornées).

---

## 8. Angles morts de cette passe

- Le build a tourné sur **Windows seulement**. La parité des validateurs sur les deux plateformes reste non
  vérifiée par moi, et les 21 cas PowerShell de la suite Python sont les seuls joués de leur moitié.
- La lane `windows-validators`, nouvelle, n'a pas encore de verdict sur `develop` : son seul run est manuel.
- Aucun scan de vulnérabilités.
- L'origine binaire des trois bases publiées n'est pas démontrable depuis le dépôt. Tout converge — paliers
  exacts, tables absentes aux bons endroits, horodatages réels, SHA-256 consignés — mais rien dans l'arbre ne
  prouve que c'est la CLI du tag qui a écrit ces octets. Le README le dit lui-même : une base reproduite
  n'aurait pas les mêmes octets, donc la reproduction ne pourrait valider que par la forme.
- Les mesures de blocs dupliqués viennent d'un script de cette passe, appliqué aux deux révisions pour être
  comparable à lui-même, mais pas garanti identique à celui du 9 octobre.
