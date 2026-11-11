# ADR-0153 — Les binaires Windows sont signés sur demande, par un seul point d'entrée, avec une identité que seul le workflow de release atteint

> **Archivée.** Cette proposition est rejetée : MORPHEUS n'a accès à aucun certificat de signature sans coût (la voie gratuite pour l'open source exige une licence approuvée par l'OSI), et aucune release n'est signée. Le mécanisme livré avec elle reste tenu par ses tests et par `.claude/rules/security.md`, pas par ce texte, qui n'est pas tenu à jour.

- Statut : **Rejetée**
- Date : 2026-10-29

## Context

L'installeur Windows, le désinstalleur qu'il déploie et le lanceur `morpheus.exe` sont publiés sans signature
Authenticode (constat AUD-DEP-05 de l'audit du 2026-10-09) : Windows les présente comme venant d'un éditeur inconnu, et
l'utilisateur n'a aucun moyen natif de distinguer l'installeur officiel d'un substitut. ADR-0089 annonce une politique
de signature fondée sur une identité cryptographique réelle.

Faits mesurés le 2026-10-29 :

- l'installeur ne copie pas l'image applicative : il embarque `morpheus-payload.zip`, et l'archive portable est faite
  avant lui ; seul le compilateur Inno Setup peut signer le désinstalleur qu'il génère. La directive de signature
  d'Inno Setup ne signe donc que l'installeur et le désinstalleur, jamais le lanceur ;
- le compilateur épinglé (7.0.2) accepte un outil défini sur sa ligne de commande (`/S<nom>=<commande>`), le lance
  pour l'installeur **et** pour le désinstalleur, et **journalise la commande complète** de l'outil ;
- `jpackage` produit un lanceur en lecture seule ; l'image ne contient qu'un exécutable hors du runtime ;
- depuis juin 2023, la clé d'un certificat de signature de code public vit sur un matériel certifié (jeton ou HSM
  cloud), pour OV comme pour EV ; un jeton USB ne se branche pas sur un runner hébergé, où la release est construite
  depuis le tag exact et attestée ;
- une copie d'un binaire Windows listé dans un catalogue se vérifie par le catalogue (`Valid`, type `Catalog`), pas par
  une signature qu'elle porte ; `New-SelfSignedCertificate` dépose aussi une copie du certificat parmi les autorités
  intermédiaires de l'utilisateur.

Options écartées : signer après le build dans un job séparé (l'archive, sa somme, la charge utile et le manifeste
seraient refaits hors des scripts de build, que la répétition cesserait d'exercer) ; signer « quand le secret est
présent » (une clé absente, renommée ou un fork publierait en silence une release non signée) ; épingler une empreinte
de certificat (un certificat public vit au plus environ quinze mois) ; un certificat d'essai durable en secret du dépôt
(un secret que le workflow nocturne pourrait lire). Le fournisseur et la garde de la clé (service géré, HSM cloud
d'une autorité, jeton matériel), le niveau de validation et l'identité légale restent à trancher par le mainteneur.

## Decision

Un build Windows signe quand son appelant le demande, par un état explicite : `none`, `test` (identité créée pour une
exécution) ou `release` (identité de l'éditeur déclaré). Il imprime l'état. Un build à qui l'on demande de signer et
qui ne peut pas signer un fichier attendu échoue ; il ne produit jamais d'artefact non signé à la place.

Le lanceur est signé juste après `jpackage`, avant l'archive, la charge utile, les sommes, le manifeste et
l'attestation ; l'installeur et le désinstalleur sont signés par le compilateur. Un seul script,
`distribution/windows/sign-windows-binary.ps1`, appelle un outil de signature : il reçoit un chemin de fichier, et
l'état et l'identité lui arrivent par l'environnement ou une identité fédérée, jamais par un argument ni un log.

L'identité de release n'est atteignable que par `release.yml`, sur un tag de version, derrière un environnement
protégé, après les gates. Toute signature de release est horodatée par l'autorité déclarée, sans repli.

Une déclaration versionnée, `distribution/windows/authenticode-signing.properties`, porte l'éditeur attendu (sujet
exact du certificat), la première version signée et l'autorité d'horodatage. À partir de cette version, une release
non signée est refusée au build et à la qualification ; en dessous, elle se déclare non signée. Les releases publiées
avant cette version ne sont ni re-signées ni republiées. La répétition nocturne signe avec une identité jetable,
jamais approuvée, retirée de tous les magasins à chaque sortie.

## Do's and Don'ts

### Do

- Passer l'état de signature en paramètre (`-SigningState`) et laisser le build l'imprimer.
- Signer le lanceur avant `Compress-PortableArchiveWithRetry`, et relire la signature dans l'archive et la charge utile.
- Mapper tout secret de workflow sous `env:` de l'étape qui l'utilise.
- Lire l'éditeur, la version de base et l'autorité d'horodatage dans la déclaration, et nulle part ailleurs.
- Remplir les trois champs de la déclaration dans le changement qui câble l'identité dans `release.yml`.

### Don't

- Ne pas signer parce qu'un credential est présent, ni sauter la signature parce qu'il manque.
- Ne pas passer de credential, de mot de passe, de PIN ou de jeton en argument, dans la commande de l'outil remise au
  compilateur, ni dans un bloc `run:`.
- Ne pas nommer l'environnement, les variables `MORPHEUS_WINDOWS_SIGNING_*` ou l'état `release` dans un workflow
  qu'une pull request ou une planification peut lancer.
- Ne pas appeler un outil de signature ailleurs que dans le point d'entrée, dans un script de build, de validation
  ou un workflow ; seuls les tests de `scripts/tests` signent leurs propres binaires jetables, avec une identité
  d'essai qu'ils retirent.
- Ne pas ajouter l'identité d'essai à un magasin racine, ni la laisser survivre à l'exécution.
- Ne pas signer l'installeur de smoke avec l'identité de release.

## Consequences

### Positives

- Archive, charge utile, sommes, manifeste et attestation décrivent les mêmes octets signés.
- Le changement de fournisseur ne touche que l'intérieur du point d'entrée et l'étape de credential de `release.yml`.
- Le chemin de signature est exercé chaque nuit sans aucun secret.

### Négatives

- Le fournisseur, l'identité légale et l'environnement protégé dépendent d'actions du mainteneur hors du dépôt.
- La répétition n'exerce ni l'identité de release, ni le service de signature, ni l'autorité d'horodatage.

### Risques

- Un jeton matériel déplacerait le build de release hors des runners hébergés et rouvrirait la règle d'atteignabilité.
- Une expiration du certificat entre deux releases fait échouer la release suivante le jour du tag.
- Une première release signée rencontre encore l'avertissement de réputation de SmartScreen.

## Compliance and Enforcement

- `WindowsReleaseSigningContractTest` : aucun bloc `run:` ne développe un secret ; seul `release.yml` nomme l'identité
  de release ; `release.yml` ne peut la câbler tant qu'un champ de la déclaration est vide ; ordre de signature dans
  `build-portable.ps1` et `build-installer.ps1` ; directive de signature compilée seulement pour un build qui signe,
  désinstalleur compris ; commande de l'outil sans credential ; point d'entrée seul appelant d'un outil de signature ;
  déclaration complète et version de base jamais en avance sur le POM.
- `GateReachabilityArchitectureTest#aReleaseRehearsalCannotPublishAttestOrPassForAQualification` : aucune étape de la
  répétition n'ajoute un certificat à un magasin racine.
- `ReleaseQualificationContractTest#bothVariantsPerformTheSameVerifications` et `scripts/tests/test_windows_authenticode.py`
  : refus nommés de la vérification et du point d'entrée, sur de vrais binaires Windows.
- `scripts/validate-m20.ps1 -TestSigning`, lancé chaque nuit : chaque fichier attendu est signé par l'identité de
  l'exécution, et la vérification de la qualification le refuse.
- Revue : le contenu du fournisseur dans le point d'entrée, et l'environnement protégé de `release.yml`, lors du
  basculement.

## References

- ADR-0089 (intégrité de production), ADR-0105 (aucun secret réutilisable au repos).
- Changements OpenSpec `sign-the-windows-installer-and-launcher` et
  `refuse-an-unsigned-or-wrongly-signed-windows-binary`.
- `docs/project/validation/RELEASE_QUALIFICATION.md`, contrôle 8 ; registre des risques RT-14 et RT-15.
