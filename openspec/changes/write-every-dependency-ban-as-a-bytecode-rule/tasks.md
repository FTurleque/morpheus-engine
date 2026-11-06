# Tâches : écrire en règle sur le bytecode chaque interdit de dépendance dont la cible le permet

## 1. Mesurer

- [ ] 1.1 Écrire la méthode sur exemples de la classe ajoutée (sept cas : membre, cible à constante non privée, cible à constante privée seulement, cible hors du dépôt, cible nommée par une classe de règles, interdit en commentaire ou en chaîne, récepteur qualifié) — exigence : `enforcement-mechanism-accounting` › Les interdits textuels de type qui attendent une règle sur le bytecode forment une population qui ne fait que rétrécir ; vérification : à écrire, rouge tant que l'expression n'existe pas, verte ensuite
- [ ] 1.2 Écrire la méthode du plafond (membres par fichier dans le message d'échec, renvoi à la règle sur le bytecode, `assertEquals` qui demande d'abaisser) et poser le plafond à la mesure de ce moment, consignée dans cette tâche avec la liste des membres — exigence : idem ; vérification : verte à la mesure ; casser une fois en ajoutant un interdit à message sur une cible sans constante (échec qui nomme le fichier et le type), puis revenir en arrière
- [ ] 1.3 Vérifier que la classe ajoutée ne porte pas `@CoherenceCheck` et que `CoherenceCheckMarkerTest` la classe en contrôle de code — exigence : `enforcement-mechanism-accounting` › A test class that reads only non-compiled artefacts SHALL be marked as a coherence check and SHALL stay in the gate module ; vérification : `CoherenceCheckMarkerTest` vert

## 2. Migrer les membres

- [ ] 2.1 Pour chaque membre consigné en 1.2 : écrire l'intention en une phrase ; si c'est une absence de dépendance, écrire la règle dans le `*ArchitectureTest` de cette intention, une méthode par intention, l'assertion textuelle gardée à côté dans le doute ; si c'est une mention, le dire dans le message de l'assertion et le consigner ici — exigence : `enforcement-mechanism-accounting` › Une règle qui remplace ou double un interdit textuel est livrée avec sa violation plantée ; vérification : pour chaque règle, la cible est dans l'ensemble importé (`morpheus-architecture-tests/pom.xml`), le littéral n'est pas un préfixe de famille
- [ ] 2.2 Casser chaque règle ajoutée une fois (violation introduite dans la classe sujet, échec constaté qui nomme la classe, retour arrière) et consigner le résultat dans cette tâche — exigence : idem ; vérification : une ligne par règle
- [ ] 2.3 Abaisser le plafond au compte mesuré après chaque membre résolu, dans le même commit — exigence : la population qui ne fait que rétrécir ; vérification : méthode du plafond verte
- [ ] 2.4 Compter les exécutions déclarées du module avant et après (relevé Surefire `morpheus-architecture-tests/target/surefire-reports/`, jamais un `grep -c '@Test'`) — vérification : le compte ne baisse pas ; `architectureTestsMinimum` (`config/m21-quality-ratchets.properties`) non touché

## 3. Règles et documentation

- [ ] 3.1 Réécrire le paragraphe « Un interdit textuel de type écrit son intention » de `.claude/rules/architecture.md` pour renvoyer au test qui tient la convention, sans recopier la valeur d'un plafond ; même renvoi dans `.claude/skills/enforcement-choice/SKILL.md` — vérification : `GovernanceSurfaceCoherenceTest` vert
- [ ] 3.2 Mettre à jour `docs/developer/specifications/enforcement-mechanism-accounting.md` : deux exigences, section de la population, diagramme de classement, écart des récepteurs qualifiés, traçabilité (le scénario « Une règle migrée sans trace de violation » marqué « tenu par la revue ») — vérification : `RepositoryDocumentationCoherenceTest` vert

## 4. Vérification d'ensemble

- [ ] 4.1 `openspec validate write-every-dependency-ban-as-a-bytecode-rule --strict` — vérification : aucun avertissement
- [ ] 4.2 `./mvnw test -pl morpheus-architecture-tests -Dtest=<classe ajoutée>,EnforcementMechanismAccountingTest,CoherenceCheckMarkerTest,AnEmptyArchitectureRuleFailsTest,ProductionClassListsArchitectureTest,RepositoryDocumentationCoherenceTest` puis chaque `*ArchitectureTest` qui reçoit une règle — vérification : verdict vert
- [ ] 4.3 `./mvnw clean verify` (le module d'architecture exige `morpheus-provider-reference` construit) — vérification : verdict vert
- [ ] 4.4 `archgate check` — vérification : aucune violation

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
- Après l'archivage, relire la spec principale consolidée et la page durable.
