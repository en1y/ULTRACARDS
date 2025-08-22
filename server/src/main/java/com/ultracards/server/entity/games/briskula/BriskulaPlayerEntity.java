package com.ultracards.server.entity.games.briskula;

import com.ultracards.games.briskula.BriskulaCard;
import com.ultracards.games.briskula.BriskulaPlayer;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.briskula.converters.BriskulaCardConverter;
import com.ultracards.server.repositories.games.PlayerEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "briskula_players")
public class BriskulaPlayerEntity extends BriskulaPlayer implements PlayerEntity {

    public BriskulaPlayerEntity() {
        super(null);
    }

    public BriskulaPlayerEntity(UserEntity user) {
        super(user.getUsername());
    }

    @Setter
    private Long id;

    @Setter
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long getId() {
        return id;
    }

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    public UserEntity getUser() {
        return user;
    }

    @Override
    @Column(name = "points")
    public int getPoints() {
        return super.getPoints();
    }

    @Override
    @Convert(converter = BriskulaCardConverter.class)
    @Column(name = "won_cards", columnDefinition = "jsonb") // replace with text for non PostgreSQL databases
    public List<BriskulaCard> getWonCards() {
        return super.getWonCards();
    }

    @Column
    @Getter @Setter
    private boolean isWinner;
}
