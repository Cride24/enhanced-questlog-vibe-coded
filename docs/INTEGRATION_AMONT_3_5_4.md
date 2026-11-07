# Intégration de Questlog amont 3.5.4 dans Enhanced Questlog

État préparé le 7 novembre 2026 sur `codex/integration-amont-3.5.4-1.21.1`. Cette intégration est destinée à **Minecraft 1.21.1 Fabric et NeoForge**. Elle n'est pas une validation en jeu.

## Révisions et retour arrière

| Rôle | Révision |
| --- | --- |
| Ancêtre commun | `9d5bcda0c36c2d9901cd3c5974d53044e09a4d14` |
| Fork avant intégration, point de retour intact | `3c978677f5f8ac94ac1b2b4fb41c36f4abe04192` |
| Amont `upstream/stonecutter` récupéré, cible 3.5.4 | `6b615d60176f0edd31b0b9dde2af0c900b4d1fa6` |

La fusion conserve les deux parents Git et les commits amont attribués à Infernal Studios. Le fork d'origine reste sur `codex/questlog-groupes-et-partage-1.21.1`. Aucun push ou PR amont n'a été fait. Licence Apache-2.0, auteurs et crédits amont sont conservés.

## Inventaire de la plage amont

| Commit | Changement amont et traitement dans le fork |
| --- | --- |
| `a3b2ee8` · 3.4.2 | Repris : déblocage des contrôles après reload, mode édition et LAN ; retrait des écouteurs d'objectifs obsolètes via `ObjectiveListeners` ; clics des composants JSON dans les descriptions ; conservation des champs d'éditeur au redimensionnement ; calcul de défilement des descriptions de 11 lignes ; lecture serveur des composants d'objets, prédicats d'entités et récompenses à composants. L'ancien garde `setActive(false)` du fork complète le retrait des écouteurs. |
| `9d7679f` · 3.5.0 | Migration Stonecutter intégrée : source commune dans `src/main`, variantes 1.21.1 sous `versions/1.21.1-*`, scripts Gradle Kotlin et prétraitement amont. Les correctifs 1.21 rétroportés dans ce commit suivent la source amont fusionnée. Les projets 1.20.1 Fabric/Forge ajoutés par amont ne sont pas configurés dans `settings.gradle.kts` : ils concernent d'autres versions et ne sont ni construits ni annoncés ici. Leurs fichiers amont restent dans le dépôt pour la traçabilité de la migration. |
| `c77a41e` · 3.5.1 | Repris : collecte des récompenses de table de butin sur Fabric, avec la garde d'inactivité du fork dans `LootTableReward`. Gradle amont 9.7.1 est employé. |
| `c9001ab` · 3.5.2 | Repris : boutons d'édition repositionnés sans chevauchement et descriptions coupées aux mots, avec préservation des symboles enrichis par d'autres mods. La validation visuelle du fork dans le panneau d'édition est gardée autour des nouvelles lignes amont. |
| `d7c749e` · 3.5.3 | Repris : objectifs de visite de biome et structure par tag, suggestions d'éditeur et documentation. La validation d'auteur du fork accepte maintenant aussi ces deux types de tags ; sans cette adaptation, l'éditeur aurait refusé les définitions que le moteur amont sait lire. L'objectif Structure emploie `canRecordMore()` et `addUnits(1)` afin de ne pas arrêter la contribution personnelle d'un participant de groupe à son premier quota. |
| `6b615d6` · 3.5.4 | Repris : position de la liste conservée après duplication ou suppression d'une entrée dans l'éditeur, via `refreshEntryList`, `pendingRightScroll` et les méthodes de `ScrollableComponent`/`RightPanelScrollable`. |

Aucun correctif amont pertinent à 1.21.1 dans cette plage n'a été exclu. Les chemins et réglages propres à 1.20.1/Forge sont présents comme source amont mais hors périmètre de compilation et d'essai.

## Adaptations du fork conservées

- **Auteur et contenu** : édition validée côté serveur, brouillons/activation, identifiants, descriptions et images, langues en ligne, infobulles et liens d'objets. Les classes EMI et JEI ont été déplacées dans `src/main` et leurs API restent des dépendances facultatives à la compilation ; les métadonnées Fabric conservent l'entrée JEI.
- **Suivi** : ordre personnel persistant, fenêtre et interactions, première quête détaillée en lignes d'objectifs avec cases automatiques, autres lignes compactes, clics et défilement.
- **Lots 1 à 4.2** : réussite et reçus de récompense durables après édition ; resets ciblés (UUID hors ligne et exception mondiale) ; groupes, candidatures, invitations, chef et règles de butin ; offres privées, partage indépendant du groupe et ouverture contrôlée ; progression collective à participants admissibles, y compris hors ligne, possessions actuelles, quotas fixes/par participant, contributions personnelles gardées après départ et droits individuels ; attente après reset avec participant toujours compté et contribution nulle jusqu'à une nouvelle action.
- **Identité et compatibilité** : nom visible `Enhanced Questlog - vibe coded`, ID `questlog`, commandes, namespaces et fichiers de sauvegarde conservés ; coordonnées du dépôt fork dans les métadonnées Fabric/NeoForge. La version d'essai est `3.5.4-vibe.1`.

La fusion a réutilisé les implémentations amont lorsqu'elles couvraient déjà le besoin (notamment nettoyage des écouteurs, défilement de l'éditeur et tags), puis n'a gardé que les adaptations nécessaires aux comportements du fork. Les paquets de groupe, partage, auteur et suivi restent enregistrés sur les deux chargeurs. Aucun lot 5/6, bonus solo, récompense d'entraide ou chantier de nouvelle version Minecraft n'a été commencé.

## Contrôles exécutés

- `:1.21.1-fabric:verifyGroupProgress` et `:1.21.1-fabric:verifyTrackingValidation` repassent après les adaptations de tags. La validation auteur compte **148 contrôles**, dont les nouveaux tags biome/structure ; la progression vérifie également que leurs objectifs de groupe peuvent accepter une contribution au-delà du seuil personnel.
- `:1.21.1-fabric:verifyProgressPreservation`, `verifyAdventureGroups`, `verifyQuestSharing`, `verifyEditorScrolling` (**36 contrôles**), `verifyRecipeLinks` (**10**), `verifyQuestDetails` (**42**), `verifyRewardOverview`, `verifyImageTooltip` (**19**) et `verifyInlineLanguages` (**49**) ont réussi en exécution légère. Le contrôle des groupes produit volontairement des erreurs de lecture sur des fichiers temporaires corrompus, puis réussit.
- `:1.21.1-fabric:build :1.21.1-neoforge:build -x test` réussit. `-x test` évite la tâche de tests classique sans cas JUnit ; les exécutables de vérification ci-dessus ont été lancés séparément. Les deux sources compilent et les JARs sont assemblés.
- Les métadonnées à l'intérieur des deux JARs portent le nom, l'ID et la version attendus ; les classes de groupe, partage, écouteurs et liens de recettes, ainsi que `fr_fr.json`, y sont présentes. `git diff --check` est sans erreur.

Les vérifications légères NeoForge ont été tentées : sous le nouvel outillage amont, leur démarrage direct s'arrête avant le code testé car `LoadingModList` n'est pas initialisé hors chargeur. Elles ont donc été exécutées avec le classpath Fabric. Cela ne prouve pas le fonctionnement NeoForge en jeu ; ses sources et son JAR compilent. Aucun client, serveur, profil ou monde Minecraft n'a été lancé ou modifié.

## JARs d'essai

| Chargeur | Fichier | SHA-256 |
| --- | --- | --- |
| Fabric 1.21.1 | `versions/1.21.1-fabric/build/libs/questlog-fabric-1.21.1-3.5.4-vibe.1.jar` | `A2325D541435B7987D78127402BDAC5E0D94C6C5D77D14A92452A9C076AA488E` |
| NeoForge 1.21.1 | `versions/1.21.1-neoforge/build/libs/questlog-neoforge-1.21.1-3.5.4-vibe.1.jar` | `EB8746AC850E78B0A1A0994F387B8343C8952D4B916C22A9E229F3C51F500309` |

Employer le **même JAR de cette version** sur client et serveur pour un chargeur donné. Ne pas mélanger avec `lot4.2`.

## Essais en jeu sur copie jetable

Le pack prêt à copier se trouve dans `examples/integration-3.5.4-test/` ; ses 17 quêtes passent `:1.21.1-fabric:verifyIntegrationPack` (lecture, validation d'auteur et construction hors jeu). Son README donne l'ordre des essais et les précautions de remplacement des anciennes quêtes de test.

Créer une copie d'un monde et de profils de test. Noter chargeur, SHA du JAR, joueurs et étapes réellement jouées. Commencer sur NeoForge avec deux joueurs A/B ; refaire les scénarios pertinents sur Fabric. Garder les profils et sauvegardes personnels intacts.

1. Faire un reload de quêtes, activer/désactiver l'édition, puis rejoindre un monde LAN : déplacements et regard restent libres. Reconnecter après reload et déclencher un objectif : une seule contribution est enregistrée. Redimensionner l'éditeur avec du texte non sauvegardé, puis vérifier les champs et le défilement d'une description de 11 lignes.
2. Ouvrir une description avec des `clickEvent` JSON (`run_command`, `open_url`, `copy_to_clipboard`), un lien de quête protégé, un lien d'objet/recette et une image. Vérifier le clic, les droits, les infobulles et les symboles fournis par un autre mod. Dans l'éditeur, vérifier les boutons sans chevauchement et la position de liste après duplication/suppression.
3. Sur un **serveur dédié de copie**, charger une quête avec composants d'objet, prédicat d'entité et récompense d'objet à composants ; réclamer une récompense de table de butin sur Fabric. Faire des visites par ID et par tag de biome/structure. Répéter une visite Structure de groupe après que A atteint son quota personnel, jusqu'à la validation collective.
4. Rejouer les scénarios ciblés des lots 1–4.2 avec A et B : édition après réussite sans seconde collecte, reset d'un seul UUID hors ligne et exception mondiale, invitation/candidature/chef, partage accepté puis reconnexion, droits de liens de chat, 40+20 contributions avant regroupement, départ/reconnexion, échange d'une même pile sans double comptage, membre hors ligne dans le quota, passage 100/128 à 100/64 lorsque B quitte le groupe, reset de A qui reste participant à zéro jusqu'à sa propre action, première quête suivie détaillée puis réordonnée. Tester le cas solo `N = 1` sur chaque chargeur utilisé.
5. Vérifier de nouveau le livre, les onglets et l'interface validée sur NeoForge, puis Fabric. Le tri entre plusieurs groupes distincts du lot 2 reste à vérifier lorsqu'un troisième joueur sera disponible.

Le retour en jeu du 7 novembre sur `lot4.2` confirme seulement le scénario de reset et d'affichage alors signalé. Il ne valide pas cette intégration 3.5.4 ni toutes les étapes ci-dessus.
