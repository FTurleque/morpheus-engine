# Validation de la release MORPHEUS 1.2.1

Relevé daté de la qualification publique de `v1.2.1`, établi selon
[`RELEASE_QUALIFICATION.md`](RELEASE_QUALIFICATION.md). C'est la première release publiée par le workflow durci : elle
est la vraie exécution tag → release qu'attendait l'issue 185.

```text
tag                 v1.2.1
commit              2a8b1ede9cdeb107244b04969726305afc672189
promotion           PR #503 (develop -> main), merge commit ci-dessus
release workflow    run 38980110816 : signed-provenance (linux-x64) success, signed-provenance (windows-x64) success,
                    publish immutable GitHub release success
publication         2026-10-16T23:37:21Z
qualification       2026-10-17, depuis le poste du mainteneur (Windows 10), hors runner
```

## Chemin parcouru avant le tag

- Répétition de la chaîne de release (lane nocturne `Release rehearsal`) verte deux fois de suite sur Linux et Windows :
  runs 38951941460 et 38958417464 (le second sur `develop` à `a998f186`).
- Lecture humaine de `THIRD-PARTY-NOTICES` signée par le mainteneur le 16 octobre 2026 (13 composants, `NOTICE`
  embarqués reproduits, `LICENSE.zentus` de sqlite-jdbc reproduit).
- Checks requis verts sur la tête de la promotion `e231f522` avant sa fusion.

Les sorties ci-dessous sont reproduites telles quelles, à une exception : le chemin du répertoire de travail temporaire
est abrégé (`...`) sur la ligne `Workdir`.

## Sortie de `bash scripts/verify-release-provenance.sh v1.2.1`

```text
MORPHEUS release qualification
Tag:     v1.2.1
Version: 1.2.1
Workdir: /c/Users/fturl/AppData/Local/Temp/claude/.../scratchpad/qualif-sh
tag reachable from main (2a8b1ede9cdeb107244b04969726305afc672189): PASS
assets: 12 published, all expected
published asset set: PASS
published checksums (4 verified): PASS
manifests: ['linux-x64', 'windows-x64'] agree on version, tag and commit
release manifests: PASS
public provenance (4 artifacts): PASS
preserved attestation bundles: PASS
inventory: 31 components, both archives covered
published inventory: PASS
RELEASE QUALIFICATION PASS: v1.2.1 @ 2a8b1ede9cdeb107244b04969726305afc672189
```

## Sortie de `scripts\verify-release-provenance.ps1 -Tag v1.2.1`

```text
MORPHEUS release qualification
Tag:     v1.2.1
Version: 1.2.1
Workdir: C:/Users/fturl/AppData/Local/Temp/claude/.../scratchpad/qualif-1.2.1b
From https://github.com/FTurleque/morpheus-engine
 * branch              main       -> FETCH_HEAD
tag reachable from main (2a8b1ede9cdeb107244b04969726305afc672189): PASS
published asset set (12 assets): PASS
published checksums (4 verified): PASS
release manifests: PASS
public provenance (4 artifacts): PASS
preserved attestation bundles: PASS
published inventory (31 components, both archives covered): PASS
RELEASE QUALIFICATION PASS: v1.2.1 @ 2a8b1ede9cdeb107244b04969726305afc672189
```

## Écart rencontré, sans rapport avec la release

Une première exécution de la variante PowerShell a échoué au contrôle 7 (`the Linux archive carries no
THIRD-PARTY-NOTICES`). La cause était locale : lancé depuis Git Bash, le script trouvait d'abord le `tar` GNU de Git, qui
lit `C:` dans un chemin Windows comme un nom d'hôte (`Cannot connect to C: resolve failed`) et n'a jamais ouvert
l'archive. Relancé avec `C:\Windows\System32` en tête du `PATH`, il rend le PASS ci-dessus ; la variante shell, qui
reçoit un chemin `/c/...`, l'a rendu du premier coup sur la même release. La release n'a pas été modifiée entre les
deux exécutions : elle ne peut pas l'être (création sans `--clobber`, assets immuables).

## Ce que ce relevé ne couvre pas

- L'installation de l'installeur publié sur une machine propre : la répétition M20 l'a exercée sur l'artefact
  construit à `a998f186`, pas sur l'asset téléchargé.
- `isImmutable` vaut `false` dans la réponse de `gh release view` : la protection d'immuabilité de GitHub n'est pas
  activée sur le dépôt. Le workflow refuse néanmoins d'écraser une release existante (`gh release view` avant
  `gh release create`, sans `--clobber`).
