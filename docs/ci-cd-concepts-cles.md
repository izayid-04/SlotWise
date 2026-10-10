# CI (GitHub Actions) — les concepts clés à retenir

Condensé — voir `ci-cd-explique.md` pour le détail.

---

| Concept | Une phrase à retenir |
|---|---|
| **`on: push` / `pull_request`** | Déclenche le workflow automatiquement à chaque push ou PR sur les branches ciblées. |
| **`runs-on`** | Le job s'exécute sur une machine virtuelle **fraîche** à chaque fois — rien n'est conservé sauf ce qui est explicitement caché. |
| **`services:` (GitHub Actions)** | Même principe qu'un service Docker Compose — démarre un conteneur (ex: Postgres) en parallèle du job, nécessaire si un test démarre un vrai contexte Spring (`@SpringBootTest`). |
| **`DB_HOST: localhost` en CI, `DB_HOST: db` en Docker Compose** | Piège à retenir : les deux outils exposent leurs services différemment. GitHub Actions → `localhost`. Docker Compose → nom du service. Jamais la même valeur entre les deux contextes. |
| **`actions/checkout`** | Action toute faite qui télécharge le code du dépôt sur la machine virtuelle — sans elle, rien à construire. |
| **`cache: maven`** | Conserve les dépendances téléchargées entre les exécutions du workflow — accélère les runs suivants, même logique que le cache de couches Docker. |
| **Tester la commande CI en local avant de la pousser** | Simuler `./mvnw clean verify` avec les mêmes variables d'environnement en local donne confiance que le workflow passera réellement, avant de le découvrir en échec sur GitHub. |
| **Scope `workflow` manquant** | GitHub exige une permission spécifique pour créer/modifier un fichier `.github/workflows/**` — au-delà du simple droit d'écrire dans le dépôt. |
