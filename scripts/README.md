# Scripts de validation

Les implémentations de validation sont regroupées dans ce répertoire :

```text
validate-<target>.ps1   Windows / PowerShell
validate-<target>.sh    Linux / WSL lorsque disponible
```

La racine du dépôt ne contient plus un wrapper par jalon. Sous Windows, utilisez le dispatcher unique :

```powershell
.\scripts\validate.cmd <target> [arguments du validateur]
```

Exemples :

```powershell
.\scripts\validate.cmd m28 -Version 1.2.0 -BaseRef origin/develop
.\scripts\validate.cmd r3 -Version 1.2.0 -BaseRef origin/develop
```

Lister les cibles PowerShell disponibles :

```powershell
.\scripts\validate.cmd list
```

Le dispatcher ne modifie pas la sémantique des validateurs spécialisés : il sélectionne `scripts/validate-<target>.ps1`, transmet les arguments tels quels et propage son code de sortie.

Les documents de preuve historiques peuvent conserver les commandes réellement exécutées à l’époque. Les guides actifs doivent utiliser le dispatcher unique.
