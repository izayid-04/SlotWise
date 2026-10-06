package com.izayid.slotwise.reservation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.izayid.slotwise.reservation.dto.ReservationRequest;
import com.izayid.slotwise.ressource.Ressource;
import com.izayid.slotwise.ressource.RessourceNotFoundException;
import com.izayid.slotwise.ressource.RessourceRepository;
import com.izayid.slotwise.utilisateur.Role;
import com.izayid.slotwise.utilisateur.Utilisateur;
import com.izayid.slotwise.utilisateur.UtilisateurRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class  ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private RessourceRepository ressourceRepository;

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @InjectMocks
    private ReservationService reservationService;

    private static final Long RESSOURCE_ID = 1L;
    private static final String EMAIL = "test@mail.com";

    private Ressource ressource;
    private Utilisateur utilisateur;

    @BeforeEach
    void setUp() {
        ressource = new Ressource("Salle A", "Description", 10, true);
        utilisateur = new Utilisateur("Test User", EMAIL, "hash", Role.USER);
    }

    @Test
    void create_devrait_reussir_quand_aucun_conflit() {
        ReservationRequest request = new ReservationRequest(
                RESSOURCE_ID,
                LocalDateTime.of(2026, 1, 1, 10, 0),
                LocalDateTime.of(2026, 1, 1, 11, 0)
        );

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

    @Test
    void create_devrait_lever_conflit_quand_creneau_chevauche_une_reservation_existante() {
        ReservationRequest request = new ReservationRequest(
                RESSOURCE_ID,
                LocalDateTime.of(2026, 1, 1, 10, 30),
                LocalDateTime.of(2026, 1, 1, 11, 30)
        );

        Reservation reservationExistante = new Reservation(
                LocalDateTime.of(2026, 1, 1, 10, 0),
                LocalDateTime.of(2026, 1, 1, 11, 0),
                StatutReservation.CONFIRMEE,
                utilisateur,
                ressource
        );

        when(ressourceRepository.findById(RESSOURCE_ID)).thenReturn(Optional.of(ressource));
        when(utilisateurRepository.findByEmail(EMAIL)).thenReturn(Optional.of(utilisateur));
        when(reservationRepository.findByRessourceIdAndStatutAndDateDebutBeforeAndDateFinAfter(
                RESSOURCE_ID, StatutReservation.CONFIRMEE, request.dateFin(), request.dateDebut()
        )).thenReturn(List.of(reservationExistante));

        assertThatThrownBy(() -> reservationService.create(request, EMAIL))
                .isInstanceOf(ReservationConflictException.class);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void create_devrait_lever_erreur_quand_date_debut_apres_date_fin() {
        ReservationRequest request = new ReservationRequest(
                RESSOURCE_ID,
                LocalDateTime.of(2026, 1, 1, 12, 0),
                LocalDateTime.of(2026, 1, 1, 10, 0)
        );

        assertThatThrownBy(() -> reservationService.create(request, EMAIL))
                .isInstanceOf(ReservationInvalidException.class);

        verifyNoInteractions(ressourceRepository, utilisateurRepository, reservationRepository);
    }

    @Test
    void create_devrait_lever_erreur_quand_ressource_introuvable() {
        ReservationRequest request = new ReservationRequest(
                RESSOURCE_ID,
                LocalDateTime.of(2026, 1, 1, 10, 0),
                LocalDateTime.of(2026, 1, 1, 11, 0)
        );

        when(ressourceRepository.findById(RESSOURCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.create(request, EMAIL))
                .isInstanceOf(RessourceNotFoundException.class);

        verifyNoInteractions(reservationRepository);
    }
}
