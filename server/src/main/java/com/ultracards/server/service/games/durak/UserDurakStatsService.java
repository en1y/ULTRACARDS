package com.ultracards.server.service.games.durak;

import com.ultracards.games.durak.DurakGameConfig;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.gamestats.UserDurakStats;
import com.ultracards.server.repositories.games.UserDurakStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDurakStatsService {
    private final UserDurakStatsRepository repository;

    public void createEmptyStats(UserEntity user) {
        repository.save(new UserDurakStats(user));
    }

    @Transactional
    public UserDurakStats getByUser(UserEntity user) {
        return repository.findByUser(user)
                .orElseGet(() -> repository.save(new UserDurakStats(user)));
    }

    @Transactional
    public void addDurakGame(UserEntity user, DurakGameConfig config, boolean won) {
        getByUser(user).addGame(config, won);
    }

    @Transactional
    public void addDurakGame(UserEntity user, String modeKey, boolean won) {
        getByUser(user).addGame(modeKey, won);
    }

    /** The user ended up as the durak: a played game, no win. */
    @Transactional
    public void addTimesDurak(UserEntity user) {
        getByUser(user).addDurak();
    }

    /** Nobody was left holding cards: a played game for everybody and no win for anybody. */
    @Transactional
    public void addDraw(UserEntity user) {
        getByUser(user).addDraw();
    }

    @Transactional
    public void addGameAgainstUser(UserEntity user, String modeKey, UserEntity otherUser, boolean won) {
        getByUser(user).addGameAgainstUser(modeKey, otherUser, won);
    }

    @Transactional
    public void reset(UserEntity user) {
        getByUser(user).reset();
    }
}
