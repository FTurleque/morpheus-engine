# Gouvernance IA — Morpheus Engine

Ce document cartographie **tout le paramétrage IA du dépôt** (Copilot + Claude Code) : où
vit chaque artefact, son rôle, et comment il reste cohérent avec les autres. Il ne
duplique aucune règle métier — pour le contenu réel, suivre les renvois.

## Principe fondamental

**Deux surfaces IA, une seule vérité.** `.github/` (Copilot) et `.claude/` (Claude Code)
doivent toujours converger vers les mêmes règles. Le contenu détaillé (exemples réels,
invariants complets) vit dans `.claude/rules/*.md` ; `.github/` en dérive des artefacts
ciblés (instructions par chemin, prompts, skill) plutôt que de le recopier intégralement.
Toute divergence constatée entre les deux surfaces doit être corrigée **dans le même
changement**, des deux côtés.

**Aucun chiffre périssable n'est jamais recopié tel quel** dans un artefact IA — seuils de
coverage, nombre de tests, nombre d'ADR, version produit. Chaque artefact renvoie vers sa
source vivante (`config/m21-quality-ratchets.properties`, `pom.xml`, `docs/adr/` compté
par `glob`, les tests sous `morpheus-architecture-tests/`). Voir `.claude/rules/meta.md`.

## Matrice des artefacts

| Type | Emplacement | Portée | Versionné | Rôle |
|---|---|---|---|---|
| Instructions repo (Copilot) | `.github/copilot-instructions.md` | Tout le dépôt, chargé automatiquement par Copilot | ✅ | Résumé court + renvoi vers `.claude/rules/` |
| Instructions ciblées (Copilot) | `.github/instructions/*.instructions.md` | Par chemin (`applyTo`) | ✅ | Ciblage fin : architecture, sécurité, tests, gouvernance, build, style |
| Prompts (Copilot) | `.github/prompts/*.prompt.md` | Invocables à la demande | ✅ | Workflows guidés : audit, validation, bug-fix, santé dépôt |
| Skill projet (Copilot) | `.github/skills/morpheus-engine/SKILL.md` | Chargée si l'outil Copilot supporte les skills projet | ✅ | Carte de navigation du savoir Morpheus, anti-dérive |
| Hook Copilot | `.github/hooks/rtk-rewrite.json` | Repo — indépendant du profil utilisateur | ✅ | Réécriture des commandes shell via `rtk` |
| Règles Claude | `.claude/CLAUDE.md` + `.claude/rules/*.md` | Chargées automatiquement par Claude Code | ✅ | Source détaillée de référence, partagée avec Copilot |
| Agents Claude / Copilot (partagés) | `.claude/agents/*.md` | Invocables comme sub-agents par les deux outils (`architect`, `bug-investigator`, `contract-guardian`, `security-reviewer`) | ✅ | Revue spécialisée avec procédure et format de réponse stricts |
| Commandes Claude | `.claude/commands/*.md` | Invocables (`/governance`, `/security-audit`, `/test-gate`, `/validate`, `/milestone`, `/health`, `/bug-fix`) | ✅ | Équivalent des prompts Copilot, workflows pas-à-pas |
| Hooks Claude | `.claude/hooks/pre-bash.ps1`, `.claude/hooks/post-edit.ps1` | `PreToolUse`/`PostToolUse`, déclarés dans `.claude/settings.json` | ✅ | Garde-fous fail-open (avertir/bloquer sans jamais bloquer 100% par bug interne) |
| Permissions Claude | `.claude/settings.json` | Allow/deny globaux (force-push protégé, `rm -rf` non borné) | ✅ | Garde-fous non négociables |
| Permissions locales | `.claude/settings.local.json` | Allow additionnel (`rtk git *`) | ✅ (choix assumé du mainteneur) | Complète `settings.json` sans le dupliquer |

## Correspondance Claude ↔ Copilot

| Commande Claude | Prompt Copilot équivalent |
|---|---|
| `/governance` | `morpheus-governance-audit.prompt.md` |
| `/security-audit` | `morpheus-security-audit.prompt.md` |
| `/test-gate` | `morpheus-test-gate.prompt.md` |
| `/validate` | `morpheus-validate.prompt.md` |
| `/milestone` | `morpheus-milestone.prompt.md` |
| `/health` | `morpheus-health.prompt.md` |
| `/bug-fix` | `morpheus-bug-fix.prompt.md` |
| *(point d'entrée additionnel, pas d'équivalent Claude dédié)* | `morpheus-orchestrator.prompt.md` |

| Règle Claude (`.claude/rules/`) | Instruction Copilot (`.github/instructions/`) |
|---|---|
| `architecture.md` | `architecture.instructions.md` |
| `security.md` | `security.instructions.md` |
| `testing.md` | `testing.instructions.md` |
| `governance.md` | `governance.instructions.md` |
| `build.md` | `build.instructions.md` |
| `code-style.md` | `code-style.instructions.md` |
| `meta.md` | Pas de miroir dédié — le principe anti-dérive est répété dans `copilot-instructions.md` et dans chaque `*.instructions.md` |

## Agents — déjà partagés nativement, aucun miroir requis

Contrairement aux commandes (qui ont un équivalent Copilot sous forme de prompt), **les
agents `.claude/agents/*.md` n'ont pas besoin de miroir dans `.github/`** : les outils
Copilot capables de lancer des sub-agents lisent directement les définitions présentes
dans `.claude/agents/` — confirmé par la disponibilité effective de `architect`,
`bug-investigator`, `contract-guardian` et `security-reviewer` comme sub-agents invocables
tels quels, sans conversion ni fichier supplémentaire.

Conséquence pratique :
- **Ne jamais dupliquer** un agent dans `.github/agents/` (ce dossier n'existe pas et ne
  doit pas être créé pour ce besoin) — un seul fichier `.claude/agents/<nom>.md` sert les
  deux écosystèmes.
- Toute correction de dérive (chiffre périssable, seuil, milestone codé en dur) faite dans
  un agent `.claude/agents/*.md` bénéficie **automatiquement** aux deux surfaces — pas de
  changement supplémentaire à faire côté `.github/`.
- Le frontmatter `tools:` de chaque agent (`Read`, `Grep`, `Glob`, `Bash`, parfois `Edit`/
  `Write`) reste la nomenclature Claude Code ; les outils Copilot équivalents sont résolus
  par l'outil hôte au moment de l'invocation, pas par une déclaration dans le repo.

## RTK — statut de détection

RTK est configuré aux deux niveaux :

- **Claude Code** : `.claude/settings.local.json` autorise `Bash(rtk git *)` explicitement,
  en complément de `.claude/settings.json`.
- **Copilot** : `.github/hooks/rtk-rewrite.json` déclare un hook `PreToolUse` qui invoque
  `rtk hook copilot` — ce fichier est le miroir repo du hook utilisateur
  `~/.copilot/hooks/rtk-rewrite.json`. Le placer dans `.github/hooks/` rend la
  configuration **portable** : elle s'applique à quiconque clone le dépôt, sans dépendre
  du profil `~/.copilot` de la machine.

Aucune action supplémentaire n'est requise pour la détection — la présence du fichier au
niveau repo suffit à ce que l'outil Copilot local applique la règle de réécriture `rtk`
pour ce dépôt.

## Procédure de changement

Toute modification d'un artefact de gouvernance IA (`.github/instructions/`,
`.github/prompts/`, `.github/skills/`, `.claude/rules/`, `.claude/agents/`,
`.claude/commands/`, `.claude/hooks/`, `.claude/settings*.json`) doit :

1. Être répercutée **des deux côtés** (`.github` et `.claude`) si elle touche une règle
   partagée
2. Éviter tout chiffre périssable — renvoyer vers la source vivante
3. Être mentionnée dans la description de la PR (ce sont des fichiers de gouvernance, pas
   de simples fichiers de config)



