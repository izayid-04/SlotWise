package com.izayid.slotwise.reservation.dto;

import java.time.LocalDateTime;

import com.izayid.slotwise.reservation.Reservation;
import com.izayid.slotwise.reservation.StatutReservation;

public record ReservationResponse(
        Long id,
        LocalDateTime dateDebut,
        LocalDateTime dateFin,
        StatutReservation statut,
        Long ressourceId,
        String ressourceNom,
        Long utilisateurId
) {
    public static ReservationResponse fromEntity(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getDateDebut(),
                reservation.getDateFin(),
                reservation.getStatut(),
                reservation.getRessource().getId(),
                reservation.getRessource().getNom(),
                reservation.getUtilisateur().getId()
        );
    }
}
