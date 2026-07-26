# M18 — Real Providers & Multi-Provider Composition

Statut : **✅ VALIDÉ TECHNIQUEMENT — PR #86 prête à intégrer**

Dernière mise à jour : 26 juillet 2026

Issue : **#85**  
PR : **#86**  
Branche : `m18/multi-provider-composition`

## 1. Question de sortie

> **MORPHEUS peut-il construire une vue cohérente à partir de plusieurs providers réels en conservant identité, provenance, priorité et conflits sans devenir dépendant d'un format particulier ?**

**Réponse : OUI.**

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

## 4. Architecture réalisée

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

### M18-S1 — Domaine / contrats ✅

- [x] `ProviderContribution` canonique et provider-neutral ;
- [x] provenance d'observation explicite ;
- [x] priorité source explicite ;
- [x] `CompositionConflict` + type/champ/candidats/résolution ;
- [x] diagnostics et résultat de composition déterministes.

### M18-S2 — Deuxième provider réel ✅

- [x] module `morpheus-provider-markdown` ;
- [x] discovery/probe d'un workspace Markdown structuré ;
- [x] lecture réelle de requirements, changes, constraints, decisions, tasks et acceptance criteria ;
- [x] provenance exacte fichier/plage de lignes + hash ;
- [x] diagnostics d'entrée invalides/partiels ;
- [x] aucun type Markdown hors adapter.

### M18-S3 — Composition multi-provider ✅

- [x] sélection explicite de plusieurs providers compatibles ;
- [x] ordre de priorité stable ;
- [x] rapprochement par clé logique provider-neutral ;
- [x] agrégation sans last-write-wins silencieux ;
- [x] absence d'un provider optionnel non fatale ;
- [x] absence d'un provider requis échoue explicitement.

### M18-S4 — Identité / conflits ✅

- [x] identités provider-scoped conservées ;
- [x] continuité/rapprochement uniquement par clé logique explicite ;
- [x] conflit de contenu explicite ;
- [x] conflit d'ownership explicite ;
- [x] conflit de type/identité explicite ;
- [x] absence vs valeur présente représentable comme conflit ;
- [x] ambiguïté conservée et requêtable ;
- [x] provenance de chaque candidat conservée.

### M18-S5 — Persistance ✅

- [x] store Memory ;
- [x] SQLite V012 ;
- [x] reopen exact ;
- [x] provenance/priorité/conflits persistés ;
- [x] état de composition snapshot-scoped ;
- [x] transaction SQLite restaure son mode auto-commit même en erreur.

### M18-S6 — Surfaces ✅

- [x] CLI `composition sync/status/conflicts` ;
- [x] MCP read-only `get_composition_status` / `list_composition_conflicts` ;
- [x] HTTP `GET /projects/{projectId}/composition` ;
- [x] HTTP `GET /projects/{projectId}/composition/conflicts` ;
- [x] OpenAPI 1.7.0 ;
- [x] packaging du provider Markdown, des surfaces M18 et de V012 ;
- [x] `validate-m18.cmd` + diagnostic automatique du premier échec.

### M18-S7 — Gate ✅ VALIDÉ

- [x] deux providers réels validés ;
- [x] même projet multi-provider ;
- [x] conflits contenu/ownership/type explicites et requêtables ;
- [x] Memory == SQLite ;
- [x] SQLite close/reopen ;
- [x] reactor Maven complet ;
- [x] packaging Windows + smokes ;
- [x] `VALIDATION_M18.md` ;
- [x] ADR-0084 acceptée après preuve.

## 6. Gate M18 final

Head de code testé :

```text
7e8caacff567f51354fcb88bd7505a6d135071c0
```

Résultat :

```text
at least two real providers validated                          PASS
same project can consume multiple providers                    PASS
conflicts are explicit and queryable                           PASS
reopen SQLite preserves provider provenance                    PASS
no provider-specific types leak into domain/application        PASS
full Maven reactor                                             PASS
Windows packaging + smokes                                     PASS

TOTAL tests                                                    418/418 PASS
Architecture                                                   170/170 PASS
Failures / Errors / Skipped                                    0 / 0 / 0
Portable ZIP                                                   33,919,431 bytes
```

Commande canonique : `validate-m18.cmd`.

Preuve : [`../validation/VALIDATION_M18.md`](../validation/VALIDATION_M18.md).

## 7. Traçabilité post-gate

Le code et les artefacts exécutables ont été validés sur `7e8caacff567f51354fcb88bd7505a6d135071c0`.

Les commits postérieurs au gate sont réservés à la clôture documentaire M18 : validation, statut ADR et plan. Ils ne modifient ni runtime, ni tests, ni migration, ni build.

La PR #86 peut être passée Ready puis intégrée si son diff post-gate reste exclusivement documentaire.