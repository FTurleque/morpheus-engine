---
id: ARCH-NNNN
title: "Titre court qui énonce la décision"
domain: architecture
rules: false
---

# Titre court qui énonce la décision

- Statut : **Proposée**
- Date : AAAA-MM-JJ

Copier ce fichier en `docs/architecture/adr/{PRÉFIXE}-{NNNN}-titre-en-kebab-case.md` : préfixe du domaine
(`architecture` → `ARCH`, `backend` → `BE`, `data` → `DATA`, `general` → `GEN`, `security` → `SEC`) et numéro qui
suit le plus haut numéro attribué, registre et archive compris. L'en-tête YAML reste en ligne 1 ; `id` reprend le début
du nom de fichier, le titre `#` et la ligne d'index reprennent exactement `title`. `rules: true` seulement avec un
fichier compagnon `{même nom}.rules.ts` dont la règle a été vue en échec ; `files` borne une règle qui ne regarde
qu'une partie du dépôt. Un ADR qui en remplace d'autres ajoute `- Remplace : ADR-XXXX` sous la date. Passer d'abord
le filtre de `.claude/rules/adr.md`, puis retirer ce paragraphe.

## Context

Faits vérifiés (`fichier:ligne`, tests, mesures), contraintes, options considérées.

## Decision

Ce qui est décidé, au présent ; ce qui est exclu.

## Do's and Don'ts

### Do

- Ce que la décision impose, en une phrase vérifiable.

### Don't

- Ce que la décision interdit.

## Consequences

### Positives

- …

### Négatives

- …

### Risques

- …

## Compliance and Enforcement

- Test de `morpheus-architecture-tests/` ou règle Archgate compagnon qui tient chaque contrainte vérifiable.
- Revue : ce qu'un relecteur doit vérifier quand aucun contrôle automatique n'existe.

## References

- ADR liés, changement OpenSpec, sections arc42, preuves de `docs/project/validation/`.
