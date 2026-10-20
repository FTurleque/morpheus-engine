# Qualification d'une release MORPHEUS

Cette page décrit **comment prouver** qu'une release publiée est conforme. Elle ne prouve rien par
elle-même, et aucune de ses étapes ne doit être exécutée sur une release fabriquée pour l'occasion.

> **Issue [#185](https://github.com/FTurleque/morpheus-engine/issues/185) — critère de clôture.**
> #185 ne se ferme qu'après une exécution tag → release réellement verte, sur une vraie release.
> Ne jamais créer un tag ou une release artificielle pour la fermer. Ce critère est rempli par `v1.2.1`
> (16/10/2026), la première release publiée par le workflow durci, dont la qualification est consignée dans
> [`VALIDATION_RELEASE_1.2.1.md`](VALIDATION_RELEASE_1.2.1.md). `v1.2.0` (30/07/2026) lui est antérieure
> et n'a jamais été qualifiée par ces scripts.

## Ce que le pipeline garantit déjà

`.github/workflows/release.yml` refuse de publier si l'une de ces conditions n'est pas remplie. Ces
invariants sont assertés par `ProductReleaseContractTest` et `ReleaseQualificationContractTest` :

```text
tag                       vX.Y.Z, sinon échec avant tout build
commit                    atteignable depuis main, vérifié sur les deux plateformes
build                     Linux + Windows, chacun depuis le SHA exact du tag
gate                      scripts/validate-m28.ps1 rejoué avant le build Windows
attestations              actions/attest via OIDC, id-token: write
bundles                   conservés comme assets .attestation.jsonl
checksums                 .sha256 publiés à côté de chaque artefact
inventaire                SBOM CycloneDX du commit tagué, produit par le build Linux, attesté avec l'archive
attributions              LICENSE + THIRD-PARTY-NOTICES à la racine de chaque image, packaging refusé sinon
cross-job                 download-artifact avec digest-mismatch: error
écrasement                refusé : gh release view avant gh release create
```

## Répétition nocturne — ce qu'elle exerce, ce qu'elle ne prouve pas

Le workflow de release ne tourne que sur un tag poussé. Pour que sa moitié « build » ne soit pas découverte le jour
du tag, la lane `Release rehearsal` de `.github/workflows/nightly.yml` lance, sur Linux et sur Windows,
`scripts/validate-m20.sh` et `scripts/validate-m20.ps1` : les mêmes `distribution/build-release.*` que la release, sur
un tag local `m20-validation-<sha>` qui ne quitte jamais le checkout. Un troisième job compare les fichiers produits
aux noms que `release.yml` exige avant de publier (`scripts/check-release-rehearsal-assets.py`, qui lit la liste dans
le workflow).

**Quand elle tourne.** Une planification exécute le fichier de workflow de `main` : la lane tourne chaque nuit à
partir du moment où `main` la porte, et seulement à la demande (`workflow_dispatch`, sur n'importe quelle branche)
avant cela. Ne pas tenir le build de release pour répété tant que la dernière exécution n'a pas été lue.

Une répétition ne publie rien et ne qualifie rien. Elle n'exerce pas :

```text
tag atteignable depuis main        vérifié seulement sur un vrai tag
attestation de provenance          actions/attest n'est jamais lancé par une répétition
publication                        aucune release, aucun asset, aucun tag sur origin
relecture publique                 verify-release-provenance ne lit qu'une release publiée
```

Une répétition verte ne ferme ni #185 ni `DT-11`, et sa sortie ne se consigne jamais comme preuve de qualification.

**Mots-clés de fermeture.** Un message de commit qui place « close », « fix » ou « resolve » devant un numéro d'issue
la ferme quand il arrive sur `main`, quelle que soit la phrase autour. C'est ainsi que #185 a été fermée par accident
le 9 octobre 2026, par un commit qui disait qu'elle restait ouverte. Écrire « l'issue 185 » dans ces phrases.

## Ce qui doit être vérifié après la publication

Le pipeline construit et publie ; il ne vérifie pas ce que GitHub a **réellement** rendu public. C'est
le rôle des deux scripts de qualification, à exécuter depuis une machine qui n'est pas le runner :

```bash
bash scripts/verify-release-provenance.sh v1.2.1
```

```powershell
scripts\verify-release-provenance.ps1 -Tag v1.2.1
```

Les deux exécutent les mêmes sept contrôles et échouent fermé sur chacun :

| # | Contrôle | Échec si |
|---|---|---|
| 1 | Tag → commit → `main` | le tag n'existe pas sur `origin`, ou son commit n'est pas atteignable depuis `main` |
| 2 | Ensemble d'assets | un asset attendu manque, **ou** un asset inattendu est publié |
| 3 | Checksums | un `.sha256` publié ne correspond pas à l'artefact publié à côté |
| 4 | Manifestes de release | `version`, `tag`, `gitSha`, `product`, la taille d'un asset, ou la couverture des deux plateformes divergent |
| 5 | Provenance | `gh attestation verify` échoue pour l'archive Linux, le SBOM, le ZIP Windows ou l'installeur |
| 6 | Bundles d'attestation | un `.attestation.jsonl` est absent ou vide |
| 7 | Inventaire | le SBOM publié décrit une autre version que le tag, une archive n'embarque pas `THIRD-PARTY-NOTICES`, ou une archive attribue un composant que le SBOM ne liste pas |

Prérequis : `gh` authentifié sur le dépôt, `git`, et — pour la variante shell — `sha256sum` et un
interpréteur Python 3 (résolu par `scripts/lib/python.sh`).

Le contrôle 2 refuse aussi les assets **inattendus** : une release correcte n'est pas seulement une
release qui contient ce qu'il faut, c'est une release qui ne contient rien d'autre.

## Déclarer le niveau de schéma de la release

Une release livre un niveau de schéma SQLite : le numéro de sa dernière migration. La version qui la suit le déclare,
avant toute autre modification, dans `morpheus-store-sqlite/src/test/resources/released-schema-levels.properties` :

```bash
git ls-tree --name-only v<version> morpheus-store-sqlite/src/main/resources/db/migration/
```

Ajouter la ligne `<version>=<numéro de la dernière migration listée>`. `ReleasedSchemaLevelsDeclarationTest` fait échouer
le build de la version suivante tant que la ligne manque ; `ReleasedSchemaUpgradeTest` éprouve ensuite la montée de
version depuis ce niveau. Si le niveau est nouveau, le test refuse de l'accepter tant que les lignes de sa base ne sont
pas écrites d'après les requêtes d'insertion des stores au tag.

## Enregistrement de la preuve

Après une exécution verte sur une vraie release :

1. copier la sortie complète des deux scripts dans `docs/project/validation/VALIDATION_RELEASE_<version>.md` ;
2. y consigner le tag, le SHA exact et la date ;
3. seulement ensuite, fermer #185 en citant ce fichier.

Une sortie `RELEASE QUALIFICATION PASS` produite sur une release fabriquée pour la circonstance ne
qualifie rien et ne doit jamais être consignée.
