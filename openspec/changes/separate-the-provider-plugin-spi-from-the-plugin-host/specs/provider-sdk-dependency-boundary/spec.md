## ADDED Requirements

### Requirement: Le SPI publié aux auteurs de plugins NE DOIT contenir que les types destinés aux plugins

Le SPI provider publié aux auteurs de plugins DOIT (MUST) se limiter au contrat qu'un plugin implémente et aux types
qu'il lit (métadonnées, version d'API). Découverte, compatibilité, activation, probe, worker, service et projections
des transports NE DOIVENT (MUST NOT) pas y être déclarés ; le build DOIT échouer en nommant le type sinon.

#### Scenario: Le SPI ne porte que les types d'un plugin

- **GIVEN** l'arbre après la séparation
- **WHEN** la suite d'architecture énumère les types publics du SPI
- **THEN** elle trouve exactement les types qu'un plugin implémente ou lit, et aucun type de découverte, de probe ni de
  projection

#### Scenario: Un type hôte ajouté au SPI fait échouer le build

- **GIVEN** une classe publique de projection remote déclarée dans le SPI
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme le type
- **AND** aucun fichier n'est déplacé par le test

### Requirement: Un plugin NE DOIT dépendre que du SPI, jamais de l'hôte de plugins

Aucune classe de `morpheus-provider-reference` ni de `morpheus-provider-testkit` NE DOIT (MUST NOT) dépendre d'un type
de l'hôte de plugins, et le build DOIT (MUST) échouer en nommant les deux classes sinon, pour les classes que la suite
importe ; un module hors de l'ensemble importé est tenu par son POM.

#### Scenario: Le plugin de référence compile contre le seul SPI

- **GIVEN** l'arbre après la séparation
- **WHEN** la suite d'architecture s'exécute
- **THEN** le plugin de référence ne dépend d'aucun type de l'hôte et la règle passe

#### Scenario: Un plugin qui appelle le service du probe fait échouer le build

- **GIVEN** une classe du plugin de référence qui référence `ProviderPluginService`
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme les deux classes
- **AND** aucun fichier n'est réécrit
