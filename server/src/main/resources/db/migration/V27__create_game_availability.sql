CREATE TABLE game_availability (
    id UUID NOT NULL,
    game_type VARCHAR(32) NOT NULL,
    mode VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL,
    CONSTRAINT pk_game_availability PRIMARY KEY (id),
    CONSTRAINT uk_game_availability_game_mode UNIQUE (game_type, mode)
);
