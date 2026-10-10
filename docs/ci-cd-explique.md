# CI (GitHub Actions) expliqué (SlotWise)

Bonus du cahier des charges : build + tests automatiques à chaque push/pull request. Fichier : `.github/workflows/ci.yml`.

---

## Déclencheurs

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
```

`name` : nom affiché dans l'onglet "Actions" de GitHub. `on` : ce workflow se lance automatiquement à chaque `push` sur `main`, et à chaque pull request visant `main`.

## Le job et sa machine virtuelle

```yaml
jobs:
  build-and-test:
    runs-on: ubuntu-latest
```

Un "job" regroupe une série d'étapes exécutées sur une machine virtuelle **fraîche**, fournie par GitHub à chaque exécution (ici, Ubuntu) — rien n'est conservé d'une exécution à l'autre, sauf ce qu'on met explicitement en cache.

## Le service Postgres — pourquoi il est nécessaire

```yaml
    services:
      postgres:
        image: postgres:16-alpine
        env:
          POSTGRES_DB: slotwise
          POSTGRES_USER: slotwise
          POSTGRES_PASSWORD: slotwise_test_password
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 5s
          --health-timeout 5s
          --health-retries 10
```

`SlotwiseApplicationTests.java` (le test par défaut généré par Spring Initializr) utilise `@SpringBootTest`, qui démarre **tout** le contexte Spring, y compris la connexion à la base — contrairement à `ReservationServiceTest` (Mockito, aucune vraie DB nécessaire). Sans un vrai Postgres accessible pendant la CI, ce test échouerait.

GitHub Actions a son propre mécanisme de **"services"** — même principe qu'un service `docker-compose.yml` : un conteneur Postgres démarré en parallèle du job, avec un `healthcheck` similaire à celui qu'on avait écrit (`pg_isready`, intervalle de vérification, nombre de tentatives).

## `DB_HOST: localhost` — différence importante avec Docker Compose

```yaml
    env:
      DB_HOST: localhost
      DB_PORT: 5432
      DB_NAME: slotwise
      DB_USER: slotwise
      DB_PASSWORD: slotwise_test_password
```

Piège à connaître : dans `docker-compose.yml`, on avait `DB_HOST: db` (le nom du **service**, car Docker Compose crée un petit réseau où chaque service est joignable par son nom). Ici, dans GitHub Actions, les services sont exposés **directement sur `localhost`**, du point de vue de la machine qui exécute le job — une vraie différence d'infrastructure entre les deux outils, pas une erreur de copier-coller. Ces variables correspondent exactement à ce qu'attend `application.properties` (`${DB_HOST:localhost}`, etc., vu dans `docker-explique.md`).

## Les étapes (`steps`)

```yaml
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
```

`actions/checkout` : une **action toute faite**, fournie par GitHub, qui télécharge le code du dépôt sur la machine virtuelle. Sans elle, le job démarrerait sur une machine complètement vide, sans le code à construire.

```yaml
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
```

Installe Java 21 (distribution `temurin`, la même qu'en local). `cache: maven` : conserve les dépendances Maven déjà téléchargées **entre les exécutions** du workflow — même logique que le cache de couches vu dans le `Dockerfile`, pour accélérer les runs suivants (pas besoin de tout re-télécharger à chaque push).

```yaml
      - name: Build and run tests
        run: ./mvnw clean verify
```

L'étape réelle : compile le projet et lance les tests. Si un test échoue, GitHub marque le workflow en échec (visible dans l'onglet "Actions", et potentiellement bloquant sur une pull request si configuré ainsi côté GitHub).

## Vérifié avant de pousser

Avant de pousser ce fichier, la commande exacte de la CI (`./mvnw clean verify`, avec les mêmes variables d'environnement `DB_HOST=localhost` etc.) a été exécutée en local, contre le Postgres déjà disponible sur la machine :

```
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

(5 tests = les 4 de `ReservationServiceTest` + `contextLoads` de `SlotwiseApplicationTests`.)

## Un piège rencontré au push

```
remote: refusing to allow an OAuth App to create or update workflow `.github/workflows/ci.yml` without `workflow` scope
```

GitHub exige une permission **spécifique** (`workflow`) pour créer/modifier un fichier sous `.github/workflows/` — au-delà du simple droit d'écrire dans le dépôt, car ces fichiers peuvent déclencher l'exécution automatique de code. Résolu en rafraîchissant l'autorisation avec ce scope en plus (`gh auth refresh -h github.com -s workflow` pour GitHub CLI).
