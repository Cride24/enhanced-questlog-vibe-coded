# Lot 1 — progression acquise et réinitialisation ciblée

État au 9 octobre 2026, branche `codex/questlog-groupes-et-partage-1.21.1`. Ce lot ne constitue pas une livraison du mod.

## Comportement implémenté

- Le champ NBT `completed`, déjà présent dans les sauvegardes, représente désormais une réussite acquise. Un changement d'objectifs ou de conditions d'échec pour le même identifiant ne la retire pas. Les objectifs compatibles d'une quête encore en cours gardent leur progression.
- Lorsqu'une définition de récompense change, les récompenses déjà récupérées gardent leur reçu. Si toutes les anciennes récompenses ont été récupérées, la nouvelle liste est considérée récupérée : une modification d'auteur ne distribue rien de nouveau par inadvertance.
- Les données mondiales ne transmettent plus les compteurs et reçus d'un joueur aux autres. Elles transmettent la réussite. L'ancien fichier `global_quests.questlog.dat` reste lu ; sa copie de sauvegarde est conservée lors d'une écriture ultérieure.
- La commande de niveau de permission 2 existante accepte désormais `/ql progress reset <id_de_quête> uuid <UUID>` pour un joueur connecté ou déconnecté et `/ql progress reset <id_de_quête> all` pour les joueurs enregistrés, y compris hors ligne. Les sélecteurs de joueurs déjà pris en charge restent disponibles. Le reset remplace seulement les données de la quête visée et ne retire aucun objet de l'inventaire.
- Un reset personnel de quête mondiale inscrit `ignore_global_completion` dans la sauvegarde UUID : l'ancienne réussite mondiale ne revient pas à la reconnexion. Un reset `all` supprime la réussite mondiale stockée, sans supprimer les autres quêtes. Les quêtes répétables gardent leur remise à zéro normale.
- La sauvegarde de fin de serveur ne réécrit plus les anciens gestionnaires de joueurs déconnectés ; elle ne sauvegarde que les joueurs actuellement connectés. Cela évite d'annuler un reset hors ligne.

## Vérifications automatiques effectuées

`common:verifyProgressPreservation` passe sans lancement du jeu : ancienne sauvegarde sans `_structure`, objectif modifié après réussite, récompense collectée modifiée, progression compatible ou incompatible d'une quête inachevée, reset ciblé et quête voisine intacte, écriture et relecture du fichier NBT d'un UUID hors ligne puis héritage mondial, reset de tous au niveau des données, et quête répétable.

`common:compileJava`, `fabric:compileJava` et `neoforge:compileJava` passent en mode hors ligne. Les avertissements Gradle et `Dist.CLIENT` sont présents mais ne bloquent pas la compilation.

## Essai en jeu à faire sur une copie

1. Copier un monde de test et ses fichiers `playerdata` ; garder la copie initiale intacte. Démarrer cette copie avec le JAR du lot, puis relever les UUID de deux joueurs de test.
2. Terminer une quête non répétable comportant un objectif et une récompense, récupérer la récompense, puis modifier l'objectif et la récompense en gardant l'identifiant. Faire `/ql reload` : la quête doit rester terminée et la récompense déjà reçue ne doit pas pouvoir être reprise.
3. Faire `/ql progress reset <id> uuid <UUID>` sur le joueur déconnecté, puis le reconnecter. La seule quête visée doit redevenir faisable et sa récompense récupérable ; les quêtes voisines et leurs récompenses doivent rester intactes. Aucun objet déjà reçu ne doit être retiré.
4. Terminer une quête mondiale, déconnecter un joueur, réinitialiser sa seule quête par UUID, puis le reconnecter : elle doit rester à refaire malgré l'ancien accomplissement mondial. Ensuite faire `/ql progress reset <id_mondial> all` et vérifier les joueurs en ligne et hors ligne après reconnexion.
5. Terminer puis relancer une quête répétable et vérifier sa progression et ses récompenses. Refaire ces scénarios sur Fabric et NeoForge si les deux plateformes sont utilisées.

Ces étapes en jeu restent à confirmer par l'utilisateur. Aucune sauvegarde personnelle ni aucun profil Minecraft n'a été modifié pour ce lot.
