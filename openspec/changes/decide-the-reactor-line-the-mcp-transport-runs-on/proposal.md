# Proposition : décider de la ligne Reactor sur laquelle tourne le transport MCP (AUD-DEP-30)

## Why

Le POM racine tient Reactor sur `reactor-bom.version` `2024.0.18` (`pom.xml`, propriété `reactor-bom.version`), qui
porte `reactor-core` 3.7.19. Cette version est dans la plage touchée par CVE-2026-47863 (`Flux.bufferTimeout` avec
`fairBackpressure`, déni de service ; avis Spring publié le 20/08/2026 : corrigé en 3.8.7 en open source, en 3.7.20
seulement pour le support commercial) et par CVE-2026-47857 (`Flux.windowTimeout` avec `fairBackpressure`). Les deux
notes sont sous `failBuildOnCVSS` (`pom.xml`, profil `d2-security`) : le gate CVE reste vert sans que personne ait
décidé de les accepter. Surtout, la ligne 3.7 ne reçoit plus de correctif open source : la prochaine CVE de
`reactor-core` n'aura pas de correctif atteignable sans changer de ligne, et `.github/dependabot.yml` refuse justement
les changements de ligne.

Preuve : constat `AUD-DEP-30` (Moyenne), `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du
17/11/2026 sur `a04f32bd` et revérifié le 17/11/2026 sur `1a539c22` (POM, commentaire de l'import, absence de
`bufferTimeout`, `windowTimeout` et `fairBackpressure` dans les sources Java de MORPHEUS, avis Spring relu). Le SDK MCP,
lui, n'a pas été lu.

Le choix de la ligne revient au mainteneur : cette proposition le prépare et ne le tranche pas.

## What Changes

- Lire les sources du SDK MCP à la version de `mcp-sdk.version` et dire s'il appelle `bufferTimeout` ou
  `windowTimeout` avec `fairBackpressure` (constat consigné dans design.md au moment de l'application).
- Selon la décision du mainteneur (design.md, D1) :
  - **option A, proposée** : qualifier le SDK MCP sur une ligne Reactor qui reçoit des correctifs open source
    (`reactor-bom` 2025.0.x, `reactor-core` 3.8.x à une version hors des plages des deux CVE), en changeant dans le même
    lot la propriété, le commentaire de l'import, la ligne attendue par `DependencyLineCurrencyTest`, la page durable de
    la capacité et la conséquence d'ARCH-0062 qui dit que le SDK impose sa ligne ;
  - **option B** : garder la ligne 2024.0 et consigner dans `docs/architecture/risks/register.md` une acceptation datée
    qui nomme les deux CVE, la raison et la condition de réouverture.
- Le spec delta est écrit pour l'option A ; si l'option B est retenue, il est réécrit avant toute implémentation
  (exigence d'acceptation écrite à la place de la ligne qui change).

## Capabilities

### New Capabilities

### Modified Capabilities
- `dependency-line-currency` : la ligne Reactor tenue est une ligne qui reçoit des correctifs open source et sur
  laquelle le SDK MCP est qualifié, et non plus nécessairement la ligne sur laquelle le SDK est construit (option A).

## Périmètre et exclusions

**Inclus :** `pom.xml` (propriété `reactor-bom.version` et commentaire de l'import), `DependencyLineCurrencyTest`,
`.github/dependabot.yml` si l'entrée `ignore` change de portée, `docs/developer/specifications/dependency-line-currency.md`,
ARCH-0062 (clarification de sa conséquence sur la ligne Reactor), le registre des risques (option B).

**Exclus :** le gate qui lie la version du SDK MCP à la ligne Reactor (`AUD-DEP-31`, change
`tie-the-mcp-sdk-version-to-the-reactor-line-it-is-qualified-on`, qui vient après celui-ci) ; la montée du SDK MCP vers
2.2.0, bâti sur `reactor-core` 3.9.0-RC1, pré-version.

## Acteurs et surfaces concernés

Le mainteneur, qui décide de la ligne ; Dependabot ; le gate CVE ; les utilisateurs du serveur MCP et des pairs
MINOS/NEXUS, dont le transport tourne sur Reactor.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| `reactor-bom` 2024.0.18 porte `reactor-core` 3.7.19 | existant vérifié | `pom.xml`, `reactor-bom-2024.0.18.pom` (Maven Central) relu par l'audit |
| 3.7.19 est dans la plage de CVE-2026-47863, corrigée en open source en 3.8.7 seulement | existant vérifié | avis Spring CVE-2026-47863, relu le 17/11/2026 |
| MORPHEUS n'appelle aucun des opérateurs visés | existant vérifié | recherche de `bufferTimeout`, `windowTimeout`, `fairBackpressure` dans les sources Java, aucun résultat |
| Le SDK MCP n'appelle pas ces opérateurs | question ouverte | à lire dans les sources du SDK (tâche 1.1) |
| Le SDK MCP 2.1.0 tourne correctement sur `reactor-core` 3.8.x | proposition à valider | tests réels du transport STDIO et des pairs (tâche 2.x) |
| La ligne Reactor tenue est un choix du mainteneur | décision à prendre | ARCH-0062, conséquences ; `AUD-DEP-30` |

## Impact

`pom.xml`, `.github/dependabot.yml`, `morpheus-architecture-tests` (`DependencyLineCurrencyTest`, fichier de
gouvernance : justification de PR), `morpheus-mcp-transport` et `morpheus-mcp` (tests réels, sans changement de code
attendu), ARCH-0062 (clarification, ADR à proposer si la décision change), registre des risques (option B), page durable
`dependency-line-currency`. Bloque `AUD-DEP-31` (`tie-the-mcp-sdk-version-to-the-reactor-line-it-is-qualified-on`).
Aucune surface publique, aucun format persisté.

## Questions ouvertes

- **Mainteneur** : option A (qualifier le SDK MCP 2.1.0 sur `reactor-bom` 2025.0.x) ou option B (accepter les deux
  CVE par une entrée datée du registre des risques) ? Voir design.md, D1.
- **Mainteneur** : si l'option A est retenue, la conséquence d'ARCH-0062 (« le SDK impose sa ligne Reactor ») se
  clarifie-t-elle en place, ou appelle-t-elle un ADR consolidé qui la remplace ?
