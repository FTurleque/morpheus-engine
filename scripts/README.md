# Scripts de validation

Les implémentations de validation sont regroupées dans ce répertoire :

```text
validate-<target>.ps1   Windows / PowerShell
validate-<target>.sh    Linux / WSL lorsque disponible
```

Sous Windows, utiliser le dispatcher unique :

```powershell
.\scripts\validate.cmd <target> [arguments du validateur]
```

Lister les cibles :

```powershell
.\scripts\validate.cmd list
```

## M21 — gate durable exact-head

La baseline corrective active est `1.2.1` :

```powershell
.\scripts\validate.cmd m21 -Version 1.2.1
```

```bash
bash ./scripts/validate-m21.sh 1.2.1
```

M21 applique notamment :

```text
clean verify
Surefire >= 698
architecture >= 250
coverage >= 47% / 40%
CycloneDX SBOM
provenance
portable smoke
product/package version = 1.2.1
HEAD exact + workspace tracked clean
```

Le même SHA doit réussir sur Windows et Linux.

## D2 — Post-R3 Repository Hardening

Windows :

```powershell
.\scripts\validate.cmd d2 -Version 1.2.1 -BaseRef origin/develop
```

Linux / WSL :

```bash
MORPHEUS_D2_BASE_REF=origin/develop bash ./scripts/validate-d2.sh 1.2.1
```

D2 reste un gate **local spécialisé** :

```text
.github/workflows delta forbidden
clean verify required
baseline tests >= 698 / architecture >= 250
absolute coverage floor >= 40% / 35%
dependency hygiene blocking
OWASP Dependency-Check local aggregate
portable smoke required
same SHA Windows/Linux required
```

Il n'est pas destiné à qualifier une PR qui modifie précisément les workflows GitHub ; le gate durable de ces PR reste M21, complété par `MORPHEUS Security` à la frontière `main`.

Les switches/variables de skip D2 sont réservés au diagnostic et ne constituent jamais une qualification finale.

Le dispatcher Windows sélectionne `scripts/validate-<target>.ps1`, transmet les arguments tels quels et propage son code de sortie.

Les documents et validateurs de preuve historiques (R2/R3/milestones) peuvent conserver les versions réellement exécutées à l’époque. Les guides et gates actifs utilisent `1.2.1`.
