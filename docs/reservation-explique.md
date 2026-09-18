# Feature `Reservation` expliquée (SlotWise)

C'est la feature centrale du cahier des charges (détection de conflit de créneaux). Elle introduit plusieurs concepts nouveaux par rapport à `utilisateur`/`ressource` : relations entre entities, requêtes dérivées combinées, et récupération de l'utilisateur connecté dans un contrôleur.

---

## `StatutReservation` (enum)

```java
public enum StatutReservation { CONFIRMEE, ANNULEE }
```

Même principe que `Role` (`ADMIN`/`USER`) — rien de nouveau.

## `Reservation` (entity) — les relations `@ManyToOne`

```java
@ManyToOne(optional = false)
@JoinColumn(name = "utilisateur_id", nullable = false)
private Utilisateur utilisateur;

@ManyToOne(optional = false)
@JoinColumn(name = "ressource_id", nullable = false)
private Ressource ressource;
```

Contrairement aux entities précédentes (champs simples : `String`, `Integer`...), `Reservation` contient **d'autres entities entières** — une réservation appartient à un utilisateur et concerne une ressource.

- `@ManyToOne` : "plusieurs `Reservation` peuvent pointer vers le même `Utilisateur`" (un utilisateur peut avoir plusieurs réservations, une réservation n'a qu'un seul utilisateur).
- `@JoinColumn(name = "utilisateur_id")` : en base, ça se traduit par une colonne `utilisateur_id` dans `reservations`, contenant l'`id` de la ligne correspondante dans `utilisateurs` — une **clé étrangère**.
- `optional = false` / `nullable = false` : une réservation doit toujours avoir un utilisateur et une ressource.

`reservation.getUtilisateur()` renvoie l'objet `Utilisateur` **complet** (pas juste son id) — Hibernate va le chercher en base via la clé étrangère.

## `ReservationRepository` — requêtes dérivées sur relation + conflit

```java
List<Reservation> findByUtilisateurId(Long utilisateurId);
```

Spring Data JPA sait **traverser une relation** : `findByUtilisateurId` va dans le `Utilisateur` lié à la réservation et compare son `id` (`WHERE utilisateur_id = ?`).

```java
List<Reservation> findByRessourceIdAndStatutAndDateDebutBeforeAndDateFinAfter(
        Long ressourceId, StatutReservation statut, LocalDateTime dateFin, LocalDateTime dateDebut);
```

Traduit directement la règle du cahier des charges : deux créneaux `[debut1,fin1]`/`[debut2,fin2]` sont en conflit si `debut1 < fin2 ET debut2 < fin1`. Ici, debut1/fin1 = réservation **existante**, debut2/fin2 = créneau **demandé** :
- `RessourceId` → même ressource
- `Statut` → uniquement les réservations `CONFIRMEE`
- `DateDebutBefore` → `dateDebut` existante **avant** une valeur fournie (`Before` = mot-clé Spring Data → `<`)
- `DateFinAfter` → `dateFin` existante **après** une valeur fournie (`After` → `>`)

Appel réel : `findBy...(ressourceId, CONFIRMEE, request.dateFin(), request.dateDebut())` → `dateDebut_existante < dateFin_demandée ET dateFin_existante > dateDebut_demandée`. Comme pour toutes les requêtes dérivées, **aucune implémentation n'est écrite** — Spring Data JPA génère tout à partir du nom de la méthode.

## Les 4 exceptions

| Exception | Code | Particularité |
|---|---|---|
| `ReservationConflictException` | 409 | Message fixe, pattern identique à `EmailDejaUtiliseException` |
| `ReservationNotFoundException` | 404 | Prend l'`id` en paramètre, identique à `RessourceNotFoundException` |
| `ReservationAccessDeniedException` | 403 | Message fixe — n'est **pas** conçue pour être réutilisée ailleurs (contrairement à la suivante) |
| `ReservationInvalidException` | 400 | Prend un `message` en paramètre — réutilisable pour plusieurs validations différentes |

Rappel important : `HttpStatus.CONFLICT/NOT_FOUND/FORBIDDEN/BAD_REQUEST` sont des constantes de l'enum `HttpStatus` fourni par Spring, chacune mappée à un code numérique (409/404/403/400).

## DTOs

`ReservationRequest` : `ressourceId` (`Long`, pas l'objet `Ressource` entier — le client n'envoie que l'id, le service va chercher l'objet complet), `dateDebut`, `dateFin` (`LocalDateTime`, `@NotNull`).

`ReservationResponse` : **aplatit** les relations plutôt que de les exposer telles quelles — `reservation.getRessource().getId()`/`.getNom()` et `reservation.getUtilisateur().getId()` sont extraits et mis à plat comme champs simples (`ressourceId`, `ressourceNom`, `utilisateurId`), au lieu de renvoyer les objets `Ressource`/`Utilisateur` imbriqués en entier. Cohérent avec le principe déjà vu : ne jamais exposer une entity brute, choisir explicitement ce qu'on renvoie.

## `ReservationService`

Injecte **3 repositories** (`ReservationRepository`, `RessourceRepository`, `UtilisateurRepository`) — contrairement aux services précédents qui n'en avaient qu'un, car cette feature doit vérifier des choses dans 3 tables différentes.

### `create(ReservationRequest request, String emailUtilisateurConnecte)`

Prend **2 paramètres** : le DTO, mais aussi l'email de la personne connectée (jamais fourni par le client dans le body — déduit du token côté contrôleur, sinon n'importe qui pourrait prétendre être quelqu'un d'autre). Déroulement :
1. `request.dateDebut().isBefore(request.dateFin())` → sinon `ReservationInvalidException` (400)
2. `ressourceRepository.findById(...)` → sinon `RessourceNotFoundException` (404) — réutilisation d'une exception d'une **autre feature**, logique car une réservation dépend forcément d'une ressource existante
3. `utilisateurRepository.findByEmail(...)` → sinon `UsernameNotFoundException` (sécurité défensive, ne devrait jamais arriver avec un token valide)
4. Vérification du conflit via la requête dérivée → sinon `ReservationConflictException` (409)
5. Construction avec `StatutReservation.CONFIRMEE` **codé en dur** (jamais laissé au client, même principe que `Role.USER` dans `register`) + `save`

### `findMyReservations(String emailUtilisateurConnecte)`

Retrouve l'`Utilisateur` par email, puis `findByUtilisateurId` — garantit que `/mes-reservations` ne renvoie jamais les réservations de quelqu'un d'autre.

### `cancel(Long id, String emailUtilisateurConnecte)`

Récupère la réservation par id (sinon 404), **vérifie la propriété** (`reservation.getUtilisateur().getEmail().equals(emailUtilisateurConnecte)`, sinon `ReservationAccessDeniedException` 403), puis change le statut à `ANNULEE` et sauvegarde — **ne supprime jamais la ligne**, conforme au cahier des charges.

## `ReservationController` — `@AuthenticationPrincipal`

```java
@PostMapping
public ResponseEntity<ReservationResponse> create(@Valid @RequestBody ReservationRequest request,
                                                    @AuthenticationPrincipal UserDetails userDetails) {
    Reservation reservation = reservationService.create(request, userDetails.getUsername());
    ...
}
```

Rappel du "casier" (`SecurityContextHolder`) rempli par `JwtAuthenticationFilter` une fois le token vérifié (voir `spring-security-jwt-explique.md`). `@AuthenticationPrincipal` dit à Spring : *"va chercher ce qui est dans le casier de cette requête, et donne-le moi directement comme paramètre"* — équivalent à `SecurityContextHolder.getContext().getAuthentication().getPrincipal()` mais en une annotation.

`userDetails.getUsername()` renvoie l'**email** (rappel : `UtilisateurDetailsService` avait mis `.username(utilisateur.getEmail())`). C'est cette valeur qui est transmise au service comme `emailUtilisateurConnecte`. Même principe dans `findMyReservations` et `cancel`.

**C'est le même casier, le même mécanisme, partout dans l'appli** — rempli une seule fois par `JwtAuthenticationFilter`, lisible depuis n'importe quel contrôleur ensuite.

## Testé et vérifié (curl) — cycle complet

| Test | Attendu | Résultat |
|---|---|---|
| Créer réservation 10h-11h | 201 | ✅ |
| Créneau chevauchant (10h30-11h30) | 409 | ✅ |
| Créneau non chevauchant (11h-12h) | 201 | ✅ |
| `dateDebut` ≥ `dateFin` | 400 | ✅ |
| Ressource inexistante | 404 | ✅ |
| `GET /mes-reservations` | 200, liste filtrée par utilisateur | ✅ |
| User2 annule la réservation de User1 | 403 | ✅ |
| User1 annule sa propre réservation | 204 | ✅ |
| Statut après annulation | `ANNULEE` (pas supprimée) | ✅ |

> Comme pour `Ressource`, aucune restriction par rôle pour l'instant. La vue admin bonus (`GET /reservations`, toutes réservations confondues) n'est pas encore implémentée — prévue avec l'étape "permissions par rôle".
