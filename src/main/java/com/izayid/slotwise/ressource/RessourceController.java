package com.izayid.slotwise.ressource;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.izayid.slotwise.ressource.dto.RessourceRequest;
import com.izayid.slotwise.ressource.dto.RessourceResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/ressources")
public class RessourceController {

    private final RessourceService ressourceService;

    public RessourceController(RessourceService ressourceService) {
        this.ressourceService = ressourceService;
    }

    @PostMapping
    public ResponseEntity<RessourceResponse> create(@Valid @RequestBody RessourceRequest request) {
        Ressource ressource = ressourceService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(RessourceResponse.fromEntity(ressource));
    }

    @GetMapping
    public ResponseEntity<List<RessourceResponse>> findAll() {
        List<RessourceResponse> ressources = ressourceService.findAll().stream()
                .map(RessourceResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(ressources);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RessourceResponse> update(@PathVariable Long id, @Valid @RequestBody RessourceRequest request) {
        Ressource ressource = ressourceService.update(id, request);
        return ResponseEntity.ok(RessourceResponse.fromEntity(ressource));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ressourceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
