# ADR archivées

Ce dossier garde les ADR qui ne sont plus en vigueur : une décision remplacée par une autre, ou une proposition
rejetée. Elles ne figurent pas dans l'[index du registre](../README.md), Archgate ne les lit pas, et aucune ne
s'applique au code.

Une ADR archivée garde son numéro, qui n'est jamais réutilisé ni renuméroté, et sa ligne de statut dit ce qui la
remplace :

- `Statut : **Remplacée par ADR-NNNN**`, où ADR-NNNN est une ADR du registre ou de cette archive ;
- `Statut : **Rejetée**`, pour une proposition qu'aucun travail ne porte.

Une ADR arrive ici par `git mv` depuis le registre, avec la suppression de sa ligne d'index dans le même changement.
Le filtre et le cycle de vie sont décrits dans `.claude/rules/adr.md` ; `AdrIndexCoherenceTest` tient l'unicité des
numéros entre le registre et l'archive, et le statut des ADR de part et d'autre.
