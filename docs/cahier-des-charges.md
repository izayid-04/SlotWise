# Système de Gestion de Réservations

## Contexte métier

Plateforme permettant à des utilisateurs de réserver des ressources (salle de réunion, bureau, matériel...) sur des créneaux horaires précis, avec un espace d'administration pour gérer les ressources disponibles.

---

## Entités (modèle de données)

### Utilisateur
| Champ | Type | Description |
|---|---|---|
| id | Long | Identifiant unique |
| nom | String | Nom de l'utilisateur |
| email | String | Email (unique), utilisé pour la connexion |
| motDePasse | String | Mot de passe hashé (BCrypt) |
| role | Enum | `ADMIN` ou `USER` |

### Ressource
| Champ | Type | Description |
|---|---|---|
| id | Long | Identifiant unique |
| nom | String | Ex: "Salle A" |
| description | String | Description libre |
| capacite | Integer | Nombre de personnes/unités |
| disponible | Boolean | Ressource active ou désactivée |

### Reservation
| Champ | Type | Description |
|---|---|---|
| id | Long | Identifiant unique |
| dateDebut | LocalDateTime | Début du créneau |
| dateFin | LocalDateTime | Fin du créneau |
| statut | Enum | `CONFIRMEE` ou `ANNULEE` |
| utilisateur | Utilisateur | Relation `@ManyToOne` |
| ressource | Ressource | Relation `@ManyToOne` |

---

## Fonctionnalités

### 1. Authentification
- `POST /auth/register` — inscription (rôle `USER` par défaut)
- `POST /auth/login` — connexion, retourne un token JWT
- Toutes les autres routes sont protégées, sauf ces deux-là

### 2. Gestion des ressources (réservé ADMIN)
- `POST /ressources` — créer une ressource
- `GET /ressources` — lister toutes les ressources (accessible à tout utilisateur connecté)
- `PUT /ressources/{id}` — modifier une ressource
- `DELETE /ressources/{id}` — supprimer une ressource

### 3. Gestion des réservations (USER connecté)
- `POST /reservations` — créer une réservation (avec vérification de conflit de créneau)
- `GET /reservations/mes-reservations` — lister ses propres réservations
- `DELETE /reservations/{id}` — annuler sa réservation (passe le statut à `ANNULEE`)

### 4. Règle métier centrale : détection de conflits
Avant de créer une réservation, vérifier qu'aucune réservation existante (statut `CONFIRMEE`) sur la **même ressource** ne chevauche le créneau demandé.

Deux créneaux `[debut1, fin1]` et `[debut2, fin2]` sont en conflit si :
```
debut1 < fin2 ET debut2 < fin1
```

### 5. Vue admin (bonus)
- `GET /reservations` — toutes les réservations, toutes ressources confondues
- Filtrage par ressource ou par période

---

## Stack technique

| Outil | Rôle |
|---|---|
| Spring Boot + Spring Web | Framework principal + création d'API REST |
| Spring Data JPA + PostgreSQL | Persistance des données |
| Spring Security + JWT | Authentification et autorisation |
| Bean Validation | Validation des données entrantes (`@Valid`, `@NotNull`...) |
| MapStruct | Conversion automatique Entity ↔ DTO |
| JUnit + Mockito | Tests unitaires (priorité : logique de conflit de créneaux) |
| Swagger / OpenAPI | Documentation interactive de l'API |
| Docker + docker-compose | Conteneurisation (app + base PostgreSQL) |
| GitHub Actions | CI : build + tests automatiques (bonus) |

---

## Architecture des dossiers (package by feature)

```
src/main/java/com/toncompte/reservation/
├── auth/
│   ├── AuthController.java
│   ├── AuthService.java
│   ├── JwtUtil.java
│   └── dto/
├── utilisateur/
│   ├── Utilisateur.java
│   └── UtilisateurRepository.java
├── ressource/
│   ├── Ressource.java
│   ├── RessourceController.java
│   ├── RessourceService.java
│   ├── RessourceRepository.java
│   └── dto/
├── reservation/
│   ├── Reservation.java
│   ├── ReservationController.java
│   ├── ReservationService.java
│   ├── ReservationRepository.java
│   └── dto/
├── exception/
│   ├── GlobalExceptionHandler.java
│   ├── ConflitReservationException.java
│   └── RessourceNotFoundException.java
├── config/
│   ├── SecurityConfig.java
│   └── SwaggerConfig.java
└── ReservationApplication.java
```

> `exception/` et `config/` restent transverses : utilisés par toutes les features, pas liés à une seule.

---

## Livrables pour le portfolio

- [ ] README avec schéma de base de données
- [ ] Endpoints documentés (lien Swagger ou capture d'écran)
- [ ] Instructions de lancement via Docker (2 commandes max)
- [ ] Tests JUnit sur la logique de conflit de créneaux
- [ ] Collection Postman ou fichier `.http` avec exemples de requêtes
