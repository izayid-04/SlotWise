# Tests JUnit + Mockito expliqués (SlotWise)

Premiers tests automatisés du projet, sur la logique de conflit de créneaux (`ReservationService.create()`) — demandés en priorité par le cahier des charges. Fichier testé : `ReservationServiceTest.java`.

---

## 1. Pourquoi des tests automatisés

Jusqu'ici, on vérifiait le comportement du code **à la main**, avec `curl` : lancer le serveur, envoyer une requête, lire le code HTTP. Ça marche, mais c'est lent, et il faut tout refaire à chaque modification pour être sûr de ne rien avoir cassé. Un test automatisé fait la même vérification, mais en code, exécutable en quelques secondes, autant de fois que nécessaire (`mvn test`).

## 2. Test unitaire — tester une seule "unité", isolée

Le test écrit ici est un **test unitaire** : il teste une seule méthode (`ReservationService.create()`) **isolée du reste** — pas de vrai serveur, pas de vraie base de données. Problème : `create()` a besoin de 3 repositories pour fonctionner (vus dans son constructeur). Pour un test unitaire, on ne veut pas faire tourner une vraie base de données juste pour tester de la logique (`if`, conditions) — c'est le rôle de Mockito de résoudre ça.

## 3. JUnit vs Mockito — deux outils différents

- **JUnit** : le framework qui fait tourner les tests. Fournit `@Test` (marque une méthode comme test), exécute chaque test, et donne le résumé final ("4 tests réussis, 0 échoué"). Équivalent de Jest (JS) ou PHPUnit (PHP) si tu connais.
- **Mockito** : permet de créer des **mocks** — des objets factices qui ressemblent à une vraie dépendance (même type, mêmes méthodes) mais ne font rien de réel. Comme une doublure de cascade au cinéma : joue le rôle, n'est pas le vrai. On **programme** nous-mêmes ce que le mock doit répondre.

## 4. Le plan "Arrange - Act - Assert"

Structure que suit (presque) tout test :
1. **Arrange** : préparer les données, programmer les mocks.
2. **Act** : appeler la vraie méthode testée.
3. **Assert** : vérifier que le résultat est celui attendu.

## 5. Les annotations de la classe

```java
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private RessourceRepository ressourceRepository;
    @Mock
    private UtilisateurRepository utilisateurRepository;

    @InjectMocks
    private ReservationService reservationService;
```

- `@ExtendWith(MockitoExtension.class)` : active Mockito pour cette classe — sans elle, `@Mock`/`@InjectMocks` ne feraient rien.
- `@Mock` : Mockito crée une fausse version de ce type (une "doublure").
- `@InjectMocks` : Mockito construit un vrai `ReservationService`, mais lui injecte les 3 mocks du dessus comme dépendances (via son constructeur) — comme le ferait Spring, mais ici fait par Mockito, sans démarrer toute l'application.

```java
@BeforeEach
void setUp() {
    ressource = new Ressource("Salle A", "Description", 10, true);
    utilisateur = new Utilisateur("Test User", EMAIL, "hash", Role.USER);
}
```

`@BeforeEach` : exécutée avant **chaque** test, pour repartir avec des données fraîches à chaque fois.

**Point à ne pas confondre** : `ressource` et `utilisateur` ici ne sont **pas** des mocks — ce sont des objets Java réels, créés avec `new`, qui servent de données d'exemple ("fixtures") à donner aux mocks. Seuls les 3 repositories sont mockés.

## 6. Test 1 — création réussie, sans conflit

```java
@Test
void create_devrait_reussir_quand_aucun_conflit() {
    ReservationRequest request = new ReservationRequest(RESSOURCE_ID,
            LocalDateTime.of(2026, 1, 1, 10, 0), LocalDateTime.of(2026, 1, 1, 11, 0));

    when(ressourceRepository.findById(RESSOURCE_ID)).thenReturn(Optional.of(ressource));
    when(utilisateurRepository.findByEmail(EMAIL)).thenReturn(Optional.of(utilisateur));
    when(reservationRepository.findByRessourceIdAndStatutAndDateDebutBeforeAndDateFinAfter(
            RESSOURCE_ID, StatutReservation.CONFIRMEE, request.dateFin(), request.dateDebut()
    )).thenReturn(List.of());
    when(reservationRepository.save(any(Reservation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

    Reservation result = reservationService.create(request, EMAIL);

    assertThat(result.getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
    assertThat(result.getUtilisateur()).isEqualTo(utilisateur);
    assertThat(result.getRessource()).isEqualTo(ressource);
    verify(reservationRepository).save(any(Reservation.class));
}
```

**Arrange** — `when(X).thenReturn(Y)` : *"quand on appelle X sur ce mock, réponds Y"*. Sans ça, un mock renvoie `null`/vide par défaut pour n'importe quel appel. La requête de conflit renvoie `List.of()` (vide) → on simule "pas de conflit". `thenAnswer(invocation -> invocation.getArgument(0))` : variante utilisée pour `save()`, qui doit renvoyer **exactement l'objet qu'on lui a passé**, pas une valeur fixe — `any(Reservation.class)` veut dire "peu importe laquelle".

**Act** — `reservationService.create(request, EMAIL)` : le seul appel au vrai code, un simple appel de méthode Java (pas de HTTP, pas de serveur).

**Assert** — `assertThat(X).isEqualTo(Y)` (AssertJ) : compare **X = valeur actuelle** (ce que le vrai appel a produit) à **Y = valeur attendue** (ce qu'on sait déjà être correct). Pas "objet réel vs objet en mémoire" — plutôt "ce qui s'est passé" vs "ce qu'on espérait". `verify(mock).save(...)` (sans argument supplémentaire) vérifie que la méthode a été appelée **exactement une fois** (comportement par défaut de `verify`).

## 7. Test 2 — conflit détecté

```java
Reservation reservationExistante = new Reservation(
        LocalDateTime.of(2026, 1, 1, 10, 0), LocalDateTime.of(2026, 1, 1, 11, 0),
        StatutReservation.CONFIRMEE, utilisateur, ressource
);

when(reservationRepository.findByRessourceIdAndStatutAndDateDebutBeforeAndDateFinAfter(...))
        .thenReturn(List.of(reservationExistante));

assertThatThrownBy(() -> reservationService.create(request, EMAIL))
        .isInstanceOf(ReservationConflictException.class);

verify(reservationRepository, never()).save(any());
```

But du test : vérifier qu'un créneau chevauchant une réservation déjà confirmée, sur la même ressource, est bien **refusé**.

- Une `Reservation` créée nous-mêmes représente une réservation **déjà existante** (simulée via le mock) — différent du test 1 où `create()` fabriquait la `Reservation` elle-même.
- `assertThatThrownBy(() -> ...)` : utilisée quand on attend une **exception**, pas un résultat normal. `() -> reservationService.create(...)` est une **lambda** — une petite fonction sans nom, nécessaire pour qu'AssertJ puisse contrôler l'appel et attraper l'exception avant qu'elle ne fasse planter le test. `.isInstanceOf(X)` vérifie le type de l'exception attrapée.
- `verify(mock, never()).save(...)` : `never()` est un raccourci de `times(0)` — vérifie que la méthode n'a **jamais** été appelée. Logique : si conflit, `create()` doit s'arrêter avant `save()`.

## 8. Test 3 — dates invalides

```java
@Test
void create_devrait_lever_erreur_quand_date_debut_apres_date_fin() {
    ReservationRequest request = new ReservationRequest(RESSOURCE_ID,
            LocalDateTime.of(2026, 1, 1, 12, 0), LocalDateTime.of(2026, 1, 1, 10, 0));

    assertThatThrownBy(() -> reservationService.create(request, EMAIL))
            .isInstanceOf(ReservationInvalidException.class);

    verifyNoInteractions(ressourceRepository, utilisateurRepository, reservationRepository);
}
```

Aucun `when(...)` ici : la vérification des dates se fait **au tout début** de `create()`, avant de toucher un seul repository — pas besoin de programmer des mocks qui ne seront jamais utilisés.

`verifyNoInteractions(...)` : encore plus strict que `never()` — vérifie qu'**aucune méthode, sur aucun de ces mocks, n'a été appelée du tout**. Confirme que `create()` échoue immédiatement.

**Point de couverture identifié (pas corrigé, noté pour mémoire)** : ce test ne couvre que le cas `dateDebut` **strictement après** `dateFin` (12h vs 10h). Le code (`!request.dateDebut().isBefore(request.dateFin())`) rejette aussi le cas où les deux dates sont **égales** (`isBefore` exclut l'égalité) — mais aucun test dédié ne le prouve explicitement. De même, `LocalDateTime` encode la date **et** l'heure ensemble (pas juste l'heure) : `isBefore`/`isAfter` comparent la chronologie complète, donc un cas avec des jours différents serait aussi géré correctement — mais pas testé explicitement non plus. Deux tests bonus possibles, non ajoutés pour l'instant.

## 9. Test 4 — ressource introuvable

```java
when(ressourceRepository.findById(RESSOURCE_ID)).thenReturn(Optional.empty());

assertThatThrownBy(() -> reservationService.create(request, EMAIL))
        .isInstanceOf(RessourceNotFoundException.class);

verifyNoInteractions(reservationRepository);
```

`Optional.empty()` : même principe que dans `UtilisateurDetailsService` (email non trouvé) — simule "rien trouvé", déclenche `.orElseThrow(RessourceNotFoundException)`.

`verifyNoInteractions(reservationRepository)` : vérifie qu'on ne va même pas jusqu'à la vérification de conflit, puisque l'erreur sur la ressource arrive avant dans le vrai code. (Remarque : ce test ne vérifie que `reservationRepository`, pas `utilisateurRepository` — pourrait être plus complet, même limite que le point de couverture ci-dessus.)

## Résultat

```
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Commande pour relancer : `./mvnw test -Dtest=ReservationServiceTest`
