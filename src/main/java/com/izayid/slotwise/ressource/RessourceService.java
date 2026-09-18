package com.izayid.slotwise.ressource;

import java.util.List;

import org.springframework.stereotype.Service;

import com.izayid.slotwise.ressource.dto.RessourceRequest;

@Service
public class RessourceService {

    private final RessourceRepository ressourceRepository;

    public RessourceService(RessourceRepository ressourceRepository) {
        this.ressourceRepository = ressourceRepository;
    }

    public Ressource create(RessourceRequest request) {
        Ressource ressource = new Ressource(
                request.nom(),
                request.description(),
                request.capacite(),
                request.disponible()
        );

        return ressourceRepository.save(ressource);
    }

    public List<Ressource> findAll() {
        return ressourceRepository.findAll();
    }

    public Ressource update(Long id, RessourceRequest request) {
        Ressource ressource = ressourceRepository.findById(id)
                .orElseThrow(() -> new RessourceNotFoundException(id));

        ressource.setNom(request.nom());
        ressource.setDescription(request.description());
        ressource.setCapacite(request.capacite());
        ressource.setDisponible(request.disponible());

        return ressourceRepository.save(ressource);
    }

    public void delete(Long id) {
        if (!ressourceRepository.existsById(id)) {
            throw new RessourceNotFoundException(id);
        }

        ressourceRepository.deleteById(id);
    }
}
