package com.ultracards.server.service.games.briskula;

import com.ultracards.cards.*;
import com.ultracards.games.briskula.*;
import com.ultracards.gateway.dto.games.*;
import com.ultracards.gateway.dto.games.games.*;
import com.ultracards.gateway.dto.games.games.briskula.*;
import com.ultracards.recorder.*;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.repositories.games.BriskulaGameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class BriskulaGameHistoryService {
 private final BriskulaGameRepository repository;
 @Transactional(readOnly=true) public List<ShortGameHistoryDTO> getPastGames(UserEntity user,int offset,String result,String sort){return getPastGames(user,result,sort).stream().skip(Math.max(0,offset)).limit(20).toList();}
 @Transactional(readOnly=true) public List<ShortGameHistoryDTO> getPastGames(UserEntity user,String result,String sort){var games=("oldest".equalsIgnoreCase(sort)||"asc".equalsIgnoreCase(sort))?repository.findPastGamesByUserIdOldest(user.getId()):repository.findPastGamesByUserIdLatest(user.getId());var out=new ArrayList<ShortGameHistoryDTO>();for(var game:games){var dto=shortHistory(game);var win=contains(dto.getWinners(),user.getId());if("wins".equalsIgnoreCase(result)||"win".equalsIgnoreCase(result)){if(win)out.add(dto);}else if("loss".equalsIgnoreCase(result)||"losses".equalsIgnoreCase(result)||"lose".equalsIgnoreCase(result)){if(!win)out.add(dto);}else out.add(dto);}return out;}
 @Transactional(readOnly=true) public BriskulaGameHistoryDTO getGameHistory(UUID id){var game=repository.findById(id).orElse(null);if(game==null)return null;var points=points(game);var rounds=new ArrayList<BriskulaGameHistoryDTO.BriskulaRoundHistoryDTO>();for(var round:game.rounds()){var plays=new ArrayList<BriskulaGameHistoryDTO.BriskulaCardPlayHistoryDTO>();for(var play:round.plays())plays.add(new BriskulaGameHistoryDTO.BriskulaCardPlayHistoryDTO(play.order(),player(play.player()),card(play.card())));var value=Integer.parseInt(round.attributes().getOrDefault("points","0"));add(game,points,round.winner(),value);var hands=new LinkedHashMap<GamePlayerDTO,List<GameCardDTO>>();for(var hand:round.startingHands()){var cards=new ArrayList<GameCardDTO>();for(var c:hand.cards())cards.add(card(c));hands.put(player(hand.player()),cards);}rounds.add(new BriskulaGameHistoryDTO.BriskulaRoundHistoryDTO(round.order(),hands,plays,player(round.winner()),value,pointsDto(game,points)));}return new BriskulaGameHistoryDTO(game.id(),game.lobbyId(),game.name(),owner(game),game.createdAt(),game.endedAt(),config(game),trump(game),players(game),teams(game),rounds,pointsDto(game,points),winners(game,points));}
 private ShortGameHistoryDTO shortHistory(RecordedBriskulaGame g){var p=points(g);for(var r:g.rounds())add(g,p,r.winner(),Integer.parseInt(r.attributes().getOrDefault("points","0")));return new ShortGameHistoryDTO(g.id(),g.lobbyId(),g.name(),GameTypeDTO.Briskula,g.createdAt(),g.endedAt(),config(g),players(g),pointsDto(g,p),winners(g,p));}
 private Map<Long,Integer> points(RecordedBriskulaGame g){var p=new LinkedHashMap<Long,Integer>();for(var x:g.players())p.put(x.id(),0);return p;}
 private void add(RecordedBriskulaGame g,Map<Long,Integer> p,RecordedPlayer w,int v){if(w==null)return;p.put(w.id(),p.getOrDefault(w.id(),0)+v);if(g.teamsEnabled()){var ids=g.teamUserIds();var i=ids.indexOf(w.id());if(i>=0){var mate=ids.get(i<2?1-i:5-i);p.put(mate,p.getOrDefault(mate,0)+v);}}}
 private BriskulaGameConfigDTO config(RecordedBriskulaGame g){var c=BriskulaGameConfig.valueOf(g.gameConfig());return new BriskulaGameConfigDTO(c.getNumberOfPlayers(),c.getCardsInHandNum(),g.teamsEnabled(),players(g));}
 private GameCardDTO trump(RecordedBriskulaGame g){return GameCardDTO.createCardDTO(new BriskulaCard(ItalianCardSuit.valueOf(g.trumpSuit()),ItalianCardValue.valueOf(g.trumpValue())));}
 private List<GamePlayerDTO> players(RecordedBriskulaGame g){var r=new ArrayList<GamePlayerDTO>();for(var x:g.players())r.add(player(x));return r;}
 private List<List<GamePlayerDTO>> teams(RecordedBriskulaGame g){var r=new ArrayList<List<GamePlayerDTO>>();if(!g.teamsEnabled())return r;var map=new HashMap<Long,GamePlayerDTO>();for(var x:g.players())map.put(x.id(),player(x));var ids=g.teamUserIds();r.add(List.of(map.get(ids.get(0)),map.get(ids.get(1))));r.add(List.of(map.get(ids.get(2)),map.get(ids.get(3))));return r;}
 private Map<GamePlayerDTO,Integer> pointsDto(RecordedBriskulaGame g,Map<Long,Integer> p){var r=new LinkedHashMap<GamePlayerDTO,Integer>();for(var x:g.players())r.put(player(x),p.getOrDefault(x.id(),0));return r;}
 private List<GamePlayerDTO> winners(RecordedBriskulaGame g,Map<Long,Integer> p){var best=Integer.MIN_VALUE;for(var v:p.values())best=Math.max(best,v);var r=new ArrayList<GamePlayerDTO>();for(var x:g.players())if(p.getOrDefault(x.id(),0)==best)r.add(player(x));return r;}
 private GamePlayerDTO owner(RecordedBriskulaGame g){for(var x:g.players())if(x.id().equals(g.ownerUserId()))return player(x);return null;} private boolean contains(List<GamePlayerDTO> p,Long id){for(var x:p)if(x.getId().equals(id))return true;return false;} private GamePlayerDTO player(RecordedPlayer p){return p==null?null:new GamePlayerDTO(p.name(),p.id());}
 private GameCardDTO card(RecordedCard c){ItalianCardValue value=ItalianCardValue.values()[0];for(var v:ItalianCardValue.values())if(v.getNumber()==c.number())value=v;return GameCardDTO.createCardDTO(new BriskulaCard(ItalianCardSuit.valueOf(c.suit()),value));}
}
