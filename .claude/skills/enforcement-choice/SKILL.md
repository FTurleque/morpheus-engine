---
name: enforcement-choice
description: Choisir entre une règle ArchUnit et une assertion textuelle pour enforcer un invariant MORPHEUS, et prouver que la règle tient avant de l'accepter. À utiliser avant d'ajouter une frontière de module, un interdit de dépendance ou un invariant de sécurité, quand une assertion assertFalse(source.contains(...)) semble être de la dette à migrer, ou avant de regrouper plusieurs assertions dans une seule méthode de test.
---

# Règle ArchUnit ou assertion textuelle

`morpheus-architecture-tests` enforce la plupart des interdits de dépendance par
`assertFalse(<source lue>.contains("Type"))` alors qu'ArchUnit est déjà une dépendance déclarée
du module. **Ce n'est pas de la dette à migrer par réflexe.** Le *pourquoi* est dans
`docs/adr/0103-textual-assertions-and-archunit-rules-enforce-different-things.md`.

## D'abord : écrire l'intention en une phrase

- « Aucune dépendance vers X » → **ArchUnit**.
- « Ce nom ne doit pas apparaître ici » → **texte**.
- Dans le doute, **garder les deux**.

Les deux mécanismes n'enforcent pas la même proposition, et aucun ne domine l'autre :

| | `assertFalse(src.contains("X"))` | règle ArchUnit |
|---|---|---|
| Interdit | la **mention** de `X` | la **dépendance** compilée |
| Dépendance indirecte, ou non épelée | ratée | **vue** |
| Référence à une constante de compilation | **vue** | ratée — `javac` l'inline |
| Portée | tout `src/main/java` | classpath de `morpheus-architecture-tests` |

## Les deux asymétries, mesurées sur ce dépôt

1. **ArchUnit rate les constantes de compilation.** `javac` inline les références
   `static final` String et primitives : une classe qui écrit `Autre.UNE_CONSTANTE` porte la
   valeur et **aucune** référence à la classe. Avant de migrer un interdit, compter les
   constantes inlinables de la cible ; s'il y en a, garder aussi le texte.
2. **`contains` produit des faux positifs.** Un littéral court est un préfixe de famille et
   attrape des types voisins qui n'ont rien à voir. Le texte rate de vraies dépendances **et**
   en signale d'imaginaires ; une règle fait exactement l'inverse.

## Trois obligations avant d'accepter une règle migrée

1. **Vérifier que la classe visée est dans l'ensemble importé.** Plusieurs modules du réacteur
   n'y sont pas. Le garde-fou `archRule.failOnEmptyShould` est actif et fait échouer une règle
   qui ne retient aucune classe — **ne pas le désactiver** : sans lui, une règle pointée hors
   classpath passe à vide.
2. **Vérifier qu'un littéral n'est pas un préfixe de famille.** Un `contains` sur un préfixe
   interdit toute une famille de types, pas un seul.
3. **Casser la règle pour prouver qu'elle tient** : introduire la violation, constater l'échec,
   revenir en arrière. Une règle vide passe aussi.

## Ce qui reste textuel par nature — ne pas le migrer

- Les interdits de chaîne scannés sur **tout le dépôt** : ils dépassent le classpath ArchUnit,
  qui n'inclut pas tous les modules.
- Les expressions de **câblage explicite** : aucune règle sur le bytecode ne dit quel argument
  un constructeur a reçu.
- Tout ce qui vise un `.yml`, `.ps1`, `.md`, `.iss`, `.sh`, `.xml`, `.tsv` : le texte y est la
  seule prise.

## Généraliser par capacité, jamais par famille entière

Une règle « toute la famille » échoue dès la première exception légitime, et l'affaiblir pour la
faire passer supprime l'invariant pour tous les autres membres. Le patron qui tient est dans
`HttpRoutesTransportBoundaryArchitectureTest` : des groupes **listés par nom**, et un test de
classification qui refuse un membre non classé, doublement classé ou disparu. Un nouveau membre
est refusé tant que personne ne l'a rangé.

`HttpRoutesFamilyArchitectureTest` porte à l'inverse les interdits vérifiés sur **chaque**
membre : n'y ajouter qu'un interdit dont on a vérifié qu'il vaut pour tous.

> `rules/architecture.md` décrit ces deux classes en prose, et cette prose a déjà menti d'une
> révision entière. **Relire la classe avant de se fier à sa description** — les noms de
> constantes de groupe sont la seule prise fiable.

## Découper par intention, pas par classe

Regrouper plusieurs assertions dans une méthode fait descendre le compte de méthodes `@Test`, et
`architectureTestsMinimum` ne se baisse pas. Découper par intention l'augmente : un test qui
portait deux intentions en produit plusieurs.

Attention à la grandeur : les ratchets de présence comparent la **somme des exécutions Surefire**
(`target/surefire-reports/TEST-*.xml`), pas un compte d'annotations. Ne jamais calculer une marge
avec un `grep -c '@Test'`, et lire la marge réelle dans un relevé de validation.

## Anatomie d'une règle de couche

```java
@Test
void applicationMustNotDependOnAdapters() {
    noClasses()
            .that().resideInAPackage("com.morpheus.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.morpheus.provider..", "com.morpheus.store..", "com.morpheus.cli..",
                    "com.morpheus.mcp..", "com.morpheus.api..", "com.morpheus.integration..",
                    "com.minos..", "com.nexus..", "com.jarvis..")
            .because("application services define use cases and ports without depending on adapters")
            .check(classes);
}
```

Le `because(...)` n'est pas décoratif : c'est le message qui apparaît dans le rapport de test en
cas de violation. Le rédiger comme la phrase qu'on voudrait lire six mois plus tard.

Règle transverse → `LayerDependencyTest`. Règle de sous-plateforme → un `*ArchitectureTest`
dédié au milestone. **Si ArchUnit ne le vérifie pas et qu'aucune assertion textuelle ne le
vérifie non plus, la règle n'existe pas.**
