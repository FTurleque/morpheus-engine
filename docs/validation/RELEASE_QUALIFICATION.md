# Qualification d'une release MORPHEUS

Cette page décrit **comment prouver** qu'une release publiée est conforme. Elle ne prouve rien par
elle-même, et aucune de ses étapes ne doit être exécutée sur une release fabriquée pour l'occasion.

> **Issue [#185](https://github.com/FTurleque/morpheus-engine/issues/185) — critère de clôture.**
> #185 ne se ferme qu'après une exécution tag → release réellement verte, sur une vraie release.
> Ne jamais créer un tag ou une release artificielle pour la fermer. La dernière release réellement
> publiée reste `v1.2.0` (30/07/2026), antérieure au workflow durci : le dépôt ne doit donc pas
> prétendre qu'une qualification end-to-end a déjà eu lieu.

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
cross-job                 download-artifact avec digest-mismatch: error
écrasement                refusé : gh release view avant gh release create
```

## Ce qui doit être vérifié après la publication

Le pipeline construit et publie ; il ne vérifie pas ce que GitHub a **réellement** rendu public. C'est
le rôle des deux scripts de qualification, à exécuter depuis une machine qui n'est pas le runner :

```bash
bash scripts/verify-release-provenance.sh v1.2.1
```

```powershell
scripts\verify-release-provenance.ps1 -Tag v1.2.1
```

Les deux exécutent les mêmes six contrôles et échouent fermé sur chacun :

| # | Contrôle | Échec si |
|---|---|---|
| 1 | Tag → commit → `main` | le tag n'existe pas sur `origin`, ou son commit n'est pas atteignable depuis `main` |
| 2 | Ensemble d'assets | un asset attendu manque, **ou** un asset inattendu est publié |
| 3 | Checksums | un `.sha256` publié ne correspond pas à l'artefact publié à côté |
| 4 | Manifestes de release | `version`, `tag`, `gitSha`, `product`, la taille d'un asset, ou la couverture des deux plateformes divergent |
| 5 | Provenance | `gh attestation verify` échoue pour l'archive Linux, le ZIP Windows ou l'installeur |
| 6 | Bundles d'attestation | un `.attestation.jsonl` est absent ou vide |

Prérequis : `gh` authentifié sur le dépôt, `git`, et — pour la variante shell — `sha256sum` et un
interpréteur Python 3 (résolu par `scripts/lib/python.sh`).

Le contrôle 2 refuse aussi les assets **inattendus** : une release correcte n'est pas seulement une
release qui contient ce qu'il faut, c'est une release qui ne contient rien d'autre.

## Enregistrement de la preuve

Après une exécution verte sur une vraie release :

1. copier la sortie complète des deux scripts dans `docs/validation/VALIDATION_RELEASE_<version>.md` ;
2. y consigner le tag, le SHA exact et la date ;
3. seulement ensuite, fermer #185 en citant ce fichier.

Une sortie `RELEASE QUALIFICATION PASS` produite sur une release fabriquée pour la circonstance ne
qualifie rien et ne doit jamais être consignée.
