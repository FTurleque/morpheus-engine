# M18 — Real Providers & Multi-Provider Composition

Statut : **🚧 EN COURS — PR Draft**

Dernière mise à jour : 26 juillet 2026

Issue : **#85**  
Branche : `m18/multi-provider-composition`

## 1. Question de sortie

> **MORPHEUS peut-il construire une vue cohérente à partir de plusieurs providers réels en conservant identité, provenance, priorité et conflits sans devenir dépendant d'un format particulier ?**

## 2. Baseline d'entrée

```text
C0 -> M17       ✅ validés / intégrés
M17 merge       02bdb38669efc85af17343d15e689743362d2e12
M17             410/410 PASS
Architecture    167/167 PASS
Packaging Win   PASS
Docs reconcile  5ff4f376f480252da71c1d5f88538a0396013b2d
```

## 3. Invariants M18

```text
provider identifier != DomainIdentity
source path != identity
provider ownership is explicit
same logical entity may have multiple provider observations
precedence != provenance erasure
ambiguous continuity must be surfaced
conflict != silent last-write-wins
provider absence != project failure when optional
composition must be deterministic and explainable
provider-specific types never leak into domain/application contracts
```

## 4. Architecture cible

```text
OpenSpec provider            Structured Markdown provider
        |                              |
        +------ normalized reads ------+
                       |
             ProviderContribution
                       |
          MultiProviderCompositionService
                       |
        identity key / precedence policy
                       |
       composed content + explicit conflicts
                       |
        Memory / SQLite composition state
                       |
              CLI / MCP / HTTP queries
```

La composition intervient après la normalisation provider et avant la publication du snapshot. Aucun provider ne connaît les autres providers.

## 5. Slices

### M18-S1 — Domaine / contrats

- [ ] `ProviderContribution` canonique et provider-neutral ;
- [ ] provenance d'observation explicite ;
- [ ] priorité source explicite ;
- [ ] `CompositionConflict` + type/champ/candidats/résolution ;
- [ ] diagnostics et résultat de composition déterministes.

### M18-S2 — Deuxième provider réel

- [ ] module `morpheus-provider-markdown` ;
- [ ] discovery/probe d'un workspace Markdown structuré ;
- [ ] lecture réelle de requirements, changes, constraints, decisions, tasks et acceptance criteria ;
- [ ] provenance exacte fichier/section ;
- [ ] diagnostics d'entrée invalides/partiels ;
- [ ] aucun type Markdown hors adapter.

### M18-S3 — Composition multi-provider

- [ ] sélection explicite de plusieurs providers compatibles ;
- [ ] ordre de priorité stable ;
- [ ] rapprochement par clé logique provider-neutral ;
- [ ] agrégation sans last-write-wins silencieux ;
- [ ] absence d'un provider optionnel non fatale.

### M18-S4 — Identité / conflits

- [ ] continuité d'identité explicable ;
- [ ] conflit de contenu explicite ;
- [ ] conflit de type/ownership explicite ;
- [ ] ambiguïté conservée et requêtable ;
- [ ] provenance de chaque candidat conservée.

### M18-S5 — Persistance

- [ ] store Memory ;
- [ ] SQLite V012 ;
- [ ] reopen exact ;
- [ ] provenance/priorité/conflits persistés ;
- [ ] état de composition snapshot-scoped.

### M18-S6 — Surfaces

- [ ] CLI composition status/conflicts ;
- [ ] MCP read-only composition tools ;
- [ ] HTTP composition endpoints ;
- [ ] OpenAPI 1.7.0 ;
- [ ] packaging du provider Markdown et de V012.

### M18-S7 — Gate

- [ ] deux providers réels validés ;
- [ ] même projet multi-provider ;
- [ ] conflits explicites/requêtables ;
- [ ] Memory == SQLite ;
- [ ] SQLite close/reopen ;
- [ ] reactor Maven complet ;
- [ ] packaging Windows + smokes ;
- [ ] `VALIDATION_M18.md` ;
- [ ] ADR M18 acceptées après preuve.

## 6. Gate M18

```text
at least two real providers validated                          PENDING
same project can consume multiple providers                    PENDING
conflicts are explicit and queryable                           PENDING
reopen SQLite preserves provider provenance                    PENDING
no provider-specific types leak into domain/application        PENDING
full Maven reactor PASS                                        PENDING
Windows packaging + smokes PASS                                PENDING
```

Aucun PASS n'est revendiqué avant exécution réelle du gate sur le head final de code.