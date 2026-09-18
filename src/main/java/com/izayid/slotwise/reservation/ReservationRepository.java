package com.izayid.slotwise.reservation;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByUtilisateurId(Long utilisateurId);

    List<Reservation> findByRessourceIdAndStatutAndDateDebutBeforeAndDateFinAfter(
            Long ressourceId, StatutReservation statut, LocalDateTime dateFin, LocalDateTime dateDebut);
}
