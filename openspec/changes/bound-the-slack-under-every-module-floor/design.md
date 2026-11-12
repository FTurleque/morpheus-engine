# Conception : borner le mou sous chaque seuil de couverture d'un module

Conception allégée : le changement touche un gate de couverture, un fichier de seuils et une décision d'architecture,
pas le code de production.

## Contexte

- [EXISTANT VÉRIFIÉ] `CoverageQualificationEvidenceTest#floorWindows` (paquet `m21`) admet tout seuil au plancher D2
  sans lire la preuve, et pour un seuil au-dessus calcule dans `windowProblem` la marge
  `headroom = plus bas couvert conservé − ⌈seuil × total⌉`, refusée sous `3 ×` la dispersion retenue
  `max(écart des runs conservés, ⌈référence de l'échelle × total du module / population⌉, 1)`. Aucune borne basse.
- [EXISTANT VÉRIFIÉ] `GEN-0166` (archivé), décision 5 : la marge d'un seuil de module suit sa taille ; section Risques : « Rien
  dans le build n'interdit de baisser un seuil dans sa fenêtre ».
- [EXISTANT VÉRIFIÉ] `.claude/rules/adr.md` : une décision qui change en partie se réécrit dans un ADR consolidé qui
  remplace le précédent.

## Exigences couvertes

| Capacité | Exigence (titre exact) | Rubriques de conception qui y répondent |
|---|---|---|
| `per-module-coverage-floor` | Le seuil d'un module reste dans sa fenêtre qualifiée | D1, D2, D3 |

## Performance et stratégie de mesure

Seuils recalculés le 12/11/2026 sur la preuve conservée (`dbd65062`, quatre résumés), plancher D2 0,40 / 0,35. Sont
listées les vingt-trois clés dont la marge dépasse neuf dispersions retenues ; la valeur cible est le plus petit
millième dont la marge reste dans la bande `[3 s, 9 s]`. Le total d'un module varie de quelques unités d'un résumé conservé à l'autre ; le gate lit celui du premier, et les
valeurs ci-dessous sont celles que son refus a nommées.

| Clé | Valeur | Plus bas couvert / total | Dispersion `s` | Marge actuelle | Cible | Marge cible |
|---|---|---|---|---|---|---|
| `morpheus-api` branches | 0,817 | 1147 / 1363 | 2 | 33 | 0,829 | 17 |
| `morpheus-api` lignes | 0,927 | 3911 / 4137 | 4 | 76 | 0,937 | 34 |
| `morpheus-cli` branches | 0,772 | 860 / 1071 | 2 | 33 | 0,787 | 17 |
| `morpheus-cli` lignes | 0,873 | 2638 / 2936 | 3 | 74 | 0,890 | 24 |
| `morpheus-domain` branches | 0,766 | 348 / 411 | 1 | 33 | 0,825 | 8 |
| `morpheus-domain` lignes | 0,880 | 1035 / 1094 | 1 | 72 | 0,938 | 8 |
| `morpheus-integration-minos` lignes | 0,560 | 184 / 200 | 1 | 72 | 0,875 | 9 |
| `morpheus-integration-nexus` lignes | 0,473 | 154 / 173 | 1 | 72 | 0,839 | 8 |
| `morpheus-mcp-transport` branches | 0,682 | 254 / 324 | 2 | 33 | 0,729 | 17 |
| `morpheus-mcp-transport` lignes | 0,831 | 743 / 807 | 1 | 72 | 0,910 | 8 |
| `morpheus-mcp` branches | 0,607 | 177 / 237 | 1 | 33 | 0,709 | 8 |
| `morpheus-mcp` lignes | 0,867 | 1443 / 1580 | 2 | 73 | 0,902 | 17 |
| `morpheus-provider-markdown` branches | 0,444 | 81 / 108 | 1 | 33 | 0,667 | 8 |
| `morpheus-provider-markdown` lignes | 0,727 | 328 / 352 | 1 | 72 | 0,907 | 8 |
| `morpheus-provider-openspec` branches | 0,794 | 377 / 433 | 1 | 33 | 0,850 | 8 |
| `morpheus-provider-openspec` lignes | 0,865 | 978 / 1047 | 1 | 72 | 0,926 | 8 |
| `morpheus-provider-sdk` branches | 0,665 | 234 / 302 | 2 | 33 | 0,716 | 17 |
| `morpheus-provider-synthetic` branches | 0,713 | 205 / 241 | 1 | 33 | 0,814 | 8 |
| `morpheus-provider-synthetic` lignes | 0,807 | 543 / 583 | 1 | 72 | 0,916 | 8 |
| `morpheus-store-memory` branches | 0,428 | 152 / 278 | 1 | 33 | 0,515 | 8 |
| `morpheus-store-memory` lignes | 0,652 | 563 / 752 | 1 | 72 | 0,737 | 8 |
| `morpheus-store-sqlite` branches | 0,678 | 859 / 1217 | 2 | 33 | 0,692 | 16 |
| `morpheus-store-sqlite` lignes | 0,793 | 3228 / 3978 | 4 | 73 | 0,803 | 33 |

Les neuf autres clés sont déjà dans la bande, y compris `morpheus-application` et les lignes de `morpheus-provider-sdk`,
ou au plancher D2 avec une fenêtre vide (branches de `morpheus-provider-reference`). Contre-mesure sur la tête :
l'artefact `m21-integrity-Windows` du run `43270298003` (`12cd8874`) donne pour chacun des vingt-trois modules un ratio
au-dessus de sa cible. Le run Linux du même commit a échoué sur une résolution de plugin Maven (« Connection reset »)
avant les tests ; la CI de la PR fournit la mesure Linux.

## Décisions

### D1 : une borne basse à neuf dispersions retenues
- **Nature** : [DÉCISION] — mainteneur, 12/11/2026
- **Exigences servies** : `per-module-coverage-floor` › Le seuil d'un module reste dans sa fenêtre qualifiée
- **Choix** : un seuil de module que la preuve porte est refusé si `headroom > 9 × s`, `s` étant la même dispersion
  retenue que pour la borne haute. Le refus nomme le plus petit seuil admis,
  `⌈(plus bas couvert − 9 s) / total × 1000⌉ / 1000`, et la bande `[3 s, 9 s]`.
- **Raison** : neuf dispersions font trois fois la marge exigée par la borne haute ; la bande a la largeur de deux
  marges, assez pour qu'un seuil tienne une variation ordinaire sans requalification, trop peu pour qu'un module perde
  l'essentiel de sa couverture en silence.
- **Alternatives écartées** : (a) six dispersions — une bande d'une seule marge, que la prochaine hausse de couverture
  ferait sortir à chaque requalification ; (b) une borne calculée sur la référence entière de l'échelle (9 × 24
  lignes) — elle ne refuserait ni nexus ni minos, dont toute la population tient dans 216 lignes ; (c) une borne en
  points de pourcentage — elle ne dit rien d'un module de quelques dizaines de lignes et invente un seuil sans source.
- **Compromis** : une requalification qui fait monter la couverture d'un module peut sortir son seuil par le bas ; elle
  relève alors ce seuil dans le même changement, ce que la procédure de la skill `coverage-ratchet` fait déjà.
- **ADR** : `GEN-0167`, consolidé, remplace `GEN-0166` (archivé).

### D2 : un seuil au plancher D2 est jugé par la même bande
- **Nature** : [DÉCISION]
- **Exigences servies** : même exigence, scénarios « au plancher D2 »
- **Choix** : le contrôle de la borne basse s'applique aussi au seuil égal au plancher D2 d'un module que la preuve
  porte. Si la fenêtre est vide, la marge au plancher est sous `3 s`, donc sous `9 s` : il passe. Si la bande est
  au-dessus du plancher, il est refusé, et un seuil plus haut y tient forcément.
- **Alternatives écartées** : exempter le plancher — ce serait le trou d'`AUD-TRV-02` (un module au plancher sans
  fenêtre vide) rouvert sous un autre nom.

### D3 : les vingt-trois clés en bas de bande, sans nouvelle mesure
- **Nature** : [DÉCISION] — mainteneur, 12/11/2026
- **Exigences servies** : même exigence
- **Choix** : chaque clé hors de la bande prend le plus petit millième qu'elle admet (tableau ci-dessus).
- **Raison** : la porte se ferme avec la marge la plus large contre la variation entre plateformes ; la borne haute
  reste l'affaire d'un relèvement décidé, qui garde au moins trois dispersions.
- **Alternatives écartées** : le haut de fenêtre — une marge de trois lignes sur onze modules, qu'un test propre à une
  plateforme ferait tomber ; ne relever que nexus et minos — le gate refuserait les vingt et une autres.

## Écarts constatés

| Constat | Source A | Source B | Traitement proposé |
|---|---|---|---|
| L'audit compte deux clés en mou ; la bande en refuse vingt-trois | `findings.md`, `AUD-TST-26` | tableau ci-dessus | les deux clés du constat sont les seules loin en points ; les autres le sont en dispersions. Relevé daté non réécrit |

## Stratégie de vérification

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| Le seuil d'un module reste dans sa fenêtre qualifiée | Un seuil abandonné loin sous la mesure | `aFloorAbandonedFarUnderItsMeasurementIsRefusedAndNamesTheLowestAdmittedFloor` | à écrire |
| idem | Un seuil au plancher D2 alors que la bande est au-dessus | `aFloorAtTheD2FloorIsRefusedWhenTheBandLiesAboveIt` | à écrire |
| idem | Un seuil au plancher D2 dont la fenêtre est vide | même test, cas fenêtre vide | à écrire |
| idem | Un seuil posé sur la mesure / sous le plancher D2 | tests existants de la fenêtre | existant |
| idem | Un seuil dans la fenêtre | `everyModuleFloorStaysInsideItsQualifiedWindow` vert avec les clés relevées ; violation plantée : nexus remis à 0,473 | à écrire |

## Documentation durable et diagrammes à mettre à jour

- `GEN-0167` (nouveau) ; `GEN-0166` (archivé) avec son bandeau ; index du registre et `09-decisions.md` ; liens vers
  `GEN-0166` (archivé) repointés dans tout le dépôt, relevés datés compris pour leurs seuls liens.
- `.claude/rules/testing.md`, puce **Fenêtre** ; `.claude/skills/coverage-ratchet/SKILL.md` ; en-tête du fichier de
  seuils ; `docs/developer/specifications/per-module-coverage-floor.md`.
- Aucun diagramme : un calcul, sans flux ni état.

## Questions ouvertes

Aucune.
