---
id: SEC-0164
title: "Les releases Windows restent non signées tant qu'aucun certificat sans coût n'est accessible, et leur provenance est la somme SHA-256 et l'attestation"
domain: security
rules: false
---

# Les releases Windows restent non signées tant qu'aucun certificat sans coût n'est accessible, et leur provenance est la somme SHA-256 et l'attestation

- Statut : **Proposée**
- Date : 2026-11-07

## Context

L'installeur Windows, le désinstalleur qu'il déploie et le lanceur `morpheus.exe` sont publiés sans signature
Authenticode. Windows les présente comme venant d'un éditeur inconnu et SmartScreen peut avertir ; l'utilisateur n'a
aucun moyen natif de distinguer l'installeur officiel d'un substitut. À l'inverse, `distribution/ensure-inno-setup.ps1`
refuse un compilateur Inno Setup dont la signature Authenticode n'est pas valide : le dépôt exige de son fournisseur un
contrôle qu'il n'applique pas à son propre livrable.

Le mécanisme de signature existe et reste tenu par ses tests : un seul point d'entrée,
`distribution/windows/sign-windows-binary.ps1`, un état demandé (`-SigningState none|test|release`) et jamais déduit,
une identité que seul `release.yml` nomme, une répétition nocturne avec une identité auto-signée jetable, et une
qualification qui refuse une signature invalide, non horodatée ou d'un autre éditeur. Ce qui manque est un certificat.

Options considérées :

1. **Certificat gratuit pour l'open source** (SignPath Foundation) : exige une licence approuvée par l'OSI. La licence
   de MORPHEUS est propriétaire ; la voie est fermée.
2. **Certificat payant OV ou EV** : coût récurrent, et le titulaire serait une personne physique, avec une clé à tenir
   hors du runner hébergé. Le mainteneur ne le prend pas.
3. **Publier sans signature** et donner à l'utilisateur deux preuves d'origine hors Authenticode : la somme SHA-256 et
   l'attestation de provenance GitHub, toutes deux publiées avec chaque binaire.

Le mainteneur a retenu l'option 3 le 2026-10-31 et accepté le risque, suivi par RT-14 dans
`docs/architecture/risks/register.md`. La proposition de signer, ADR-0153, est archivée `Rejetée`.

## Decision

1. **Aucune release Windows n'est signée Authenticode** tant qu'aucun certificat de signature de code sans coût n'est
   accessible à MORPHEUS. Aucun certificat payant n'est pris.
2. **La provenance d'un binaire publié est sa somme SHA-256 et son attestation GitHub**, publiées avec chaque release ;
   `docs/user/INSTALLATION.md` dit l'avertissement attendu et donne la commande `gh attestation verify`.
3. **Le mécanisme de signature reste en place et inactif.** `release.yml` construit dans l'état `none`, les trois champs
   de `distribution/windows/authenticode-signing.properties` restent vides, et la branche `release` du point d'entrée
   échoue tant qu'ils le sont. Le chemin de signature reste exercé chaque nuit avec une identité d'essai jetable.
4. **Condition de réouverture** : un certificat sans coût devient accessible (changement de licence vers une licence
   approuvée par l'OSI, ou offre sans coût compatible avec une licence propriétaire). Ce changement remplit les trois
   champs ensemble, relie l'identité de release dans `release.yml` et remplace cet ADR.

## Do's and Don'ts

### Do

- Publier avec chaque binaire Windows sa somme SHA-256 et son attestation de provenance.
- Garder dans `docs/user/INSTALLATION.md` l'avertissement d'éditeur inconnu et la commande de vérification.
- Garder RT-14 ouvert et accepté dans le registre des risques tant que cette décision est en vigueur.

### Don't

- Ne pas remplir un seul des trois champs de `authenticode-signing.properties` : ils se remplissent ensemble, par le
  changement qui remplace cet ADR.
- Ne pas brancher `release.yml` sur l'état `release` ni lui faire nommer une identité de signature réelle.
- Ne pas présenter la somme SHA-256 ou l'attestation comme l'équivalent d'une signature pour Windows : SmartScreen ne
  les lit pas.

## Consequences

### Positives

- Aucun coût récurrent ni clé privée à protéger hors du runner hébergé.
- La provenance reste vérifiable par un opérateur, par deux preuves indépendantes.
- Le mécanisme de signature, tenu par ses tests, permet de basculer sans réécrire la chaîne de release.

### Négatives

- Windows présente l'installeur et le lanceur comme venant d'un éditeur inconnu ; SmartScreen peut avertir.
- La seule preuve d'origine demande une commande hors produit, que l'utilisateur final n'exécutera souvent pas.
- Le dépôt continue d'exiger de son fournisseur Inno Setup une signature qu'il n'applique pas à ses binaires.

### Risques

- Un substitut de l'installeur, distribué hors de la page de release, n'est pas distinguable par Windows (RT-14).

## Compliance and Enforcement

- `WindowsReleaseSigningContractTest#theReleaseWorkflowCannotBeWiredToSignWhileTheDeclarationIsIncomplete` : le workflow
  de release ne peut pas demander l'état `release` tant que la déclaration est incomplète.
- `WindowsReleaseSigningContractTest#onlyTheReleaseWorkflowNamesTheReleaseSigningIdentity` et
  `#theEntryPointIsTheOnlyCallerOfASigningTool` : l'identité et l'outil restent confinés.
- `distribution/build-release.ps1` : une version au-delà d'une base de signature déclarée exige l'état `release`
  (`WINDOWS_SIGNING_REQUIRED`) ; la base étant vide, aucune release n'y est soumise.
- Revue : la page `docs/user/INSTALLATION.md` garde l'avertissement et la commande de vérification ; RT-14 reste ouvert.

## References

- ADR-0089 (intégrité de la surface de production).
- RT-14, `docs/architecture/risks/register.md`.
- `docs/user/INSTALLATION.md`, section sur l'avertissement de Windows et la vérification de provenance.
- `.claude/rules/security.md`, section « Signature Authenticode Windows ».
