# Étude — Format de `DomainIdentity`

Statut : **Conclusion M0 proposée : UUIDv7**

Date : 22 juillet 2026

## 1. Objectif

ADR-0009 impose de séparer :

```text
DomainIdentity
EntityVersion
SourceLocator
ExternalReference
```

Il reste à choisir un format concret de `DomainIdentity` qui soit :

- opaque ;
- générable hors ligne ;
- indépendant du provider ;
- indépendant du backend ;
- indépendant du langage ;
- sérialisable dans CLI / MCP / API ;
- raisonnablement efficace comme clé de stockage ;
- sûr pour la génération concurrente ;
- utilisable dans un futur contexte multi-projets.

Le format ne doit encoder :

- ni chemin ;
- ni titre ;
- ni provider ;
- ni type métier ;
- ni hash de contenu ;
- ni identifiant MINOS/NEXUS/JARVIS.

---

## 2. Candidats étudiés

### UUIDv4

Avantages :

- standard UUID ;
- génération locale simple ;
- très large support ;
- 122 bits pseudo-aléatoires disponibles ;
- aucune information métier encodée.

Inconvénient principal :

- distribution purement aléatoire, moins favorable à la localité d'index et à l'ordre d'insertion.

### UUIDv7

RFC 9562 définit UUIDv7 comme un UUID fondé sur le temps Unix en millisecondes.

Structure utile à MORPHEUS :

```text
48 bits  Unix timestamp milliseconds
4 bits   version = 7
2 bits   UUID variant
74 bits  randomness / monotonicity space
```

Avantages :

- standard UUID ;
- 128 bits ;
- ordre temporel naturel dans les bits de poids fort ;
- génération hors ligne ;
- pas de coordination centrale ;
- représentation UUID standard ;
- compatible avec les types UUID natifs de nombreux stores ;
- meilleur comportement potentiel pour les index ordonnés qu'un UUIDv4 aléatoire ;
- indépendant du provider.

Inconvénients :

- la valeur révèle approximativement le moment de génération de l'identité ;
- toutes les runtimes ne fournissent pas encore un générateur UUIDv7 natif ;
- la monotonicité dans une même milliseconde dépend de l'implémentation.

### ULID

La spécification ULID définit :

```text
48 bits timestamp milliseconds
80 bits randomness
```

avec une représentation canonique Base32 sur 26 caractères, lexicalement triable.

Avantages :

- compact en texte ;
- URL-safe ;
- lexicalement triable ;
- 128 bits ;
- génération locale ;
- nombreuses implémentations.

Inconvénients :

- format et écosystème distincts d'UUID ;
- pas de type SQL/driver standard universel dédié comparable au type UUID ;
- spécification communautaire plutôt qu'un standard IETF ;
- ajoute un choix d'encodage spécifique alors que UUIDv7 fournit maintenant des propriétés voisines dans le standard UUID.

### Identifiant numérique local

Rejeté comme identité publique générale :

- nécessite coordination ou allocation par store ;
- fusion multi-store/multi-projet plus délicate ;
- risque de couplage au backend ;
- moins adapté à une création offline distribuée.

### Hash déterministe

Rejeté comme identité principale :

- changement du contenu ou du locator peut changer le hash ;
- encourage à confondre identité et version ;
- collisions sémantiques/faux rapprochements difficiles ;
- ne résout pas les cas où deux objets distincts possèdent momentanément le même contenu.

---

## 3. Décision proposée

Utiliser **UUIDv7** comme format canonique de `DomainIdentity`.

Représentation publique textuelle :

```text
xxxxxxxx-xxxx-7xxx-yxxx-xxxxxxxxxxxx
```

avec la représentation UUID canonique en minuscules aux frontières JSON/CLI/API.

Le stockage interne pourra utiliser :

- un type UUID natif ;
- 16 octets ;
- une chaîne canonique ;

selon le backend, sans modifier le contrat du domaine.

---

## 4. Invariant d'opacité

Même si UUIDv7 contient une composante temporelle, les consommateurs doivent traiter `DomainIdentity` comme **opaque**.

Interdit :

```text
extraire le timestamp de l'ID pour déterminer createdAt
utiliser l'ordre UUID pour déterminer une version métier
utiliser l'ID pour déduire CURRENT / PROPOSED / HISTORICAL
utiliser l'ID pour déduire le provider
```

Les informations métier restent portées par des champs explicites.

---

## 5. Pourquoi UUIDv7 plutôt qu'ULID

ULID répond techniquement à une grande partie du besoin.

UUIDv7 est préféré parce que :

1. il appartient au standard UUID actuel ;
2. il conserve une représentation 128 bits très interopérable ;
3. il bénéficie des types UUID déjà présents dans de nombreux outils et bases ;
4. il apporte l'ordre temporel sans créer un format d'identité spécifique à MORPHEUS ;
5. la différence de longueur textuelle n'est pas critique pour un identifiant technique opaque.

ULID reste une alternative raisonnable si une contrainte de runtime ou d'interopérabilité future invalide UUIDv7.

---

## 6. Pourquoi UUIDv7 plutôt qu'UUIDv4

UUIDv4 reste parfaitement valide en unicité.

UUIDv7 est préféré pour :

- la localité temporelle ;
- l'ordre naturel des créations ;
- le comportement attendu des index ordonnés ;
- les futures analyses/debugs où une distribution entièrement aléatoire n'apporte pas d'avantage particulier.

La création temporelle encodée ne devient toutefois jamais une sémantique métier.

---

## 7. Sécurité et confidentialité

UUIDv7 révèle approximativement le temps de génération.

Pour MORPHEUS, cette propriété est considérée acceptable parce que :

- les identifiants sont des clés techniques, pas des secrets ;
- les dates de création et de version sont déjà des métadonnées normales du moteur ;
- aucun nom de projet, provider, utilisateur ou contenu n'est encodé ;
- l'ID ne doit pas être utilisé comme jeton d'autorisation ou secret d'accès.

Une API publique future ne doit jamais considérer l'imprédictibilité d'un ID comme mécanisme de sécurité.

---

## 8. Compatibilité stack

Le choix du **format** UUIDv7 ne choisit pas :

- Java ;
- Go ;
- Python ;
- une librairie précise ;
- SQLite ;
- un framework.

La future stack produit devra sélectionner une implémentation conforme à RFC 9562 ou fournir une implémentation minimale testée par vecteurs de conformité.

---

## 9. Tests requis

Avant fondation M1 :

- génération locale sans réseau ;
- validation version=7 et variant UUID ;
- unicité sur un lot représentatif ;
- round-trip string ↔ représentation native ;
- conservation exacte dans store mémoire ;
- conservation exacte dans backend persistant ;
- aucun changement d'identité lors d'un changement de locator/titre/contenu lorsque la continuité E03 est démontrée.

---

## 10. Sources primaires

- RFC 9562 — *Universally Unique IDentifiers (UUIDs)*, RFC Editor / IETF.
- `ulid/spec` — spécification canonique ULID.

---

## 11. Conclusion

Décision proposée :

```text
DomainIdentity format = UUIDv7
public representation = canonical UUID string
semantic interpretation = opaque
provider content encoded = none
backend dependency = none
```
