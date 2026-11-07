# Pack de test — Enhanced Questlog 3.5.4

Ce dossier contient **17 quêtes et 2 chapitres** pour Minecraft **1.21.1**, Fabric ou NeoForge, version `3.5.4-vibe.1`. Tous les essais du pack se font avec **deux joueurs au maximum**. Les identifiants commencent par `integration354_` : une progression sauvegardée sur les anciennes quêtes `lot4_` ne doit pas fausser les nouveaux essais.

## Installation dans un monde de test

1. Utiliser une **copie jetable** du monde et des profils. Arrêter le serveur de test. Installer le même JAR `3.5.4-vibe.1` sur ce serveur et ses clients.
2. Sauvegarder le dossier `config/questlog/` de cette copie. Pour remplacer les anciennes quêtes de test, supprimer les **JSON de quêtes** de `config/questlog/quests/` et les anciens **JSON de chapitres** de `config/questlog/chapters/` dans cette copie seulement. Ne pas supprimer les données de joueurs ni les sauvegardes du monde.
3. Copier le contenu de `questlog/quests/` et `questlog/chapters/` du présent pack dans les dossiers homonymes de `config/questlog/` ; redémarrer le serveur.
4. Vérifier deux onglets : « Tests Enhanced Questlog — intégration 3.5.4 » et « Tests 3.5.4 — exploration ».

**Ne pas ajouter `examples/questlog` ou `examples/lot4-server-test` en même temps** : ils brouilleraient la liste et certains scénarios. Ce pack ne contient pas les fichiers de sauvegarde de progression.

## Quêtes et ordre conseillé

| Numéro | Essai | Action utile |
| --- | --- | --- |
| 01 | Suivi personnel | Suivre 01 puis 02 ; fabriquer une table de craft et un four, changer l'ordre et regarder les cases. Garder B sans ces fabrications jusqu'au test de partage. |
| 02 | Groupe, quantité fixe | Former A+B **avant** de fabriquer les 64 torches ; le quota reste 64. |
| 03 | Groupe, par participant | A fabrique 40 boutons et B 20 avant regroupement : vérifier 60/128 après regroupement. Tester arrivée/départ, membre hors ligne, puis le passage de 100/128 à 100/64 quand B quitte le groupe. |
| 04 | Possession, par participant | Mettre 64 pierres dans l'inventaire principal, transférer la pile de A à B : elle ne doit compter qu'une fois. |
| 05 | Groupe répétable | Fabriquer 8 planches, réclamer l'expérience, répéter puis tester le reset ciblé de A, seul et avec B. |
| 06 | Verrouillée et partageable | A termine 01 ; partager 06 à B sans lui faire terminer 01. B accepte puis se reconnecte. |
| 07 | Mondiale | Lire sur A, faire rejoindre B, puis tester un reset ciblé sans seconde récompense involontaire. |
| 08 | Clics JSON, objets, image | Cliquer « copier », « aide » (`/help`) et « page externe » (demande d'ouverture d’`example.org`) ; ouvrir les détails pour l'infobulle d'objet, EMI/JEI, l'image et un lien de quête soumis aux droits. |
| 09 | Description 11 lignes | Contrôler la présence et le comportement de la barre de défilement ; redimensionner l'éditeur avec du texte non sauvegardé. |
| 10 | Récompense à composants | Sur serveur dédié, posséder un livre puis réclamer le livre Fortune III. |
| 11 | Prédicat d'entité | Sur serveur dédié, approcher une vache ; vérifier que la définition se charge et que l'objectif réagit. |
| 12 | Table de butin | Sur Fabric dédié, lire puis réclamer le butin de donjon une fois. |
| 13 | Tag de biome | Entrer dans un biome de l'Overworld ; le tag doit être reconnu. |
| 14 | Structure de groupe | Former A+B avant d'aller dans un village. Laisser B hors de la structure ; A doit pouvoir continuer à contribuer **au-delà de ses 4 premières unités** vers le quota collectif de 8. |
| 15 | Liste longue d'éditeur | Descendre au bas des 12 entrées, en dupliquer puis en supprimer une ; rester près de cette position de défilement. |
| 16 | Objet à composants | Sur serveur dédié, vérifier le chargement puis tester une plume nommée `Magic Feather`. |
| 17 | Voisine non partageable | Après acceptation de 06 par B, 17 doit rester verrouillée chez B : l'accès partagé ne révèle pas les quêtes voisines. |

Pour les corrections amont sans quête dédiée : exécuter `/questlog reload`, basculer le mode édition, reconnecter A et tester un monde LAN ; déplacements et regard doivent rester libres, et un seul événement doit créditer un objectif. Tester les boutons d'édition sans chevauchement. Les commandes de reset sont détaillées dans [le rapport d'intégration](../../docs/INTEGRATION_AMONT_3_5_4.md). Le scénario entre plusieurs groupes distincts reste hors de portée avec deux joueurs.

Les 17 fichiers passent la validation d'auteur et la construction de quêtes avec `:1.21.1-fabric:verifyIntegrationPack`. Ce contrôle ne lance pas Minecraft ; les résultats visuels, réseau et serveur dédié restent à confirmer en jeu.
