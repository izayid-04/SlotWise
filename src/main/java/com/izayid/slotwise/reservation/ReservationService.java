package com.izayid.slotwise.reservation;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.izayid.slotwise.reservation.dto.ReservationRequest;
import com.izayid.slotwise.ressource.Ressource;
import com.izayid.slotwise.ressource.RessourceNotFoundException;
import com.izayid.slotwise.ressource.RessourceRepository;
import com.izayid.slotwise.utilisateur.Utilisateur;
import com.izayid.slotwise.utilisateur.UtilisateurRepository;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final RessourceRepository ressourceRepository;
    private final UtilisateurRepository utilisateurRepository;

    public ReservationService(ReservationRepository reservationRepository,
                               RessourceRepository ressourceRepository,
                               UtilisateurRepository utilisateurRepository) {
        this.reservationRepository = reservationRepository;
        this.ressourceRepository = ressourceRepository;
        this.utilisateurRepository = utilisateurRepository;
    }

    public Reservation create(ReservationRequest request, String emailUtilisateurConnecte) {
        if (!request.dateDebut().isBefore(request.dateFin())) {
            throw new ReservationInvalidException("La date de début doit être avant la date de fin");
        }

        Ressource ressource = ressourceRepository.findById(request.ressourceId())
                .orElseThrow(() -> new RessourceNotFoundException(request.ressourceId()));

        Utilisateur utilisateur = utilisateurRepository.findByEmail(emailUtilisateurConnecte)
                .orElseThrow(() -> new UsernameNotFoundException("Aucun utilisateur avec l'email : " + emailUtilisateurConnecte));

        boolean conflit = !reservationRepository.findByRessourceIdAndStatutAndDateDebutBeforeAndDateFinAfter(
                request.ressourceId(), StatutReservation.CONFIRMEE, request.dateFin(), request.dateDebut()
        ).isEmpty();

        if (conflit) {
            throw new ReservationConflictException();
        }

        Reservation reservation = new Reservation(
                request.dateDebut(), request.dateFin(), StatutReservation.CONFIRMEE, utilisateur, ressource
        );

        return reservationRepository.save(reservation);
    }

    public List<Reservation> findMyReservations(String emailUtilisateurConnecte) {
        Utilisateur utilisateur = utilisateurRepository.findByEmail(emailUtilisateurConnecte)
                .orElseThrow(() -> new UsernameNotFoundException("Aucun utilisateur avec l'email : " + emailUtilisateurConnecte));

        return reservationRepository.findByUtilisateurId(utilisateur.getId());
    }

    public List<Reservation> findAll(Long ressourceId, LocalDateTime debut, LocalDateTime fin) {
        return reservationRepository.findAll().stream()
                .filter(reservation -> ressourceId == null || reservation.getRessource().getId().equals(ressourceId))
                .filter(reservation -> debut == null || !reservation.getDateFin().isBefore(debut))
                .filter(reservation -> fin == null || !reservation.getDateDebut().isAfter(fin))
                .toList();
    }

    public void cancel(Long id, String emailUtilisateurConnecte) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));

        if (!reservation.getUtilisateur().getEmail().equals(emailUtilisateurConnecte)) {
            throw new ReservationAccessDeniedException();
        }

        reservation.setStatut(StatutReservation.ANNULEE);
        reservationRepository.save(reservation);
    }
}
