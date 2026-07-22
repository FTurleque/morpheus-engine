# ADR-0015 — Utiliser UUIDv7 comme format canonique de `DomainIdentity`

- Statut : **Proposée — à valider pendant M0**
- Date : 22 juillet 2026
- Dépend de : ADR-0009, ADR-0014
- Portée : identité, sérialisation, stockage, intégrations futures

---

## 1. Contexte

ADR-0009 impose de distinguer l'identité logique MORPHEUS de :

- la version de l'entité ;
- son emplacement source ;
- son identifiant externe ;
- son contenu ;
- son titre.

E03 a confirmé cette sémantique sur les scénarios de déplacement, renommage, suppression/restauration, archivage et collisions.

Il reste à choisir un format concret pour l'identité opaque elle-même.

Ce choix doit rester indépendant :

- du provider ;
- du backend ;
- du langage de production ;
- de MINOS/NEXUS/JARVIS ;
- du contenu de l'entité.

---

## 2. Problème

MORPHEUS a besoin d'identifiants :

- générables localement et hors ligne ;
- uniques sans coordinateur central ;
- sérialisables de manière stable ;
- adaptés à des stores locaux et futurs systèmes distribués ;
- opaques pour les consommateurs ;
- suffisamment standardisés pour éviter un format propriétaire.

---

## 3. Forces en présence

### Interopérabilité

CLI, MCP, API, stores et intégrations cross-engine doivent pouvoir transporter l'identifiant sans dépendre d'une librairie MORPHEUS spécifique.

### Local-first

La génération ne doit nécessiter ni base centrale ni service réseau.

### Stockage

Un format 128 bits compatible avec les types UUID usuels est souhaitable.

### Ordre d'insertion

Une certaine localité temporelle est favorable aux index ordonnés et au diagnostic.

### Opacité

L'identifiant ne doit pas devenir une clé sémantique encodant le provider, le chemin ou le type métier.

### Standardisation

Un standard largement documenté est préférable à une convention propriétaire.

---

## 4. Décision proposée

Adopter **UUIDv7** comme format canonique de `DomainIdentity`.

Représentation publique :

```text
xxxxxxxx-xxxx-7xxx-yxxx-xxxxxxxxxxxx
```

sous forme UUID canonique en minuscules.

Le format est défini par RFC 9562.

Le domaine considère la valeur comme **opaque**.

---

## 5. Invariants

1. `DomainIdentity` est créé une fois puis conservé tant que la continuité logique est démontrée.
2. UUIDv7 ne remplace pas `createdAt`.
3. L'ordre des UUID ne remplace pas `EntityVersion` ni `SpecificationVersion`.
4. Le timestamp encodé ne détermine aucun état métier.
5. Aucun provider, chemin, titre ou contenu n'est encodé dans l'identifiant.
6. Le backend peut utiliser une représentation native/binaire différente tant que le round-trip produit le même UUID canonique.
7. Un identifiant n'est jamais un secret ni un mécanisme d'autorisation.

---

## 6. Structure UUIDv7 pertinente

RFC 9562 définit UUIDv7 avec :

```text
48 bits  Unix timestamp en millisecondes
4 bits   version = 7
12 bits  rand_a / monotonicity
2 bits   variant UUID
62 bits  rand_b / monotonicity
```

La valeur reste sur 128 bits.

La future implémentation de production devra utiliser une source d'aléa adaptée et une stratégie conforme à la RFC pour les créations dans une même milliseconde.

---

## 7. Alternatives étudiées

### A. UUIDv4

**Non retenu comme premier choix.**

Avantages : simplicité, standardisation, très large support.

Inconvénient : distribution totalement aléatoire sans localité temporelle, alors que MORPHEUS crée naturellement des séries d'entités lors des ingestions.

UUIDv4 reste un fallback techniquement valide si une contrainte forte de runtime rend UUIDv7 disproportionné.

### B. ULID

**Alternative crédible mais non retenue.**

ULID fournit également 128 bits, un timestamp milliseconde et une représentation lexicalement triable compacte.

UUIDv7 est préféré parce qu'il apporte désormais des propriétés similaires à l'intérieur du standard UUID et profite de l'écosystème de types UUID existant.

### C. Identifiant numérique local

**Rejeté comme identité générale.**

Il introduirait une allocation liée au store et compliquerait les créations offline/multi-store.

### D. Hash du contenu ou du locator

**Rejeté.**

Il confondrait identité, version et emplacement, en contradiction directe avec ADR-0009 et E03.

### E. Format MORPHEUS propriétaire

**Rejeté.**

Aucun besoin métier ne justifie la création d'un nouveau standard d'identifiant.

---

## 8. Conséquences positives

- standard UUID actuel ;
- génération locale ;
- 128 bits ;
- sérialisation standard ;
- compatibilité avec de nombreux types UUID de bases/ORM/drivers ;
- bonne localité temporelle potentielle ;
- pas de coordination centrale ;
- aucun couplage provider ;
- pas de format spécifique MORPHEUS.

---

## 9. Conséquences négatives

- le temps approximatif de génération est observable dans l'ID ;
- certaines runtimes peuvent nécessiter une librairie ou une petite implémentation dédiée ;
- ordre strict intra-milliseconde dépend de la stratégie de génération ;
- représentation texte plus longue qu'ULID ;
- il faut empêcher les consommateurs d'utiliser le timestamp comme fait métier implicite.

---

## 10. Risques et mitigations

### Risque — dépendance à une librairie immature

**Mitigation :** choisir l'implémentation uniquement après la stack produit et tester des vecteurs RFC.

### Risque — utilisation du timestamp comme `createdAt`

**Mitigation :** conserver `createdAt` explicitement et documenter l'opacité du domaine ID.

### Risque — ordre trompeur

**Mitigation :** tout tri métier utilise des champs explicites, pas l'ordre UUID seul.

### Risque — fuite temporelle

**Mitigation :** accepter cette propriété pour des IDs techniques non secrets ; réévaluer uniquement si une future surface publique l'interdit.

---

## 11. Validation M0

Le spike doit démontrer :

1. génération totalement locale ;
2. version UUID = 7 ;
3. variant UUID conforme ;
4. extraction du timestamp uniquement comme test de conformité, pas comme contrat métier ;
5. round-trip de chaîne canonique ;
6. conservation exacte par le store mémoire ;
7. conservation exacte par SQLite candidat ;
8. conservation de l'identité à travers une modification d'entité/snapshot ;
9. aucune dépendance provider dans la chaîne.

---

## 12. Critères d'acceptation

Cette ADR peut passer à **Acceptée** lorsque :

- la conformité structurelle UUIDv7 est testée ;
- les deux stores M0 préservent exactement la valeur ;
- ADR-0009 conserve sa sémantique de continuité ;
- aucune stack de production n'est choisie implicitement par la librairie du spike.

---

## 13. Impact

Cette décision complète ADR-0009 et influence :

- modèles `RequirementId`, `ChangeId`, etc. ;
- snapshots ;
- stores ;
- sérialisation JSON ;
- CLI/MCP/API ;
- références cross-engine ;
- futures migrations multi-projets.

Elle ne décide pas le format des identifiants externes fournis par les providers.

---

## 14. Référence de recherche

Voir : [`../research/domain-identity-format.md`](../research/domain-identity-format.md).
