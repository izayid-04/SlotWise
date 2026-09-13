# Spring Security + JWT expliqué (SlotWise)

Ce document reprend, fichier par fichier, l'implémentation actuelle de l'authentification dans SlotWise. Il sert de mémo personnel pour se rappeler *pourquoi* chaque fichier existe et *comment* il fonctionne, sans avoir à tout redécouvrir.

> Ce document sera complété au fur et à mesure (il manque encore `JwtAuthenticationFilter`, `SecurityConfig` et les exceptions custom).

---

## 1. Organisation en "package by feature"

Le projet est organisé par fonctionnalité métier plutôt que par couche technique : chaque dossier (`auth/`, `utilisateur/`, plus tard `ressource/`, `reservation/`) contient tout ce qui le concerne (entity, repository, service, controller, dto), au lieu d'avoir un dossier `controllers/`, un dossier `services/`, etc. rassemblant tout le projet.

---

## 2. Les DTO (Data Transfer Object)

Un DTO décrit **la forme des données échangées via l'API** — rien à voir avec la base de données ni la logique métier. Ici, ce sont des `record` Java (classes immuables où Java génère automatiquement constructeur, getters, `equals`/`hashCode`/`toString`).

### DTO "Request" (`RegisterRequest`, `LoginRequest`)

```java
public record LoginRequest(
        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "L'email doit être valide")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire")
        String motDePasse
) {}
```

Point important : **le DTO seul ne valide rien**. Les annotations (`@NotBlank`, `@Email`, `@Size`) ne sont que des règles déclarées sur les champs. Ce qui déclenche réellement la vérification, c'est `@Valid` posé dans le contrôleur :

```java
public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request)
```

Si une règle échoue, Spring renvoie un 400 automatiquement, sans jamais exécuter le corps de la méthode.

### DTO "Response" (`LoginResponse`, `UtilisateurResponse`)

```java
public record UtilisateurResponse(Long id, String nom, String email, Role role) {
    public static UtilisateurResponse fromEntity(Utilisateur utilisateur) {
        return new UtilisateurResponse(
                utilisateur.getId(), utilisateur.getNom(),
                utilisateur.getEmail(), utilisateur.getRole()
        );
    }
}
```

Décrit la forme du JSON renvoyé, et surtout **filtre ce qu'on expose**. L'entity `Utilisateur` contient `motDePasse` (le hash BCrypt) — si on la renvoyait directement, ce hash fuiterait dans la réponse JSON. `fromEntity(...)` fait donc un mapping manuel qui ne garde que les champs safe à exposer. (Plus tard, MapStruct générera ce mapping automatiquement.)

---

## 3. Le package `utilisateur`

### `Utilisateur` (entity JPA)

```java
@Entity
@Table(name = "utilisateurs")
public class Utilisateur { ... }
```

`@Entity` dit à JPA/Hibernate que cette classe correspond à une table. `@Table(name = "utilisateurs")` fixe le nom exact de la table. `@Id` + `@GeneratedValue` désignent la clé primaire auto-incrémentée.

### `Role` (enum)

```java
public enum Role { ADMIN, USER }
```

Utilisé avec `@Enumerated(EnumType.STRING)` sur l'entity : Hibernate stocke `"ADMIN"`/`"USER"` en texte en base (plutôt que `0`/`1`, plus fragile).

### `UtilisateurRepository`

```java
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {
    Optional<Utilisateur> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

Une interface **sans implémentation écrite**, et pourtant fonctionnelle :

- En héritant de `JpaRepository<Utilisateur, Long>` (`Utilisateur` = type de l'entity, `Long` = type de sa clé primaire), on obtient gratuitement `save()`, `findById()`, `findAll()`, `deleteById()`, etc. Spring génère lui-même une implémentation en coulisses au démarrage.
- `findByEmail` et `existsByEmail` sont des **derived query methods** : Spring Data JPA lit le nom de la méthode et en déduit la requête tout seul (`findBy` + nom du champ → `WHERE champ = ?`). Aucune requête SQL/JPQL à écrire tant que le nom respecte la convention. Pour des requêtes trop complexes, on utiliserait `@Query`.

---

## 4. `UtilisateurDetailsService` — le traducteur vers Spring Security

Spring Security ne connaît rien de la classe `Utilisateur` — c'est une classe propre au projet. Il ne connaît que son propre contrat : l'interface `UserDetailsService`, avec une seule méthode obligatoire `loadUserByUsername`. Ce fichier est **le pont** entre le monde métier (`Utilisateur`, en base) et le monde de Spring Security (`UserDetails`).

```java
@Override
public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    Utilisateur utilisateur = utilisateurRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("Aucun utilisateur avec l'email : " + email));

    return User.builder()
            .username(utilisateur.getEmail())
            .password(utilisateur.getMotDePasse())
            .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + utilisateur.getRole().name())))
            .build();
}
```

Explication détaillée :

- `loadUserByUsername` : nom et signature **imposés** par l'interface `UserDetailsService`, non modifiables. `load` = charger/lire une donnée existante, **pas créer**.
- `utilisateurRepository.findByEmail(email)` renvoie un `Optional<Utilisateur>` (peut être vide).
- `.orElseThrow(() -> new UsernameNotFoundException(...))` : si vide, lève cette exception spécifique **fournie par Spring Security**, qu'`AuthenticationManager` saura reconnaître comme "échec d'authentification" plus tard.
- `User.builder()...build()` : `User` ici est une classe **de Spring Security** (`org.springframework.security.core.userdetails.User`), totalement différente de `Utilisateur` (l'entity JPA du projet). Ne pas confondre les deux.
  - `.build()` ne persiste rien en base — ça assemble juste un **objet Java temporaire, en mémoire**, qui existe seulement le temps de traiter la requête en cours (contrairement à `utilisateurRepository.save(...)` qui, lui, écrit vraiment en base).
  - `.username(...)` : le champ s'appelle `username` par convention générique de Spring Security ("l'identifiant de connexion", quel qu'il soit), même si dans ce projet on y met un email.
  - `.password(...)` : le hash BCrypt déjà stocké en base (jamais un mot de passe en clair).
  - `.authorities(...)` : liste des droits. `SimpleGrantedAuthority("ROLE_" + role.name())` — le préfixe `"ROLE_"` est une convention stricte de Spring Security, nécessaire pour que des vérifications comme `hasRole("ADMIN")` fonctionnent plus tard (elles cherchent en fait `"ROLE_ADMIN"`).

**Résumé** : ce fichier répond à une seule question posée par Spring Security au moment du login : *"donne-moi cet utilisateur, dans le format que je comprends, à partir de son identifiant"*. Il ne compare pas le mot de passe et ne génère pas de token — juste récupération + traduction.

**Ce fichier n'est pas un pattern à répéter par feature.** Il n'existe qu'**une seule fois** dans tout le projet, car il n'y a qu'un seul système d'authentification pour toute l'application. Les futures features (`Ressource`, `Reservation`) n'auront pas besoin d'un équivalent — elles seront juste protégées par le mécanisme JWT déjà en place.

---

## 5. `JwtService` — fabriquer et vérifier les tokens

Un JWT est une chaîne `header.payload.signature`. La signature garantit qu'on ne peut pas fabriquer ou modifier un token sans connaître la clé secrète du serveur.

### Les champs de configuration

```java
@Value("${jwt.secret}")
private String secret;

@Value("${jwt.expiration-ms}")
private long expirationMs;
```

`@Value("${...}")` va chercher la valeur dans `application.properties` :
```
jwt.secret=${JWT_SECRET:FwFS0TdZlc75jHZTklS5GMsWOMG5XbKbwUbYoXvYGWY=}
jwt.expiration-ms=${JWT_EXPIRATION_MS:86400000}
```
`secret` = clé utilisée pour signer/vérifier les tokens (jamais codée en dur, configurable via variable d'env `JWT_SECRET`). `expirationMs` = durée de vie du token (86400000 ms = 24h par défaut).

### `generateToken`

```java
public String generateToken(UserDetails userDetails) {
    long now = System.currentTimeMillis();

    return Jwts.builder()
            .subject(userDetails.getUsername())
            .issuedAt(new Date(now))
            .expiration(new Date(now + expirationMs))
            .signWith(getSigningKey())
            .compact();
}
```

- `UserDetails userDetails` : l'objet construit par `UtilisateurDetailsService`.
- `.subject(userDetails.getUsername())` : à qui appartient le token (ici, l'email).
- `.issuedAt(...)` / `.expiration(...)` : date de création / date d'expiration.
- `.signWith(getSigningKey())` : signe le token avec la clé secrète — rend le token infalsifiable.
- `.compact()` : assemble tout en la chaîne finale `header.payload.signature`.

### `getSigningKey`

```java
private SecretKey getSigningKey() {
    byte[] keyBytes = Decoders.BASE64.decode(secret);
    return Keys.hmacShaKeyFor(keyBytes);
}
```

Décode le secret (stocké en Base64) en octets bruts, puis construit la clé HMAC-SHA utilisée **à la fois** pour signer (création) et vérifier (lecture) — signature symétrique, même clé des deux côtés.

### `extractClaim` (méthode générique de lecture)

```java
private <T> T extractClaim(String token, Function<Claims, T> resolver) {
    Claims claims = Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();

    return resolver.apply(claims);
}
```

- `.parseSignedClaims(token)` : **vérifie la signature** du token (recalculée et comparée à celle contenue dans le token). Si le contenu a été modifié ou signé avec une autre clé, une exception est levée ici — le token est rejeté avant même d'être lu.
- `.getPayload()` renvoie l'objet `Claims` (map contenant `subject`, `issuedAt`, `expiration`...).
- `Function<Claims, T> resolver` : une fonction passée en paramètre (comme un callback), qui précise *quelle* donnée extraire du `Claims`, sans dupliquer la logique de vérification à chaque fois.

Cette méthode ne "extrait pas la signature" — elle **vérifie** la signature puis **extrait une donnée du payload**.

### `extractEmail` et `isTokenExpired`

```java
public String extractEmail(String token) {
    return extractClaim(token, Claims::getSubject);
}

private boolean isTokenExpired(String token) {
    return extractClaim(token, Claims::getExpiration).before(new Date());
}
```

`Claims::getSubject` / `Claims::getExpiration` sont des références de méthode (équivalent de `claims -> claims.getSubject()`). `extractEmail` relit l'email mis dans le token à sa création. `isTokenExpired` compare la date d'expiration à maintenant.

### `isTokenValid` (méthode qui englobe tout)

```java
public boolean isTokenValid(String token, UserDetails userDetails) {
    String email = extractEmail(token);
    return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
}
```

Deux conditions requises en même temps :
1. L'email du token correspond à l'utilisateur vérifié.
2. Le token n'est pas expiré.

(La vérification de la signature, elle, a déjà eu lieu plus tôt dans `extractClaim` — si elle échoue, une exception est levée avant d'arriver ici.)

Cette méthode sera appelée par `JwtAuthenticationFilter` à chaque requête protégée (à documenter).

---

## 6. `JwtAuthenticationFilter` — identifier l'utilisateur à chaque requête

À bien distinguer de `SecurityConfig` (vu après) : **ce filtre ne décide jamais si une route est publique ou protégée**. Il s'exécute sur *toutes* les requêtes, sans exception, et fait toujours la même chose : *"s'il y a un token exploitable, vérifie-le et note qui est connecté ; sinon, ne fais rien de spécial."* Dans tous les cas, il laisse toujours la requête continuer son chemin. C'est **`SecurityConfig`**, exécuté juste après dans la chaîne, qui applique la vraie règle "cette route précise exige-t-elle d'être authentifié".

### La classe et ses dépendances

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UtilisateurDetailsService utilisateurDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UtilisateurDetailsService utilisateurDetailsService) {
        this.jwtService = jwtService;
        this.utilisateurDetailsService = utilisateurDetailsService;
    }
```

`extends OncePerRequestFilter` : classe fournie par Spring, garantit que le filtre s'exécute **une seule fois par requête** reçue par le client (même si, en interne, un forward Servlet pourrait sinon le redéclencher). `jwtService` et `utilisateurDetailsService` sont injectés via le constructeur — ce filtre a besoin de décoder/vérifier un token, et de recharger un utilisateur.

### `doFilterInternal` — la méthode exécutée à chaque requête

Signature imposée par `OncePerRequestFilter` :
```java
protected void doFilterInternal(HttpServletRequest request,
                                 HttpServletResponse response,
                                 FilterChain filterChain) throws ServletException, IOException {
```
- `request` : la requête entrante (headers, body, URL...).
- `response` : la réponse à construire (non utilisée directement dans ce filtre).
- `filterChain` : représente "la suite du chemin" — les filtres suivants, puis finalement le contrôleur.

**Étape 1 — lire le header et sortir tôt si rien d'exploitable :**
```java
String authHeader = request.getHeader("Authorization");

if (authHeader == null || !authHeader.startsWith("Bearer ")) {
    filterChain.doFilter(request, response);
    return;
}
```
`request.getHeader("Authorization")` lit le header HTTP `Authorization` (ex: `Authorization: Bearer eyJhbGc...`). La condition : si le header est absent, **ou** ne commence pas par `"Bearer "`, on laisse passer la requête sans rien authentifier (`filterChain.doFilter`) et on sort immédiatement (`return`) — le reste de la méthode ne s'exécute pas. C'est ce chemin qui est emprunté pour `/auth/register` et `/auth/login`.

**Étape 2 — extraire le token et l'email :**
```java
String token = authHeader.substring(7);
String email = jwtService.extractEmail(token);
```
`"Bearer "` fait exactement 7 caractères ; `substring(7)` coupe ce préfixe et ne garde que le token JWT brut. `jwtService.extractEmail(token)` vérifie la signature du token et en lit le `subject` (l'email mis dedans à la création).

**Étape 3 — le casier `SecurityContextHolder` :**

Chaque requête HTTP reçoit **son propre "casier" temporaire** (jamais partagé entre deux requêtes différentes, même simultanées), qui sert à stocker "qui est authentifié pour cette requête précise". `SecurityContextHolder.getContext().getAuthentication()` lit ce qu'il y a dedans (`null` si vide) ; `SecurityContextHolder.getContext().setAuthentication(...)` y dépose quelqu'un.

```java
if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
```
Les deux conditions (reliées par `&&`) doivent être vraies pour continuer : un email a bien été extrait, **et** le casier de cette requête est encore vide (personne authentifié avant, par un autre mécanisme éventuel). Ce n'est pas un cas d'erreur si l'une est fausse — juste une raison de ne rien faire de plus.

**Étape 4 — recharger l'utilisateur, vérifier le token, remplir le casier :**
```java
    UserDetails userDetails = utilisateurDetailsService.loadUserByUsername(email);

    if (jwtService.isTokenValid(token, userDetails)) {
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}

filterChain.doFilter(request, response);
```
- `loadUserByUsername(email)` recharge l'utilisateur **depuis la base** (pas juste depuis le token), pour avoir son état actuel (ex: si son rôle a changé depuis la création du token).
- `isTokenValid(token, userDetails)` revérifie correspondance d'email + expiration.
- `UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())` : objet Spring Security représentant "une authentification confirmée" — `null` à la place du mot de passe, car on ne re-vérifie jamais le mot de passe à chaque requête, seulement le token.
- `setAuthentication(authToken)` : dépose l'utilisateur dans le casier de cette requête.
- `filterChain.doFilter(request, response)` en toute fin, **en dehors de tous les `if`** : s'exécute toujours, quel que soit le résultat des étapes précédentes — ce filtre ne bloque jamais lui-même une requête, il se contente d'identifier quand c'est possible.

### Résumé du fichier

À chaque requête : token exploitable ? Non → laisse passer tel quel. Oui → extrait email + token, vérifie que le casier est vide, recharge l'utilisateur, vérifie le token ; si valide, dépose l'utilisateur dans le casier de cette requête. Dans tous les cas, la requête continue son chemin — la décision "cette route a-t-elle besoin d'être authentifiée" revient entièrement à `SecurityConfig`.

---

## 7. `SecurityConfig` — assembler tout le reste

Le fichier de configuration central : c'est lui qui décide **quelles routes ont besoin d'être authentifiées**, qui expose les beans réutilisables par le reste de l'appli, et qui insère `JwtAuthenticationFilter` dans la chaîne de filtres de Spring Security.

### `@Configuration` et le concept de "bean"

```java
@Configuration
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }
```

`@Configuration` dit à Spring : *"cette classe contient des instructions de configuration à lire au démarrage"* (différent de `@Service`/`@Component`, qui déclarent des objets métier). Un **bean** est un objet dont Spring gère lui-même la création et le cycle de vie, rendu disponible pour être injecté ailleurs dans l'appli. Une méthode annotée `@Bean` est exécutée une fois au démarrage, et son résultat devient un bean réutilisable partout via injection (constructeur), sans jamais avoir à écrire `new` soi-même à chaque endroit qui en a besoin.

### Bean 1 : `passwordEncoder`

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

`PasswordEncoder` est une interface Spring Security (le contrat "je sais hasher et comparer un mot de passe"). `BCryptPasswordEncoder` est l'implémentation concrète fournie toute faite par Spring Security — c'est elle qui contient réellement le code de hash/comparaison (`encode(...)`, `matches(...)`). Le bean, c'est cet objet `BCryptPasswordEncoder` lui-même (pas une chose séparée) : la méthode `passwordEncoder()` est juste la "recette" qui le fabrique une fois ; l'injection ailleurs (ex: dans un service) ne fait que livrer cet objet déjà construit, elle ne hashe rien elle-même. Déclarer ce bean une seule fois ici permet de le réutiliser partout, et de changer d'algorithme facilement en ne modifiant que cette méthode.

### Bean 2 : `authenticationManager`

```java
@Bean
public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
}
```

`AuthenticationManager` est une interface Spring Security dont le rôle est : *"vérifier si un couple identifiant + mot de passe est correct."* `AuthenticationConfiguration config` est un objet fourni automatiquement par Spring (même principe que l'injection habituelle), qui sait construire un `AuthenticationManager` complet en réutilisant en coulisses les autres beans déjà déclarés dans l'appli (dont `UtilisateurDetailsService` et `PasswordEncoder`). Cette méthode ne code aucune logique de vérification elle-même — elle ne fait qu'exposer, comme bean réutilisable, un objet déjà entièrement assemblé par Spring Security.

### Bean 3 : `filterChain` — l'assemblage final

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/auth/**").permitAll()
                    .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
}
```

`HttpSecurity http` : un objet de configuration fourni automatiquement, qu'on remplit par chaînage de méthodes (chaque `.xxx(...)` configure un aspect et renvoie l'objet modifié, permettant d'enchaîner directement l'appel suivant). `SecurityFilterChain` = le type de la config finale et figée que Spring utilisera pour chaque requête ; `.build()` la fabrique à la fin.

**`.csrf(AbstractHttpConfigurer::disable)`** — désactive la protection CSRF. Cette protection est pensée pour les applis avec sessions/cookies : elle empêche un site malveillant de profiter de l'envoi automatique des cookies par le navigateur. Comme ce projet envoie le token manuellement dans un header (jamais automatiquement par le navigateur), cette faille ne s'applique pas.

**`.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))`** — Spring Security crée des sessions HTTP **par défaut**. Cette ligne force explicitement à ne jamais en créer (`STATELESS`) — nécessaire, pas juste optionnelle, car sans elle Spring garderait quand même des sessions en mémoire côté serveur, ce qui serait incohérent avec une architecture 100% basée sur le token.

**`.authorizeHttpRequests(auth -> ...)`** — la règle qui décide vraiment si une route a besoin d'authentification :
```java
.requestMatchers("/auth/**").permitAll()
.anyRequest().authenticated()
```
- `.requestMatchers("/auth/**")` sélectionne toutes les routes sous `/auth/` (`**` = joker, n'importe quoi après, y compris plusieurs niveaux). `.permitAll()` les rend accessibles sans authentification.
- `.anyRequest().authenticated()` est un filet de sécurité qui attrape tout le reste : il exige que quelqu'un soit présent dans le "casier" de la requête (`SecurityContextHolder`, rempli par `JwtAuthenticationFilter` — voir section 6). Si le casier est vide, 401.
- **L'ordre compte** : les règles sont évaluées dans l'ordre d'écriture ; les règles spécifiques doivent toujours précéder `anyRequest()`, sinon celle-ci attraperait tout avant que les règles précises n'aient leur chance.

Important : le casier ne contient **jamais le token JWT brut**. Une fois vérifié par le filtre, le token n'est conservé nulle part — le casier ne contient que l'identité (`UserDetails`) et les rôles de la personne, construits à partir du token, pas le token lui-même. Cohérent avec l'approche stateless : rien n'est retenu après la requête.

**`.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`** — Spring Security fait en réalité tourner toute une **chaîne** de filtres empilés (d'où `SecurityFilterChain`), la plupart fournis par défaut et invisibles. `UsernamePasswordAuthenticationFilter` est le filtre par défaut prévu pour une connexion classique par formulaire HTML — non utilisé ici, mais présent quand même dans la chaîne par défaut. `addFilterBefore(X, Y)` insère X juste avant Y dans l'ordre d'exécution : ça garantit que le filtre JWT du projet s'exécute tôt, avant les mécanismes par défaut de Spring Security.

### Résumé du fichier

`filterChain` assemble 4 réglages : pas de CSRF (inutile en JWT stateless), pas de session (stateless), les règles d'accès par route (`/auth/**` libre, le reste protégé — en lisant le casier rempli par `JwtAuthenticationFilter`), et l'insertion du filtre JWT dans la chaîne globale de Spring Security.

---

## À venir dans ce document

- Les exceptions custom (`EmailDejaUtiliseException`, `IdentifiantsInvalidesException`)
- Tests Postman de chaque scénario (register, login, requête protégée)
