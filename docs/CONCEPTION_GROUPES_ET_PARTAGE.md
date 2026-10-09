# Questlog : groupes, partage et récompenses

Document de référence — 9 octobre 2026.

## 1. Objectif et portée

Permettre aux auteurs de créer des quêtes de groupe qui encouragent la coopération, les échanges et l'entraide, tout en restant réalisables seul.

Ce document décrit les comportements retenus pour le développement. Il ne constitue pas une preuve d'implémentation ou de validation en jeu.

- Conserver le design de Questlog, ses composants et ses habitudes d'utilisation.
- Les quêtes, les contributions personnelles et les validations appartiennent au joueur.
- Une quête de groupe peut être réalisée seul, avec un effectif de un. La réalisation en solo ne déclenche pas de prime supplémentaire.
- Les groupes d'aventure sont intégrés à Questlog et indépendants des équipes de claims.
- Réutiliser les mécanismes existants et conserver la compatibilité des définitions et sauvegardes.

### Quêtes personnelles et quêtes de groupe

Le paramètre « Quête de groupe » détermine les règles collectives disponibles. Appartenir à un groupe ne transforme pas une quête personnelle en quête de groupe.

| Comportement | Quête personnelle | Quête de groupe |
| --- | --- | --- |
| Quantité fixe | Pour le joueur. | Pour les participants réunis. |
| Quantité par participant | Non applicable. | Au choix de l'auteur. |
| Cumul des inventaires et des contributions | Non. | Oui, pour les participants à cette quête. |
| Validation collective | Non. | Oui, pour les participants. |
| Distance des bénéficiaires d'une récompense de groupe | Non applicable. | Au choix de l'auteur. |
| Exemplaire unique d'équipe et politique de butin | Non applicables. | Au choix de l'auteur et du chef selon leurs réglages respectifs. |
| Partage d'accès | Possible si la quête est partageable ; progression personnelle. | Possible si la quête est partageable ; participation collective dans un même groupe. |
| Accomplissement mondial et exploit annoncé | Options indépendantes. | Options indépendantes. |

L'éditeur et la validation des définitions empêchent les combinaisons incompatibles, notamment une quantité par participant sur une quête personnelle.

## 2. Journal et chapitres

- Une quête sans chapitre explicitement défini est rangée dans `main`.
- Les conditions existantes rendent les quêtes visibles, sans acceptation manuelle supplémentaire pour la découverte normale.
- Le périmètre utilise les déclencheurs existants.
- Un chapitre reste caché tant qu'il ne contient aucune quête visible pour le joueur.
- Une quête rendue visible, notamment par un partage accepté, peut révéler son chapitre. Les autres quêtes conservent leurs propres conditions.
- Les chapitres organisent et présentent les quêtes. Leurs réglages de présentation sont conservés : identifiant, titre traduit, icône, ordre, chapitre par défaut et visibilité.
- Les paramètres de groupe, de partage et de récompense sont définis sur les quêtes.

## 3. Partage d'une quête et références dans le chat

### Accès

- Chaque quête dispose d'un paramètre « Partageable ».
- « Partageable » et « Quête de groupe » sont indépendants. Activer l'un ne modifie pas l'autre ; l'auteur choisit explicitement si la quête peut être partagée.
- Le destinataire choisit d'accepter la quête proposée.
- L'acceptation donne accès à cette quête sans devoir remplir ses prérequis. Elle ne termine ni ses objectifs, ni ses quêtes préalables.
- L'accès ainsi obtenu reste acquis après un départ du groupe.
- Le partage porte sur la quête sélectionnée. Il n'accorde pas l'accès aux autres quêtes de son chapitre.
- Une quête non répétable déjà terminée reste consultable, mais ne peut pas être recommencée grâce à un partage.
- Une quête répétable conserve ses conditions de répétition.

### Parcours de partage

1. Une quête partageable affichée présente un bouton « Partager » sous le panneau de gauche.
2. Ce bouton ouvre une popup avec une barre de recherche et une liste de dix joueurs au maximum, en réutilisant le composant de sélection des invitations de groupe.
3. Sans recherche, les membres du groupe sont prioritaires, puis les joueurs les plus proches. La recherche permet de trouver un joueur éloigné. Les joueurs déjà membres d'un groupe restent des destinataires possibles.
4. L'expéditeur sélectionne un joueur et clique sur « Partager », ou ferme la popup avec « Annuler » sans rien envoyer.
5. Le destinataire reçoit un message privé indiquant l'expéditeur et contenant un lien vers la quête.
6. Le lien ouvre le descriptif de la proposition. « Accepter » remplace « Partager » ; « Retour » permet de quitter sans accepter.
7. Pour une quête non répétable déjà terminée, le descriptif indique cet état et ne permet pas une nouvelle acceptation donnant accès à des récompenses supplémentaires.

### Références dans le chat

La saisie `quest:NomDeLaQuete` produit une référence identifiable et cliquable. Le clic ouvre le descriptif selon les droits de visibilité du lecteur.

Une référence sert à consulter et désigner une quête ; elle ne constitue pas une proposition de partage et ne contourne pas ses prérequis. Une proposition de partage adressée au joueur lui permet, elle, de consulter la quête avant de l'accepter, même si ses prérequis ne lui donnaient pas accès à celle-ci.

Les annonces d'exploit réutilisent ces références.

## 4. Participants, contributions et quotas

### Participants à une quête de groupe

Un même ensemble sert au quota, aux contributions et à la validation :

- Membres ayant débloqué ou accepté cette quête.
- Membres connectés ou déconnectés.
- Quête encore à réaliser pour ces membres.

Un membre sans accès, un candidat au groupe ou un membre ayant déjà terminé la quête n'entre pas dans cet ensemble. Ses actions et son inventaire ne sont pas additionnés au total de cette réalisation collective.

Le nombre de participants est visible dans la quête. Il est distinct de l'effectif de l'annuaire du groupe, qui comprend tous ses membres actuels.

### Quantités et objectifs

Pour une quête de groupe, l'auteur choisit une quantité fixe ou une quantité par participant.

| Demande | Un participant | Trois participants |
| --- | ---: | ---: |
| 64 rails au total | 64 | 64 |
| 64 rails par participant | 64 | 192 |

Posséder, fabriquer et poser si l'objectif existant le prévoit restent des actions distinctes.

- Possession : compter la barre d'action et l'inventaire Minecraft des participants, même hors ligne. Les objets peuvent être répartis librement entre eux. Le total représente les possessions actuelles, sans compter plusieurs fois une pile échangée.
- Fabrication et autres actions : compter les contributions attribuées personnellement à leur auteur et enregistrées pour cette quête.

Les objectifs de livraison sont hors du périmètre de cette mise à jour ; ils pourront faire l'objet d'une éventuelle mise à jour ultérieure.

Pour un objectif de combat d'une quête de groupe, la mort de l'entité reste nécessaire. Le joueur de référence est le participant admissible ayant infligé le plus de dégâts à cette entité pendant le combat. Le coup final d'un membre ayant déjà terminé la quête ne doit pas annuler la contribution des participants : la mort compte si au moins un participant a infligé des dégâts comptabilisés pour cette quête. Un vétéran exclu des participants n'augmente pas le quota et ne gagne pas à nouveau la récompense. Une égalité des meilleures contributions utilise la loterie lorsque la politique du groupe est « joueur qui valide ».

### Conservation des contributions

Chaque joueur conserve ce qu'il a personnellement accompli et qui a été comptabilisé pour cette quête, avant comme pendant son passage dans un groupe. Le total collectif additionne les contributions des participants actuels.

| Événement | Comportement |
| --- | --- |
| Arrivée dans le groupe | Si le joueur est participant, sa contribution déjà enregistrée rejoint immédiatement le total collectif et le quota est recalculé. |
| Déblocage ou acceptation de la quête dans le groupe | Le membre entre dans les participants si la quête reste à réaliser ; le quota est recalculé. |
| Nouvelle action | Elle augmente la contribution de son auteur et le total collectif correspondant. |
| Déconnexion | Le participant reste compté. |
| Départ ou expulsion | Sa contribution est retirée du total de l'ancien groupe, mais reste dans son journal personnel. Le quota est recalculé immédiatement. |

Le joueur n'emporte pas les contributions des autres. Le système ne reconstitue pas d'actions anciennes qui n'avaient jamais été comptabilisées pour cette quête. Les possessions restent constatées dans les inventaires actuels ; les objets et les constructions ne sont pas détruits lors d'un départ.

Exemple pour 64 rails fabriqués par participant :

| Situation | Alice | Bob | Total de la réalisation |
| --- | ---: | ---: | --- |
| Alice seule | 40 | — | 40/64 |
| Alice rejoint Bob | 40 | 20 | 60/128 |
| Alice fabrique dix rails | 50 | 20 | 70/128 |
| Alice quitte avant validation | Elle conserve 50 | Il conserve 20 | Alice : 50/64 ; Bob : 20/64. |

La confirmation de départ explique :

> En quittant ce groupe, vos contributions seront retirées de son progrès. Vous conserverez vos contributions personnelles, vos objets, vos quêtes personnelles et vos quêtes de groupe déjà validées.

Elle propose « Quitter le groupe » et « Annuler ». La confirmation d'expulsion explique au chef les mêmes conséquences pour le membre et le groupe.

## 5. Validation

- La validation est automatique lorsque tous les objectifs sont remplis, en l'absence d'échec.
- Un quota non atteint correspond à un objectif incomplet.
- La validation collective concerne les participants à la quête au moment de la réussite.
- Les validations sont enregistrées personnellement et restent acquises après un changement de groupe.
- Un recalcul peut terminer une quête sans nouvelle action. Exemple : après un départ, le quota passe de 192 à 128 avec 150 rails encore réunis. L'objectif est rempli ; la quête est validée si ses autres objectifs le sont aussi.
- L'option « Accomplissement mondial » applique la portée particulière décrite en section 7.
- Les conditions de récompense restent indépendantes de la validation.

Une réussite doit conserver ses bénéficiaires et droits à récompense. Marquer leur quête terminée ne doit pas les exclure ensuite de leur propre récompense. Un nouveau membre ne reçoit pas rétroactivement une réussite antérieure à son arrivée ; il conserve ses propres validations.

### Modifications par l'auteur et intervention du MJ

- Modifier la définition d'une quête en conservant son identifiant ne retire pas les validations déjà acquises, y compris si ses objectifs changent.
- Les joueurs l'ayant terminée n'ont pas à la refaire du seul fait de la modification. Les joueurs qui doivent encore la réaliser utilisent sa définition active.
- Modifier une récompense ne doit pas distribuer automatiquement une seconde récompense aux joueurs ayant déjà récupéré la leur. Les droits de récupération acquis restent distingués de la définition courante.
- Le MJ autorisé peut réinitialiser une quête précise pour un joueur ou pour tous les joueurs concernés, y compris hors ligne, sans effacer leur progression sur les autres quêtes.
- Cette réinitialisation explicite permet de refaire la quête et d'en obtenir à nouveau les récompenses, même si elle n'est pas répétable. Elle ne retire pas les objets déjà présents dans le monde ou les inventaires.
- Une réinitialisation ciblée ne doit pas être annulée à la reconnexion par une ancienne sauvegarde, une synchronisation de groupe ou l'héritage d'un accomplissement mondial. Le traitement des données doit respecter la portée choisie par le MJ et préserver les droits des autres joueurs.
- Le MJ peut également restituer un objet nécessaire à la progression au moyen de la commande Minecraft de don d'objet, sans réinitialiser la quête.

Ces opérations sont des interventions administratives contrôlées côté serveur. Les commandes existantes de progression sont le point de départ de leur mise en œuvre.

## 6. Récompenses et butin

### Bénéficiaires

Chaque récompense a ses propres destinataires. Pour une récompense de groupe :

| Distance | Bénéficiaires |
| --- | --- |
| 0 | Tous les participants bénéficiaires de la validation, connectés ou non, sans limite de distance. |
| Supérieure à 0 | Parmi ces participants, ceux connectés dans le rayon défini autour du joueur qui valide. |

Le champ explique : « 0 = tous les participants, membres hors ligne compris ».

La distance ne rend pas admissible un membre sans accès à la quête ou l'ayant déjà terminée avant cette réussite. Un joueur hors ligne peut conserver une récompense attribuée et la récupérer ensuite.

Une quête non répétable déjà terminée ne redonne pas ses récompenses parce que le joueur aide un autre groupe. Les quêtes répétables conservent leurs règles de répétition.

### Exemplaires et politique de groupe

L'auteur dispose du choix « Un seul exemplaire : pour l'équipe / par joueur ».

Pour un exemplaire unique d'équipe, le chef règle l'attribution :

1. Joueur qui valide.
2. Loterie entre les volontaires.
3. Destinataire désigné par le chef.

Ces modes respectent l'admissibilité définie par la récompense.

- Un destinataire désigné conserve son droit de récupération, même s'il est déconnecté ou manque de place.
- Sans destinataire, le premier membre admissible qui récupère l'objet l'obtient ; les autres ne peuvent plus le récupérer.
- Tous les membres peuvent consulter les règles du butin ; seul le chef peut les modifier.
- Une attribution unique ne peut être consommée qu'une fois, y compris en cas de demandes simultanées.

### Joueur qui valide et objectif de référence

L'auteur peut choisir un objectif de référence pour déterminer le destinataire dans le mode « joueur qui valide » : dégâts les plus élevés sur le mob parmi les participants admissibles, quantité fabriquée ou possédée la plus élevée, selon l'objectif. Pour le combat, l'attribution est déterminée après la mort de l'entité, selon les règles de la section 4.

La récompense reste une récompense de quête, récupérable après la validation de toute la quête.

Exemple : l'épée unique revient au joueur qui accomplit « Tuer le gardien », mais devient récupérable seulement après la réalisation des autres objectifs également.

### Loterie

- Le chef ouvre la quête et lance le tirage avec « Loterie », qui remplace le bouton de récupération de la récompense unique concernée.
- Une popup en haut de l'écran présente l'objet, son infobulle et « Voulez-vous participer à la loterie ? », avec « Oui » et « Non ».
- Le compte à rebours visible dure deux minutes. Seuls les joueurs admissibles ayant répondu « Oui » à temps participent ; les absents ne prolongent pas le délai.
- Chaque participant reçoit un entier aléatoire de 0 à 100 inclus. Le plus grand gagne.
- Une égalité au meilleur score entraîne un nouveau tirage entre les ex æquo, jusqu'à un gagnant unique, sans rouvrir le délai de réponse.
- Le résultat est envoyé par message privé à chaque membre. Un chat de groupe disponible peut remplacer ces messages multiples.

Si la politique est « joueur qui valide » et que plusieurs contributeurs admissibles sont ex æquo au meilleur niveau sur l'objectif de référence, la loterie concerne uniquement ces joueurs. La popup indique : « Vous êtes à égalité. Voulez-vous participer à la loterie pour gagner cet objet ? ». Les deux minutes et les règles de tirage sont identiques.

### Attribution par le chef

« Attribuer » remplace le bouton de récupération de la récompense concernée. La popup reprend la sélection des invitations, limitée aux membres du groupe et respectant l'admissibilité à la récompense. Elle propose « Valider » et « Annuler ».

## 7. Accomplissement mondial et annonce

Les deux options sont indépendantes.

### Accomplissement mondial

La réussite valide la quête pour tous les joueurs, connectés ou non. Les futurs arrivants héritent également de cet accomplissement. Les récompenses conservent leurs propres règles.

À l'activation de cette option, afficher :

> Cette quête sera validée pour tous les joueurs, y compris les joueurs hors ligne et les futurs arrivants. Les récompenses conservent leurs propres règles d'attribution. Si une récompense est indispensable à la suite, assurez-vous que chacun pourra l'obtenir, sinon certains joueurs pourraient rester bloqués.

Les quêtes suivantes apparaissent selon leurs propres conditions.

### Exploit annoncé

L'annonce célèbre une réussite auprès des joueurs connectés sans modifier validation ou récompenses.

- Bannière de cinq à dix secondes en haut de l'écran, dans le style de Questlog.
- Texte lisible, confettis et feux d'artifice visuels contenus en haut ; centre dégagé et contrôle du joueur conservé.
- Nom du joueur ou du groupe et nom de la quête.
- Copie dans le chat avec la liste des membres uniquement dans ce message.
- Nom de quête cliquable, selon les droits de visibilité.

Exemples :

- Solo : « Alice a accompli la quête “La cité oubliée” ! Félicitations ! »
- Bannière de groupe : « L'équipe Les Explorateurs a accompli la quête “La cité oubliée” ! Félicitations ! »
- Chat : « Les joueurs de l'équipe Les Explorateurs — Alice, Bob et Chloé — ont accompli la quête “La cité oubliée” ! Félicitations ! »

## 8. Interface des groupes

### Navigation

Deux onglets sur le bord gauche du panneau gauche reprennent la forme des onglets de chapitre adaptée à cette orientation :

- Livre ouvert : « Journal », avec l'affichage actuel et ses chapitres.
- Tête de Steve : « Groupes ».

Le survol indique leur nom. Revenir au journal retrouve le chapitre et la quête précédemment sélectionnés.

### Joueur sans groupe

Le panneau gauche affiche les groupes avec défilement Questlog. Chaque entrée présente le nom, l'effectif et le recrutement ouvert ou fermé.

Le tri propose « Nom / Effectif / Recrutement » avec une flèche pour inverser l'ordre. L'effectif inclut les membres hors ligne, mais pas les candidats.

| Situation | Panneau gauche | Panneau droit |
| --- | --- | --- |
| Aucune sélection | Liste des groupes. | Créer un groupe. |
| Groupe sélectionné | Liste des groupes. | Candidater, Inspecter. |
| Inspection | Nom du groupe et membres. | Candidater, Retour. |
| Candidature envoyée | Liste ou inspection. | « Candidature en attente », Annuler ma candidature. |

« Créer un groupe » reste accessible tant que le joueur est sans groupe, même après une sélection. Il ouvre une popup de saisie du nom avec validation.

Les visiteurs voient les membres, mais pas les candidatures.

### Joueur membre d'un groupe

- Le panneau gauche affiche membres et candidats, avec identification du chef et des candidats.
- Les membres déconnectés sont grisés, marqués « Hors ligne » et restent sélectionnables.
- Le panneau droit propose « Règles du butin » et « Quitter le groupe ».
- Le chef sélectionnant un membre peut l'expulser ou lui transférer son rôle.
- Le chef sélectionnant un candidat peut accepter ou refuser.
- Départ et expulsion demandent une confirmation expliquant le retrait des contributions du total collectif et leur conservation personnelle.
- Le chef transfère son rôle avant de quitter un groupe qui conserve des membres. Le départ du dernier membre dissout le groupe.
- « Fermer le recrutement » bloque les nouvelles candidatures, conserve les demandes déjà reçues et laisse le chef inviter.

## 9. Invitations et notifications

Le chef dispose d'un bouton « Inviter un joueur ». La popup contient recherche, liste, sélection, « Inviter » et « Retour ».

- Sans recherche : jusqu'à dix joueurs connectés sans groupe, du plus proche au plus éloigné dans la même dimension.
- Avec recherche : jusqu'à dix résultats correspondants, sans limite de distance, y compris dans d'autres dimensions.

Le destinataire reçoit une notification privée, par exemple :

> Alice vous invite à rejoindre le groupe « Les Explorateurs ». Ouvrez l'onglet Groupes pour répondre.

Les invitations reçues apparaissent dans un bloc de la liste des groupes. En sélectionnant une invitation, le joueur consulte les membres à gauche et peut accepter, refuser ou revenir à droite. La consultation seule ne fait pas rejoindre.

Accepter fait rejoindre le groupe et retire les autres invitations et candidatures du joueur.

Le chef reçoit des notifications privées pour les nouvelles candidatures, leurs annulations, les invitations acceptées ou refusées et les départs. Une déconnexion n'est pas un départ. Ces notifications restent dans le chat.

## 10. Préparation et vérification du développement

Utiliser ce document comme référence commune aux discussions de réalisation. Chaque lot comprend l'adaptation de l'interface au design actuel, la conservation des données et des vérifications ciblées.

Les domaines de travail sont : groupes et interface, partage et journal, objectifs et progression, récompenses, références de chat, accomplissement mondial et annonces. Leur ordre technique dépend des mécanismes existants à réutiliser.

Avant chaque lot, examiner les chemins de progression, validation, récompense et sauvegarde ainsi que leurs appelants. Les règles sont contrôlées côté serveur, au-delà de leur présentation dans l'éditeur. Distinguer les vérifications automatisées des essais effectivement réalisés en jeu.

### Scénarios de référence

| Scénario | Résultat attendu |
| --- | --- |
| Quête personnelle d'un membre de groupe | Progression personnelle ; rejet d'un quota par participant. |
| Une quête de groupe réalisée seul | Un participant, objectifs réalisables et contribution personnelle conservée. |
| Quatre membres : deux ont la quête à faire, un ne l'a pas, un l'a terminée | Deux participants pour quota, contributions et validation. |
| Un participant se déconnecte | Il reste compté, possessions comprises ; la récompense conserve ses conditions. |
| Acceptation d'un partage avant validation | Accès à la seule quête proposée et inclusion immédiate parmi les participants si elle reste à réaliser. |
| Arrivée avec 40 rails déjà fabriqués pour cette quête | Contribution personnelle de 40 apportée au total collectif. |
| Départ avec une contribution personnelle de 50 | Retrait de 50 du total de l'ancien groupe, conservation personnelle de 50 et recalcul immédiat. |
| Quota passant de 192 à 128 avec 150 objets restants | Objectif rempli ; validation si les autres objectifs sont également remplis. |
| Départ après validation | Validation et droits à récompense déjà acquis conservés. |
| Deux demandes simultanées sur un exemplaire unique | Une seule récupération effective. |
| Objectif de référence rempli avant les autres | Récompense récupérable seulement à la fin de la quête. |
| Égalité de contribution en mode « joueur qui valide » | Loterie entre les contributeurs admissibles ex æquo. |
| Égalité au meilleur score de loterie | Nouveau tirage entre ces ex æquo ; aucune participation ajoutée hors délai. |
| Un vétéran ayant terminé la quête donne le coup final après les dégâts d'un participant | Le combat compte pour la quête de groupe ; le vétéran n'augmente pas le quota et ne reçoit pas une seconde récompense. |
| Activation du paramètre « Quête de groupe » | Le choix « Partageable » reste inchangé. |
| Modification d'objectifs sur une quête déjà terminée | Validation personnelle conservée, sans obligation de refaire la quête. |
| Modification d'une récompense déjà récupérée | Aucune seconde distribution automatique. |
| Réinitialisation administrative d'une quête pour une personne | Seule cette quête est réinitialisée pour la cible ; les autres progressions sont conservées et la réinitialisation reste effective après reconnexion. |
| Réinitialisation administrative pour tous les joueurs concernés | Traitement des joueurs connectés et hors ligne, sans effacement des autres quêtes. |
| Redémarrage et chargement d'une sauvegarde existante | Données conservées, sans nouvelle attribution des récompenses récupérées. |
