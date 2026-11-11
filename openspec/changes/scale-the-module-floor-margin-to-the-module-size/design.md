# Conception : proportionner à la taille du module la marge de son seuil de couverture

Conception allégée : le changement touche un gate de couverture et une décision d'architecture, pas le code de
production.

## Contexte

- [EXISTANT VÉRIFIÉ] `CoverageQualificationEvidenceTest#everyModuleFloorStaysInsideItsQualifiedWindow` (paquet `m21`)
  calcule pour chaque module et chaque grandeur : plus basse mesure conservée, dispersion retenue
  `max(écart observé, REFERENCE_*_SPREAD)`, marge `3 ×` dispersion, et refuse un seuil hors de
  `[plancher D2, (couvert − marge) / total]`. Les constantes `REFERENCE_LINE_SPREAD = 24L` et
  `REFERENCE_BRANCH_SPREAD = 11L` (lignes 66-67) servent aussi à la marge des ratchets d'échelle.
- [EXISTANT VÉRIFIÉ] Les résumés de `config/coverage-qualification/per-module/` portent les compteurs de chaque module
  (`module.<artifactId>.lineCovered|lineMissed|branchCovered|branchMissed`) et les totaux de la population ; le test
  exige déjà que les compteurs fassent les totaux.
- [EXISTANT VÉRIFIÉ] `GEN-0163`, décision 4 : « le seuil d'un module sous la plus basse mesure de ce module, d'au moins
  trois fois la dispersion (celle des runs gardés ou la référence, la plus large) ».
- [EXISTANT VÉRIFIÉ] `.claude/rules/adr.md` : une décision qui change en partie se réécrit dans un ADR consolidé qui
  remplace le précédent ; pas de section d'amendement.

## Exigences couvertes

| Capacité | Exigence (titre exact) | Rubriques de conception qui y répondent |
|---|---|---|
| `coverage-qualification-evidence` | La dispersion d'un module se lit dans la preuve conservée | D1, D2, D3 |

## Performance et stratégie de mesure

Fenêtres calculées le 11/11/2026 sur la preuve conservée (`dbd65062`, quatre résumés aux compteurs identiques pour
ces modules, écart observé nul), population 29 900 lignes / 10 148 branches, plancher D2 0,40 / 0,35 :

| Clé | Couvert / total | Marge actuelle | Fenêtre actuelle | Dispersion proportionnelle | Marge proposée | Borne haute proposée |
|---|---|---|---|---|---|---|
| `provider-testkit` lignes | 51 / 63 | 72 | vide | ⌈24 × 63 / 29 900⌉ = 1 | 3 | 0,761 |
| `provider-testkit` branches | 22 / 32 | 33 | vide | ⌈11 × 32 / 10 148⌉ = 1 | 3 | 0,593 |
| `provider-reference` lignes | 62 / 70 | 72 | vide | 1 | 3 | 0,842 |
| `provider-reference` branches | 6 / 10 | 33 | vide | 1 | 3 | 0,300 < 0,35 : **vide** |
| `integration-minos` branches | 50 / 71 | 33 | vide | 1 | 3 | 0,661 |
| `integration-nexus` branches | 23 / 33 | 33 | vide | 1 | 3 | 0,606 |

Les bornes hautes sont tronquées au millième. Elles se relisent au moment d'appliquer : une requalification entre-temps
change la preuve. Cinq clés obtiennent une fenêtre non vide ; la sixième demande un test dans
`morpheus-provider-reference` puis une requalification (proposal, Questions ouvertes).

## Décisions

### D1 : une dispersion de référence proportionnelle à la part du module
- **Nature** : [PROPOSITION] — à valider par le mainteneur, avec l'ADR consolidé
- **Exigences servies** : `coverage-qualification-evidence` › La dispersion d'un module se lit dans la preuve conservée
- **Choix** : `référence du module = ⌈référence de l'échelle × total du module / total de la population⌉`, par grandeur ;
  dispersion retenue = `max(écart observé, référence du module)` ; marge = `3 ×` dispersion retenue. La part est lue
  dans le même résumé conservé que la mesure.
- **Raison** : la dispersion de référence est une mesure de toute la population (24 lignes entre plateformes sur
  ~30 000) ; l'imposer entière à un module de 63 lignes suppose que toute la variation de la population puisse s'y
  concentrer, ce que l'écart observé de chaque module, conservé et lu par le gate, dément. L'écart observé reste le
  garde-fou : un module réellement instable garde sa propre marge.
- **Alternatives écartées** : (a) un plancher de marge absolu plus petit pour les modules sous une taille donnée — un
  seuil de taille inventé, sans source ; (b) une marge en pourcentage du module — pour un gros module elle dépasserait
  la référence de l'échelle et resserrerait des fenêtres qualifiées sans raison mesurée ; (c) laisser les petits
  modules au plancher et les retirer de la population — `GEN-0163` décision 3 garde l'outillage dans la population, et
  un module sans seuil peut cesser de se tester sans être nommé, ce que le constat reproche.
- **Compromis** : pour un petit module, un seul test spécifique à une plateforme pèse plus lourd que la marge ; la
  qualification sur les deux plateformes le voit dans l'écart observé, à condition que la preuve conservée porte des
  runs des deux systèmes (déjà exigé).
- **ADR** : **ADR à proposer** — consolidé, il reprend toute la décision de `GEN-0163` avec la décision 4 réécrite, et
  remplace `GEN-0163`, qui part en archive. Numéro : celui qui suit le plus haut attribué dans le registre et l'archive,
  relu au moment de l'écrire.

### D2 : les ratchets et plafonds d'échelle gardent la référence entière
- **Nature** : [DÉCISION]
- **Exigences servies** : même exigence, scénario « Les plafonds et les ratchets ne changent pas de marge »
- **Choix** : seule la fenêtre d'un seuil de module emploie la référence proportionnelle ; `everyCoverageRatchetKeepsThreeTimesTheSpreadUnderTheLowestKeptMeasurement`
  n'est pas modifié.
- **Alternatives écartées** : proportionner aussi la marge d'échelle — la part de l'échelle dans elle-même vaut 1, ce
  serait une réécriture sans effet.
- **ADR** : couvert par l'ADR consolidé de D1.

### D3 : relever les clés dans le même changement, sans nouvelle mesure
- **Nature** : [DÉCISION]
- **Exigences servies** : même exigence (le seuil n'a de sens que s'il est posé dans la fenêtre ouverte)
- **Choix** : les cinq clés dont la fenêtre s'ouvre sont posées au plus haut millième de leur fenêtre, par la procédure
  de la skill `coverage-ratchet` (étape 5) ; la clé de branches de `provider-reference` reste au plancher.
- **Alternatives écartées** : laisser les clés au plancher en attendant une requalification — la fenêtre ouverte ne
  protégerait rien, et la preuve conservée suffit (`per-module-coverage-floor` › Le seuil d'un module reste dans sa
  fenêtre qualifiée).
- **Compromis** : une marge de trois lignes ou trois branches : un test retiré d'un petit module fait échouer le build
  sous le nom du module, ce qui est l'effet recherché.
- **ADR** : non concerné au-delà de D1.

## Écarts constatés

| Constat | Source A | Source B | Traitement proposé |
|---|---|---|---|
| L'audit dit qu'une marge proportionnelle « fermerait ce constat » ; elle laisse vide la fenêtre des branches de `provider-reference` | `docs/project/audits/2026-10-09/findings.md`, `AUD-TRV-02` | calcul ci-dessus | signalé en question ouverte ; le relevé daté n'est pas réécrit |

## Stratégie de vérification

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| La dispersion d'un module … | Un module plus instable que la référence | test existant de la fenêtre, adapté à la référence proportionnelle | à adapter |
| idem | Un module stable / Un petit module stable | test sur résumés synthétiques : module de 63 lignes, écart nul → marge 3, refus qui nomme la part | à écrire |
| idem | Les plafonds et les ratchets ne changent pas de marge | `everyCoverageRatchetKeepsThreeTimesTheSpreadUnderTheLowestKeptMeasurement` inchangé et vert | existant |
| idem | (seuils posés) | `everyModuleFloorStaysInsideItsQualifiedWindow` vert avec les cinq clés relevées ; violation plantée : une clé un millième au-dessus de sa borne | à écrire |

## Documentation durable et diagrammes à mettre à jour

- ADR consolidé (nouveau) ; `GEN-0163` archivé avec son bandeau ; `docs/architecture/adr/README.md` et
  `docs/architecture/arc42/09-decisions.md` ; références à `GEN-0163` dans `.claude/rules/testing.md` et ailleurs,
  cherchées dans tout le dépôt (`rules/documentation.md`, procédure de déplacement).
- `.claude/rules/testing.md`, § « Un seuil propre à chaque module », puce **Fenêtre**.
- `.claude/skills/coverage-ratchet/SKILL.md`, si elle décrit la dispersion d'un module.
- `docs/developer/specifications/coverage-qualification-evidence.md` et `per-module-coverage-floor.md`.
- Aucun diagramme : un calcul, sans flux ni état.

## Questions ouvertes

Voir `proposal.md` : acceptation de D1 par le mainteneur (bloquante pour l'application) ; branches de
`morpheus-provider-reference` (hors changement).
