# Règles — Style de Code

Aucun linter n'est configuré. La cohérence vient du code existant et des tests d'architecture.

## TOUJOURS

- Utiliser des **records Java** ou des classes `final` à champs `final` dans `morpheus-domain`
- Passer par les types du domaine comme langage commun entre couches (`DomainIdentity`, `ProjectSpecificationId`, `ProviderId`…)
- Déléguer la version à `ProductMetadata.version()` / `ProductMetadata.current()` — jamais de littéral
- Valider aux **frontières système** (entrée CLI, endpoint HTTP, outil MCP) et faire confiance au code interne
- Rendre les échecs **explicites** : exception nommée (`SyncBaselineInconsistentException`) plutôt que retour silencieux
- Nommer les états de rejet explicitement (`SCAN_INCOMPLETE`, `PLUGIN_SHA256_REQUIRED`, `existing-unmanaged-morpheus-entry`)

## Anatomie d'un type domaine (exemple réel)

`DomainIdentity` illustre plusieurs règles à la fois : record immuable, validation dans le
constructeur compact, échec explicite nommé, jamais de littéral magique caché :

```java
public record DomainIdentity(UUID value) implements Comparable<DomainIdentity> {
    public DomainIdentity {
        Objects.requireNonNull(value, "value");
        if (value.version() != 7 || value.variant() != 2) {
            throw new IllegalArgumentException("DomainIdentity must be an RFC 9562 UUIDv7");
        }
    }

    public static DomainIdentity generate() {
        long unixMillis = System.currentTimeMillis() & 0x0000FFFFFFFFFFFFL;
        long randomA = RANDOM.nextInt(1 << 12);
        long mostSignificantBits = (unixMillis << 16) | (0x7L << 12) | randomA;

        long randomB = RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL;
        long leastSignificantBits = 0x8000000000000000L | randomB;

        return new DomainIdentity(new UUID(mostSignificantBits, leastSignificantBits));
    }

    public static DomainIdentity parse(String value) {
        Objects.requireNonNull(value, "value");
        return new DomainIdentity(UUID.fromString(value.trim()));
    }
}
```

À retenir de cet exemple :
- La validation d'invariant (`version() != 7`) est dans le **constructeur compact**, pas
  dans une méthode `validate()` séparée qu'on pourrait oublier d'appeler
- L'exception a un message qui nomme précisément l'invariant violé — pas
  `"invalid value"`, mais `"DomainIdentity must be an RFC 9562 UUIDv7"`
- Aucun champ mutable, aucun setter — les deux fabriques (`generate`, `parse`) sont les
  seuls points d'entrée pour construire un `DomainIdentity` valide

## JAMAIS

- Jamais de commentaire qui explique **quoi** fait le code — le nom s'en charge
- Jamais de commentaire référençant la tâche courante, un ticket, ou un appelant ("ajouté pour X", "utilisé par Y")
- Jamais d'abstraction prématurée — trois lignes similaires ne justifient pas un helper
- Jamais de gestion d'erreur pour un cas impossible
- Jamais de feature flag ni de shim de compatibilité rétroactive — modifier le code directement
- Jamais de code mort (variables inutilisées, re-exports vides, blocs commentés)
- Jamais de `last-write-wins` silencieux — un conflit se signale
- Jamais de dégradation silencieuse — préférer l'échec explicite au fallback implicite

## Déterminisme

Le projet vend du déterminisme (ADR-0039, 0043, 0045, 0053, 0056, 0085). Concrètement :

- Ordonner explicitement toute collection retournée par une requête
- Sérialiser en **JSON canonique** (`CanonicalJsonSerializer`) pour toute sortie comparable
- Ne jamais laisser un `HashMap`/`HashSet` déterminer un ordre observable
- Borner toute traversée (profondeur, nœuds, liens) et exposer une **raison de troncature**

## Sémantique tri-state

`UNKNOWN` n'est jamais silencieusement converti en `BLOCKED` ou en `PASS`.
Si une information manque, la réponse le dit. Cf. ADR-0078, ADR-0093.
