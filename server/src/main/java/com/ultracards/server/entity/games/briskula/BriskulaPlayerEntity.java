package com.ultracards.server.entity.games.briskula;

import com.ultracards.games.briskula.BriskulaCard;
import com.ultracards.games.briskula.BriskulaPlayer;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.briskula.converters.BriskulaCardConverter;
import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "briskula_players")
public class BriskulaPlayerEntity extends BriskulaPlayer{

    public BriskulaPlayerEntity() {
        super(null);
    }

    public BriskulaPlayerEntity(UserEntity user) {
        super(user.getUsername());
    }

    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
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

}
