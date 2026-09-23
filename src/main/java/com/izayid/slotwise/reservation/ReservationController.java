package com.izayid.slotwise.reservation;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.izayid.slotwise.reservation.dto.ReservationRequest;
import com.izayid.slotwise.reservation.dto.ReservationResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody ReservationRequest request,
                                                        @AuthenticationPrincipal UserDetails userDetails) {
        Reservation reservation = reservationService.create(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReservationResponse.fromEntity(reservation));
    }

    @GetMapping("/mes-reservations")
    public ResponseEntity<List<ReservationResponse>> findMyReservations(@AuthenticationPrincipal UserDetails userDetails) {
        List<ReservationResponse> reservations = reservationService.findMyReservations(userDetails.getUsername()).stream()
                .map(ReservationResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(reservations);
    }

    @GetMapping
    public ResponseEntity<List<ReservationResponse>> findAll(
            @RequestParam(required = false) Long ressourceId,
            @RequestParam(required = false) LocalDateTime debut,
            @RequestParam(required = false) LocalDateTime fin) {
        List<ReservationResponse> reservations = reservationService.findAll(ressourceId, debut, fin).stream()
                .map(ReservationResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(reservations);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        reservationService.cancel(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
