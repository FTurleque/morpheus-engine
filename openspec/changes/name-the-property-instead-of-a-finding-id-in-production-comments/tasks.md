# Tâches : nommer la propriété plutôt qu'un identifiant de constat dans les commentaires de production (AUD-QUA-39)

## 1. Correction

- [ ] 1.1 Réécrire les lignes 17 et 39 de la Javadoc de `PageArguments` selon D1 — exigence : aucune
  (`skip_specs: true`) ; vérification : `grep -nE "\bMCP-[0-9]+\b" morpheus-mcp/src/main/java` ne rend rien
- [ ] 1.2 Rejouer la recherche des préfixes de constat (`MCP`, `CLI`, `SEC`, `DT`, `RT`, `PRF`, `QUA`, `TRV`, `DEP`,
  `ARC`, `TST`, `AUD-`) suivis d'un nombre dans les sources principales — vérification : seuls des identifiants d'ADR
  en vigueur restent

## 2. Vérification d'ensemble

- [ ] 2.1 Lancer `openspec validate name-the-property-instead-of-a-finding-id-in-production-comments --strict` —
  vérification : aucun avertissement
- [ ] 2.2 Lancer `./mvnw test -pl morpheus-mcp` — vérification : vert

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
