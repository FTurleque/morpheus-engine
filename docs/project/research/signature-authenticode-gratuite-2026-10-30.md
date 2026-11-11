# Signature Authenticode gratuite — options pour MORPHEUS (relevé du 2026-10-30)

Relevé daté. Il répond à une question du constat AUD-DEP-05 de l'audit du 2026-10-09 : existe-t-il une signature
Authenticode **sans coût** pour l'installeur `MORPHEUS-<version>-windows-x64-setup.exe` et le lanceur `morpheus.exe` ?
Le mainteneur n'acquiert un certificat que s'il est gratuit. Les sources sont les pages des fournisseurs, lues le
2026-10-30.

## Contraintes de MORPHEUS qui décident

- **Licence** : `LICENSE` est une licence propriétaire « source-available ». Elle dit elle-même que le logiciel n'est
  ni open source ni libre. Aucune licence approuvée par l'OSI ne couvre le dépôt.
- **Éditeur** : le titulaire des droits est une personne physique, en France. Aucune entité juridique n'est déclarée.
- **Build** : la release est construite et attestée sur un runner GitHub hébergé, depuis le tag exact (`release.yml`).
  Une clé sur jeton USB ne s'y branche pas.
- **Credential** : uniquement par une variable d'environnement ou une identité fédérée, et seulement dans
  `release.yml` (`.claude/rules/security.md`, section « Signature Authenticode Windows »).

## Comparaison

| Option | Coût réel | Éligibilité | Intégration CI | Éditeur affiché, SmartScreen | Verdict pour MORPHEUS |
|---|---|---|---|---|---|
| **SignPath Foundation** (programme OSS de SignPath.io) | gratuit | licence approuvée par l'OSI **sans double licence commerciale**, **aucun composant propriétaire**, projet déjà publié, politique de signature publiée sur la page du projet | action GitHub de SignPath ; la signature est déclenchée depuis le workflow et SignPath vérifie que le binaire vient du dépôt déclaré ; un jeton d'API SignPath, mappé sous `env:` | « SignPath Foundation » : la fondation est l'éditeur ; certificat partagé entre projets OSS, réputation construite par les téléchargements | **inéligible** : la licence n'est pas OSI et tout le code est propriétaire |
| **Azure Artifact Signing** (ex-Trusted Signing) | **payant** : Basic 9,99 $/mois (5 000 signatures), Premium 99,99 $/mois | certificats publics : organisations des États-Unis, du Canada, de l'UE, du Royaume-Uni et de quelques autres pays ; **personnes physiques : États-Unis et Canada seulement** | identité fédérée OIDC GitHub vers Azure (aucun secret long), outil `signtool` avec le plugin Azure ou action Microsoft | nom validé par Microsoft ; certificats courts renouvelés par le service ; la réputation SmartScreen se construit d'elle-même selon l'historique de téléchargement | **non gratuit**, et une personne physique en France n'est pas éligible ; il faudrait une entité juridique européenne |
| **Certum Open Source Code Signing** | **payant** : 69 € HT le jeu carte + lecteur, renouvellement payant | développeur qui publie un projet open source ; certificat au nom d'une personne physique, préfixé « Open Source Developer » | carte cryptographique sur lecteur USB : **incompatible avec un runner hébergé** (la variante cloud SimplySign est aussi payante) | nom de la personne ; Certum annonce une construction de réputation SmartScreen | **non gratuit**, incompatible avec le build sur runner hébergé, et la condition « open source » n'est pas remplie |
| **Microsoft Store** (compte développeur) | compte gratuit, particulier ou entreprise | vérification d'identité | sans objet pour `Setup.exe` | pour un `.exe`/`.msi` soumis au Store, **le binaire doit déjà être signé** par un certificat d'une autorité du programme racine Microsoft ; seul un paquet MSIX est signé par le Store | **ne signe pas** l'installeur Inno Setup publié sur GitHub ; passer au MSIX distribué par le Store serait une autre décision de distribution |
| Certificat auto-signé | gratuit | — | déjà exercé par la répétition (`-SigningState test`) | non approuvé par Windows : l'avertissement « éditeur inconnu » reste, et SmartScreen aussi | **sans effet** pour l'utilisateur final |

## Conclusion

Aucune option ne donne gratuitement à MORPHEUS une signature Authenticode reconnue par Windows. La seule voie gratuite
pour un binaire distribué hors du Store, SignPath Foundation, exige une licence OSI et l'absence de code propriétaire :
c'est une décision de licence, pas d'outillage. Les voies conformes à la licence actuelle sont payantes : Azure Artifact
Signing (qui exige en plus une entité juridique pour un éditeur européen) ou un certificat OV/EV d'une autorité, dont la
clé vit sur un matériel certifié.

ADR-0153 reste donc archivé `Rejetée`, pour le motif qu'il donne, et aucun ADR nouveau n'est justifié : la décision ne
change pas. Le mécanisme de signature reste en place et tenu par ses tests ; il n'attend qu'un fournisseur.

Ce qui rouvrirait la question, et qui revient au mainteneur :

- passer MORPHEUS sous une licence approuvée par l'OSI, sans double licence commerciale, ce qui rend SignPath
  Foundation accessible ; l'éditeur affiché serait alors « SignPath Foundation » ;
- accepter un coût récurrent, avec Azure Artifact Signing par une entité juridique éligible, dont l'intégration par
  identité fédérée OIDC respecte la règle « aucun secret dans un argument » sans secret de dépôt ;
- distribuer un paquet MSIX par le Microsoft Store, signé par le Store, à côté de l'installeur GitHub.

Tant qu'aucune n'est retenue, la vérification d'origine offerte à l'utilisateur est la somme SHA-256 et l'attestation
de provenance GitHub, décrites dans `docs/user/INSTALLATION.md`, section « Signature de l'éditeur (Windows) ».

## Sources

- SignPath Foundation, conditions pour les projets open source : <https://signpath.org/terms>
- Azure Artifact Signing, tarifs : <https://azure.microsoft.com/en-us/pricing/details/artifact-signing/>
- Azure Artifact Signing, prérequis et pays éligibles : <https://learn.microsoft.com/en-us/azure/artifact-signing/quickstart>
- Azure Artifact Signing, FAQ (réputation SmartScreen) : <https://learn.microsoft.com/en-us/azure/artifact-signing/faq>
- Certum, Open Source Code Signing : <https://shop.certum.eu/open-source-code-signing.html>
- Microsoft Store, compte développeur sans frais : <https://learn.microsoft.com/en-us/windows/apps/publish/partner-center/open-a-developer-account>
- Microsoft Store, exigences d'un paquet MSI/EXE : <https://learn.microsoft.com/en-us/windows/apps/publish/publish-your-app/msi/app-package-requirements>
