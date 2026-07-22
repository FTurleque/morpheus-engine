# E03b — UUIDv7 as concrete `DomainIdentity` format

Statut : **PASS**

Date : 22 juillet 2026

## Objectif

Compléter E03 en choisissant et vérifiant un format concret d'identité opaque sans choisir la stack de production.

## Références

- RFC 9562 pour UUIDv7 ;
- `docs/research/domain-identity-format.md` pour la comparaison UUIDv4 / UUIDv7 / ULID ;
- ADR-0015 pour la décision proposée.

## Spike

```text
experiments/m0/spikes/e03b_uuidv7_identity_python/
├── uuid7_identity.py
└── test_uuid7_identity.py
```

L'implémentation de génération est uniquement un **test de conformité M0**. La stack produit devra utiliser une implémentation conforme adaptée à son runtime.

## Scénarios exercés

### Vecteur RFC

Le vecteur UUIDv7 utilisé par RFC 9562 est reconnu comme :

```text
version = 7
timestamp = 1645557742000 ms
```

et effectue un round-trip vers la représentation UUID canonique.

### Génération locale

Le générateur expérimental construit un UUIDv7 avec :

```text
48 bits timestamp milliseconds
version 7
variant UUID
74 bits randomness
```

sans réseau ni coordination centrale.

### Ordre inter-milliseconde

À aléa contrôlé identique :

```text
UUIDv7(t+1ms) > UUIDv7(t)
```

ce qui confirme la disposition temporelle des bits de poids fort.

### Unicité expérimentale

Un contrôle M0 a généré :

```text
10 000 UUIDv7
même milliseconde forcée
10 000 valeurs distinctes
```

Ce contrôle n'est pas une preuve mathématique d'absence de collision ; il confirme seulement que le mécanisme expérimental ne présente pas de défaut évident de génération.

### Store mémoire

Un requirement portant le même UUIDv7 à travers deux snapshots avec contenu modifié conserve exactement le même `domain_id`.

Le store ne dérive aucune sémantique depuis la valeur.

### Store SQLite

Le même UUIDv7 est conservé :

- après écriture ;
- après fermeture/réouverture ;
- après publication d'un nouveau snapshot modifiant le contenu de l'entité.

### Opacité

La chaîne UUIDv7 n'encode explicitement :

```text
ni provider
ni locator
ni type métier
ni titre
```

Le timestamp techniquement encodé ne doit pas être utilisé comme `createdAt` ou version métier.

## Impact ADR-0009

E03 + E03b couvrent maintenant :

- règles de continuité ;
- ambiguïtés/collisions ;
- séparation locator/content/external ID ;
- format concret ;
- conservation dans les deux stores ;
- absence de format provider-specific.

ADR-0009 peut être réévaluée vers `Acceptée` lors de la revue M0.

## Impact ADR-0015

Les conditions de validation M0 sont couvertes.

Décision proposée :

```text
DomainIdentity = UUIDv7
public serialization = canonical lowercase UUID string
semantic interpretation = opaque
```

## Décision

```text
E03b = PASS
DOMAIN_ID_FORMAT = UUIDv7
UUIDV7_TIMESTAMP_AS_DOMAIN_FACT = REJECT
```
