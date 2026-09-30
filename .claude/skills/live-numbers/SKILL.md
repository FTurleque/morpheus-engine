---
name: live-numbers
description: Relire tout chiffre périssable de MORPHEUS depuis sa source vivante avant de le citer - ratchets de couverture et leurs deux échelles, minimums de tests, version produit, numérotation des ADR, compte de modules, version de schéma SQLite, versions pinnées. À utiliser avant toute réponse, tout audit, toute description de PR ou tout message de commit qui énonce un de ces nombres, et avant d'attribuer un nouveau numéro d'ADR.
---

# Chiffres vivants

Ce dépôt a déjà raisonné sur des seuils faux. Le 31/08/2026, trois pages citaient trois valeurs
différentes du même ratchet de couverture, et la source normative en donnait une quatrième. La
règle qui en est sortie vit dans [rules/meta.md](../../rules/meta.md) : **tout chiffre écrit
dans une page est une copie, jamais l'original.**

Cette skill existe pour rendre la relecture moins coûteuse que la mémoire.

## Faire

```bash
bash .claude/skills/live-numbers/numbers.sh
```

```powershell
powershell -NoProfile -File .claude/skills/live-numbers/numbers.ps1
```

Les deux sorties sont identiques ligne pour ligne — la parité dual-platform vaut ici comme pour
les validateurs. Aucune valeur n'est codée dans les scripts : chaque section annonce la source
qu'elle vient de lire.

## Lire la sortie

| Section | Source normative | Piège |
|---|---|---|
| Ratchets | `config/m21-quality-ratchets.properties` | **Deux paires de clés.** Un seuil de couverture cité sans son échelle (`aggregate*` ou `perModule*`) ne veut rien dire. |
| Plafonds qualifiés | une constante par échelle, dans le gate de cette échelle | Le ratchet ne doit jamais dépasser le plafond **de sa propre échelle**. Voir [coverage-ratchet](../coverage-ratchet/SKILL.md). |
| Preuves de couverture | `target/m21-*-coverage-summary.txt` | La première ligne déclare la portée mesurée, et une preuve dont la portée ne correspond pas est refusée. Absente = le `clean verify` n'a pas tourné. |
| ADR | les fichiers numérotés de `docs/adr/` | Le répertoire contient aussi un `README.md` : « nombre de fichiers » et « nombre d'ADR » ne sont pas le même chiffre. La ligne « doublons » n'est pas décorative — un doublon de numérotation a déjà existé. |
| Manifeste | `contracts/public-surfaces.tsv` | Le compte de lignes inclut l'en-tête. Les sentinelles se comptent, elles ne se recopient pas. |
| Schéma SQLite | la constante qui le déclare | `SqliteServerMaintenance` doit **consommer** cette constante, jamais restituer un littéral. |

## Quand un chiffre diverge

Si une valeur lue ici contredit une valeur écrite dans `.claude/`, dans `README.md` ou dans
`docs/` :

1. **Ne pas trancher silencieusement.** Le signaler explicitement dans la réponse.
2. La source vivante gagne — le code et les scripts de validation font foi, pas la documentation.
3. Proposer la correction de la page fautive dans la même session.
4. Si la divergence porte sur un ratchet, ne pas corriger à la main la liste des destinations :
   lancer `RepositoryDocumentationCoherenceTest` et `ProductionIntegrityContractTest`, et laisser
   les échecs énumérer les pages réellement concernées. Une liste écrite à la main s'est déjà
   révélée incomplète.

## Ne pas faire

- Ne pas citer un seuil, un compte de tests ou un compte d'ADR de mémoire dès que la réponse a
  un impact décisionnel : passer ou casser un gate, autoriser une PR, juger une régression.
- Ne pas recopier un nombre depuis une exécution antérieure de cette skill : il devient faux au
  prochain milestone.
- Ne pas écrire un total périssable dans une page de gouvernance.
  `RepositoryDocumentationCoherenceTest` refuse cette formulation sur ces surfaces : c'est
  l'instruction de compter qui y a sa place, pas le résultat du comptage.
- Ne pas confondre une **mesure datée** et une **affirmation courante**. Une preuve horodatée ne
  se met pas à jour : la réécrire falsifie un enregistrement au lieu de rafraîchir une règle.
