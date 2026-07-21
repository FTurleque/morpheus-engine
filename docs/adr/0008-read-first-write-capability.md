# ADR-0008 — Le cœur MORPHEUS est read-first ; l'écriture est une capacité séparée

- Statut : **Proposée — à valider pendant C0 et M0**
- Date : 22 juillet 2026
- Dépend de : ADR-0001, ADR-0002
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

Il serait tentant de rendre ces opérations obligatoires dans `SpecificationProvider` dès le départ.

Cependant, lire une source et la modifier présentent des contraintes très différentes.

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

Les capacités de lecture et de découverte constituent le socle :

```text
DISCOVER
CURRENT_SPECIFICATIONS
CHANGES
REQUIREMENTS
SCENARIOS
DESIGN
TASKS
ACCEPTANCE_CRITERIA
HISTORY
```

L'écriture est représentée comme une capacité distincte :

```text
WRITE
ARCHIVE
```

Un provider valide peut être entièrement read-only.

Aucun service métier de lecture ne doit dépendre de la présence des capacités d'écriture.

---

## 3. Raisons

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

## 4. Architecture candidate

```text
SpecificationProvider
        │
        ├── Read capabilities
        │
        └── optional Write capabilities
```

Deux modèles restent possibles à valider :

### Option A — Interface unique avec capacités

```text
SpecificationProvider
supports(WRITE)
```

### Option B — Ports séparés

```text
SpecificationReader
SpecificationWriter
```

La décision de forme API exacte est différée jusqu'à M0. L'invariant fonctionnel est déjà fixé : **l'écriture n'est pas obligatoire**.

---

## 5. Conséquences positives

- sécurité accrue ;
- MVP plus simple ;
- providers read-only possibles ;
- séparation claire des permissions ;
- testabilité ;
- absence d'effets de bord pendant l'indexation ;
- possibilité de soumettre une proposition sans l'appliquer.

---

## 6. Conséquences négatives

- une seconde abstraction pourra être nécessaire ;
- certains workflows nécessiteront deux chemins différents ;
- plus de code si un provider implémente lecture et écriture ;
- synchronisation à gérer après mutation future.

---

## 7. Alternatives étudiées

### A. Provider CRUD universel obligatoire

**Rejeté.**

Il exclurait les sources read-only et mélangerait compréhension et mutation.

### B. MORPHEUS strictement read-only pour toujours

**Non décidé.**

Cette option serait trop restrictive pour certains workflows futurs, mais peut rester le périmètre initial.

### C. Read-first + écriture optionnelle

**Retenue.**

---

## 8. Garde-fous futurs pour l'écriture

Toute capacité d'écriture devra considérer :

- intention explicite de l'utilisateur ou orchestrateur autorisé ;
- précondition sur la version source ;
- détection de conflit ;
- validation avant écriture ;
- écriture atomique lorsque possible ;
- diff observable ;
- possibilité de rollback via Git ou mécanisme provider ;
- journalisation de la mutation sans fuite de secrets ;
- resynchronisation de l'index après succès.

L'autorisation d'un agent à lire ne doit jamais impliquer automatiquement une autorisation d'écrire.

---

## 9. Validation M0

Le premier provider de référence devra fonctionner entièrement en lecture pour :

- découverte ;
- ingestion ;
- requêtes ;
- traçabilité ;
- reconstruction courant/proposé.

Une preuve de concept d'écriture n'est pas nécessaire pour valider le MVP.

Si elle est explorée, elle doit être isolée et ne pas devenir une dépendance des services de lecture.

---

## 10. Condition d'acceptation

Cette ADR passe à **Acceptée** lorsque l'API provider C0/M0 démontre qu'un provider read-only peut satisfaire l'ensemble du MVP sans implémenter de méthode de mutation obligatoire.

Toute activation de l'écriture dans un jalon fonctionnel devra préciser ses permissions, garanties de concurrence et règles de synchronisation.