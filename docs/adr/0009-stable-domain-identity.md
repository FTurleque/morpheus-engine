# ADR-0009 — Séparer l'identité logique, la version, l'emplacement et l'identifiant externe

- Statut : **Proposée — à valider pendant C0 et M0**
- Date : 22 juillet 2026
- Portée : modèle de domaine, ingestion, versionnement, stockage

---

## 1. Contexte

MORPHEUS ingère des sources externes dont la structure physique peut évoluer indépendamment de l'identité métier des éléments décrits.

Une exigence peut :

- être déplacée dans un autre fichier ;
- changer de titre ;
- être renommée ;
- être réécrite sans changer de sens ;
- être archivée ;
- être réintroduite ;
- posséder un identifiant externe stable ;
- ne posséder aucun identifiant explicite ;
- être décrite par plusieurs sources.

Si MORPHEUS confond identité et emplacement physique, l'historique devient instable : un simple déplacement de fichier ressemble à une suppression suivie d'une création.

À l'inverse, une stratégie trop agressive de rapprochement peut fusionner deux exigences distinctes simplement parce qu'elles se ressemblent.

---

## 2. Problème

MORPHEUS doit répondre séparément à quatre questions :

```text
Qui est cet élément ?
Dans quelle version est-il observé ?
Où est-il décrit dans la source ?
Comment le système externe l'identifie-t-il ?
```

Ces questions ne doivent pas être représentées par une seule valeur.

---

## 3. Forces en présence

### Stabilité

Les identités doivent survivre autant que possible aux mouvements purement physiques.

### Fidélité

Deux éléments réellement distincts ne doivent pas être fusionnés.

### Explicabilité

Une décision de rapprochement doit pouvoir être expliquée.

### Provider-agnostic

Le domaine ne doit pas dépendre d'une clé OpenSpec ou d'un chemin Markdown.

### Historique

Les versions successives d'une même entité doivent être reliables.

### Ingestion incrémentale

L'identité stable réduit les invalidations inutiles.

### Simplicité

Le système ne doit pas exiger un moteur complexe de record linkage dès le MVP.

---

## 4. Décision proposée

MORPHEUS distingue explicitement :

```text
DomainIdentity
EntityVersion
SourceLocator
ExternalReference
```

### `DomainIdentity`

Identité logique opaque possédée par MORPHEUS.

Exemples conceptuels :

```text
RequirementId
SpecificationId
ChangeId
ConstraintId
```

Elle ne doit pas exposer une structure dépendante du provider dans les contrats publics.

### `EntityVersion`

Identifie un état de l'entité dans un snapshot ou une version de spécification.

### `SourceLocator`

Décrit où l'élément a été observé :

```text
file
section
line/range
json pointer
other provider locator
```

### `ExternalReference`

Conserve l'identifiant ou la clé du système source lorsqu'il existe.

---

## 5. Invariants

1. un chemin de fichier seul ne constitue jamais une identité logique suffisante ;
2. un numéro de ligne ne constitue jamais une identité logique ;
3. un identifiant externe peut aider à résoudre l'identité mais ne devient pas automatiquement l'identité MORPHEUS ;
4. une même identité logique peut apparaître dans plusieurs versions ;
5. une même identité logique peut changer de locator ;
6. deux entités ambiguës ne doivent pas être fusionnées silencieusement ;
7. une collision doit produire un diagnostic ;
8. l'identité finale doit être persistable indépendamment du provider.

---

## 6. Stratégie de résolution candidate

L'identité est résolue par niveaux de confiance.

### Niveau A — Correspondance explicite

Une source fournit un identifiant stable et non ambigu déjà associé à une identité MORPHEUS.

```text
externalId -> DomainIdentity
```

Résolution : `RESOLVED`.

### Niveau B — Correspondance structurelle déterministe

La source ne fournit pas d'identifiant stable mais un ensemble de propriétés permet une correspondance non ambiguë selon une règle documentée.

Exemples possibles :

- clé de spécification + clé d'exigence ;
- namespace logique + key ;
- identifiant composite normalisé.

Résolution : `RESOLVED` ou `PARTIALLY_RESOLVED` selon la règle.

### Niveau C — Correspondance heuristique

Titre, contenu ou voisinage suggèrent une continuité mais ne la prouvent pas.

Résolution : `HEURISTIC`.

Cette correspondance ne doit pas fusionner automatiquement deux identités permanentes sans politique explicite.

### Niveau D — Nouvelle identité

Aucune correspondance suffisamment fiable n'existe.

Une nouvelle identité MORPHEUS est créée.

---

## 7. Identifiants opaques

Le format concret de `DomainIdentity` reste un détail d'implémentation.

Options possibles :

- UUID ;
- ULID ;
- identifiant numérique local ;
- hash contrôlé ;
- autre identifiant opaque.

La décision ne sera prise qu'après avoir évalué :

- portabilité ;
- génération offline ;
- fusion multi-source ;
- sérialisation ;
- lisibilité des diagnostics ;
- performance du store.

Cette ADR ne choisit donc pas encore UUID vs ULID vs autre.

---

## 8. Pourquoi ne pas exposer une identité sémantique encodée

Une identité publique de type :

```text
project/spec/file/section/title
```

semble lisible mais introduit plusieurs problèmes :

- changement dès qu'un segment est renommé ;
- fuite de détails de source ;
- encodage difficile des collisions ;
- dépendance au provider ;
- migration coûteuse si la stratégie évolue.

Une clé lisible peut exister comme `key`, mais elle reste distincte de `id`.

---

## 9. Renommage et déplacement

### Déplacement de fichier

Si un élément possède une identité explicite stable :

```text
same DomainIdentity
new SourceLocator
new EntityVersion
```

### Renommage de titre

Le titre est une propriété versionnée, pas l'identité.

### Renommage de clé externe

Cas plus délicat :

- si la source fournit une relation de renommage, conserver l'identité ;
- sinon appliquer une règle de résolution ;
- en cas d'ambiguïté, ne pas fusionner automatiquement.

---

## 10. Suppression

Lorsqu'un élément disparaît de la source :

- son identité ne doit pas être immédiatement réutilisée ;
- l'état historique peut être conservé ;
- les liens historiques doivent rester explicables ;
- la version courante ne doit plus le présenter comme actif.

Une réapparition future peut être rapprochée de l'ancienne identité uniquement selon une règle de résolution explicite.

---

## 11. Multi-provider

À terme, deux providers peuvent décrire la même réalité logique.

Exemple :

```text
OpenSpec Requirement R-12
GitHub Issue #42
```

Ils ne doivent pas devenir automatiquement une seule entité.

Le rapprochement peut être représenté par :

```text
TraceabilityLink
ExternalReference
Alias / equivalence relation future
```

La fusion d'identité doit rester une opération contrôlée et explicable.

---

## 12. Conséquences positives

- historique stable ;
- moins de faux add/delete lors des déplacements ;
- provider-agnosticisme réel ;
- meilleure ingestion incrémentale ;
- possibilité de corriger un locator sans casser les références ;
- meilleure traçabilité cross-version ;
- collisions visibles ;
- compatibilité avec plusieurs sources futures.

---

## 13. Conséquences négatives

- nécessité d'un mécanisme de résolution d'identité ;
- stockage d'un mapping externalId -> DomainIdentity ;
- cas ambigus difficiles ;
- besoin de conserver l'historique ;
- tests plus complexes ;
- identité moins lisible pour l'humain qu'une clé naturelle.

---

## 14. Alternatives étudiées

### A. Utiliser le chemin de fichier + section

**Rejetée.**

Trop instable et provider-specific.

### B. Utiliser uniquement l'identifiant externe

**Rejetée comme modèle général.**

Tous les providers n'offrent pas d'identifiant stable et un identifiant externe peut changer de sémantique ou de format.

### C. Utiliser un hash du contenu

**Rejetée comme identité principale.**

La moindre modification de texte crée une nouvelle identité alors qu'il peut s'agir de la même exigence.

Le hash reste utile comme empreinte de version ou aide au rapprochement.

### D. Identité MORPHEUS opaque + clés externes séparées

**Retenue.**

C'est la seule option qui sépare proprement domaine, source et version.

---

## 15. Risques et mitigations

### Risque — faux rapprochement

Mitigation : niveaux de résolution, ambiguïté explicite, aucune fusion heuristique silencieuse.

### Risque — explosion d'identités après renommage

Mitigation : exploiter les external IDs stables et les règles déterministes avant de créer une nouvelle identité.

### Risque — complexité utilisateur

Mitigation : exposer des `key` lisibles en plus des IDs opaques.

### Risque — migration de stratégie

Mitigation : ne pas encoder la stratégie d'identité dans les contrats publics ; prévoir version du mapping si nécessaire.

---

## 16. Validation M0

L'expérience E03 doit couvrir :

```text
move file
rename title
change external key
modify statement
duplicate requirement
delete
restore
archive
provider collision
```

Pour chaque cas, enregistrer :

```text
expected identity
observed identity
resolution state
reason
warnings
```

---

## 17. Critères d'acceptation

Cette ADR peut passer à **Acceptée** lorsque :

1. un format concret d'identifiant MORPHEUS est choisi ;
2. les règles de résolution MVP sont documentées ;
3. les scénarios E03 passent sans fusion silencieuse ambiguë ;
4. un déplacement de source ne change pas l'identité lorsque la continuité est démontrable ;
5. un backend mémoire et le backend persistant de référence implémentent les mêmes invariants ;
6. l'identité publique ne contient aucun type provider-specific.

---

## 18. Impact sur les autres décisions

Cette ADR structure :

- `SpecificationKnowledgeStore` ;
- versionnement et snapshots ;
- ingestion incrémentale ;
- traçabilité ;
- références cross-engine ;
- comparaison current/proposed ;
- historique ;
- API/CLI futures.