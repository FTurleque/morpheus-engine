# ADR-0074 — Mapping projet NEXUS explicite sans ownership de lifecycle

- Statut : **Proposée — M13 gate pending**
- Date : 24 juillet 2026
- Dépend de : ADR-0007, ADR-0073
- Portée : M13 — identité du projet technique NEXUS

## Décision proposée

Chaque demande de contexte M13 transporte explicitement :

```text
nexusProject = UUID ou nom unique du projet NEXUS
```

MORPHEUS ne déduit pas ce mapping depuis le chemin workspace, le nom du projet MORPHEUS ou une heuristique.

## Lifecycle

M13 n'appelle aucun workflow NEXUS de mutation :

```text
project add
index
rebuild
remove
```

Le registre et l'index NEXUS restent administrés par NEXUS ou par un orchestrateur externe.

## Conséquences

- aucune collision de nom silencieuse ;
- aucune indexation coûteuse déclenchée par une lecture MORPHEUS ;
- le même projet MORPHEUS peut viser différents projets NEXUS selon l'appel ;
- l'absence ou l'état non indexé du projet NEXUS est un fait externe explicite.

## Critères d'acceptation

1. `nexusProject` obligatoire sur toute construction augmentée ;
2. aucune heuristique de mapping ;
3. aucun appel de mutation/indexation NEXUS ;
4. projet absent retourné comme indisponibilité/erreur externe explicite ;
5. tests prouvent que seul `list_projects`/`build_context`/`explain_context` est requis.