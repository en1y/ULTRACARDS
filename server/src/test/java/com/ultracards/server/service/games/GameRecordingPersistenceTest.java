package com.ultracards.server.service.games;

import com.ultracards.games.treseta.TresetaGame;
import com.ultracards.games.treseta.TresetaGameConfig;
import com.ultracards.games.treseta.TresetaPlayer;
import com.ultracards.recorder.RecordedPlayer;
import com.ultracards.recorder.TresetaGameRecorder;
import com.ultracards.server.repositories.games.TresetaGameRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.main.web-application-type=none",
        "app.database.startup-check.enabled=false",
        "app.mail.startup-check.enabled=false"
})
@Transactional
class GameRecordingPersistenceTest {
    @Autowired
    private TresetaGameRepository repository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsACompleteTresetaRound() {
        var game = new TresetaGame(new ArrayList<>(List.of(new TresetaPlayer("one"), new TresetaPlayer("two"))),
                TresetaGameConfig.TWO_PLAYERS);
        var recorder = new TresetaGameRecorder(UUID.randomUUID(), UUID.randomUUID(), "persistence test", 1L,
                TresetaGameConfig.TWO_PLAYERS.name(), false, List.of(),
                player -> new RecordedPlayer((long) player.getName().length(), player.getName()));

        recorder.attach(game);
        game.start();
        for (var ignored : List.of(1, 2)) {
            var field = game.getPlayingField();
            var player = field.getCurrentPlayer();
            var leadSuit = field.getPlayedCards().isEmpty() ? null : field.getPlayedCards().getFirst().getSuit();
            var card = player.getHand().getCards().stream()
                    .filter(candidate -> leadSuit == null || candidate.getSuit().equals(leadSuit) || !player.getHand().containsSuit(leadSuit))
                    .findFirst()
                    .orElseThrow();
            field.play(card, player);
        }

        var recording = recorder.recording();
        repository.saveAndFlush(recording);
        entityManager.clear();

        var persisted = repository.findById(recording.id()).orElseThrow();
        assertEquals(1, persisted.rounds().size());
        assertEquals(2, persisted.rounds().getFirst().startingHands().size());
        assertEquals(2, persisted.rounds().getFirst().plays().size());
    }
}
