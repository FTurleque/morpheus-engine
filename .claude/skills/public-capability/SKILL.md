---
name: public-capability
description: Ajouter, modifier ou retirer une capacité publique MORPHEUS (commande CLI, outil MCP, route HTTP) en gardant le port, l'adaptateur, le manifeste de convergence, l'OpenAPI, les gates et l'ADR en phase. À utiliser dès qu'une surface visible de l'extérieur bouge, quand un gate de convergence échoue, ou pour décider si un changement mérite un ADR.
---

# Livrer une capacité publique

Le manifeste `contracts/public-surfaces.tsv` et les specs `docs/openapi/morpheus-v1-*.yaml` sont
comparés **caractère par caractère** par les gates. Modifier une signature sans mettre à jour les
deux casse le build. Ce qui suit est l'ordre qui évite d'y revenir trois fois.

## 1. Placer la capacité : port dans `application`, implémentation dans un adaptateur

`morpheus-application` **définit les ports** et ne connaît **aucun** adaptateur. Les adaptateurs
(`provider`, `store`, `cli`, `mcp`, `api`, `integration`) dépendent vers l'intérieur et sont
**frères** — ils ne s'appellent pas entre eux. Le câblage est explicite dans `MorpheusMain` : ni
framework, ni réflexion, ni scan de classpath.

Un consommateur de port doit rester provider-neutre : il fonctionne avec les trois providers
(openspec, markdown, synthetic). C'est vérifié.

## 2. Écrire la ligne du manifeste

Format TSV, tabulations réelles :

```
capability	intent	cli	mcp	http	notes
```

`intent` vaut `READ` ou `WRITE`. Les colonnes `cli` / `mcp` / `http` portent soit une surface
réelle, soit un sentinelle explicite :

| Sentinelle | Sens |
|---|---|
| `EXPLICITLY_NOT_EXPOSED` | Délibérément absent de ce transport |
| `EXPLICITLY_LOCAL_ONLY` | Local uniquement, jamais remote |
| `EXPLICITLY_REMOTE_ONLY` | Remote uniquement, jamais local |
| `EXPLICITLY_OFFLINE_ONLY` | Hors-ligne uniquement |

**Une case vide est une violation** — c'est le mode de défaillance le plus fréquent et le plus
silencieux de ce système. L'absence se déclare, elle ne se déduit pas. La colonne `notes`
justifie le sentinelle : « ce code tiers est exécutable, donc jamais model-facing » est une
justification, « non exposé » n'en est pas une.

Toute valeur autre qu'un sentinelle doit correspondre à une surface **réellement implémentée**.

## 3. Mettre l'OpenAPI au même niveau

Sur les schémas d'entrée :

- `additionalProperties: false` ;
- des bornes explicites : `maximum`, `maxItems`, `maxLength` ;
- **aucun** vocabulaire d'échappement : `sql query`, `sql passthrough`, `script source`,
  `apply mutation` sont interdits.

Une capacité `READ` reste `READ` : le reasoning porte `const: false` côté mutation.

## 4. Respecter les sémantiques non négociables

- **Tri-state** : `UNKNOWN` n'est **jamais** implicitement `BLOCKED` ni `PASS`. Si une
  information manque, la réponse le dit.
- **CAS obligatoire** sur les écritures de configuration (`expected revision`) : un writer périmé
  échoue explicitement. Pas de last-write-wins silencieux.
- **Non destructif** : `missing`, `archive`, `deactivate` conservent identité, références et
  révision antérieure.
- **Lifecycle** : `WRITE_CHANGE` + confirmation + CAS restent obligatoires.
- Un **saved view n'est pas une vérité matérialisée** — il s'exécute contre la vérité publiée
  courante.
- Toute sortie comparable est sérialisée en JSON canonique, toute collection retournée est
  ordonnée explicitement, toute traversée est bornée et expose une **raison de troncature**.

## 5. Déléguer la version

Toute surface publique passe par `ProductMetadata.version()` / `ProductMetadata.current()`. Aucun
littéral, et aucun fichier sous `src/main/java/` ne doit contenir `0.1.0-SNAPSHOT` ni
`FALLBACK_VERSION`.

## 6. Décider si un ADR est nécessaire

Toute décision **structurelle** en demande un : nouveau port, nouvelle sous-plateforme,
déplacement d'une frontière de sécurité, nouveau transport. Choisir le fait, pas le mécanisme :
un ADR dit *pourquoi*, le test dit *quoi*.

Avant d'attribuer un numéro, lire la sortie de [live-numbers](../live-numbers/SKILL.md) : elle
donne le plus haut numéro attribué **et** signale les doublons. Un doublon de numérotation a déjà
existé. Ajouter la ligne d'index dans `docs/adr/README.md` dans le même changement — un gate
exige une ligne et une seule par record, un verdict identique des deux côtés, et un statut pris
dans le vocabulaire que l'index déclare lui-même.

## 7. Vérifier

```bash
./mvnw test -pl morpheus-architecture-tests
./mvnw dependency:analyze
```

Puis, si un gate de milestone est concerné, son quadruplet complet — voir
[milestone-quadruplet](../milestone-quadruplet/SKILL.md).

## 8. Justifier en PR

`contracts/public-surfaces.tsv`, `config/*ratchets*.properties`, `docs/openapi/*.yaml` et tout
test sous `morpheus-architecture-tests/` sont des **fichiers de gouvernance**. Toute modification
de l'un d'eux se justifie dans la description de la PR.

## Interdits

- Jamais contourner un gate en éditant le test pour qu'il passe à tort.
- Jamais supprimer une règle sans la remplacer par une équivalente ou plus stricte.
- Jamais laisser une case vide dans le manifeste.
- Jamais exposer côté MCP une capacité qui exécute du code tiers : elle reste
  `EXPLICITLY_NOT_EXPOSED`, remote-only et ADMIN.
