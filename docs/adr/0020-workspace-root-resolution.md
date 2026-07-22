# ADR-0020 — Résoudre la racine de workspace sans dépendance Git obligatoire

- Statut : **Proposée — validation M1 requise**
- Date : 22 juillet 2026
- Dépend de : ADR-0001, ADR-0002, ADR-0011, ADR-0017
- Portée : découverte de workspace, sélection de provider, chemins locaux, monorepos, provenance

---

## 1. Contexte

M1 doit découvrir de manière déterministe un workspace local et les sources de spécification qu'il contient.

Le premier vertical slice M1 accepte déjà un `Path` explicite et exécute les probes des providers sur ce chemin. Cette base ne suffit pas encore pour les cas réels :

- l'utilisateur peut lancer MORPHEUS depuis un sous-répertoire ;
- un dépôt Git peut contenir plusieurs sous-projets ;
- un workspace peut ne pas utiliser Git ;
- le chemin peut être Windows, Unix, relatif ou absolu ;
- un provider peut être présent au chemin explicite même si une racine Git existe plus haut ;
- Git ne doit pas devenir une dépendance obligatoire du cœur MORPHEUS.

---

## 2. Problème

Une stratégie naïve « remonter systématiquement jusqu'à la racine Git » casserait les monorepos.

Exemple :

```text
repo/
├── .git/
├── service-a/
│   └── openspec/
└── service-b/
```

Pour une invocation sur `repo/service-a`, la racine fonctionnelle pertinente peut être `service-a`, pas nécessairement `repo`.

À l'inverse, une stratégie « ne jamais remonter » échoue lorsqu'un utilisateur lance MORPHEUS depuis `repo/src/main/java` alors que la source de spécification est à `repo/openspec`.

---

## 3. Forces en présence

1. préserver le chemin explicitement fourni par l'utilisateur ;
2. supporter les monorepos ;
3. supporter les workspaces sans Git ;
4. ne pas exiger le binaire `git` ;
5. ne pas exécuter de commande externe pendant la discovery ;
6. conserver un comportement déterministe et explicable ;
7. garder la logique de format dans les providers ;
8. éviter une exploration récursive arbitraire du filesystem au M1 ;
9. produire une provenance/locator stable et portable entre Windows et Unix lorsque possible.

---

## 4. Décision

MORPHEUS adopte une stratégie **explicit-first avec fallback Git structurel**.

### 4.1 Chemin explicite

Le chemin demandé est :

```text
requestedPath
    -> toAbsolutePath()
    -> normalize()
```

Il doit exister et être un répertoire.

MORPHEUS exécute d'abord la découverte des providers sur ce chemin normalisé.

### 4.2 Fallback Git

Uniquement lorsqu'aucun provider compatible n'est découvert au chemin explicite, MORPHEUS recherche vers les parents le premier répertoire contenant un marqueur :

```text
.git
```

Le marqueur peut être un fichier ou un répertoire, afin de rester compatible avec les worktrees et certaines configurations Git.

Aucune commande `git` n'est exécutée.

Si une racine Git distincte est trouvée, les probes sont réessayés sur cette racine.

### 4.3 Priorité

```text
1. chemin explicite
2. racine Git ancêtre, seulement en fallback
3. échec déterministe
```

Un provider valide découvert au chemin explicite gagne donc toujours sur une racine Git située plus haut.

### 4.4 Source invalide ou reconnue mais non supportée

Le fallback Git ne doit pas masquer une source explicitement reconnue mais invalide/non supportée.

Exemples :

```text
OpenSpec config présent + schema inconnu
OpenSpec config présent mais illisible
```

Dans ces cas, MORPHEUS retourne les diagnostics du provider au chemin explicite au lieu de chercher silencieusement une autre source plus haut.

### 4.5 Workspace sans Git

Un workspace sans marqueur `.git` reste parfaitement valide. Le chemin explicite normalisé est sa racine de discovery.

---

## 5. Inventaire des sources

La discovery doit distinguer :

```text
workspace root
provider probe
specification source
```

Un provider supporté expose un locator de source suffisamment précis pour expliquer pourquoi il a matché.

Exemple OpenSpec :

```text
scheme = file
value  = openspec/config.yaml
```

Les locators fichiers relatifs sont normalisés avec `/` comme séparateur logique afin de ne pas injecter les séparateurs Windows dans les contrats persistables.

La discovery ne parse pas les exigences ou changements : elle inventorie uniquement les signatures de sources.

---

## 6. Invariants

1. Git n'est pas une dépendance obligatoire du cœur ;
2. aucune commande Git n'est nécessaire à la discovery M1 ;
3. le chemin explicite est évalué avant tout fallback ;
4. un provider valide au chemin explicite n'est jamais remplacé par un provider trouvé plus haut ;
5. une source reconnue mais invalide/non supportée n'est pas masquée par un fallback ;
6. un workspace non-Git est supporté ;
7. les providers restent propriétaires de leurs signatures de format ;
8. la couche discovery ne parse aucun concept métier OpenSpec ;
9. les résultats exposent `requestedPath`, `workspaceRoot` résolu et l'origine de cette résolution ;
10. un locator n'est jamais une `DomainIdentity`.

---

## 7. Alternatives étudiées

### A. Toujours utiliser la racine Git

**Rejetée.**

Simple mais incorrecte pour les monorepos et sous-projets autonomes.

### B. Ne jamais utiliser Git

**Rejetée.**

Trop fragile lorsqu'une commande est lancée depuis un sous-répertoire ordinaire du projet.

### C. Exécuter `git rev-parse --show-toplevel`

**Rejetée pour M1.**

Cela introduirait une dépendance au binaire Git et à l'exécution de processus externes alors qu'un simple marqueur structurel suffit au besoin actuel.

### D. Recherche récursive globale de toutes les sources

**Différée.**

Elle augmente fortement le coût, les règles d'exclusion et les ambiguïtés. M1 reste limité à la racine explicite puis au fallback Git ancêtre.

### E. Explicit-first + fallback Git structurel

**Retenue.**

Elle protège les monorepos, reste locale, déterministe et sans dépendance externe.

---

## 8. Conséquences positives

- comportement prévisible en monorepo ;
- fonctionnement sans Git installé ;
- support naturel des workspaces non-Git ;
- pas de processus externe ;
- diagnostics non masqués ;
- meilleure explicabilité du résultat de discovery ;
- locators compatibles avec la future provenance.

---

## 9. Conséquences négatives

- une racine Git détectée par marqueur n'offre pas toutes les garanties de `git rev-parse` ;
- les sousmodules/worktrees complexes pourront nécessiter des raffinements ;
- une source située dans un répertoire frère n'est pas découverte automatiquement ;
- la recherche récursive multi-source est différée.

Ces limites sont intentionnelles au M1.

---

## 10. Risques et mitigations

### Marqueur `.git` atypique

Mitigation : accepter fichier ou répertoire et tester les deux cas.

### Monorepo avec source au sous-projet et à la racine

Mitigation : le chemin explicite est évalué en premier et gagne dès qu'un provider compatible y est trouvé.

### Source invalide au sous-projet et valide à la racine

Mitigation : ne pas masquer l'erreur ; une signature reconnue avec diagnostic bloquant arrête le fallback.

### Symlinks / junctions

M1 ne promet pas de canonicalisation physique systématique par `toRealPath()`. La normalisation lexicale est la règle initiale ; les comportements spécifiques aux liens seront ajoutés uniquement sur cas démontré afin de ne pas modifier silencieusement l'identité visible des chemins.

---

## 11. Plan de validation

Tests Java obligatoires :

1. chemin relatif/absolu normalisé ;
2. répertoire inexistant -> `INVALID_SOURCE` ;
3. provider présent au chemin explicite -> aucun fallback Git ;
4. aucun provider au sous-répertoire + `.git` ancêtre + provider à la racine -> fallback réussi ;
5. workspace sans Git + provider local -> succès ;
6. OpenSpec reconnu mais schema non supporté au chemin explicite -> pas de fallback ;
7. marqueur `.git` fichier accepté ;
8. locator OpenSpec = `file:openspec/config.yaml` ;
9. résultat déterministe sur exécutions répétées ;
10. ArchUnit confirme l'absence de dépendance adapter -> domaine inversée.

Gate :

```text
.\mvnw.cmd clean test
```

sous Windows, baseline `release=21`.

---

## 12. Critère d'acceptation

ADR-0020 peut passer à **Acceptée — M1** lorsque les tests démontrent :

```text
explicit-first
+ Git fallback sans binaire Git
+ workspace non-Git
+ source locator provider-neutral
+ diagnostics non masqués
+ BUILD SUCCESS
```

---

## 13. Impact

Cette décision affine la discovery M1 sans modifier :

- le domaine métier M2 ;
- la sémantique des providers ;
- la stratégie de persistance ;
- les snapshots ;
- la traçabilité fonctionnelle.

Elle prépare directement `SourceLocator`, `SpecificationSource` et la future provenance, tout en maintenant :

```text
DomainIdentity != SourceLocator
```
