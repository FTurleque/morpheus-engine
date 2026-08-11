# §8 — Concepts transverses

> **Sources actives** : `pom.xml`, ADR, code du HEAD `develop`, preuves R3/D2,
> `morpheus-architecture-tests/` et contrats publics.

---

## 8.1 Identité

MORPHEUS sépare explicitement :

```text
DomainIdentity
EntityVersionId
SourceLocator
ExternalReference
```

`DomainIdentity` est une identité logique opaque et stable. Un chemin, une
version ou un identifiant externe ne devient jamais implicitement l'identité
métier.

---

## 8.2 Authentification et autorisation

### Local

Les surfaces locales restent local-first. Le serveur HTTP local est contraint
par sa politique de host/loopback ; le MCP natif utilise STDIO et n'ouvre pas de
port réseau.

### Remote opt-in

Le mode serveur d'équipe ajoute une frontière réseau contrôlée :

- HTTPS/TLS ;
- authentification Bearer ;
- rôles `READ`, `WRITE`, `ADMIN` ;
- séparation `authentication != authorization` ;
- concurrence bornée ;
- racines de workspace autorisées.

Le mode remote ne fournit pas un IAM entreprise complet dans la baseline 1.2.0.

---

## 8.3 Sécurité des entrées et du filesystem

Le hardening D2 renforce les frontières qui traitent des données non fiables :

| Frontière | Mécanisme |
|-----------|-----------|
| Workspaces HTTP/remote | `AllowedWorkspaceRoots`, politique de host et résolveurs sûrs |
| Fichiers workspace | `SafeWorkspaceFileResolver` et refus des évasions hors racine |
| Ingestion provider | budgets explicites (`ProviderIngestionBudget`) et erreurs bornées |
| JSON | baseline Jackson 3.1.5 + tests de régression de profondeur / parsing |
| Plugins externes | intégrité JAR et activation explicite |
| SQLite | accès transactionnels, checksums de migrations, sécurité du fichier |

Aucune règle de sécurité ne doit dépendre d'une simple convention documentaire
lorsqu'elle peut être testée ou imposée par le code.

---

## 8.4 Données et snapshots

Invariants structurants :

```text
SpecificationVersion != KnowledgeSnapshot
PROPOSED never leaks into CURRENT
candidate != published snapshot
activation == atomic
conflict != silent last-write-wins
cross-project identity != source path
```

SQLite est le backend persistant initial. Les migrations sont versionnées et
vérifiées ; leur numéro courant est une propriété d'implémentation et n'est pas
figé dans cette documentation d'architecture.

---

## 8.5 Interfaces et versionnement

| Surface | Contrat stable |
|---------|---------------|
| HTTP | URI versionnée `/api/v1` |
| MCP | serveur natif STDIO basé sur le SDK Java MCP 2.0.0 |
| CLI | surface locale versionnée par le produit et ses contrats |
| Provider SDK | contrats d'extension derrière `morpheus-provider-sdk` |
| SQLite | migrations forward-only contrôlées par le schema manager |

La valeur **2.0.0** est la version du SDK MCP utilisé par le build ; elle ne
doit pas être présentée comme un numéro de version du protocole MORPHEUS.

---

## 8.6 Gestion des erreurs

| Situation | Comportement attendu |
|-----------|----------------------|
| Provider partiellement lisible | Résultat partiel / diagnostic explicite selon contrat |
| MINOS ou NEXUS indisponible | Dégradation explicite sans perte des faits locaux |
| Transition lifecycle invalide | Aucun changement d'état ; décision/erreur explicite |
| Révision obsolète lors d'une mutation | Pas d'écrasement silencieux |
| Migration SQLite incohérente | Échec explicite plutôt que réparation silencieuse |
| Input externe hors budget | Refus borné et déterministe |
| Workspace hors racines autorisées | Refus avant accès au contenu |

---

## 8.7 Résilience et concurrence

- SQLite utilise WAL lorsque requis par la configuration du store.
- Les opérations applicatives conservent des frontières transactionnelles
  explicites.
- Le mode remote borne la concurrence et peut retourner une saturation
  explicite plutôt que dégrader silencieusement.
- Les intégrations externes sont optionnelles et fault-isolated.
- Les backups et le restore offline sont des opérations distinctes du runtime
  normal.

---

## 8.8 Configuration

Les paramètres sensibles ou structurants doivent être explicites : arguments,
variables d'environnement, options de lancement ou fichiers d'état possédés par
MORPHEUS selon la fonctionnalité. Cette documentation ne présuppose pas un
unique mécanisme universel de configuration.

Pour le câblage MCP client, l'ownership est enregistré et l'écrasement d'une
entrée étrangère est interdit.

---

## 8.9 Observabilité et preuves

MORPHEUS privilégie des sorties structurées et des preuves reproductibles :

- endpoints d'opérabilité définis par le contrat HTTP ;
- résultats et warnings structurés sur les surfaces publiques ;
- rapports de tests et JaCoCo ;
- SBOM CycloneDX ;
- provenance de build / release ;
- checksums et manifests d'artefacts ;
- preuves exact-head Windows + Linux selon le gate concerné.

Le choix de logging doit préserver la pureté de stdout en mode MCP STDIO.

---

## 8.10 Supply chain

Baseline 1.2.0 :

```text
Java                  21
Maven Wrapper         3.9.16
sqlite-jdbc           3.53.2.0
Jackson BOM           3.1.5
MCP SDK Java          2.0.0
JUnit                 6.1.0
ArchUnit              1.4.2
JaCoCo                0.8.15
CycloneDX plugin      2.9.2
Dependency-Check      12.2.2
```

Le build applique également une analyse Maven bloquante des dépendances
déclarées. Les versions autoritatives restent celles de `pom.xml`.

---

## 8.11 Déploiement, upgrade et rollback

```text
program != persistent state
upgrade != reset knowledge store
schema migration != application rollback
logical snapshot rollback != schema rollback
backup != live mutation
```

Une restauration de base est une opération offline explicite. Les migrations de
schéma sont forward-only ; revenir à un ancien binaire nécessite donc de tenir
compte de la compatibilité de la base et des backups disponibles.

---

## 8.12 Qualification

Le pipeline public actuel exécute le gate d'intégrité M21 paramétré pour la
version 1.2.0 sur Ubuntu et Windows. Les preuves historiques/spécialisées M22 à
M28 ainsi que D2 restent conservées dans `docs/validation/` et ne sont pas
remplacées par le seul workflow CI.
