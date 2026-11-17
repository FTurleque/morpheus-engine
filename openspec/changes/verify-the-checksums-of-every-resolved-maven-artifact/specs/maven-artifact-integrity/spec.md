## Purpose

Garantir que tout artefact que Maven résout pour construire MORPHEUS est vérifié par sa somme de contrôle, et que le
build qui produit une release ne réutilise pas un cache rempli par d'autres exécutions. Page durable à créer :
`docs/developer/specifications/maven-artifact-integrity.md`.

## ADDED Requirements

### Requirement: Toute résolution Maven du dépôt DOIT échouer sur une somme de contrôle absente ou fausse

`.mvn/maven.config` DOIT (MUST) porter `--strict-checksums`, pour que toute exécution du wrapper du dépôt refuse un
artefact dont la somme de contrôle manque ou ne correspond pas, et le build DOIT échouer quand ce réglage est absent, en
nommant le fichier et l'option.

#### Scenario: Un artefact altéré sur le dépôt distant est refusé au téléchargement

- **GIVEN** un dépôt distant ou un miroir qui sert un artefact dont le contenu ne correspond pas à sa somme publiée
- **WHEN** `./mvnw` télécharge cet artefact
- **THEN** la résolution échoue sur une erreur de somme de contrôle qui nomme l'artefact
- **AND** le build s'arrête sans produire de livrable

#### Scenario: Le réglage retiré fait échouer le build

- **WHEN** `--strict-checksums` est retiré de `.mvn/maven.config`, ou le fichier supprimé
- **THEN** la suite d'architecture échoue et nomme `.mvn/maven.config` et l'option
- **AND** aucun fichier n'est réécrit par le test

#### Scenario: Le reactor se construit à cache vide sous le réglage

- **GIVEN** un dépôt Maven local vide
- **WHEN** `./mvnw clean verify` s'exécute
- **THEN** toutes les dépendances et tous les plugins sont résolus avec leur somme vérifiée et le build passe

### Requirement: Le build de release NE DOIT PAS restaurer un cache Maven rempli par une autre exécution

Les jobs de `.github/workflows/release.yml` qui exécutent Maven NE DOIVENT PAS (MUST NOT) restaurer de cache du dépôt
Maven local (`cache: maven` d'`actions/setup-java`, ou `actions/cache` sur `~/.m2`), et le build DOIT échouer sinon en
nommant le job.

#### Scenario: Les jobs de release résolvent depuis le dépôt distant

- **GIVEN** le workflow de release après ce changement
- **WHEN** le test de contrat lit ses jobs
- **THEN** aucun job qui lance `./mvnw` ou un script de construction ne restaure de cache Maven
- **AND** le test passe

#### Scenario: Un cache réintroduit fait échouer le build

- **WHEN** `cache: maven` est ajouté à l'étape `actions/setup-java` d'un job de release
- **THEN** la suite d'architecture échoue et nomme le job
