# ADR-0089 — Production integrity and public-surface convergence

Statut : **Proposée — M21**

Date : 27 juillet 2026

## Contexte

MORPHEUS 1.0.0 est publié. La baseline possède plusieurs adapters publics — CLI, MCP STDIO et HTTP — ainsi qu’une chaîne de packaging Windows/Linux. La croissance incrémentale M0→M20 a volontairement privilégié les vertical slices fonctionnelles. Après 1.0, le risque principal devient la divergence silencieuse : versions différentes selon les transports, documentation dupliquée, CI spécifique à un milestone, qualité non mesurée, métadonnées de release non vérifiables ou découverte de mise à jour confondue avec mutation automatique.

M21 doit renforcer la production sans déplacer la source de vérité métier vers un service distant et sans introduire de dépendance réseau cachée.

## Décision

### 1. CI durable et non liée aux milestones

Le dépôt porte un workflow générique `ci.yml` exécutant le reactor Maven complet sur Windows et Linux avec JDK 21. Les validateurs locaux restent supportés ; le workflow est une preuve reproductible supplémentaire et non un remplacement magique des invariants produit.

### 2. Couverture mesurée et bornée

JaCoCo instrumente les modules Java. M21 introduit des floors aggregate volontairement conservateurs : 25% lignes et 20% branches. Ces floors empêchent une perte d’instrumentation ou une chute massive ; ils ne constituent pas une affirmation de couverture suffisante par domaine.

### 3. Métadonnées produit convergentes

La version produit est résolue par une primitive application-level unique à partir des métadonnées de build, avec fallback de développement explicite. CLI, MCP et HTTP n’entretiennent plus de version codée indépendamment.

### 4. Manifeste public de surfaces

`contracts/public-surfaces.tsv` est la source machine lisible des capabilities publiques partagées et de leur intention READ/WRITE. Une capability peut avoir des formes de transport différentes. Une absence sur un transport doit être déclarée explicitement et ne peut plus être une divergence implicite.

### 5. Documentation single-source-of-truth

La documentation humaine explique le manifeste mais ne redéfinit pas un deuxième contrat normatif. Des tests de cohérence vérifient version, manifeste et références documentaires critiques.

### 6. Supply chain vérifiable

Le build produit un SBOM CycloneDX. Les artefacts release sont accompagnés de SHA-256 et d’un manifeste de provenance contenant au minimum version, commit, tag/source ref et environnement de build. Un checksum n’est jamais présenté comme une signature. Une signature cryptographique n’est exigée que lorsqu’une identité/clé de signature réelle est configurée ; elle ne doit jamais être simulée.

### 7. Découverte de mise à jour explicite et read-only

La découverte d’une version disponible est un service read-only. Elle n’est exécutée que sur demande explicite avec une URI de manifeste fournie par l’appelant. Aucun accès réseau n’est effectué au démarrage. Le service peut lire `file:`, `http:` ou `https:`. Il valide version, channel, URI d’artefact et SHA-256, puis compare la version cible à la version courante. Il ne télécharge, n’installe et ne remplace aucun binaire.

## Invariants

```text
surface parity != same transport shape
read surface != write capability
release metadata != runtime business state
update discovery != automatic update
security metadata != hidden network dependency
checksum != signature
CI evidence != business source of truth
local-first remains default
```

## Conséquences

Positives :

- un seul contrat machine identifie les capabilities publiques ;
- la version ne peut plus diverger silencieusement entre CLI/MCP/HTTP ;
- la qualité et la couverture deviennent mesurables ;
- le workflow n’a plus à être recréé à chaque milestone ;
- les releases disposent d’un SBOM et d’une provenance vérifiable ;
- la découverte d’update ne crée aucune mutation implicite.

Coûts :

- temps de CI supérieur à cause de la matrice Windows/Linux et de JaCoCo ;
- maintenance du manifeste de surfaces lors de toute évolution publique ;
- une future politique de signature devra fournir une vraie identité cryptographique et pourra nécessiter une ADR complémentaire.

## Alternatives rejetées

### Un workflow par milestone

Rejeté : produit de la dette temporaire et rend la CI non durable.

### Déduire automatiquement le contrat public depuis la documentation

Rejeté : le texte humain n’est pas un contrat machine fiable.

### Forcer exactement les mêmes commandes/routes/outils

Rejeté : la parité porte sur l’intention et la sémantique, pas sur la forme du transport.

### Vérifier les updates automatiquement au démarrage

Rejeté : introduit une dépendance réseau cachée et rompt le caractère local-first.

### Télécharger/appliquer automatiquement une mise à jour trouvée

Rejeté : `update discovery != automatic update` ; une mutation de programme exige un flux séparé et explicitement autorisé.

## Validation requise avant Acceptée

- reactor complet Windows + Linux ;
- tests et architecture >= baseline M20 ;
- JaCoCo aggregate au-dessus des floors ;
- tests de convergence CLI/MCP/HTTP ;
- tests de cohérence documentaire ;
- SBOM généré ;
- provenance/checksums vérifiés ;
- tests update `file:` et HTTP explicite ;
- preuve qu’aucun check réseau n’est exécuté au démarrage.
