package com.ultracards.recorder;
import com.ultracards.games.briskula.*;
import com.ultracards.games.treseta.TresetaGame;
import com.ultracards.games.treseta.TresetaGameConfig;
import com.ultracards.games.treseta.TresetaPlayer;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class GameRecorderTest {
 @Test void ignoresMissingPlayersFromOrderedPersistenceCollection(){var recorder=new BriskulaGameRecorder(UUID.randomUUID(),UUID.randomUUID(),"game",1L,BriskulaGameConfig.TWO_PLAYERS.name(),false,List.of(),p->new RecordedPlayer(1L,p.getName()));recorder.recording().started(Arrays.asList(null,new RecordedPlayer(2L,"two")),Map.of());assertEquals(List.of("two"),recorder.recording().players().stream().map(RecordedPlayer::name).toList());}

 @Test void recordsBriskulaRound(){var game=new BriskulaGame(BriskulaGameConfig.TWO_PLAYERS,new ArrayList<>(List.of(new BriskulaPlayer("one"),new BriskulaPlayer("two"))));var recorder=new BriskulaGameRecorder(UUID.randomUUID(),UUID.randomUUID(),"game",1L,BriskulaGameConfig.TWO_PLAYERS.name(),false,List.of(),p->new RecordedPlayer(1L,p.getName()));recorder.attach(game);game.start();for(var i:List.of(1,2)){var field=game.getPlayingField();field.play(field.getCurrentPlayer().getHand().getCards().getFirst(),field.getCurrentPlayer());}var record=recorder.recording();assertEquals(1,record.rounds().size());assertEquals(2,record.rounds().getFirst().plays().size());assertFalse(record.trumpSuit().isBlank());assertTrue(record.rounds().getFirst().attributes().containsKey("points"));}

 @Test void recordsTresetaRound(){var game=new TresetaGame(new ArrayList<>(List.of(new TresetaPlayer("one"),new TresetaPlayer("two"))),TresetaGameConfig.TWO_PLAYERS);var recorder=new TresetaGameRecorder(UUID.randomUUID(),UUID.randomUUID(),"game",1L,TresetaGameConfig.TWO_PLAYERS.name(),false,List.of(),p->new RecordedPlayer((long)p.getName().length(),p.getName()));recorder.attach(game);game.start();for(var i:List.of(1,2)){var field=game.getPlayingField();var player=field.getCurrentPlayer();var lead=field.getPlayedCards().isEmpty()?null:field.getPlayedCards().getFirst().getSuit();var card=player.getHand().getCards().stream().filter(c->lead==null||c.getSuit().equals(lead)||!player.getHand().containsSuit(lead)).findFirst().orElseThrow();field.play(card,player);}var record=recorder.recording();assertEquals(1,record.rounds().size());var round=record.rounds().getFirst();assertSame(record,round.game());assertTrue(round.startingHands().stream().allMatch(hand->hand.round()==round));assertEquals(2,round.plays().size());assertTrue(round.plays().stream().allMatch(play->play.round()==round));assertTrue(round.attributes().containsKey("points"));}

 @Test void preservesStartingPlayerOrderWhenGameEnds(){var game=new TresetaGame(new ArrayList<>(List.of(new TresetaPlayer("one"),new TresetaPlayer("two"))),TresetaGameConfig.TWO_PLAYERS);var recorder=new TresetaGameRecorder(UUID.randomUUID(),UUID.randomUUID(),"game",1L,TresetaGameConfig.TWO_PLAYERS.name(),false,List.of(),p->new RecordedPlayer((long)p.getName().length(),p.getName()));recorder.attach(game);game.start();Collections.rotate(game.getPlayers(),1);game.gameEnd();var names=new ArrayList<String>();for(var player:recorder.recording().players())names.add(player.name());assertEquals(List.of("one","two"),names);}
}
