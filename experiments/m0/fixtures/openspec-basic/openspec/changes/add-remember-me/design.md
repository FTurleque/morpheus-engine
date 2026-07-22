# Design: Remember-me sessions

## Technical approach

Le changement introduit un mécanisme de session persistante distinct de la session standard.

Principes de conception :

1. une session standard conserve son comportement d'expiration actuel ;
2. l'option remember-me doit être explicitement demandée ;
3. un token persistant doit être révocable ;
4. le token persistant ne doit pas remplacer les contrôles de session courants ;
5. une déconnexion explicite invalide la persistance associée.

## Decisions

### Separate persistent credential

La persistance SHALL utiliser un artefact révocable distinct du cookie de session court afin de ne pas transformer toutes les sessions en sessions longues.

### Explicit opt-in

Aucune session ne devient persistante sans intention explicite de l'utilisateur.

## Fallback

En cas d'échec de restauration de la session persistante, le système revient au comportement normal et demande une authentification complète.
