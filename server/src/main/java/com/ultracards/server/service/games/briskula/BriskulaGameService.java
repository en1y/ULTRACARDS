package com.ultracards.server.service.games.briskula;

import com.ultracards.server.entity.games.briskula.BriskulaPlayerEntity;
import com.ultracards.server.repositories.games.briskula.BriskulaPlayerEntityRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BriskulaGameService {

    private final BriskulaPlayerEntityRepository playerRepository;

    public BriskulaGameService(BriskulaPlayerEntityRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public void saveGameResults(List<BriskulaPlayerEntity> gamePlayers) {
        playerRepository.saveAll(gamePlayers);
    }
}
