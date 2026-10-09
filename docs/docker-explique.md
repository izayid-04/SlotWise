# Docker pour SlotWise expliqué

Dockerisation complète du projet (appli + base de données), lancée en une seule commande : `docker compose up -d --build`. Fichiers : `Dockerfile`, `.dockerignore`, `docker-compose.yml`, + un petit ajustement dans `application.properties`.

---

## 1. Concepts de base

- **Image** : un modèle figé, en lecture seule — contient tout ce qu'il faut pour faire tourner un programme (code, Java, librairies). Ne s'exécute pas directement.
- **Conteneur** : une **instance en cours d'exécution** d'une image — comme la différence entre une classe et un objet.
- **`Dockerfile`** : la recette pour **construire une seule image** précise.
- **`docker-compose.yml`** : orchestre **plusieurs conteneurs ensemble** (réseau interne, variables d'environnement, ordre de démarrage) — à partir d'images déjà prêtes (ex: `postgres:16-alpine`, téléchargée depuis Docker Hub) ou construites via un `Dockerfile` (notre appli, puisqu'aucune image "SlotWise" n'existe sur internet).

## 2. `application.properties` — rendre l'hôte de la DB configurable

```properties
spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:slotwise}
```

Avant, `localhost` était codé en dur. Problème : une fois l'appli elle-même dans un conteneur, `localhost` désignerait le conteneur de l'appli lui-même, pas celui de la base. `${DB_HOST:localhost}` garde `localhost` par défaut (dev local sans Docker pour l'appli), mais permet de le surcharger (`DB_HOST=db` dans `docker-compose.yml`, une fois dans le réseau Docker).

## 3. `Dockerfile` — construire l'image de l'appli, en 2 étapes ("multi-stage build")

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Étape 1 ("build")** : part d'une image contenant Maven + JDK 21. `COPY pom.xml .` puis `RUN mvn dependency:go-offline` **avant** de copier le code source : optimisation de cache — si seul le code change (pas `pom.xml`), Docker réutilise la couche de téléchargement des dépendances déjà faite, au lieu de tout re-télécharger. `COPY src ./src` puis `mvn clean package -DskipTests` compile et assemble le `.jar` (tests déjà vérifiés séparément, pas besoin de les refaire à chaque build d'image).

**Étape 2** : une **deuxième image, séparée**, ne contenant que le JRE (pas Maven, pas le JDK complet) — "alpine" = distribution Linux minimaliste. `COPY --from=build /app/target/*.jar app.jar` : va chercher **uniquement le `.jar` déjà compilé** dans le système de fichiers de l'étape "build", et le copie ici — tout le reste (Maven, code source, dépendances téléchargées) est jeté. `ENTRYPOINT ["java", "-jar", "app.jar"]` : la commande lancée au démarrage du conteneur.

**Pourquoi 2 étapes séparées** : l'étape 1 est lourde (Maven + JDK + dépendances + code source), mais tout ça n'est utile que pour **construire**. L'étape 2 ne reçoit que **le résultat déjà compilé**, ce qui rend l'image finale beaucoup plus légère — plus rapide à télécharger/déployer, surface d'attaque réduite.

## 4. `.dockerignore`

```
target/
.git/
.idea/
*.log
```

Évite de copier ces dossiers/fichiers dans le contexte de build (pas besoin pour construire l'image, et ça ralentirait inutilement la construction).

## 5. `docker-compose.yml` — orchestrer les 2 conteneurs

```yaml
services:
  db:
    image: postgres:16-alpine
    ...
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 5s
      timeout: 5s
      retries: 10

  app:
    build:
      context: .
    depends_on:
      db:
        condition: service_healthy
    environment:
      DB_HOST: db
      DB_PORT: 5432
      DB_NAME: ${POSTGRES_DB}
      DB_USER: ${POSTGRES_USER}
      DB_PASSWORD: ${POSTGRES_PASSWORD}
    ports:
      - "8080:8080"
    restart: unless-stopped

volumes:
  slotwise-db-data:
```

- **`healthcheck`** sur `db` : vérification périodique (`pg_isready`, toutes les 5s, 10 tentatives max) que Postgres accepte réellement des connexions — un conteneur "démarré" n'est pas forcément "prêt".
- **`build: context: .`** (service `app`) : contrairement à `db` (`image: ...`, toute faite), ici on construit depuis le `Dockerfile` du dossier courant.
- **`depends_on: db: condition: service_healthy`** : attend que le `healthcheck` de `db` passe avant de démarrer l'appli — évite le problème classique "l'appli démarre et tente de se connecter avant que la base soit prête" (qu'on a rencontré plusieurs fois manuellement).
- **`DB_HOST: db`** : à l'intérieur du réseau créé par Docker Compose, chaque service est joignable par les autres **via son nom de service** (un petit DNS privé) — jamais via `localhost`, qui désignerait le conteneur appelant lui-même. `app` pourrait de la même façon être contacté par un futur service via le nom `app`.
- **`DB_PORT: 5432`** (codé en dur, pas `${POSTGRES_PORT}`) : le port **interne** où Postgres écoute toujours, à l'intérieur du réseau Docker — différent du port côté hôte (celui mappé pour les connexions externes comme `curl` depuis le terminal).
- **`restart: unless-stopped`** : politique de redémarrage automatique — si le conteneur plante ou si Docker redémarre, il est relancé automatiquement, sauf arrêt volontaire explicite.
- **`volumes: slotwise-db-data:`** (en bas) : déclare un **volume nommé**, géré par Docker, séparé du cycle de vie du conteneur — garantit que les données Postgres survivent même si le conteneur `db` est supprimé/recréé (sans ça, `docker compose down` puis `up` repartirait avec une base vide).

## Testé et vérifié

```bash
docker compose up -d --build
```
- `slotwise-db` → `Up (healthy)`
- `slotwise-app` → `Up`, connecté à la DB via `DB_HOST=db`
- `GET /v3/api-docs` → 200
- Cycle `register`/`login` complet via `curl` → fonctionne de bout en bout

Conforme au livrable du cahier des charges : "Instructions de lancement via Docker (2 commandes max)" — fait en **une seule commande**.
