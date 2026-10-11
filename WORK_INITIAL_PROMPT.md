# Prompt initial pour ChatGPT Work

Tu travailles sur **Kodvido**, application locale Java 21/Spring Boot de pilotage de projets (développement ET exploitation), sous Windows et Linux. L'archive jointe constitue le point de départ, et non un produit déjà fini.

1. Crée un dépôt GitHub **privé** nommé `kodvido` dans mon compte, après avoir vérifié le compte et obtenu les autorisations nécessaires. Si la création GitHub n'est pas possible, explique précisément le blocage et prépare le dépôt local sans inventer de publication réussie.
2. Importe le contenu de cette archive dans le dépôt. Lis intégralement `AGENTS.md`, `docs/VISION.md`, `docs/REQUIREMENTS.md`, `docs/ARCHITECTURE.md`, `docs/DATA-MODEL.md`, `docs/PROJECT-FILES.md`, `docs/ROADMAP.md` et `docs/DECISIONS.md` avant de coder.
3. Commence par M0 : compile et teste `mvn -B verify`; le squelette n'a pas été compilé lors de la préparation. Corrige toutes les erreurs. Vérifie la CI GitHub Actions sur Windows et Ubuntu.
4. Implémente ensuite **M1 uniquement** comme première tranche verticale réelle : scan de racines telles que `~/git` ou `D:\donnees\git`, reconnaissance Maven/Python et projets Maven multimodules, proposition des découvertes, validation d'import, catalogue H2/Flyway, liste des projets, relocalisation et suppression du catalogue sans suppression sur disque. Interface web simple Spring Boot, pas Angular.
5. Écris des tests unitaires et d'intégration sérieux, avec fichiers temporaires, cas d'erreur, sécurité des chemins et non-mutation des projets. Vise les couvertures JaCoCo de `AGENTS.md`. N'annonce un test « réussi » que si tu l'as réellement exécuté.
6. Effectue des commits clairs et propose une pull request pour M1 si possible. Mets à jour les docs et les critères d'acceptation. Réponds avec le dépôt/PR, les fonctionnalités réellement achevées, commandes testées, couverture mesurée, limites et suite M2.

Contraintes incontournables : utiliser uniquement le nom **Kodvido** ; moteur réutilisable par une future CLI/MCP, zéro modification des projets pendant la détection, pas de données personnelles/professionnelles dans GitHub, pas d'actions automatiques sur Git/scripts, sauvegardes INCONNUES par défaut. Ne commence pas M2/M3 tant que M1 n'est pas vérifié.
