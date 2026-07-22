# ADR-0008 — Le cœur MORPHEUS est read-first ; l'écriture est une capacité séparée

- Statut : **Proposée — à valider pendant C0 et M0**
- Date : 22 juillet 2026
- Dépend de : ADR-0001, ADR-0002
- Complétée par : ADR-0011
- Portée : contrat des providers et sécurité des mutations

---

## 1. Contexte

MORPHEUS doit d'abord comprendre et interroger les spécifications existantes.

Certains providers permettent ou permettront également de :

- créer un changement ;
- modifier une exigence ;
- mettre à jour une tâche ;
- archiver un changement ;
- réécrire une spécification.

Lire une source et la modifier présentent des contraintes très différentes.

L'écriture introduit notamment :

- risque de corruption ;
- gestion de concurrence ;
- respect d'invariants propres au format ;
- gestion de Git ;
- conflits ;
- atomicité ;
- rollback ;
- permissions ;
- sécurité d'un agent autonome.

---

## 2. Décision proposée

Le premier contrat provider est **read-first**.

Les capacités de lecture et de découverte forment le socle :

```text
DISCOVER_PROJECT
READ_CURRENT_SPECIFICATIONS
READ_CHANGES
READ_REQUIREMENTS
READ_CONSTRAINTS
READ_SCENARIOS
READ_DESIGN_DECISIONS
READ_ACCEPTANCE_CRITERIA
READ_IMPLEMENTATION_TASKS
READ_HISTORY
READ_ARCHIVES
```

Les capacités de mutation sont séparées :

```text
WRITE_CHANGE
WRITE_TASK_STATE
ARCHIVE_CHANGE
```

Un provider valide peut être entièrement read-only.

Aucun service métier de lecture ne doit dépendre de la présence des capacités d'écriture.

ADR-0011 définit la négociation de ces capacités et leur caractère effectif selon la source et la version du format.

---

## 3. Invariants

1. une lecture ne doit jamais modifier la source ;
2. `READ_CHANGES` n'implique jamais `WRITE_CHANGE` ;
3. `READ_IMPLEMENTATION_TASKS` n'implique jamais `WRITE_TASK_STATE` ;
4. `READ_ARCHIVES` n'implique jamais `ARCHIVE_CHANGE` ;
5. un provider read-only doit pouvoir satisfaire l'ensemble du MVP ;
6. toute mutation future requiert une intention explicite et des permissions ;
7. un succès d'écriture doit être confirmable par la source ou une preuve adaptée.

---

## 4. Raisons

### Sécurité

Comprendre une spécification ne doit jamais modifier sa source par effet de bord.

### Compatibilité

Certains providers ou sources seront naturellement en lecture seule.

### Simplicité du MVP

Le MVP doit prouver la valeur de compréhension avant de résoudre tous les problèmes de mutation.

### Agents IA

Une lecture peut être accordée largement alors qu'une mutation nécessite des garde-fous et une intention explicite.

### Formats externes

Chaque format peut imposer ses propres invariants de réécriture.

---

## 5. Architecture candidate

Deux formes API restent possibles :

### Option A — Interface capability-based

```text
SpecificationProvider
  supports(WRITE_CHANGE)
  supports(ARCHIVE_CHANGE)
```

### Option B — Ports séparés

```text
SpecificationReader
SpecificationWriter
```

La forme exacte est différée jusqu'à M0.

L'invariant fonctionnel est déjà proposé : **l'écriture n'est jamais obligatoire**.

---

## 6. Conséquences positives

- sécurité accrue ;
- MVP plus simple ;
- providers read-only possibles ;
- séparation claire des permissions ;
- testabilité ;
- absence d'effets de bord pendant l'ingestion ;
- possibilité de comprendre ou proposer sans appliquer ;
- meilleure compatibilité avec le local-first.

---

## 7. Conséquences négatives

- une seconde abstraction pourra être nécessaire ;
- certains workflows nécessiteront deux chemins ;
- plus de code pour les providers lecture + écriture ;
- synchronisation obligatoire après mutation future ;
- nécessité de définir une politique de conflits.

---

## 8. Alternatives étudiées

### A. Provider CRUD universel obligatoire

**Rejeté.**

Il exclurait les sources read-only et mélangerait compréhension et mutation.

### B. MORPHEUS strictement read-only pour toujours

**Non décidé.**

Possible à long terme mais potentiellement trop restrictif pour certains workflows orchestrés.

### C. Read-first + écriture optionnelle

**Retenue.**

---

## 9. Garde-fous futurs pour l'écriture

Toute capacité d'écriture devra considérer :

- intention explicite de l'utilisateur ou orchestrateur autorisé ;
- précondition sur la version source ;
- détection de conflit ;
- validation MORPHEUS avant écriture ;
- capacité provider explicite ;
- écriture atomique lorsque possible ;
- diff ou preview observable ;
- rollback via Git ou mécanisme provider lorsque disponible ;
- journalisation de la mutation sans fuite de secrets ;
- resynchronisation après succès ;
- confirmation de l'état réellement observé.

L'autorisation de lire ne doit jamais impliquer l'autorisation d'écrire.

---

## 10. Risques et mitigations

### Risque — divergence après écriture

Mitigation : réingestion ou confirmation provider avant publication du nouveau snapshot.

### Risque — agent sur-privilégié

Mitigation : permissions séparées et opt-in explicite des capabilities de mutation.

### Risque — provider partiellement transactionnel

Mitigation : version precondition, preview, diagnostics et stratégie de récupération propre au provider.

### Risque — contrats lecture/écriture trop couplés

Mitigation : tests démontrant qu'un provider read-only satisfait tous les cas d'usage MVP.

---

## 11. Validation M0

Le premier provider de référence doit fonctionner entièrement en lecture pour :

- découverte ;
- ingestion ;
- requêtes ;
- traçabilité ;
- reconstruction courant/proposé ;
- lecture de l'historique disponible.

Une preuve de concept d'écriture n'est pas nécessaire pour valider le MVP.

Si elle est explorée, elle doit être isolée et ne devenir une dépendance d'aucun service de lecture.

---

## 12. Critères d'acceptation

Cette ADR peut passer à **Acceptée** lorsque :

1. un provider read-only satisfait tous les cas d'usage MVP ;
2. la taxonomie de capacités d'ADR-0011 est validée ;
3. aucun contrat de lecture n'exige une méthode de mutation ;
4. les permissions d'écriture restent séparables ;
5. une future stratégie d'écriture peut être ajoutée sans modifier le domaine de lecture.

Toute activation fonctionnelle de l'écriture dans un jalon ultérieur devra faire l'objet d'une ADR précisant permissions, concurrence, conflits, confirmation et rollback.