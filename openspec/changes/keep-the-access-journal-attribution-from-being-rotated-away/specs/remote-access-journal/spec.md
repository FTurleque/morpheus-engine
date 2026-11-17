## ADDED Requirements

### Requirement: Un flot de lectures ne chasse pas l'enregistrement d'une écriture
Les enregistrements d'une requête qui modifie quelque chose (route `WRITE`, ou route `ADMIN` hors `GET` et `HEAD`) DOIVENT (MUST) être gardés dans une rétention bornée que les enregistrements des lectures ne consomment pas. Aucun volume de lectures autorisées NE DOIT (MUST NOT) faire supprimer par rotation l'enregistrement d'une écriture tant que la rétention des écritures n'est pas elle-même pleine d'écritures.

#### Scenario: Des lectures en masse après une écriture
- **GIVEN** un serveur distant dont le journal a consigné une écriture d'un principal de rôle `WRITE`
- **WHEN** un principal de rôle `READ` enchaîne assez de lectures pour faire tourner plusieurs fois la rétention des lectures
- **THEN** les enregistrements `accepted` et `completed` de l'écriture sont encore présents dans le journal
- **AND** la rétention des lectures reste bornée en taille et en générations

#### Scenario: La rétention des écritures reste bornée
- **GIVEN** un serveur distant qui reçoit des écritures au-delà de la borne de leur rétention
- **WHEN** la rétention des écritures est pleine
- **THEN** elle tourne à son tour, et le nombre de générations gardées ne dépasse pas sa borne

### Requirement: Le risque de rotation du journal par un principal authentifié est écrit
`SECURITY.md` et le guide du serveur distant DOIVENT (MUST) dire quels principaux peuvent faire tourner chaque rétention du journal d'accès, ce qui reste garanti malgré cette rotation, et que la collecte continue du journal est ce qui garde une trace au-delà de sa borne.

#### Scenario: L'opérateur lit la politique de sécurité
- **GIVEN** `SECURITY.md` et le guide du serveur distant
- **WHEN** un opérateur cherche combien de temps une écriture reste attribuable
- **THEN** les deux pages nomment les rétentions, ce qui les fait tourner et la collecte continue comme garantie au-delà
