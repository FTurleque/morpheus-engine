# Tâches : comparer à son gate chaque liste de classes qu'un ADR énumère

## 1. Reproduire

- [ ] 1.1 Écrire le contrôle textuel de D6 (nom qualifié d'`ARCH-0150` introuvable dans `morpheus-application/src/main/java`), avec son test sur un corpus temporaire — exigence : `application-technology-boundary` › Une classe que le registre de la frontière technologique nomme par un nom qualifié DOIT exister à ce nom ; vérification : rouge sur l'`ARCH-0150` actuel en nommant `security.LocalWritePermissionHardener`, et le scénario « le contrôle a lu des noms » échoue sur un ADR sans nom qualifié
- [ ] 1.2 Ajouter à `ProductionClassListsArchitectureTest` la variante de sorte « table de faits énoncée par un ADR » (D5), déclarer `ApiEntryTypesArchitectureTest.ENTRY_TYPES` sous `ARCH-0155`, et le test de refus d'un ADR absent, non accepté ou sans énumération — exigence : `adapter-internal-boundaries` › Une table de faits qu'un ADR en vigueur énonce DOIT déclarer cet ADR ; vérification : rouge tant qu'`ARCH-0155` §3 ne porte pas de bloc délimité
- [ ] 1.3 Rendre `CONFINED_FAMILIES` et `REFLECTION_AND_LOADING` lisibles du test de comparaison sans changer leur nom, leur contenu ni leur taille — vérification : `ProductionClassListsArchitectureTest` et `ApplicationTechnologyBoundaryArchitectureTest` verts, clés de `config/debt-counter-ratchets.properties` inchangées
- [ ] 1.4 Écrire le test de comparaison (D1, D4) : déclarer dans `KINDS` si l'ADR de chaque liste l'énumère ou la référence seulement (`CONFINED_FAMILIES`, `REFLECTION_AND_LOADING`, `ENTRY_TYPES` énumérées ; `COMPOSITION_ROOTS`, `RUNTIME_ENTRY_POINTS`, `RUNTIME_PUBLIC_TYPES` référencées) ; pour une liste énumérée, exiger le bloc `<!-- liste TestClass.CHAMP -->` et comparer dans les deux sens ; refuser un bloc absent ou vide, une ligne sans span de code, une famille hors vocabulaire. Tests sur corpus temporaire pour chaque scénario. Laisser à `CoherenceCheckMarkerTest` le verdict sur `@CoherenceCheck` — exigence : `adapter-internal-boundaries` › Une liste de classes qu'un ADR en vigueur énumère DOIT égaler la constante du gate qui la tient ; vérification : les scénarios de corpus passent ; sur le dépôt, rouge pour `ARCH-0150` et `ARCH-0155` (bloc absent)
- [ ] 1.5 Reproduire le constat : écrire dans `ARCH-0150` le bloc délimité avec les noms de la table « Mesure » telle qu'elle est aujourd'hui (dont `product.UpdateDiscoveryService` et `security.LocalWritePermissionHardener`, sans `security.PeerLaunchSettingsResolver`) — vérification : le test de comparaison échoue en nommant ces trois écarts ; consigner le message dans le compte rendu de la tâche

## 2. Clarifier les ADR

- [ ] 2.1 Recopier la table « Mesure » d'`ARCH-0150` telle quelle, avec sa date et son SHA (`b60a9286`, 25/10/2026), dans un relevé daté sous `docs/project/research/` ; laisser dans l'ADR une phrase et un lien relatif vers ce relevé — exigence : `application-technology-boundary` › Le registre de la frontière technologique NE DOIT PAS porter de mesure périssable de ses classes nommées ; vérification : `RepositoryDocumentationCoherenceTest` vert (liens)
- [ ] 2.2 Réécrire la décision 2 d'`ARCH-0150` (D3) : la règle et les sorts, renvoi à une table unique délimitée sous `Compliance and Enforcement` qui porte nom qualifié, famille et sort de chaque classe, horloge comprise ; retirer les totaux du texte au présent ; garder la section `Decision` sous 2 000 caractères — exigence : idem et `adapter-internal-boundaries` › Une liste de classes qu'un ADR en vigueur énumère DOIT égaler la constante du gate qui la tient ; vérification : tests 1.1 et 1.3 verts, `archgate check` sans `briefingWarnings` pour `ARCH-0150`
- [ ] 2.3 Encadrer la liste de six types d'`ARCH-0155` §3 par son bloc délimité — exigence : `adapter-internal-boundaries` › Une table de faits qu'un ADR en vigueur énonce DOIT déclarer cet ADR ; vérification : test 1.2 vert
- [ ] 2.4 Casser chaque règle une fois : ajouter un nom à `CONFINED_FAMILIES` (et à sa clé de cliquet) sans toucher l'ADR, puis un type à `ENTRY_TYPES`, puis réécrire `files.` en `security.` dans le bloc ; constater chaque échec et son message ; revenir en arrière — vérification : trois échecs nommant l'ADR, la constante et le nom ; arbre propre ensuite (`git diff` vide sur ces fichiers)

## 3. Documentation durable

- [ ] 3.1 Mettre à jour `docs/developer/specifications/adapter-internal-boundaries.md` (exigences, comportement, diagramme de la cible, traçabilité) — vérification : page relue, liens contrôlés par `RepositoryDocumentationCoherenceTest`
- [ ] 3.2 Mettre à jour `docs/developer/specifications/application-technology-boundary.md` (exigences, écart « Section Mesure d'ARCH-0150 datée » résolu, traçabilité) — vérification : idem
- [ ] 3.3 Ajouter à `.claude/rules/architecture.md`, au paragraphe des compteurs de dette, que l'énumération d'une liste par son ADR est comparée par le build — vérification : relecture ; aucun chiffre recopié

## 4. Vérification d'ensemble

- [ ] 4.1 `openspec validate hold-every-class-list-of-a-decision-record-to-its-gate --strict` — vérification : aucun avertissement
- [ ] 4.2 Compléter la matrice de traçabilité des deux pages durables — vérification : chaque exigence a une preuve existante ou « preuve manquante »
- [ ] 4.3 `./mvnw test -pl morpheus-architecture-tests -Dtest=ProductionClassListsArchitectureTest,ApplicationTechnologyBoundaryArchitectureTest,ApiEntryTypesArchitectureTest,AdrIndexCoherenceTest,RepositoryDocumentationCoherenceTest,CoherenceCheckMarkerTest,EnforcementMechanismAccountingTest` puis la suite d'architecture complète `./mvnw test -pl morpheus-architecture-tests` — vérification : verdict vert ; le nombre d'exécutions ne baisse pas sous `architectureTestsMinimum` (`config/m21-quality-ratchets.properties`)
- [ ] 4.4 `archgate check` — vérification : aucune violation, aucun `briefingWarnings` nouveau

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire les specs principales consolidées et les deux pages durables.
