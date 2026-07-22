# Validation C0 — MORPHEUS

Statut : **C0 VALIDÉE — passage en M0 autorisé**

Date : 22 juillet 2026

---

## 1. Décision

La phase **C0 — Cadrage fonctionnel et architectural** est considérée comme suffisamment complète et cohérente pour autoriser l'ouverture de **M0 — Faisabilité technique**.

Cette validation signifie que MORPHEUS dispose désormais d'un cadre suffisamment précis pour expérimenter sans redéfinir à chaque spike :

- la nature du produit ;
- ses non-objectifs ;
- ses frontières avec MINOS, NEXUS et JARVIS ;
- son modèle de domaine candidat ;
- la distinction entre état courant, proposé et historique ;
- le cycle de vie des changements ;
- la traçabilité ;
- la provenance et les preuves ;
- le modèle de providers ;
- l'abstraction de stockage ;
- les snapshots de connaissance ;
- le principe read-first ;
- le fonctionnement local-first ;
- le périmètre MVP ;
- les cas d'usage prioritaires ;
- les expériences nécessaires pour trancher les hypothèses techniques.

La validation C0 **ne signifie pas** que les hypothèses dépendantes d'une expérimentation deviennent automatiquement des décisions techniques acceptées.

---

## 2. Principe de validation des ADR

Les ADR sont classées en deux catégories.

### 2.1 Décisions validables par le cadrage seul

Une ADR peut être acceptée pendant C0 lorsqu'elle constitue une règle de gouvernance ou une frontière architecturale dont les critères d'acceptation sont déjà satisfaits par les documents de cadrage.

### 2.2 Décisions nécessitant une preuve M0

Lorsqu'une ADR exige explicitement :

- un provider réel ;
- un prototype ;
- un backend ;
- des tests contractuels ;
- un benchmark ;
- une preuve d'isolation ;
- une mesure de performance ;
- une validation sur fixture réelle ;

elle reste **Proposée** jusqu'à production de la preuve correspondante.

La sortie de C0 n'est donc pas conditionnée par l'acceptation prématurée de ces ADR.

---

## 3. Décisions acceptées à la sortie C0

### ADR-0014 — Différer le choix de la stack de production

**Décision : ACCEPTÉE.**

Les conditions C0 sont satisfaites :

- aucune stack de production n'est engagée ;
- M0 est explicitement défini comme phase de preuves ;
- les technologies de spike sont considérées expérimentales ;
- toute adoption de langage, build, framework ou backend devra faire l'objet d'une décision explicite ;
- Windows et le fonctionnement local-first font partie des critères de choix futurs.

Cette ADR devient la règle de gouvernance technique pour M0.

---

## 4. ADR maintenues au statut Proposée pendant M0

Les ADR suivantes restent volontairement **Proposées**, car leurs propres critères d'acceptation requièrent des preuves M0 ou ultérieures :

| ADR | Sujet | Preuve principale attendue |
|---|---|---|
| ADR-0001 | domaine indépendant des providers | isolation démontrée par provider réel + provider de test |
| ADR-0002 | OpenSpec provider de référence | ingestion fidèle du corpus et absence de fuite OpenSpec |
| ADR-0003 | `SpecificationKnowledgeStore` | backend mémoire + backend persistant + tests contractuels |
| ADR-0004 | local-first / sans LLM obligatoire | vertical slice MVP hors réseau |
| ADR-0005 | traçabilité first-class | parcours et relations sur corpus réel |
| ADR-0006 | current / proposed / historical | reconstruction fiable sur corpus réel |
| ADR-0007 | intégrations cross-engine découplées | preuve de résolution externe optionnelle |
| ADR-0008 | read-first / écriture séparée | provider MVP entièrement read-only |
| ADR-0009 | identité stable | scénarios E03 et format concret d'identifiant |
| ADR-0010 | taxonomie des relations | mappings et traversées cohérentes sur backends |
| ADR-0011 | négociation de capacités | sélection déterministe sur fixtures |
| ADR-0012 | snapshots versionnés | activation atomique et idempotence sur deux stores |
| ADR-0013 | machine d'état des changements | mapping provider et règles d'étapes facultatives |

Le statut `Proposée` ne remet pas en cause leur rôle d'hypothèse structurante pour les spikes ; il interdit seulement de les présenter comme techniquement prouvées avant les expériences.

---

## 5. Décisions explicitement ouvertes pendant M0

Les choix suivants restent ouverts :

1. langage d'implémentation de production ;
2. runtime ;
3. système de build ;
4. framework CLI ;
5. framework serveur ;
6. format concret de `DomainIdentity` ;
7. backend persistant initial ;
8. nécessité d'un graph store ;
9. stratégie incrémentale exacte ;
10. politique de rétention des snapshots ;
11. forme finale des ports de lecture/écriture ;
12. politique des étapes facultatives du cycle de vie ;
13. composition multi-provider ;
14. recherche sémantique.

Aucun de ces choix ne doit être figé par la simple technologie utilisée dans un spike.

---

## 6. Porte d'entrée M0

M0 peut commencer avec la matrice :

```text
E01 provider detection
E02 domain mapping
E03 stable identity
E04 current reconstruction
E05 snapshots
E06 traceability
E07 memory store
E08 persistent store
E09 optional graph store
E10 lexical search
E11 incremental ingestion
E12 diagnostics
E13 compact context
E14 external references
```

Chaque expérience doit documenter :

```text
hypothèse
question
jeu de données
environnement
technologie utilisée
raison du choix expérimental
mesures
résultats
limites
impact sur les ADR
décision proposée
```

---

## 7. Seuils de performance

Les seuils chiffrés définitifs ne sont pas inventés pendant C0.

M0 doit d'abord produire une **baseline reproductible**, puis fixer des seuils motivés à partir :

- du corpus de référence ;
- de volumes représentatifs ;
- de l'environnement Windows local-first prioritaire ;
- des coûts mémoire/disque ;
- des besoins de latence des CLI, agents et futures intégrations.

L'absence de seuil arbitraire à la sortie C0 est donc volontaire et ne bloque pas M0.

---

## 8. Conclusion

> **C0 est validée. MORPHEUS entre en M0 — Faisabilité technique.**

M0 n'a pas pour objectif de construire immédiatement le produit complet. Il doit transformer les hypothèses structurantes de C0 en décisions étayées par des preuves reproductibles.
