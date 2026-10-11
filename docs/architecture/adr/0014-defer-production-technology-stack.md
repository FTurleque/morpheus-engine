# ADR-0014 — Différer le choix de la stack de production jusqu'aux preuves nécessaires

- Statut : **Acceptée — validation C0 du 22 juillet 2026**
- Date : 22 juillet 2026
- Portée : langage, build, frameworks, distribution et spikes M0

---

## 1. Contexte

MORPHEUS possède désormais un cadrage fonctionnel et architectural détaillé, mais aucune contrainte métier ne justifie encore définitivement :

- un langage d'implémentation ;
- une version de runtime ;
- un système de build ;
- un framework d'injection ;
- un framework CLI ;
- un framework serveur ;
- une technologie de persistance ;
- une topologie de déploiement.

Le premier prototype fonctionnel crée souvent un effet d'ancrage : une technologie choisie uniquement pour réaliser rapidement un spike devient progressivement la stack « officielle » sans décision explicite.

Le projet MINOS a déjà montré l'intérêt de ne pas figer prématurément une stack avant validation des besoins et contraintes.

MORPHEUS doit éviter la même dérive.

---

## 2. Problème

Comment autoriser M0 à produire rapidement des preuves techniques sans laisser les outils expérimentaux définir implicitement l'architecture de production ?

---

## 3. Forces en présence

### Vitesse d'expérimentation

Les spikes doivent être rapides à écrire et jeter.

### Pérennité

Le produit final doit rester maintenable plusieurs années.

### Windows local-first

L'environnement Windows est prioritaire et la distribution locale doit rester réaliste.

### Écosystème existant

Plusieurs briques de l'écosystème peuvent avoir leurs propres stacks, mais l'homogénéité ne doit pas l'emporter automatiquement sur l'adéquation technique.

### Intégration future

CLI, MCP, API et interactions avec MINOS/NEXUS/JARVIS devront être supportables.

### Portabilité

Windows et Linux doivent être raisonnablement supportables.

---

## 4. Décision

Pendant C0 :

> **aucun langage, runtime, système de build, framework serveur ou backend persistant n'est considéré comme stack de production acceptée.**

Pendant M0 :

- les spikes peuvent utiliser une technologie choisie pour la vitesse de validation ;
- chaque spike doit clairement être identifié comme expérimental ;
- les contrats de domaine ne doivent pas dépendre des bibliothèques du spike ;
- l'adoption de la stack produit nécessite une décision explicite après évaluation des contraintes réelles.

---

## 5. Distinction spike / production

### Spike

Objectif : répondre à une question technique.

Caractéristiques :

- code jetable autorisé ;
- architecture simplifiée autorisée ;
- couverture limitée à l'hypothèse ;
- aucune garantie de compatibilité future ;
- dépendances temporaires autorisées si documentées.

### Production foundation

Objectif : devenir le socle maintenu de MORPHEUS.

Caractéristiques attendues :

- stack explicitement décidée ;
- conventions de projet ;
- tests ;
- distribution ;
- sécurité ;
- observabilité ;
- stratégie de mise à jour ;
- compatibilité OS ;
- dépendances et licences examinées.

Un spike ne devient pas le socle de production par simple continuité de branche.

---

## 6. Critères pour le langage d'implémentation

Le choix devra considérer au minimum :

- qualité des bibliothèques de parsing nécessaires aux providers ;
- manipulation de Markdown/YAML/JSON ;
- performance d'ingestion ;
- consommation mémoire ;
- concurrence ;
- distribution Windows/Linux ;
- création d'un CLI ;
- implémentation MCP ;
- API future ;
- accès aux backends envisagés ;
- testabilité ;
- tooling développeur ;
- maintenance ;
- disponibilité des compétences ;
- compatibilité avec l'écosystème existant.

Aucun critère unique ne suffit à décider.

---

## 7. Critères pour le système de build

À considérer après choix du langage :

- reproductibilité ;
- dépendances ;
- packaging ;
- multi-module si nécessaire ;
- tests ;
- génération de distribution ;
- CI ;
- expérience Windows.

Un build tool ne doit pas être sélectionné avant le langage uniquement pour homogénéiser artificiellement l'écosystème.

---

## 8. Framework serveur

Le serveur API est prévu en M11 et ne justifie aucune décision C0.

La stack de domaine ne doit donc pas être structurée autour d'un framework HTTP choisi aujourd'hui.

Le MCP arrive en M10 ; lui non plus ne doit pas définir prématurément le domaine.

Conséquence :

```text
Domain / Application
       │
       ├── CLI adapter
       ├── MCP adapter future
       └── API adapter future
```

et non :

```text
Web framework
    ↓
entire architecture
```

---

## 9. Backend persistant

Le choix du backend est explicitement soumis à M0.

Les familles candidates incluent :

- relationnel embarqué ;
- documentaire ;
- graphe ;
- hybride.

Le backend mémoire est requis pour tester le port mais n'est pas un backend production par défaut.

SQLite peut être évalué parce qu'il est local et embarqué, mais cette ADR ne le sélectionne pas.

---

## 10. Distribution locale

La future stack doit permettre une expérience développeur réaliste :

```text
install
configure project
sync
query
```

sans imposer plusieurs services complexes si les besoins peuvent être satisfaits par une distribution plus légère.

Un composant externe lourd devra démontrer une valeur mesurable avant adoption.

---

## 11. Structure des spikes M0

Chaque spike doit contenir ou référencer :

```text
Hypothesis
Question
Dataset
Technology used
Reason for technology choice
Measurement protocol
Results
Limitations
Decision impact
Disposable / reusable assessment
```

Le champ `Technology used` ne constitue jamais une décision de production.

---

## 12. Réutilisation d'un spike

Du code de spike peut être réutilisé uniquement si :

1. sa stack a ensuite été explicitement acceptée ;
2. sa qualité est remise au niveau production ;
3. les tests et frontières sont conformes ;
4. aucune dépendance expérimentale indésirable ne fuite dans le domaine ;
5. la réutilisation est volontaire et documentée.

Sinon, le spike reste une preuve jetable.

---

## 13. Conséquences positives

- évite l'ancrage technologique accidentel ;
- permet des expérimentations rapides ;
- garde les options ouvertes ;
- force les décisions de stack à être justifiées ;
- protège le domaine des frameworks ;
- réduit le risque de devoir déconstruire une architecture prématurée ;
- rend les ADR technologiques plus crédibles.

---

## 14. Conséquences négatives

- la structure du repository reste plus longtemps minimaliste ;
- certains spikes peuvent être jetés ;
- un peu de travail peut être dupliqué lors du passage à la production ;
- le démarrage de l'implémentation produit intervient plus tard ;
- nécessite de résister à la tentation de « garder le prototype puisqu'il marche ».

---

## 15. Alternatives étudiées

### A. Choisir immédiatement la stack préférée du développeur

**Rejetée.**

Préférence personnelle insuffisante comme justification architecturale.

### B. Copier la stack de MINOS ou NEXUS

**Rejetée comme règle automatique.**

L'homogénéité est un avantage à mesurer, pas une contrainte absolue.

### C. Choisir la stack utilisée par le premier provider

**Rejetée.**

Le provider est un adaptateur et ne doit pas définir le cœur.

### D. Différer jusqu'aux preuves M0

**Retenue.**

---

## 16. Risques et mitigations

### Risque — analyse infinie sans choix

Mitigation : définir une porte de décision de stack après les expériences M0 nécessaires, pas reporter indéfiniment.

### Risque — spikes incompatibles entre eux

Mitigation : concentrer chaque spike sur son hypothèse et standardiser les datasets/mesures, pas nécessairement le code.

### Risque — duplication de travail

Mitigation : réutiliser seulement les composants dont les décisions ont été validées.

### Risque — choix subjectif final

Mitigation : matrice de critères avec pondération explicite lorsque la décision de stack sera prise.

---

## 17. Validation

Cette ADR ne nécessite pas un benchmark complet pour être acceptée ; elle constitue une règle de gouvernance C0.

La validation C0 a confirmé que :

- aucun document normatif ne déclare une stack produit ;
- aucun jalon C0/M0 ne dépend d'un framework particulier ;
- les expériences M0 peuvent être exécutées sans figer les contrats publics ;
- la roadmap distingue explicitement la phase de preuves de la fondation de production.

---

## 18. Critères d'acceptation

Les critères sont satisfaits à la sortie C0 :

1. le dépôt reste sans stack de production implicitement engagée ;
2. M0 est explicitement reconnu comme phase de preuves ;
3. les technologies de spike sont considérées jetables par défaut ;
4. toute adoption de langage/build/framework/backend nécessite une ADR ou une décision documentée ;
5. la compatibilité Windows/local-first fait partie des critères futurs.

Décision : **Acceptée le 22 juillet 2026.**

---

## 19. Impact sur les autres décisions

Cette ADR protège :

- ADR-0001 — domaine indépendant ;
- ADR-0003 — abstraction de store ;
- ADR-0004 — local-first ;
- ADR-0011 — providers capability-based ;
- ADR-0012 — snapshots indépendants du backend ;
- la matrice M0.

Elle doit être remplacée ou complétée lorsqu'une ADR de stack de production sera prise.
