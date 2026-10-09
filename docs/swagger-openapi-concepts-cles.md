# Swagger / OpenAPI — les concepts clés à retenir

Condensé — voir `swagger-openapi-explique.md` pour le détail.

---

| Concept | Une phrase à retenir |
|---|---|
| **Swagger / springdoc-openapi** | Génère une doc interactive de l'API **automatiquement** à partir des contrôleurs — rien écrit à la main. |
| **`/v3/api-docs`** | La spec de l'API en JSON brut, générée automatiquement. |
| **`/swagger-ui/index.html`** | L'interface visuelle, construite à partir de ce JSON. |
| **`OpenApiConfig` (`@Bean OpenAPI`)** | Personnalise la doc générée : titre/description (présentation) + déclaration du schéma d'auth (bearer JWT) pour activer le bouton "Authorize". |
| **`permitAll()` sur les routes Swagger** | Même règle que `/error` : une page chargée avant authentification doit être publique, sinon problème de l'œuf et la poule (impossible de charger la page qui donne accès à l'auth). |
| **"Authorize" dans l'interface** | Aucune logique de sécurité propre à Swagger — juste un confort qui ajoute automatiquement le header `Authorization: Bearer <token>` à chaque requête de test, au lieu de le retaper à chaque fois. |
| **Toute la vraie sécurité reste côté backend** | `JwtAuthenticationFilter` + `SecurityConfig`, inchangés — Swagger ne fait qu'appeler l'API normalement, avec ou sans header selon qu'on a "autorisé" ou pas. |
