# ADR-0004 — Le cœur MORPHEUS est local-first et ne dépend d'aucun LLM

- Statut : **Proposée — à valider pendant C0**
- Date : 22 juillet 2026
- Portée : sécurité, confidentialité et dépendances d'exécution

---

## 1. Contexte

Les spécifications d'un projet peuvent contenir des informations sensibles : architecture future, exigences métier, contraintes internes, décisions stratégiques, vulnérabilités connues, noms de clients ou plans non publiés.

MORPHEUS doit également être utilisé par des agents IA. Il serait tentant de confier au LLM des opérations fondamentales comme l'extraction d'exigences, la classification ou la recherche.

Cette dépendance rendrait toutefois le moteur :

- non déterministe ;
- dépendant d'un fournisseur ;
- plus coûteux ;
- moins utilisable hors ligne ;
- potentiellement incompatible avec des dépôts confidentiels.

---

## 2. Décision proposée

Le fonctionnement de base de MORPHEUS doit être :

- **local-first** ;
- **sans LLM obligatoire** ;
- **sans service cloud obligatoire** ;
- **indépendant du fournisseur IA**.

Les capacités essentielles suivantes doivent fonctionner sans IA générative :

```text
discovery
ingestion
normalization
storage
versioning
traceability
current/proposed separation
structured querying
compact context output
```

---

## 3. Ce que « local-first » signifie

Par défaut :

1. les fichiers de spécification restent sur la machine ;
2. aucune donnée n'est envoyée vers une API externe ;
3. l'index et les métadonnées peuvent être reconstruits localement ;
4. les providers réseau sont opt-in ;
5. la télémétrie externe n'est pas requise ;
6. le fonctionnement hors ligne est possible après installation des dépendances locales nécessaires.

Local-first ne signifie pas que toute intégration réseau est interdite. Elle doit être explicite et optionnelle.

---

## 4. Place future des LLM

Des capacités IA pourront être ajoutées sous forme d'extensions ou providers spécialisés, par exemple :

- proposition de liens de traçabilité ;
- résumé de changements ;
- détection heuristique de contradictions ;
- génération assistée de brouillons ;
- recherche sémantique.

Ces résultats devront être clairement identifiés comme dérivés ou heuristiques.

Un LLM ne doit jamais devenir la seule source de vérité pour un fait déterministe disponible dans les spécifications.

---

## 5. Conséquences positives

- confidentialité accrue ;
- fonctionnement sur dépôts privés ;
- réduction des coûts IA ;
- comportement reproductible ;
- meilleure testabilité ;
- indépendance fournisseur ;
- possibilité d'utilisation CI hors réseau ;
- séparation nette entre faits et inférences.

---

## 6. Conséquences négatives

- certaines fonctions avancées seront plus difficiles sans IA ;
- extraction depuis du texte libre moins fiable ;
- nécessité de privilégier des providers structurés ;
- éventuelle duplication locale de modèles ou index pour les fonctions sémantiques futures.

---

## 7. Alternatives étudiées

### A. LLM obligatoire pour toute ingestion

**Rejetée.**

Incompatible avec les exigences de confidentialité, reproductibilité et autonomie.

### B. Service SaaS central comme backend

**Rejeté comme prérequis.**

Peut devenir un mode de déploiement futur optionnel.

### C. Cœur déterministe + extensions IA optionnelles

**Retenu.**

---

## 8. Sécurité

Les implémentations devront notamment considérer :

- masquage des secrets dans les logs ;
- exclusions configurables ;
- stockage local protégé selon les capacités OS ;
- absence de contenu complet dans la télémétrie ;
- consentement explicite avant appel à un provider externe ;
- signalement clair des données envoyées lorsqu'une intégration cloud est activée.

---

## 9. Tests de conformité

Avant acceptation :

1. exécuter le vertical slice MVP sans réseau ;
2. vérifier qu'aucune clé API n'est requise ;
3. vérifier qu'aucun appel externe n'est effectué par défaut ;
4. exécuter les tests principaux sans LLM ;
5. documenter toute dépendance nécessitant un téléchargement initial.

---

## 10. Condition d'acceptation

Cette ADR passe à **Acceptée** lorsque le MVP prouve que toutes les fonctions obligatoires fonctionnent en mode local et qu'aucun contrat du domaine n'exige un type ou service LLM.

Toute future fonctionnalité nécessitant obligatoirement un service distant devra faire l'objet d'une décision explicite et ne pourra pas modifier le comportement par défaut sans nouvelle ADR.