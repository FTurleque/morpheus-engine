# Tâches : écrire que le launcher porte le SDK provider, et le tenir par une règle

## 1. Décision du mainteneur

- [ ] 1.1 Trancher D1 de design.md : clarification de `SEC-0129` §1 (proposée) ou ADR consolidé qui le remplace, seul
  ou avec `AUD-ARC-30` — vérification : décision consignée dans la PR ; aucun ADR déclaré accepté par l'agent

## 2. Règle sur les consommateurs du SDK

- [ ] 2.1 Écrire dans `ProviderPluginPlatformContractTest` la méthode qui compare les modules déclarant
  `morpheus-provider-sdk` en compilation à une liste exacte, et refuse `domain`, `application` et les providers
  intégrés — exigence : `provider-sdk-dependency-boundary` › Les modules qui compilent contre le SDK provider DOIVENT
  être exactement ceux que le build nomme ; vérification : violations plantées (SDK ajouté au POM de
  `morpheus-store-sqlite`, module nommé sans SDK, provider intégré nommé) chacune refusée en nommant le module, puis
  retirées ; justification de PR (test d'architecture ajouté)
- [ ] 2.2 Garder le contrôle existant du plugin de référence absent de `morpheus-cli/pom.xml` — vérification :
  `#referenceProviderIsNotEmbeddedAndExecutableSurfacesAreFailClosed` inchangé sur ce point et vert

## 3. Décision écrite et documentation durable

- [ ] 3.1 Appliquer la décision de 1.1 : réécrire `SEC-0129` §1 (et la ligne de conformité qui cite la nouvelle
  méthode), ou rédiger l'ADR consolidé `Proposée` avec la ligne d'index et `09-decisions.md` — vérification :
  `AdrIndexCoherenceTest`, `AdrBriefingBudgetTest` verts ; `archgate check` sans erreur
- [ ] 3.2 Préciser `docs/developer/PROVIDER_SDK.md:5` (sans recompiler le launcher) et corriger le libellé du lien de la
  ligne 385 — vérification : `RepositoryDocumentationCoherenceTest` vert
- [ ] 3.3 Ajouter l'exigence et sa matrice à `docs/developer/specifications/provider-sdk-dependency-boundary.md` —
  vérification : page relue, lien vers la méthode résolu

## 4. Vérification d'ensemble

- [ ] 4.1 Lancer `openspec validate state-that-the-launcher-carries-the-provider-sdk --strict` — vérification : aucun
  avertissement
- [ ] 4.2 Compléter la matrice exigence → scénario → D2 → tâche 2.1 → test — vérification : chaque scénario a sa preuve
- [ ] 4.3 Lancer `./mvnw test -pl morpheus-architecture-tests -Dtest=ProviderPluginPlatformContractTest,AdrIndexCoherenceTest,AdrBriefingBudgetTest`
  puis `./mvnw clean verify` — vérification : verts

## Workflow follow-up

- Appliquer ensuite `separate-the-provider-plugin-spi-from-the-plugin-host` (`AUD-ARC-30`).
- Archiver le changement après la revue, sur demande explicite.
