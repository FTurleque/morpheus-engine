# Proposal: Add remember-me sessions

## Intent

Permettre à un utilisateur de conserver volontairement une session authentifiée plus longtemps sur un appareil de confiance, sans modifier le comportement par défaut des sessions existantes.

## Scope

Le changement :

- ajoute une option explicite « remember me » lors de l'authentification ;
- étend la durée maximale de la session lorsque cette option est activée ;
- conserve l'expiration actuelle de 30 minutes pour les sessions normales ;
- exige une révocation lorsque l'utilisateur se déconnecte explicitement.

## Constraints

- une session standard SHALL conserver le comportement courant par défaut ;
- aucune persistance SHALL être activée sans opt-in explicite.

## Out of scope

- synchronisation multi-appareils ;
- administration distante des appareils ;
- authentification multifacteur ;
- modification du mécanisme d'identification principal.

## Risks

- prolongation involontaire d'une session sur un appareil partagé ;
- confusion entre durée absolue et délai d'inactivité ;
- révocation incomplète d'un token persistant.
