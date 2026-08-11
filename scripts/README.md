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

## D2 — Post-R3 Repository Hardening

Windows :

```powershell
.\scripts\validate.cmd d2 -Version 1.2.0 -BaseRef origin/develop
```

Linux / WSL :

```bash
MORPHEUS_D2_BASE_REF=origin/develop bash ./scripts/validate-d2.sh 1.2.0
```

D2 est un gate **local-only** :

```text
GitHub Actions not used
.github/workflows delta forbidden
clean verify required
coverage >= 40% / 35%
dependency hygiene blocking
OWASP Dependency-Check local aggregate
portable smoke required
same SHA Windows/Linux required
```

Les switches/variables de skip D2 sont réservés au diagnostic et ne constituent jamais une qualification finale.

Le dispatcher Windows sélectionne `scripts/validate-<target>.ps1`, transmet les arguments tels quels et propage son code de sortie.

Les documents de preuve historiques peuvent conserver les commandes réellement exécutées à l’époque. Les guides actifs utilisent le dispatcher unique.
