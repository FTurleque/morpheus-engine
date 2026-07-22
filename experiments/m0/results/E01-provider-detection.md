# E01 — Provider detection

Statut : **PASS**

Date : 22 juillet 2026

## Hypothèse

MORPHEUS peut détecter une source de spécifications supportée, exposer ses capacités effectives et sélectionner un provider de manière déterministe sans confondre présence du format, capacités réelles et permissions.

## Datasets

```text
experiments/m0/fixtures/openspec-basic
experiments/m0/fixtures/openspec-unsupported-schema
experiments/m0/fixtures/synthetic-basic
```

## Environnement d'exécution

```text
Python 3.13.5
Linux container
standard library only for spike runtime
```

La technologie est expérimentale conformément à ADR-0014 et ne constitue pas une décision de stack de production.

## Protocole exécuté

```text
python -m unittest -v
```

Suite E01/E02 complète :

```text
Ran 15 tests
15 PASS
0 FAIL
```

Sous-ensemble E01 : **9 tests PASS**.

## Résultats E01

### Source OpenSpec supportée

Le provider détecte :

```text
provider = openspec
schema = spec-driven
format_version = null
supported = true
```

`format_version = null` est volontaire : le `config.yaml` OpenSpec testé expose un **schema de workflow** et le spike n'invente pas un numéro de format absent de la source.

### Capabilities effectives

```text
DISCOVER_PROJECT
READ_CURRENT_SPECIFICATIONS
READ_CHANGES
READ_REQUIREMENTS
READ_SCENARIOS
READ_DESIGN_DECISIONS
READ_IMPLEMENTATION_TASKS
READ_HISTORY
READ_ARCHIVES
```

Le provider ne revendique notamment pas :

```text
READ_ACCEPTANCE_CRITERIA
WRITE_CHANGE
WRITE_TASK_STATE
ARCHIVE_CHANGE
```

La présence de `Scenario` ne suffit pas à produire automatiquement la sémantique MORPHEUS `AcceptanceCriterion`.

### Schéma OpenSpec non pris en charge

Une fixture utilisant un schéma différent de `spec-driven` produit :

```text
UNSUPPORTED_PROVIDER_SCHEMA
```

Le résultat ne prétend pas qu'un schéma custom est invalide dans OpenSpec ; il indique seulement que **ce provider expérimental MORPHEUS ne sait pas encore l'interpréter**.

### Source absente

```text
NO_PROVIDER_FOUND
```

### Plusieurs providers candidats

Deux providers possédant les mêmes capabilities produisent un gagnant déterministe.

À score égal :

```text
local > remote
puis provider_id lexical pour départage stable
```

Le diagnostic :

```text
MULTIPLE_PROVIDER_MATCHES
```

reste visible.

### Provider explicite incompatible

```text
EXPLICIT_PROVIDER_INCOMPATIBLE
```

Une configuration explicite ne transforme jamais une incompatibilité en succès silencieux.

### Capability obligatoire manquante

```text
MISSING_REQUIRED_CAPABILITY
```

Aucun provider insuffisant n'est sélectionné.

### Capability préférée manquante

L'opération peut continuer, mais expose :

```text
OPTIONAL_CAPABILITY_UNAVAILABLE
```

avec la liste des capabilities absentes.

### Provider distant

Sans opt-in :

```text
REMOTE_PROVIDER_REQUIRES_OPT_IN
```

Avec opt-in explicite, le provider peut devenir candidat.

### Provider read-only

Le provider OpenSpec du spike satisfait E01/E02 sans aucune capability d'écriture.

## Conclusion

Les critères E01 sont couverts :

- [x] découverte d'une source supportée ;
- [x] schéma détecté ;
- [x] absence de version de format représentée explicitement plutôt qu'inventée ;
- [x] schéma non supporté diagnostiqué ;
- [x] capabilities effectives ;
- [x] absence de provider ;
- [x] plusieurs providers candidats ;
- [x] provider explicite incompatible ;
- [x] required capability manquante ;
- [x] optional capability manquante ;
- [x] provider read-only ;
- [x] provider distant avec opt-in ;
- [x] politique de sélection déterministe.

## Impact ADR

### ADR-0011 — Capability negotiation

**Preuve positive forte.**

E01 démontre que le contrat capability-based permet :

- de ne pas exagérer les capacités du provider ;
- de représenter une dégradation optionnelle ;
- de bloquer une opération sans capacité obligatoire ;
- de conserver une préférence local-first ;
- d'exiger un opt-in pour un provider distant ;
- de sélectionner de manière déterministe.

ADR-0011 reste toutefois `Proposée` jusqu'à validation des autres fixtures/contrats prévues par ses critères d'acceptation M0.

### ADR-0002 — Provider de référence

OpenSpec reste un candidat viable pour le provider de référence sur le schéma `spec-driven`.

## Décision

```text
E01 = PASS
CONTINUE_PROVIDER_STRATEGY
```

La négociation par capacités est conservée pour les expériences suivantes.
