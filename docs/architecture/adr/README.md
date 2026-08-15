# ADR — Documentation d'architecture MORPHEUS ENGINE

Ce répertoire contient les ADR **spécifiques à la documentation d'architecture**
produite dans `docs/architecture/`. Il ne remplace pas le registre ADR principal
du projet situé dans [`../../adr/`](../../adr/) (ADR-0001 à ADR-0096).

---

## Quand créer un ADR ici vs dans `../../adr/`

| Cas | Emplacement |
|-----|------------|
| Décision architecturale affectant le code, les dépendances ou les surfaces du système | [`../../adr/`](../../adr/) |
| Décision sur la structure ou les conventions de cette documentation d'architecture | Ce répertoire |

---

## Critères de création d'un ADR (rappel)

Un ADR est requis lorsqu'un choix :

- est coûteux à inverser ;
- modifie une frontière ou une dépendance majeure ;
- engage une technologie structurante ;
- répond à un objectif qualité critique ;
- introduit un risque important ;
- a nécessité de comparer plusieurs options.

---

## Gabarit

Voir [`template.md`](template.md).

---

## Convention de nommage

```
NNNN-titre-en-kebab-case.md
```

Exemple : `0001-cadre-documentaire-arc42-c4.md`

---

## Statuts valides

| Statut | Signification |
|--------|--------------|
| **Proposée** | En cours de discussion |
| **Acceptée** | Validée et effective |
| **Remplacée par ADR-XXXX** | Supersédée |
| **Rejetée** | Option étudiée puis non retenue |

Un ADR accepté n'est **jamais supprimé** — créer un nouvel ADR et marquer
l'ancien « Remplacé ».
