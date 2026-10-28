# Bases écrites par les versions publiées

Une base SQLite par niveau de schéma publié (`released-schema-levels.properties`), écrite par la CLI de la première
version qui a livré ce niveau. `ReleasedSchemaUpgradeTest` (fabrique
`aDatabaseThatEveryPublishedProgramWroteIsUpgradedWithoutLosingWhatItHeld`, logique dans `PublishedDatabaseUpgrade`)
copie chacune, la fait monter jusqu'au schéma courant avec le runtime de l'arbre, vérifie que chaque ligne écrite par
la version publiée est retrouvée à l'identique, puis relit identités et historique par les stores.

| Répertoire | Niveau | Version | Tag | Commit |
|---|---|---|---|---|
| `level-12/` | 12 | 1.0.0 | `v1.0.0` | `51f6a120f3461c8d8c24323f3db8211d28d6cb42` |
| `level-15/` | 15 | 1.1.0 | `v1.1.0` | `31506029ded1101f0571edeb0d79c59bbf3f68c6` |
| `level-20/` | 20 | 1.2.1 | `v1.2.1` | `2a8b1ede9cdeb107244b04969726305afc672189` |

1.2.0 a livré le niveau 15, comme 1.1.0 : la base de 1.1.0 le couvre.

Chaque répertoire porte `provenance.properties` (version, tag, commit, SHA-256 de `morpheus.db`). Le test refuse une
base dont l'empreinte n'est pas celle consignée, une base dont la version ne déclare pas ce niveau, et un niveau
déclaré sans base.

## Comment chaque base a été produite

Le 28/10/2026, sous Linux (WSL, OpenJDK 21.0), avec la CLI construite depuis le tag lui-même et la fixture
`experiments/m0/fixtures/openspec-basic` **du même tag** :

```bash
git worktree add --detach /work/v1.1.0 v1.1.0
cd /work/v1.1.0 && ./mvnw -B -q -DskipTests package -pl morpheus-cli -am
JAR=/work/v1.1.0/morpheus-cli/target/morpheus-cli-1.1.0-all.jar
B=/tmp/morpheus-published && mkdir -m 700 -p "$B"
cp -r experiments/m0/fixtures/openspec-basic "$B/openspec-basic"
m() { java -jar "$JAR" --data-dir "$B/data" --config-dir "$B/config" --db "$B/data/morpheus.db" "$@"; }

m --json projects add --workspace "$B/openspec-basic"          # -> projectId P
m --json sync --project "$P" --revision rev-1
printf '\nThe session lasts at most eight hours.\n' >> "$B/openspec-basic/openspec/specs/auth-session/spec.md"
m --json sync --project "$P" --revision rev-2
m --json composition sync --project "$P" --revision rev-3

# À partir de 1.1.0 seulement (niveau 13 et au-delà) :
m --json portfolio create --name 'Published fixture'             # -> id F
m --json portfolio add-project --portfolio "$F" --project "$P" --name openspec-basic
m --json views create --name 'Requirements' --project "$P" --entity REQUIREMENT
m --json policy pack create --name 'Published pack' \
  --rules 'new|No findings|QUALITY_THRESHOLD|BLOCKER|FINDINGS|LTE|0' --actor fixture --reason create   # -> id K
m --json policy pack update --id "$K" --expected-revision 1 --name 'Published pack v2' \
  --rules '<ruleId de la version 1>|No findings|QUALITY_THRESHOLD|WARNING|FINDINGS|LTE|0' --actor fixture --reason update

cp "$B/data/morpheus.db" level-<niveau>/morpheus.db
```

Ce que la base contient donc : un projet, trois snapshots (deux retirés, un actif), trois versions de spécification
chaînées, un état de synchronisation et son inventaire, un état de composition OpenSpec + Structured Markdown avec ses
conflits (aux niveaux 12 et 15, sous la valeur `SELECTED_BY_PRECEDENCE` que V020 renomme), et à partir du niveau 15 un
portfolio, une vue sauvegardée et un pack de politiques à deux versions avec deux enregistrements d'audit.

Seul `morpheus.db` est gardé : le journal est en mode `PERSIST`, son en-tête est remis à zéro à chaque validation, et le
runtime recrée le fichier compagnon à l'ouverture. Les identifiants (UUIDv7) et les horodatages sont ceux de
l'exécution : une base reproduite n'a pas les mêmes octets, elle remplace alors le fichier **et** son empreinte dans
`provenance.properties`. Le chemin de l'espace de travail enregistré dans la base est `/tmp/morpheus-published/openspec-basic`.

## Ajouter le niveau d'une nouvelle release

Quand une release livre un niveau nouveau, la version qui la suit déclare la ligne dans
`released-schema-levels.properties` (voir `docs/project/validation/RELEASE_QUALIFICATION.md`), produit
`level-<niveau>/` avec la CLI du tag par la procédure ci-dessus, et écrit `provenance.properties`. Tant que le
répertoire manque, le test échoue en le nommant.
