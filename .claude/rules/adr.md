# Règles — Quand écrire un ADR (et quand ne pas en écrire)

Le registre `docs/architecture/adr/` ne garde que des **décisions d'architecture durables, en vigueur et coûteuses à
inverser**, dont les raisons se perdraient sans lui. Tout le reste a un autre emplacement, plus adapté à son lecteur.
Le gabarit, la numérotation et l'index sont décrits dans `rules/documentation.md` ; cette page dit **quand** un ADR
est justifié et **sous quelle forme** il vit.

## Le filtre : trois questions, trois « oui »

Avant de créer un ADR, répondre aux trois. Un seul « non » suffit : ce n'est pas un ADR.

1. **Décision** : fixe-t-il un choix entre plusieurs options réelles ? Un constat, une mesure, un plan ou un état
   d'avancement n'est pas une décision.
2. **Portée et durée** : contraint-il durablement plusieurs modules, une surface publique (CLI, MCP, HTTP), un format
   persisté (schéma SQLite, JSON canonique), un modèle de confiance ou de sécurité, la toolchain ou la livraison ?
3. **Coût du retour arrière** : l'inverser casserait-il des données, des contrats, des garanties de sécurité ou une
   part importante du code ?

## Ce n'est pas un ADR

| Contenu | Où l'écrire |
|---|---|
| état d'un jalon, preuve de qualification, SHA, mesure datée | `docs/project/validation/`, `docs/project/audits/` |
| plan, backlog, ordre de travail | `docs/project/roadmap/`, changement OpenSpec (`openspec/changes/`) |
| exigence et scénarios d'un comportement | spécifications OpenSpec (`openspec/specs/`) |
| étude, comparaison, option sans décision | `docs/project/research/` |
| description d'un mécanisme, mode d'emploi | `docs/developer/` ou `docs/user/` |
| convention locale, nommage, style | `.claude/rules/`, ou un test d'architecture |
| correctif, fonctionnalité, classe | le commit et la PR |

## Avant d'écrire : modifier plutôt que créer

- **Un ADR existant couvre déjà le sujet** : le citer, ou clarifier son texte si son sens ne change pas. Jamais d'ADR
  jumeau.
- **La décision change en entier** : le nouvel ADR passe `Acceptée`, l'ancien passe `Remplacée` (champ
  « Remplacée par » du gabarit), puis part en archive.
- **La décision change en partie** : pas de section « amendement » qui cohabite avec l'original. Écrire un ADR
  consolidé qui réunit toute la décision en vigueur et remplace les précédents. À tout moment, une décision se lit
  dans **un seul** ADR actif.
- **Un ADR qui porte déjà des sections d'amendement** se consolide dans le premier changement qui modifie de nouveau
  sa décision : on n'y ajoute pas un amendement de plus.

## Cycle de vie

- `Proposée` est un état transitoire. Une proposition qu'aucun travail en cours ne porte est archivée `Rejetée`.
- Une décision exécutée une fois pour toutes (migration, renommage, déplacement) et qui n'impose plus de règle au
  code est archivée. Si elle impose encore une règle, l'ADR ne garde que cette règle.
- Un ADR retiré n'est **jamais supprimé** : il va dans `docs/architecture/adr/archive/` avec un bandeau qui dit ce qui
  le remplace. Son numéro reste unique, n'est jamais réutilisé ni renuméroté. Archgate ne lit pas l'archive.
- Déplacer un ADR suit la procédure de `rules/documentation.md` (`git mv`, recherche de l'ancien chemin dans tout le
  dépôt, liens recalculés) : des tests, des règles et d'autres ADR citent ces chemins.

## Forme d'un ADR actif

- Rédigé **au présent** : il dit ce qui est décidé aujourd'hui, sans récit du chantier, ni « auparavant »,
  « désormais » ou « mis à jour pour… ». L'historique est dans Git et dans les relevés datés de `docs/project/`.
- Sa section `Decision` tient sous **2 000 caractères** : c'est elle qu'Archgate remet aux agents, et `archgate check`
  signale au-delà un `briefingWarnings` sans échouer. Les raisons et les options vont dans `Context`.
- Il porte des **Do's and Don'ts vérifiables**. Une contrainte qui se lit dans le code ou les fichiers a sa règle
  exécutable — un test sous `morpheus-architecture-tests/` (le mécanisme du dépôt, cf. `rules/architecture.md`) ou une
  règle Archgate compagnon ; sinon, la section de conformité dit comment elle est tenue (test, revue, gate de CI).
- Un ADR sans contrainte vérifiable ni conséquence sur le code est **suspect** : c'est probablement une étude ou une
  documentation, à ranger selon le tableau ci-dessus.

## Contrôles

Tenus par `AdrIndexCoherenceTest` : numéro unique entre le registre et l'archive, une ligne
d'index par ADR du registre et réciproquement, statut de l'ADR et de sa ligne d'index concordants et pris dans le
vocabulaire que l'index déclare, aucun ADR `Remplacée` ou `Rejetée` dans le registre, rien d'autre dans l'archive, et
un ADR remplacé qui nomme un successeur existant (`Statut : **Remplacée par ADR-NNNN**`).

Tenus par la règle Archgate d'ADR-0132 (`archgate check`, hors CI) : fichier `{PRÉFIXE}-{NNNN}-{slug}.md` avec un
en-tête YAML en ligne 1 et un `id` égal, préfixe conforme au domaine, titre `#` égal au `title` et à la ligne d'index,
sections du gabarit présentes, aucune section d'amendement, `.archgate/adrs/` réduit à `.gitkeep`.

Tenus par la revue : une `Proposée` qu'aucun travail ne porte, la rédaction au présent, le bandeau d'un ADR archivé.
