# ADR-0016 — Utiliser Java 25 comme runtime de production MORPHEUS

- Statut : **Proposée — décision de sortie M0**
- Date : 22 juillet 2026
- Dépend de : ADR-0014
- Portée : langage, runtime, packaging, maintenance

---

## 1. Contexte

M0 a volontairement utilisé Python pour produire rapidement des preuves fonctionnelles.

ADR-0014 interdit explicitement de transformer la technologie du premier spike en fondation de production par inertie.

La sortie de M0 exige maintenant un langage/runtime maintenable pour :

- le domaine riche MORPHEUS ;
- les providers ;
- le stockage local ;
- la CLI ;
- les futurs MCP/API ;
- Windows et Linux ;
- une durée de vie longue.

---

## 2. Décision proposée

Adopter :

```text
Language = Java
Production baseline = Java 25
```

Le cœur de production MORPHEUS sera implémenté en Java 25.

Les outils ou spikes de recherche peuvent utiliser d'autres langages lorsqu'ils restent hors runtime produit.

---

## 3. Pourquoi Java 25

JDK 25 est disponible en GA depuis septembre 2025 et est une release LTS chez la plupart des vendors.

MORPHEUS étant un nouveau projet sans contrainte legacy de bytecode/runtime, il n'existe pas de raison fonctionnelle de cibler une LTS plus ancienne par défaut.

Java apporte :

- types explicites pour le domaine ;
- records/sealed types lorsque pertinents ;
- excellente intégration IDE ;
- JDBC ;
- outillage de tests mature ;
- bibliothèques Markdown/YAML/JSON ;
- packaging multiplateforme ;
- écosystème adapté à CLI/API/MCP futurs ;
- cohérence avec l'environnement de développement existant.

---

## 4. Forces en présence

### Typage du domaine

MORPHEUS manipule de nombreux concepts orthogonaux : identités, versions, temporal states, lifecycle states, relations, evidence, diagnostics.

Un langage fortement typé réduit le risque de transformer ces dimensions en dictionnaires ou chaînes interchangeables.

### Windows local-first

Le runtime doit fonctionner de manière fiable sur Windows sans imposer Docker ou WSL.

### Maintenance

Le projet doit rester lisible et testable sur plusieurs années.

### Écosystème

L'homogénéité avec d'autres composants Java est un avantage, mais elle n'est pas la seule justification.

### Performance

Les mesures M0 n'indiquent aucun besoin nécessitant un langage système plus bas niveau.

---

## 5. Alternatives

### Go

**Non retenu pour la fondation initiale.**

Très bon choix pour CLI et distribution binaire, mais le gain opérationnel ne compense pas ici :

- un nouvel écosystème à maintenir ;
- moins d'alignement avec les composants existants ;
- aucun besoin de concurrence/performance démontré qui rende Go nécessaire.

Go reste techniquement viable si une contrainte future de distribution invalide Java.

### Python

**Conservé pour les spikes, non retenu comme runtime produit.**

Sa rapidité de prototypage a été très utile en M0, mais ce bénéfice ne suffit pas à justifier :

- packaging/runtime supplémentaire ;
- garanties runtime plus faibles sur un domaine fortement structuré ;
- distribution Windows plus délicate.

### Java 21

**Alternative compatible mais non retenue comme baseline.**

MORPHEUS démarre après la disponibilité de Java 25 et ne possède aucune contrainte d'exécution exigeant Java 21.

---

## 6. Invariants

1. le domaine ne dépend d'aucun framework Java ;
2. Java 25 ne signifie pas Spring/Quarkus ;
3. aucune preview feature n'est nécessaire à la fondation ;
4. les contrats du domaine restent sérialisables indépendamment du runtime ;
5. les providers et stores restent des adapters ;
6. les tests de fixtures M0 doivent être portés en Java avant M1 ;
7. les spikes Python restent des preuves, pas une seconde implémentation officielle à maintenir.

---

## 7. Conséquences positives

- domaine fortement typé ;
- très bon support IDE ;
- maintenance cohérente ;
- accès JDBC mature ;
- Windows/Linux réalistes ;
- écosystème de tests robuste ;
- capacité d'évoluer vers CLI/MCP/API sans réécrire le cœur.

---

## 8. Conséquences négatives

- runtime/package plus lourd qu'un binaire Go ;
- nécessité de définir une stratégie de distribution du JRE ou de jpackage/jlink plus tard ;
- temps de démarrage et mémoire potentiellement supérieurs à Go ;
- certaines bibliothèques devront être évaluées pour Java 25.

---

## 9. Risques et mitigations

### Packaging Windows

Mitigation : prévoir une expérimentation de distribution avant stabilisation CLI ; le développement ne dépend pas d'un JDK global chez l'utilisateur final à terme.

### Dépendance à des frameworks

Mitigation : fondation plain Java, ports/adapters, aucune logique domaine dans les adapters.

### Usage prématuré de features preview

Mitigation : ne pas autoriser `--enable-preview` dans le build de production sans ADR dédiée.

---

## 10. Critères d'acceptation

Cette ADR peut être acceptée à la sortie M0 lorsque :

- les contraintes M0 ne montrent aucun besoin incompatible avec Java ;
- SQLite/JDBC reste viable ;
- le build est défini séparément ;
- la distribution Windows reste une exigence explicite ;
- aucune dépendance des spikes Python n'est requise par le runtime.

Ces conditions sont satisfaites par l'état M0 actuel.

---

## 11. Impact

Cette ADR permet de démarrer la fondation M1 et déclenche des décisions séparées sur :

- Maven ;
- modules/packages ;
- tests ;
- SQLite JDBC ;
- parsing OpenSpec ;
- CLI future.

Elle ne choisit ni framework web, ni framework DI, ni bibliothèque CLI.
