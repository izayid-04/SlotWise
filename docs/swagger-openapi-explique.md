# Swagger / OpenAPI expliqué (SlotWise)

Doc interactive de l'API, générée automatiquement à partir du code — pas écrite à la main. Fichiers : `OpenApiConfig.java` + 2 lignes ajoutées dans `SecurityConfig.java`.

---

## Pourquoi Swagger

Jusqu'ici, pour tester une route, il fallait `curl` ou Postman, en retapant à la main les headers, le JSON, le token. Swagger génère une **page web** qui liste toutes les routes de l'API (lues directement depuis les contrôleurs), avec un formulaire pour chacune, et une interface pour coller son token une fois et l'appliquer automatiquement à tous les tests suivants.

Librairie utilisée : `springdoc-openapi-starter-webmvc-ui` (version `3.1.1`, la version compatible avec Spring Boot 4 / Spring Framework 7 — les versions `2.x` ne supportent que Boot 3).

## Deux URLs à connaître

- `/v3/api-docs` : la spec de l'API en **JSON brut** (générée automatiquement en lisant les contrôleurs) — ce que Swagger UI va chercher pour savoir quoi afficher.
- `/swagger-ui/index.html` : l'**interface visuelle**, construite à partir de ce JSON.

## `OpenApiConfig` — personnaliser la doc générée

```java
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI slotwiseOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SlotWise API")
                        .description("Système de gestion de réservations de ressources")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
```

`OpenAPI` est l'objet racine que springdoc transforme en JSON puis en interface — même principe `@Configuration`/`@Bean` que `SecurityConfig`.

- `.info(...)` : juste la présentation (titre, description, version) affichée en haut de la page — pas fonctionnel.
- `new SecurityScheme().type(HTTP).scheme("bearer").bearerFormat("JWT")` : **décrit** le type d'authentification de l'API (bearer token, format JWT) — c'est ce qui fait apparaître le bon type de champ dans le bouton "Authorize" (un simple champ texte pour le token, pas un login/mot de passe).
- `.components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME, ...))` : **enregistre** ce schéma sous un nom (`"bearerAuth"`), réutilisable.
- `.addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))` : **applique** ce schéma à **toutes** les routes par défaut, globalement (pas route par route). Conséquence cosmétique : `/auth/register`/`/auth/login` affichent aussi un cadenas dans l'interface, même si elles n'ont en réalité pas besoin de token côté backend — ça n'empêche rien de fonctionner, juste un petit décalage visuel qu'on a choisi d'accepter pour rester simple.

## Les 2 lignes ajoutées dans `SecurityConfig`

```java
.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
```

Même principe déjà vu avec `/error` : toute route chargée par le navigateur **sans authentification préalable** doit être explicitement `permitAll()`, sinon le filet général (`anyRequest().authenticated()`) la bloque. Sans cette ligne : impossible de charger la page Swagger pour **obtenir** un token, puisqu'il faudrait déjà être authentifié pour voir la page qui permet de s'authentifier — un problème de l'œuf et la poule.

## Ce qui change concrètement entre "connecté" et "pas connecté" dans l'interface

Aucune logique de sécurité ne vit dans Swagger lui-même — tout reste côté backend (`JwtAuthenticationFilter` + `SecurityConfig`, déjà étudiés). La seule différence :

- **Pas cliqué sur "Authorize"** : chaque requête de test envoyée par Swagger part **sans** header `Authorization` → comportement identique à un `curl` sans token → 403 sur les routes protégées.
- **Après avoir collé un token dans "Authorize"** : Swagger **retient** ce token (côté navigateur) et l'ajoute automatiquement en header `Authorization: Bearer <token>` sur **chaque** requête de test suivante — évite de le coller manuellement à chaque route testée, exactement l'équivalent de `-H "Authorization: Bearer $TOKEN"` qu'on tapait avec `curl`.

## Testé et vérifié

- `GET /v3/api-docs` → 200, toutes les routes détectées (`/auth/register`, `/auth/login`, `/ressources`, `/ressources/{id}`, `/reservations`, `/reservations/mes-reservations`, `/reservations/{id}`)
- `GET /swagger-ui/index.html` → 200
- Connexion via l'interface avec un compte promu `ADMIN`, token collé dans "Authorize", test réussi sur les routes protégées
