# Conception : vérifier les sommes de contrôle de chaque artefact Maven résolu

<!-- Conception allégée : configuration Maven, workflow de release et test de cohérence, sans code de production. -->

## Contexte

- [EXISTANT VÉRIFIÉ] `.mvn/` ne contient que `wrapper/maven-wrapper.properties` (`distributionType=only-script`,
  `distributionSha256Sum`) ; `mvnw` vérifie la distribution Maven, pas les artefacts résolus ensuite.
- [EXISTANT VÉRIFIÉ] Aucune occurrence de `strict-checksums`, `trustedChecksums` ni `maven.config` dans les workflows,
  les scripts, les POM et les tests (recherche du 17/11/2026, hors relevés datés).
- [EXISTANT VÉRIFIÉ] `release.yml` : `actions/setup-java` avec `cache: maven` dans `linux-release` et
  `windows-release`. `ci.yml`, `security.yml` et `nightly.yml` utilisent aussi ce cache.
- [EXISTANT VÉRIFIÉ] Toolchain : Maven Wrapper vers la distribution que fixe `distributionUrl` (GEN-0133).

## Exigences couvertes

| Capacité | Exigence (titre exact) | Rubriques de conception qui y répondent |
|---|---|---|
| `maven-artifact-integrity` | Toute résolution Maven du dépôt DOIT échouer sur une somme de contrôle absente ou fausse | D1, D3 |
| `maven-artifact-integrity` | Le build de release NE DOIT PAS restaurer un cache Maven rempli par une autre exécution | D2, D3 |

## Sécurité et isolation

Le cache GitHub Actions est partagé entre exécutions de la même branche ; un cache rempli par une exécution
compromise serait restauré par le build de release. `--strict-checksums` vérifie les sommes au **téléchargement**, pas
le contenu d'un dépôt local restauré : il ne couvre donc pas seul le cache. D'où les deux exigences : sommes strictes
partout, et pas de cache réutilisé là où une attestation est signée. Les sommes publiées par Maven Central protègent
contre l'altération en transit ou sur un miroir, pas contre un artefact publié malveillant (hors périmètre).

## Décisions

### D1 : `--strict-checksums` dans `.mvn/maven.config`
- **Nature** : [DÉCISION]
- **Exigences servies** : `maven-artifact-integrity` › Toute résolution Maven du dépôt DOIT échouer sur une somme de contrôle absente ou fausse
- **Choix** : un fichier `.mvn/maven.config` d'une ligne, lu par Maven à chaque exécution depuis la racine du projet,
  donc par le wrapper en local comme en CI et par les validateurs `scripts/validate-*`.
- **Alternatives écartées** : passer `-C` dans chaque workflow (un oubli suffit, et le local n'est pas couvert) ; une
  politique `<checksumPolicy>fail</checksumPolicy>` par dépôt dans le POM (ne couvre pas les dépôts de plugins hérités
  du super-POM sans les redéclarer).
- **Compromis** : un dépôt qui ne publie pas de somme pour un artefact fait échouer le build ; à mesurer à cache vide
  (tâche 2.2) avant fusion.
- **ADR** : non concerné.

### D2 : build de release sans cache, ou sommes de confiance
- **Nature** : [QUESTION] pour le mainteneur, avec [PROPOSITION]
- **Exigences servies** : `maven-artifact-integrity` › Le build de release NE DOIT PAS restaurer un cache Maven rempli par une autre exécution
- **Options** :
  - **a** — retirer `cache: maven` des deux jobs de release : chaque release télécharge tout depuis Maven Central sous
    `--strict-checksums`. Coût : quelques minutes par release (cadence faible).
  - **b** — versionner une source de sommes de confiance du résolveur (`aether.trustedChecksumsSource.*`) et l'activer
    pour la release : vérifie aussi le contenu d'un dépôt local restauré. Coût : un fichier à régénérer à chaque montée
    de dépendance ou de plugin, que Dependabot ne régénère pas ; un nouvel outillage à documenter.
- **[PROPOSITION]** : a. Elle ferme le constat pour le build qui signe la provenance au coût le plus bas ; b reste
  ouverte si le mainteneur veut étendre la garantie à la CI. Sous b, la seconde exigence est réécrite (vérification
  contre les sommes de confiance) avant toute implémentation.
- **ADR** : non concerné sous a ; sous b, une décision durable d'outillage de livraison qui passe le filtre de
  `.claude/rules/adr.md` : ADR à proposer.

### D3 : un test de cohérence de la famille `d2`
- **Nature** : [PROPOSITION]
- **Exigences servies** : les deux exigences de la capacité
- **Choix** : un test (paquet `d2`, `@CoherenceCheck`) lit `.mvn/maven.config` et exige `--strict-checksums` ; il lit
  `release.yml`, découpe les jobs et refuse `cache: maven` ou un `actions/cache` sur `~/.m2` dans un job qui exécute
  Maven. Assertion textuelle : la cible est un fichier de configuration et un `.yml`.
- **Alternatives écartées** : étendre `D2RepositoryHardeningArchitectureTest` (déjà volumineux, intention distincte).
- **ADR** : non concerné.

## Écarts constatés

Aucun écart constaté entre code, tests et documentation : aucun document n'affirme aujourd'hui une vérification des
artefacts résolus.

## Stratégie de vérification

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| Toute résolution Maven du dépôt DOIT échouer sur une somme de contrôle absente ou fausse | Un artefact altéré sur le dépôt distant est refusé au téléchargement | essai manuel : miroir `file:` jetable (déclaré dans un `settings.xml` du scratchpad) qui sert un `.jar` altéré avec sa somme d'origine, dépôt local vide | manuel |
| idem | Le réglage retiré fait échouer le build | test de D3, violation plantée | à écrire |
| idem | Le reactor se construit à cache vide sous le réglage | `./mvnw -Dmaven.repo.local=<dossier vide> clean verify` | manuel |
| Le build de release NE DOIT PAS restaurer un cache Maven rempli par une autre exécution | Les jobs de release résolvent depuis le dépôt distant | test de D3 | à écrire |
| idem | Un cache réintroduit fait échouer le build | test de D3, violation plantée sur l'arbre courant | à écrire |

## Documentation durable et diagrammes à mettre à jour

Nouvelle page `docs/developer/specifications/maven-artifact-integrity.md` (gabarit de
`docs/developer/specifications/README.md`), ligne d'index dans « Gates, preuves et dépendances » ;
`docs/developer/BUILD_AND_TEST.md` (le réglage et son effet sur un dépôt local corrompu).

## Questions ouvertes

Aucune autre que D2, à trancher avant le groupe 3 des tâches.
