# Tests JUnit + Mockito — les concepts clés à retenir

Condensé — voir `tests-junit-mockito-explique.md` pour le détail du code.

---

| Concept | Une phrase à retenir |
|---|---|
| **Test unitaire** | Teste une seule méthode, isolée, sans vraie base de données ni serveur. Aucun réseau, aucun HTTP — juste un appel de méthode Java normal. |
| **JUnit** | Le framework qui fait tourner les tests (`@Test`) et donne le résumé pass/fail. |
| **Mockito** | Fabrique de fausses dépendances ("mocks") — comme une doublure de cascade, qui joue le rôle sans être le vrai. |
| **`@Mock` / `@InjectMocks`** | `@Mock` = crée un faux repository. `@InjectMocks` = construit le vrai service en lui injectant les mocks, via son constructeur, sans démarrer Spring. |
| **Arrange - Act - Assert** | Le plan de tout test : préparer les mocks → appeler la vraie méthode → vérifier le résultat. |
| **Objets de test (`new Ressource(...)`) ≠ mocks** | Les données créées avec `new` sont réelles, pas mockées — elles servent juste d'exemples réalistes à donner aux mocks. |
| **`when(X).thenReturn(Y)`** | Programme un mock : "quand on appelle X, réponds Y". Sans ça, un mock renvoie `null`/vide par défaut. |
| **`thenAnswer(...)`** | Variante quand la réponse doit dépendre de ce qu'on a reçu (ex: `save()` qui renvoie l'objet tel quel), plutôt qu'une valeur fixe. |
| **`assertThat(actuel).isEqualTo(attendu)`** | Compare ce que le vrai code a produit à ce qu'on sait être correct — pas "objet réel vs en mémoire", juste "résultat observé vs résultat espéré". |
| **`verify(mock).method(...)`** | Vérifie un **comportement** (la méthode a bien été appelée), pas une valeur. Par défaut : exactement 1 fois. |
| **`verify(mock, never())` / `verifyNoInteractions(...)`** | Vérifie l'**absence** d'appel — utile pour prouver qu'un code s'arrête bien avant d'atteindre une étape (ex: pas de `save()` si conflit détecté). |
| **`assertThatThrownBy(() -> ...)`** | Pour les cas où on attend une **exception**. La lambda (`() -> ...`) permet à AssertJ de contrôler l'appel pour l'attraper, au lieu de laisser planter le test. |
| **`Optional.empty()`** | Simule "rien trouvé en base" — même usage que dans `UtilisateurDetailsService`, ici appliqué aux mocks. |
| **Réflexe de couverture** | Un test qui prouve un cas (ex: date après) ne prouve pas forcément le cas voisin (ex: dates égales). Se poser la question "est-ce que ce test couvre vraiment tous les cas limites ?" plutôt que de supposer que oui. |
| **`LocalDateTime` compare toute la chronologie** | `isBefore`/`isAfter` comparent date + heure ensemble, jamais juste l'heure isolée — un jour différent est pris en compte, pas seulement l'heure du jour. |

## Le fil à garder en tête

Un test unitaire = **vrai code + fausses dépendances programmées à l'avance**, structuré en 3 étapes (Arrange/Act/Assert), qui vérifie soit une **valeur** (`assertThat`) soit un **comportement** (`verify`).
