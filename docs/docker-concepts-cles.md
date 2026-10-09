# Docker — les concepts clés à retenir

Condensé — voir `docker-explique.md` pour le détail.

---

| Concept | Une phrase à retenir |
|---|---|
| **Image vs conteneur** | Image = modèle figé (comme une classe). Conteneur = instance en cours d'exécution (comme un objet). |
| **`Dockerfile`** | La recette pour construire **une** image précise. |
| **`docker-compose.yml`** | Orchestre **plusieurs conteneurs ensemble** (réseau, env, ordre de démarrage), à partir d'images prêtes ou construites via un `Dockerfile`. |
| **Multi-stage build** | Construire dans un environnement lourd (Maven+JDK), mais ne garder que le résultat final (le `.jar`) dans une image minimale pour l'exécution — image finale plus légère. |
| **`COPY --from=build`** | Récupère un fichier précis d'une étape précédente du `Dockerfile`, sans garder tout le reste de cette étape. |
| **Cache des couches Docker** | Copier `pom.xml` et télécharger les dépendances **avant** de copier le code source évite de tout re-télécharger à chaque modification du code. |
| **Nom de service = nom d'hôte interne** | À l'intérieur du réseau Docker Compose, chaque service est joignable par les autres via son nom (`db`, `app`) — jamais via `localhost`. |
| **Port interne vs port hôte** | Le port interne (`5432` pour Postgres) sert à la communication entre conteneurs ; le port mappé côté hôte (`${POSTGRES_PORT}`) sert aux connexions externes (`curl` depuis le terminal). |
| **`healthcheck` + `depends_on: condition: service_healthy`** | Un conteneur "démarré" n'est pas forcément "prêt" — attendre une vraie vérification de disponibilité évite les erreurs de connexion au démarrage. |
| **`restart: unless-stopped`** | Redémarre automatiquement un conteneur qui plante, sauf arrêt volontaire explicite. |
| **Volume nommé** | Stockage séparé du cycle de vie du conteneur — sans lui, les données disparaissent à chaque `down`/`up`. |
