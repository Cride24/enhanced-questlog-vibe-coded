# Lot 2 — groupes d'aventure et interface de base

État du 12 octobre 2026 sur `codex/questlog-groupes-et-partage-1.21.1`. **Lot 2 validé par l'utilisateur** après essai de `3.5.1-lot2.4` sur NeoForge. Seul le changement d'ordre d'une liste contenant plusieurs groupes reste à confirmer, faute d'un troisième joueur pour cet essai. Cette validation du lot n'est pas une publication de version.

## Comportement implémenté

- Chaque monde possède un registre Questlog séparé des claims : UUID stable du groupe, nom unique, chef, membres UUID et noms (même hors ligne), candidats, invitations, recrutement ouvert ou fermé et préférence de butin. Le fichier est `data/questlog_groups.dat` dans la sauvegarde du monde ; l'écriture temporaire garde une copie `_old`. Si les deux fichiers sont illisibles, les mutations sont suspendues pour éviter d'écraser la sauvegarde.
- La création, les candidatures et annulations, l'acceptation et le refus par le chef, les invitations à un joueur connecté sans groupe, leur réponse, l'expulsion, le transfert du rôle de chef, le départ et la dissolution au dernier départ sont vérifiés côté serveur. Une entrée dans un groupe retire les autres candidatures et invitations de ce joueur. Le chef doit transférer son rôle avant de partir si d'autres membres restent. Les requêtes périmées sont rejetées.
- Fermer le recrutement bloque les nouvelles candidatures, conserve celles déjà reçues et permet toujours au chef d'inviter. Le choix de butin (joueur qui valide, loterie, attribution par le chef) est visible par les membres et modifiable par le chef. La distribution effective des récompenses n'est pas dans ce lot ; une infobulle le précise.
- Le livre propose les onglets latéraux Journal et Groupes. Le retour conserve l'instance du journal ou de la fiche de quête, donc sa sélection. L'annuaire affiche nom, effectif des membres (hors ligne compris) et recrutement ; tri Nom/Effectif/Recrutement dans les deux sens. Créer un groupe reste accessible après sélection. L'inspection expose les membres, pas les candidatures aux visiteurs.
- Le groupe du joueur affiche chef, membres, candidats et état hors ligne ; un membre hors ligne reste sélectionnable. Le chef dispose des actions de gestion et d'une sélection de joueurs connectés sans groupe : dix résultats au plus, proximité dans la même dimension sans recherche, recherche par nom dans toutes les dimensions. Les invitations reçues ont une entrée privée. Les notifications de candidature, annulation, réponse à une invitation et départ sont privées dans le chat ; la déconnexion a un message distinct.
- Les réponses réseau sont reconstruites côté serveur pour chaque destinataire. Les candidatures ne sont envoyées qu'aux membres du groupe concerné. Les paquets sont enregistrés sur Fabric et NeoForge ; le protocole NeoForge passe à `2.3`.

## Vérifications réalisées

- `common:verifyAdventureGroups` : droits chef/membre/visiteur, candidature en recrutement fermé, invitation du chef malgré fermeture, cible hors ligne refusée, entrée supprimant les autres offres, transfert avant départ, dissolution au dernier départ, conservation d'un membre hors ligne et des offres après relecture, secours de la copie précédente et suspension sur fichier illisible.
- `common:verifyProgressPreservation` : les invariants automatisés du lot 1 passent toujours.
- `fabric:build` et `neoforge:build` en mode hors ligne, avec version d'essai `3.5.1-lot2.4` : compilation et assemblage réussis. JARs : `fabric/build/libs/questlog-fabric-1.21.1-3.5.1-lot2.4.jar` et `neoforge/build/libs/questlog-neoforge-1.21.1-3.5.1-lot2.4.jar`.
- `common:verifyQuestDetails` : 42 vérifications sans client lancé.
- Les JSON `en_us` et `fr_fr` sont lisibles ; toutes les nouvelles clés `questlog.groups.*` existent en français et en anglais. `git diff --check` ne signale pas d'erreur de contenu.

## Retour visuel et correction

Les captures fournies par l'utilisateur montrent, sur NeoForge avec `3.5.1-lot2.1`, que les groupes et membres sont sélectionnables mais leurs lignes restent invisibles, que les titres et messages placés à droite du livre sont peu lisibles, que les boutons de création se chevauchent et que la recherche d'invitation apparaît sans résultats. Le composant de défilement transmet des coordonnées locales aux clics, mais le contenu doit appliquer lui-même le décalage du composant pour être dessiné dans sa zone visible. Les lignes du groupe ne l'appliquaient pas.

Dans `3.5.1-lot2.2`, les lignes sont dessinées dans la zone de défilement ; les titres, indications et résultats sont placés sur la page du livre. La création affiche « Entrer un nom de groupe » au-dessus du champ et des boutons séparés « Créer » et « Annuler ». La recherche d'invitation et sa liste figurent l'une sous l'autre sur la page. Le second retour visuel de l'utilisateur confirme l'affichage des lignes de groupes et de membres, tout en signalant une mise en page encore insuffisante.

Dans `3.5.1-lot2.3`, tous les titres de panneau sont centrés et utilisent le séparateur des fiches de quête. La création a un titre distinct, une consigne alignée avec le champ centré verticalement et deux boutons espacés. L'annuaire conserve « Liste des groupes » après sélection et présente nom, nombre de joueurs et recrutement dans trois colonnes séparées ; ses lignes alternent deux tons et les en-têtes cliquables changent le tri et son sens. Le groupe invité n'est plus dupliqué dans la liste. Les actions à droite de la liste des membres ont un espacement constant et sont centrées dans la hauteur disponible selon leur nombre. Le retour en jeu sur `lot2.3` signale une flèche masquée dans « Joueurs » et demande la touche E pour revenir en arrière.

Après le retour utilisateur sur `lot2.3`, `3.5.1-lot2.4` élargit « Joueurs » de huit pixels en réduisant « Nom », sans modifier « Recrutement », afin de laisser apparaître la flèche de tri. La touche E et la touche configurée pour ouvrir Questlog reprennent le comportement de retour des écrans de quête : fermer la saisie ou l'invitation, quitter l'inspection, puis revenir au journal. Quand le champ de texte est actif, E reste saisissable.

## Validation de l'utilisateur

L'utilisateur indique le 12 octobre 2026 que le lot 2 peut être validé après ses essais avec `3.5.1-lot2.4`. Le seul essai qu'il signale comme manquant est le changement d'ordre de l'annuaire quand plusieurs groupes sont présents, pour lequel il lui faudrait un troisième joueur. Les captures et essais rapportés concernent NeoForge ; aucun essai en jeu sur Fabric n'est établi. Le tri lui-même est implémenté et compilé, mais l'ordre obtenu avec plusieurs groupes n'est pas confirmé en jeu.

Les vérifications automatisées restent sans interface de jeu. L'utilisateur a effectué les essais NeoForge ; l'agent n'a lancé aucun client ou serveur Minecraft ni modifié le profil ou le monde de l'utilisateur. Les transitions simultanées sont sérialisées sur le thread serveur par les gestionnaires de paquets ; aucun essai Fabric en jeu n'est établi.

## Protocole de validation en jeu

Utiliser une **copie jetable** de monde avec le JAR correspondant au chargeur et à la même version côté serveur et clients. Garder l'original intact. Idéalement, réunir trois joueurs A, B et C, puis refaire les étapes sur Fabric et NeoForge.

1. A ouvre Groupes, crée « Explorateurs » ; vérifier le titre et son séparateur, la consigne proche du champ et les boutons équilibrés. B voit l'annuaire, l'effectif 1 et peut inspecter A. Vérifier les trois colonnes, l'alternance des lignes, le titre fixe après sélection, le tri par chaque en-tête dans les deux sens, le retour au Journal puis à la quête et au chapitre précédemment ouverts. Vérifier que Créer reste accessible après sélection.
2. B candidate ; A reçoit un message privé et voit B candidat. Vérifier le titre centré et séparé de la liste des membres, et l'espacement régulier des actions à droite avec et sans membre sélectionné. C inspecte le groupe sans voir B candidat. B annule et A reçoit un message ; B recandidate. A ferme le recrutement : B reste candidat, C ne peut plus candidater. A accepte B ; B apparaît membre, effectif 2.
3. C crée un autre groupe ou candidate ailleurs, puis quitte si nécessaire. A invite C malgré recrutement fermé. Sans recherche, vérifier les dix joueurs connectés sans groupe les plus proches dans la dimension ; avec recherche, essayer un joueur éloigné ou dans une autre dimension. C reçoit le message privé, voit l'invitation, inspecte avant de répondre, refuse ; A reçoit le refus. Refaire et accepter ; les autres invitations et candidatures de C doivent disparaître.
4. Déconnecter B : son entrée reste grisée, « Hors ligne », sélectionnable, et l'effectif reste 3 ; la déconnexion est signalée sans retirer B. Reconnecter B. Tester une invitation ou acceptation devenue périmée entre le clic et la réponse ; le serveur doit refuser la seconde action.
5. B essaie les actions réservées au chef ; aucun effet. A essaie de partir avec d'autres membres : refus. A transfère le rôle à B ; A peut alors partir. B expulse C après confirmation. B quitte en dernier ; le groupe disparaît de l'annuaire.
6. Dans un groupe conservé, choisir chaque règle de butin comme chef ; vérifier qu'un membre la voit mais ne peut la modifier. Vérifier après redémarrage du serveur le nom, les UUID liés aux membres hors ligne, les candidatures, invitations, recrutement et choix du butin. La distribution réelle d'objets ne doit pas être attendue à ce stade.
7. Refaire une courte vérification du lot 1 sur la copie : réussite acquise après modification de définition, récompense déjà collectée non redonnée et reset ciblé sans perte d'une quête voisine.

## Limites de ce lot

Le partage des quêtes et liens de chat (lot 3), les contributions et quotas collectifs (lot 4), les dégâts, attributions et loteries réelles (lot 5) ainsi que les annonces (lot 6) ne sont pas implémentés. Aucun objectif de livraison n'a été ajouté. Un essai avec plusieurs groupes doit encore confirmer l'ordre du tri ; Fabric n'a été vérifié que par compilation et tests sans jeu.
