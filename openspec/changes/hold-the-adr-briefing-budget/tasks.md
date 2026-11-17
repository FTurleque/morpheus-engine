# Tâches : les sections remises aux agents tiennent dans le briefing d'Archgate

## 1. Le contrôle

- [x] 1.1 Écrire `AdrBriefingBudgetTest` (`@CoherenceCheck`) : mesure de `Decision` et `Do's and Don'ts` de chaque
  ADR du registre, fins de ligne normalisées, refus d'un dépassement, d'une section vide et d'un registre vide —
  exigences : les deux ; vérification : la méthode sur le registre échoue avant la tâche 2 en nommant les 56 ADR.
- [x] 1.2 Réécrire en place la borne dans `.claude/rules/adr.md` (deux sections, sous-sections comprises) et nommer
  le test dans sa section « Contrôles » — vérification : contrôles `-Dgroups=coherence` verts.
- [x] 1.3 Dire la mesure et la cécité CRLF d'Archgate dans `docs/developer/ARCHGATE.md`, et la borne dans le gabarit
  d'ADR — vérification : relecture.

## 2. Le registre

- [x] 2.1 Ramener les 56 ADR hors borne sous 2 000 caractères par section, chaque règle gardée dans `Decision`, le
  reste déplacé dans `Context` — exigence : la première ; vérification : `AdrBriefingBudgetTest` vert, et
  `archgate check` sur une copie LF du registre sans `briefingWarnings`.
- [x] 2.2 Relire chaque `Decision` réécrite contre sa version antérieure : aucune règle, aucun nom de classe, de test,
  de constante, de code de refus ni référence `ADR-NNNN` ne disparaît — vérification : revue du diff.

## 3. Documentation durable

- [x] 3.1 Créer `docs/developer/specifications/adr-briefing-budget.md` et sa ligne dans l'index du dossier —
  vérification : `RepositoryDocumentationCoherenceTest` (liens) vert.

## 4. Vérification d'ensemble

- [x] 4.1 Lancer `openspec validate hold-the-adr-briefing-budget --strict` — vérification : valide.
- [x] 4.2 Lancer `./mvnw -o test -pl morpheus-architecture-tests -Dgroups=coherence` — vérification : 0 échec.
- [x] 4.3 Lancer `archgate check` — vérification : aucune violation.

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
