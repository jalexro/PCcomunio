CREATE TABLE team (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    short_name VARCHAR(10)  NOT NULL,
    crest_url  VARCHAR(300)
);

CREATE TABLE player (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(100)   NOT NULL,
    position     VARCHAR(20)    NOT NULL CHECK (position IN ('PORTERO', 'DEFENSA', 'CENTROCAMPISTA', 'DELANTERO')),
    team_id      BIGINT         NOT NULL REFERENCES team (id),
    market_value NUMERIC(12, 2) NOT NULL DEFAULT 0,
    photo_url    VARCHAR(300),
    status       VARCHAR(20)    NOT NULL DEFAULT 'DISPONIBLE' CHECK (status IN ('DISPONIBLE', 'LESIONADO', 'SANCIONADO', 'DUDA'))
);
CREATE INDEX idx_player_team ON player (team_id);

CREATE TABLE matches (
    id           BIGSERIAL PRIMARY KEY,
    matchday     INT          NOT NULL,
    kickoff      TIMESTAMPTZ  NOT NULL,
    home_team_id BIGINT       NOT NULL REFERENCES team (id),
    away_team_id BIGINT       NOT NULL REFERENCES team (id),
    home_goals   INT,
    away_goals   INT,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PROGRAMADO' CHECK (status IN ('PROGRAMADO', 'EN_JUEGO', 'FINALIZADO')),
    CHECK (home_team_id <> away_team_id)
);
CREATE INDEX idx_matches_matchday ON matches (matchday);

-- Datos brutos de un jugador en un partido. 'points' se calcula con ScoringService.
-- Los campos de portero (saves, goals_conceded, shots_faced) solo se usan para PORTERO.
CREATE TABLE player_match_stats (
    id               BIGSERIAL PRIMARY KEY,
    player_id        BIGINT        NOT NULL REFERENCES player (id),
    match_id         BIGINT        NOT NULL REFERENCES matches (id),
    minutes          INT           NOT NULL DEFAULT 0,
    goals            INT           NOT NULL DEFAULT 0,
    assists          INT           NOT NULL DEFAULT 0,
    tackles_won      INT           NOT NULL DEFAULT 0,
    yellow_cards     INT           NOT NULL DEFAULT 0,
    red_cards        INT           NOT NULL DEFAULT 0,
    passes_completed INT           NOT NULL DEFAULT 0,
    passes_failed    INT           NOT NULL DEFAULT 0,
    saves            INT           NOT NULL DEFAULT 0,
    goals_conceded   INT           NOT NULL DEFAULT 0,
    shots_faced      INT           NOT NULL DEFAULT 0,
    rating           NUMERIC(3, 1),
    points           NUMERIC(6, 2),
    UNIQUE (player_id, match_id)
);
