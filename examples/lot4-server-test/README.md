# Quêtes de test du lot 4 (NeoForge 1.21.1)

Ces définitions remplacent les quêtes actives du serveur de test. Elles sont volontairement distinctes des anciennes quêtes : leurs identifiants commencent par `lot4_`, donc les anciennes progressions sauvegardées ne faussent pas les essais. La sauvegarde du monde reste intacte.

Copier `questlog/chapters/main.json` et les fichiers de `questlog/quests/` dans `config/questlog/` du serveur **arrêté**. Utiliser le même JAR `3.5.1-lot4.2` sur le serveur et les clients, puis redémarrer. Le chapitre « Tests Questlog — lot 4 » contient :

1. **01 Suivi personnel** : deux objectifs distincts pour vérifier la première ligne du suivi. Suivre cette quête puis une autre, et changer leur ordre.
2. **02 Groupe, quantité fixe** : fabriquer 64 torches au total entre les membres. La quantité reste 64 quel que soit le nombre de participants.
3. **03 Groupe, par participant** : fabriquer 64 boutons de chêne par participant (128 à deux). Un bouton fabriqué compte pour une unité ; cela permet de vérifier 40 + 20 puis le départ et le retour d'un membre.
4. **04 Possession, par participant** : posséder 64 pierres par participant dans l'inventaire principal. Transférer une pile de A à B sans dupliquer le total ; tester aussi B hors ligne après sauvegarde.
5. **05 Groupe répétable** : fabriquer 8 planches de chêne au total, réclamer l'expérience, puis utiliser la répétition et un reset MJ ciblé.
6. **06 Verrouillée et partageable** : cette quête personnelle nécessite la réussite de 01. Tester le partage à un membre qui ne l'a pas débloquée.
7. **07 Mondiale** : lire la quête pour vérifier séparément l'héritage mondial et son reset ciblé.

Pour les essais à plusieurs participants, utiliser des profils et un monde **jetables**. Les étapes complètes et les résultats attendus sont dans `docs/LOT_4_PROGRESSION_COLLECTIVE_ET_SUIVI.md`. La création de ces JSON et leur lecture statique ne valident pas le comportement en jeu.

Former le groupe **avant** de fabriquer les quantités finales ou de réunir toutes les pierres ; une quête achevée en solo ne sera pas rejouée automatiquement pour un membre arrivé ensuite.

Après la répétition de la quête 05, vérifier seul dans le groupe : **1 participant**, objectif **0/8** et aucun « Completed ». Avec d'autres membres ayant encore la quête à valider, leur nombre doit être affiché et pris en compte dans le quota ; un joueur qui vient de réinitialiser ne contribue de nouveau qu'après sa propre action. Fabriquer ensuite les planches et vérifier que la quête peut être terminée à nouveau.
