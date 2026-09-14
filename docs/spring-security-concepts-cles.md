# Spring Security + JWT — les concepts clés à retenir

Ce document ne détaille pas le code (voir `spring-security-jwt-explique.md` pour ça). Il liste juste **ce qu'il faut avoir en tête**, comme un réflexe, sans avoir besoin de relire le code à chaque fois. Le reste (syntaxe exacte, détails d'implémentation) s'ancre avec la pratique sur plusieurs projets, pas par cœur.

---

## Les briques Spring Data JPA (déjà acquis)

| Concept | Ce qu'il faut retenir |
|---|---|
| `JpaRepository<Entity, IdType>` | Donne gratuitement `save`, `findById`, `findAll`, `deleteById`... sans rien écrire |
| Derived query methods | `findByX` / `existsByX` → Spring génère la requête à partir du nom de méthode, pas de SQL à écrire pour les cas simples |

---

## Les briques Spring Security — l'essentiel

| Concept | Une phrase à retenir |
|---|---|
| **`UserDetailsService`** | Le contrat obligatoire : "donne-moi comment retrouver un utilisateur à partir de son identifiant". **Un seul par appli**, jamais répété par feature. |
| **`PasswordEncoder` (BCrypt)** | Jamais stocker/comparer un mot de passe en clair. On hash à l'inscription (`encode`), on ne recompare jamais nous-mêmes (`AuthenticationManager` le fait via `matches`). |
| **`AuthenticationManager`** | "Vérifie-moi ce couple email/mot de passe" — orchestre tout seul `UserDetailsService` + `PasswordEncoder`, on n'écrit jamais cette logique de comparaison nous-mêmes. |
| **JWT (le concept)** | Un token signé, auto-suffisant (contient l'identité + une date d'expiration), que le serveur ne stocke jamais — vérifié à chaque requête via sa signature. |
| **Filtre (`JwtAuthenticationFilter`)** | S'exécute sur *chaque* requête. Rôle unique : *identifier* (poser l'utilisateur dans le contexte si le token est valide). **Ne décide jamais** si une route est publique ou protégée. |
| **`SecurityContextHolder`** | Le "casier" — propre à chaque requête, jamais partagé entre deux requêtes. Contient l'identité déjà vérifiée, jamais le token brut lui-même. |
| **`SecurityConfig` / `authorizeHttpRequests`** | **C'est ici, et seulement ici**, que se décide quelle route a besoin d'être authentifiée. Séparation stricte : le filtre identifie, la config autorise. **Piège à retenir** : toujours mettre `/error` en `permitAll()`, sinon une erreur HTTP générée par l'appli (409, 400...) peut être écrasée par un 403 (le forward interne vers `/error` repasse par la sécurité). |
| **Stateless (`SessionCreationPolicy.STATELESS`)** | Spring crée des sessions par défaut — il faut explicitement dire de ne jamais le faire quand on fait du 100% token. |
| **`@Bean` / `@Configuration`** | Un objet fabriqué une fois par Spring, réutilisable partout par injection, sans jamais faire `new` soi-même à chaque endroit. |

---

## Les DTO — l'essentiel

| Type | Une phrase à retenir |
|---|---|
| **Request DTO** | Décrit la forme du JSON entrant + porte des règles (`@NotBlank`, `@Email`...). Ne valide rien tout seul — c'est `@Valid` dans le contrôleur qui déclenche la vérification. |
| **Response DTO** | Décrit ce qu'on renvoie, et surtout **filtre** ce qu'on expose. Ne jamais renvoyer une entity brute (fuite potentielle, ex: mot de passe hashé). |

---

## Le fil conducteur à garder en tête

1. **Register** : pas de sécurité, juste hash (`PasswordEncoder`) + `save`.
2. **Login** : `AuthenticationManager` vérifie (via `UserDetailsService` + `PasswordEncoder`) → si OK, `JwtService` fabrique un token.
3. **Requête protégée** : le filtre identifie (si token valide) → `SecurityConfig` autorise ou non selon la route.

Le reste (nom exact des méthodes, ordre des lignes, syntaxe du builder...) n'a pas besoin d'être mémorisé — il se retient naturellement en pratiquant sur d'autres projets. Ce qui compte, c'est de savoir **quel fichier fait quoi**, et de pouvoir retrouver/relire le détail (dans `spring-security-jwt-explique.md`) quand besoin.
