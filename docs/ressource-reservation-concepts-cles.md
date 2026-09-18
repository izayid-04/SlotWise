# Ressource & Reservation — les concepts clés à retenir

Condensé, dans l'esprit de `spring-security-concepts-cles.md` : pas le détail du code (voir `ressource-explique.md`/`reservation-explique.md`), juste l'essentiel à garder en tête.

---

| Concept | Une phrase à retenir |
|---|---|
| **`Ressource` (feature)** | Pattern CRUD simple, identique à ce qu'on connaissait déjà (`utilisateur` + `auth`) — sert surtout à consolider, rien de nouveau conceptuellement. |
| **`@ManyToOne` / `@JoinColumn`** | Une entity peut contenir une autre entity entière (pas juste son id) — Hibernate va la chercher en base via une clé étrangère. `reservation.getUtilisateur()` renvoie l'`Utilisateur` complet. |
| **Requête dérivée sur une relation** (`findByUtilisateurId`) | Spring Data JPA sait traverser une relation `@ManyToOne` dans le nom de la méthode, pas seulement les champs simples. |
| **Requête dérivée combinée** (`...AndStatutAndDateDebutBeforeAndDateFinAfter`) | Plusieurs conditions s'enchaînent avec `And`, `Before`/`After` = `<`/`>`. Toujours généré par Spring, jamais implémenté à la main. |
| **Service avec plusieurs repositories** | Normal qu'un service dépende de plusieurs repositories différents dès qu'une feature touche plusieurs tables (ex: `Reservation` a besoin de `Ressource` et `Utilisateur`). |
| **Réutiliser une exception d'une autre feature** | Logique et acceptable quand une feature dépend structurellement d'une autre (`RessourceNotFoundException` réutilisée dans `ReservationService`). |
| **`@AuthenticationPrincipal UserDetails`** | Raccourci pour lire le "casier" (`SecurityContextHolder`) directement en paramètre de contrôleur. `.getUsername()` = l'email de la personne connectée. Toujours le même casier, rempli une seule fois par le filtre JWT. |
| **Jamais faire confiance au client pour "qui il est"** | L'identité (email → utilisateur) vient toujours du token via `@AuthenticationPrincipal`, jamais d'un champ envoyé dans le body. |
| **Vérification de propriété (ownership)** | Comparer `entity.getUtilisateur().getEmail()` à l'email connecté = logique métier simple (pas Spring Security), à ne pas confondre avec une vérification de **rôle**. |
| **DTO Response et relations** | On "aplatit" toujours les relations dans un DTO de sortie (`ressourceId`, `ressourceNom` plutôt que l'objet `Ressource` imbriqué) — même principe que "ne jamais exposer une entity brute". |
| **Annuler ≠ supprimer** | `DELETE /reservations/{id}` ne supprime pas la ligne — il change juste `statut` à `ANNULEE`. Toujours vérifier ce que demande vraiment le cahier des charges avant de coder un `deleteById`. |

## Ce qui reste volontairement en suspens

Les restrictions par **rôle** (`ADMIN` uniquement sur `POST`/`PUT`/`DELETE /ressources`, vue admin bonus sur `/reservations`) ne sont pas encore faites — c'est l'étape suivante, délibérément séparée pour avoir de vraies routes à tester dessus.
