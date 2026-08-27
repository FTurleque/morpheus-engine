# Preuves de validation

Cette section regroupe les preuves de sortie des phases, jalons, consolidations et releases MORPHEUS.

## Index

```text
VALIDATION_C0.md
VALIDATION_M0.md → VALIDATION_M14.md
VALIDATION_D0.md
VALIDATION_M15.md → VALIDATION_M20.md
VALIDATION_D1.md
VALIDATION_R1.md
VALIDATION_M21.md → VALIDATION_M28.md
VALIDATION_R2.md
VALIDATION_R3.md
VALIDATION_D2.md   ← preuve historique D2
```

Chaque preuve historique conserve les décisions, SHA, commandes et résultats réellement observés. Une preuve historique n’est jamais réécrite pour fabriquer un PASS ni pour adopter rétroactivement une commande moderne.

## Baseline stable publiée

MORPHEUS **1.2.0** a été consolidé via R3.

```text
qualified executable    d08542026817f0d743766656a0197790c6809eca
main release commit     3ad9ebf030b58df97482e21e272c24feae6b9d86
tag                     v1.2.0
Windows tests           608 PASS
Linux/WSL tests         608 PASS
architecture            243 PASS sur les deux plateformes
same SHA                PASS
exact-tag builds        PASS Windows + Linux
GitHub Release          stable / latest / 8 assets
published parity        8/8 PASS
```

Preuve : [`VALIDATION_R3.md`](VALIDATION_R3.md).

## D2 — Post-R3 Repository Hardening

Statut courant : **COMPLETE / QUALIFIED / MERGED**.

La preuve `VALIDATION_D2.md` est volontairement conservée comme document historique : son texte enregistre le candidat et le protocole de qualification au moment où le fichier a été écrit. La qualification finale exact-head a ensuite été attachée à la PR #121, sans réécrire cette preuve pour lui attribuer un SHA qu’elle ne contenait pas.

```text
issue                   #120 CLOSED / completed
PR                      #121 MERGED
qualified exact head    fa54b3d6a316357b2ef79afd2243619a64a05f3b
develop merge commit    c12882d6e43daab600f6580f22f8eff2fbc6f4de
Windows local gate      PASS
Linux/WSL local gate    PASS
same exact SHA          PASS
.github/workflows delta NONE
CI used as D2 gate      false
Jackson                 3.1.5 LTS
sqlite-jdbc             3.53.2.0
SCA                     OWASP Dependency-Check 12.2.2
coverage floors         40% line / 35% branch
dependency hygiene      blocking
```

Preuve historique : [`VALIDATION_D2.md`](VALIDATION_D2.md).
Preuves exact-head finales : PR #121.

La règle « CI non utilisée » décrit uniquement le protocole D2 historique. Les protections continues de la baseline `1.2.1` utilisent désormais GitHub Actions.

## Baseline développement 1.2.1

Les validations courantes sont documentées dans [`../developer/BUILD_AND_TEST.md`](../developer/BUILD_AND_TEST.md).

```text
MORPHEUS CI                exact-head Windows + Ubuntu
MORPHEUS Security          OWASP Dependency-Check
MORPHEUS CodeQL            security-extended
Surefire total             >= 820
architecture               >= 258
JaCoCo global lines        >= 50.6%
JaCoCo global branches     >= 43.0%
PR changed lines           >= 80%
PR changed branches        >= 70%
SBOM / provenance          required
```

Ces ratchets appartiennent à la baseline de développement courante. Ils ne sont pas injectés rétroactivement dans les preuves historiques.

## Évolutions 1.x historiquement qualifiées

```text
M21  473 tests | architecture 187
M22  494 tests | architecture 190
M23  507 tests | architecture 195
M24  543 tests | architecture 221
M25  565 tests | architecture 231
M26  579 tests | architecture 234
M27  602 tests | architecture 238
R2   603 tests | architecture 238
M28  608 tests | architecture 243
R3   608 tests | architecture 243
```

Les comptes plus récents de la baseline corrective sont des mesures de CI courante et ne doivent pas être substitués aux comptes historiques ci-dessus.

## Politique de preuve

```text
historical SHA/result attribution   immutable
current CI checkout                 exact head
current CI platforms                Windows + Ubuntu
current SCA                         blocking at CVSS >= 7
current code scanning               CodeQL security-extended
release publication                 only from real vX.Y.Z tags reachable from main
external/admin settings             never claimed verified without direct evidence
```
