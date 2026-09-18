# Feature `Ressource` expliquée (SlotWise)

Cette feature reprend le pattern déjà vu avec `utilisateur` (entity + repository) et `auth` (DTO + service + controller), sans concept nouveau — elle sert surtout à consolider ce pattern avant d'attaquer `Reservation`, plus complexe.

---

## `Ressource` (entity)

```java
@Entity
@Table(name = "ressources")
public class Ressource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    private String description;

    @Column(nullable = false)
    private Integer capacite;

    @Column(nullable = false)
    private Boolean disponible;

    // constructeur + getters + setters
}
```

Même pattern que `Utilisateur`. Seule nuance : `description` n'a **pas** `@Column(nullable = false)` — elle peut être `null` en base (description libre, optionnelle selon le cahier des charges). Contrairement à `Utilisateur`, cette entity a des **setters** sur tous les champs, car `PUT /ressources/{id}` doit pouvoir modifier un objet déjà chargé depuis la base.

## `RessourceRepository`

```java
public interface RessourceRepository extends JpaRepository<Ressource, Long> {
}
```

Aucune méthode custom — les opérations `save`, `findById`, `findAll`, `existsById`, `deleteById` fournies gratuitement par `JpaRepository` suffisent, contrairement à `UtilisateurRepository` qui avait besoin de `findByEmail`.

## `RessourceNotFoundException`

```java
@ResponseStatus(HttpStatus.NOT_FOUND)
public class RessourceNotFoundException extends RuntimeException {
    public RessourceNotFoundException(Long id) {
        super("Ressource introuvable avec l'id : " + id);
    }
}
```

Même pattern que `EmailDejaUtiliseException`/`IdentifiantsInvalidesException` (`@ResponseStatus` + `RuntimeException`), avec `HttpStatus.NOT_FOUND` (404) cette fois.

## DTOs

```java
public record RessourceRequest(
        @NotBlank String nom,
        String description,
        @NotNull @Positive Integer capacite,
        @NotNull Boolean disponible
) {}
```

Même principe que `RegisterRequest` (validation via annotations + `@Valid` dans le contrôleur). Nouveauté mineure : `@Positive` sur `capacite` — vérifie que le nombre est strictement supérieur à 0 (en plus de `@NotNull` qui vérifie juste qu'une valeur est présente).

```java
public record RessourceResponse(Long id, String nom, String description, Integer capacite, Boolean disponible) {
    public static RessourceResponse fromEntity(Ressource ressource) { ... }
}
```

Même pattern que `UtilisateurResponse` — ici, tous les champs de l'entity sont safe à exposer (pas de mot de passe à filtrer comme pour `Utilisateur`).

## `RessourceService`

```java
public Ressource create(RessourceRequest request) { ... }          // new + save
public List<Ressource> findAll() { ... }                            // repository.findAll()
public Ressource update(Long id, RessourceRequest request) { ... }  // findById + orElseThrow + setters + save
public void delete(Long id) { ... }                                 // existsById + deleteById
```

`update` : on récupère l'entity existante (`findById().orElseThrow(RessourceNotFoundException)`), on modifie ses champs via les setters, puis on la sauvegarde — Hibernate détecte que c'est une entity déjà existante (elle a un `id`) et fait un `UPDATE` SQL plutôt qu'un `INSERT`.

`delete` : on vérifie d'abord `existsById` avant d'appeler `deleteById`, pour pouvoir lever une 404 propre si l'id n'existe pas (sinon `deleteById` sur un id inexistant ne lève pas d'erreur par défaut, ce qui masquerait le problème).

**Convention de nommage** : méthodes en anglais (`create`, `findAll`, `update`, `delete`), cohérent avec `AuthService` (`register`, `login`) — décision prise explicitement pour homogénéiser tout le projet, même si les noms de domaine (`Ressource`, champs) restent en français.

## `RessourceController`

```java
@PostMapping   public ResponseEntity<RessourceResponse> create(@Valid @RequestBody RessourceRequest request) { ... }   // 201
@GetMapping    public ResponseEntity<List<RessourceResponse>> findAll() { ... }                                        // 200
@PutMapping("/{id}") public ResponseEntity<RessourceResponse> update(@PathVariable Long id, ...) { ... }               // 200
@DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id) { ... }                              // 204
```

`@PathVariable Long id` : extrait l'`id` directement depuis l'URL (`/ressources/3` → `id = 3`). `ResponseEntity.noContent().build()` sur `delete` renvoie un 204 (succès, mais rien à renvoyer dans le body — cohérent avec une suppression).

## Testé et vérifié (curl)

| Route | Résultat |
|---|---|
| `POST /ressources` | 201 |
| `GET /ressources` | 200, liste |
| `PUT /ressources/{id}` | 200 |
| `DELETE /ressources/{id}` | 204 |
| `PUT`/`DELETE` sur un `id` inexistant | 404 (`RessourceNotFoundException`, grâce au correctif `/error` appliqué plus tôt) |
| `GET /ressources` sans token | 403 (comportement par défaut de Spring Security sans `AuthenticationEntryPoint` custom — voir `spring-security-jwt-explique.md`) |

> Pour l'instant, toutes les routes sont juste `authenticated()` — aucune restriction par rôle. Le cahier des charges prévoit `/ressources` en écriture (`POST`/`PUT`/`DELETE`) réservé aux `ADMIN` : à faire dans l'étape "permissions par rôle", volontairement mise de côté pour l'instant.
