# ADR-0018 — Utiliser SQLite comme backend persistant initial derrière `SpecificationKnowledgeStore`

- Statut : **Proposée — décision de sortie M0**
- Date : 22 juillet 2026
- Dépend de : ADR-0003, ADR-0012, ADR-0016
- Portée : persistance locale initiale, snapshots, traçabilité

---

## 1. Contexte

MORPHEUS a besoin d'une persistance locale pour :

- snapshots ;
- spécifications normalisées ;
- identités ;
- versions ;
- changements ;
- relations de traçabilité ;
- diagnostics et métadonnées utiles.

C0 a explicitement interdit de coupler le domaine à un produit de stockage.

M0 a donc évalué :

- un store mémoire ;
- SQLite comme candidat persistant ;
- la nécessité éventuelle d'un graph store.

---

## 2. Résultats M0

E08 a démontré sur SQLite :

- création locale sans service ;
- persistance après redémarrage ;
- transactions ;
- activation atomique de snapshots ;
- rejet d'un predecessor obsolète ;
- idempotence ;
- comparaison de snapshots ;
- requêtes simples.

E06b a démontré :

- persistance des relations de traçabilité ;
- traversée directe/inverse ;
- profondeur 3 ;
- conservation des références non résolues.

E09 n'a montré aucun besoin mesuré imposant une graph database au MVP.

---

## 3. Décision proposée

Adopter SQLite comme **backend persistant initial de production** derrière :

```text
SpecificationKnowledgeStore
```

Pour Java :

```text
JDBC adapter
```

avec un driver SQLite maintenu et multiplateforme.

Cette décision ne rend pas SQLite visible dans :

- le domaine ;
- les services applicatifs ;
- les DTO publics ;
- les contrats MCP/API.

---

## 4. Important : le schéma du spike est rejeté comme schéma de production

E08 stocke volontairement un payload JSON complet afin de valider rapidement les invariants de snapshots.

Cette approche est utile comme spike mais ne devient pas le schéma cible.

Le schéma de production devra distinguer au minimum les familles :

```text
projects
knowledge_snapshots
specifications
requirements
changes
constraints
scenarios
design_decisions
acceptance_criteria
implementation_tasks
traceability_links
external_references
provenance / evidence
```

La forme exacte sera conçue pendant la fondation M1 à partir des contrats validés.

---

## 5. Pourquoi SQLite

### Local-first

Un fichier local suffit ; aucun serveur de base n'est requis.

### Transactions

SQLite fournit les primitives nécessaires à une activation cohérente des snapshots.

### Portabilité

Le driver Java candidat fournit des binaires pour Windows, Linux et macOS.

### Distribution

SQLite évite d'imposer Docker ou un service séparé pour le MVP.

### Reconstructibilité

Le store reste une projection reconstruisible ; un fichier SQLite peut être supprimé puis régénéré depuis les sources.

### Requêtes de graphe bornées

Les traversées actuellement nécessaires restent compatibles avec une modélisation relationnelle et des index adaptés.

---

## 6. Pourquoi pas une graph database au MVP

Le domaine de traçabilité est naturellement graphique, mais cela ne signifie pas que le stockage doit être une graph database.

E09 a montré qu'une traversée profondeur 3 sur un corpus synthétique de dizaines de milliers d'arêtes reste très peu coûteuse avec SQLite et des index adaptés.

Une graph database introduirait :

- un service supplémentaire ;
- installation/configuration ;
- lifecycle opérationnel ;
- nouvelles dépendances ;
- nouveaux problèmes de distribution Windows ;

sans valeur mesurée suffisante aujourd'hui.

Décision :

```text
Graph model = yes
Graph database mandatory = no
```

---

## 7. Alternatives

### PostgreSQL

**Non retenu pour le backend local initial.**

Excellent backend serveur mais disproportionné pour le mode local-first du MVP.

Pourrait devenir un adapter futur en mode serveur/multi-utilisateur.

### FalkorDB / Neo4j / autre graph DB

**Non retenu au MVP.**

À réévaluer uniquement sur limites mesurées.

### Fichiers JSON uniquement

**Rejeté comme store principal.**

Insuffisant pour transactions, indexation, requêtes structurées et migrations robustes.

### Store mémoire seulement

**Rejeté comme backend utilisateur.**

Conservé comme implémentation de référence/test.

---

## 8. Driver Java

Le candidat actuel est Xerial SQLite JDBC.

Critères :

- JDBC standard ;
- natives Windows/Linux/macOS disponibles ;
- distribution Maven ;
- projet maintenu en suivant les versions SQLite ;
- intégration possible avec Java 25.

La version exacte doit être verrouillée dans le POM lors de la création du socle et mise à jour par maintenance normale.

Le choix du driver peut être remplacé sans modifier le domaine si un problème apparaît.

---

## 9. Migrations

Le schéma SQLite de production doit être versionné.

Exigences :

- migration explicite ;
- migration testée sur copie ;
- version de schéma stockée ;
- rollback ou restauration documentée selon le type de migration ;
- reconstruction depuis les sources toujours possible pour les données dérivées.

Le mécanisme précis de migration fera l'objet de la fondation M1.

---

## 10. Invariants

1. SQLite reste un adapter ;
2. aucune API SQL dans le domaine ;
3. snapshot actif atomique au niveau observable ;
4. `DomainIdentity` conservé exactement ;
5. liens de traçabilité associés au snapshot ;
6. références non résolues conservées ;
7. store reconstructible ;
8. migrations explicites ;
9. fonctionnement sans réseau ;
10. graph store réévaluable ultérieurement sans refonte du domaine.

---

## 11. Conséquences positives

- installation minimale ;
- très bon fit local-first ;
- transactions ;
- fichier sauvegardable ;
- requêtes SQL/indexes ;
- Windows/Linux ;
- pas de service externe ;
- coût opérationnel faible ;
- backend mémoire reste disponible pour les tests.

---

## 12. Conséquences négatives

- moins adapté à une forte concurrence multi-writer ;
- certaines traversées complexes seraient plus naturelles en graphe ;
- schéma relationnel à concevoir et migrer ;
- driver JNI/natif selon l'implémentation Java choisie ;
- éventuelle migration nécessaire si MORPHEUS devient un service collaboratif centralisé.

---

## 13. Risques et mitigations

### Concurrence future

Mitigation : ne pas promettre un usage multi-writer serveur avec le backend MVP ; conserver le port de stockage.

### Traversées complexes futures

Mitigation : benchmarks et adapter graphe spécialisé si nécessaire.

### Corruption/fichier local

Mitigation : transactions, sauvegarde, reconstruction depuis sources, vérifications d'intégrité.

### Driver natif

Mitigation : tests Windows/Linux et version explicitement verrouillée.

---

## 14. Critères d'acceptation

Cette ADR peut être acceptée à la sortie M0 lorsque :

- E08 est PASS ;
- E06b confirme la traçabilité persistée ;
- E05 confirme snapshots/reconstruction ;
- E09 ne démontre pas la nécessité d'un graph store ;
- Java/JDBC est retenu ;
- SQLite reste caché derrière le port.

Ces conditions sont satisfaites par les preuves M0 actuelles.

---

## 15. Critères de réévaluation

Réouvrir cette décision si :

- volume ou latence dépassent les objectifs ;
- concurrence multi-utilisateur devient prioritaire ;
- traversées complexes deviennent dominantes ;
- distribution du driver devient problématique ;
- un mode serveur central devient le déploiement principal.

---

## 16. Décision de sortie M0

```text
Initial persistent backend = SQLite
Memory backend = contract test/reference
Dedicated graph store = not required for MVP
Storage boundary = SpecificationKnowledgeStore
```
