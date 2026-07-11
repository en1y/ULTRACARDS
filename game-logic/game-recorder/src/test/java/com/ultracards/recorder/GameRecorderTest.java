package com.ultracards.recorder;
import com.ultracards.games.briskula.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class GameRecorderTest {
 @Test void recordsBriskulaRound(){var game=new BriskulaGame(BriskulaGameConfig.TWO_PLAYERS,new ArrayList<>(List.of(new BriskulaPlayer("one"),new BriskulaPlayer("two"))));var recorder=new BriskulaGameRecorder(UUID.randomUUID(),UUID.randomUUID(),"game",1L,BriskulaGameConfig.TWO_PLAYERS.name(),false,List.of(),p->new RecordedPlayer(1L,p.getName()));recorder.attach(game);game.start();for(var i:List.of(1,2)){var field=game.getPlayingField();field.play(field.getCurrentPlayer().getHand().getCards().getFirst(),field.getCurrentPlayer());}var record=recorder.recording();assertEquals(1,record.rounds().size());assertEquals(2,record.rounds().getFirst().plays().size());assertFalse(record.trumpSuit().isBlank());assertTrue(record.rounds().getFirst().attributes().containsKey("points"));}
}
