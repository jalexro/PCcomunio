CREATE TABLE league (
    id                        BIGSERIAL PRIMARY KEY,
    name                      VARCHAR(40)  NOT NULL,
    owner_id                  BIGINT       NOT NULL REFERENCES app_user (id),
    join_code                 VARCHAR(12)  NOT NULL UNIQUE,
    season                    INT          NOT NULL,
    current_matchday          INT          NOT NULL DEFAULT 0,
    market_cycle              INT          NOT NULL DEFAULT 0,
    status                    VARCHAR(12)  NOT NULL DEFAULT 'EN_CURSO' CHECK (status IN ('EN_CURSO', 'FINALIZADA')),
    matchday_interval_seconds BIGINT       NOT NULL CHECK (matchday_interval_seconds >= 10),
    next_advance_at           TIMESTAMPTZ  NOT NULL,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_league_due ON league (status, next_advance_at);

CREATE TABLE league_member (
    id           BIGSERIAL PRIMARY KEY,
    league_id    BIGINT         NOT NULL REFERENCES league (id) ON DELETE CASCADE,
    user_id      BIGINT         NOT NULL REFERENCES app_user (id),
    budget       NUMERIC(14, 2) NOT NULL CHECK (budget >= 0),
    formation    VARCHAR(8)     NOT NULL DEFAULT 'F442' CHECK (formation IN ('F433', 'F532', 'F442', 'F352')),
    total_points NUMERIC(8, 2)  NOT NULL DEFAULT 0,
    joined_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    UNIQUE (league_id, user_id)
);

-- UNIQUE (league_id, player_id): en una liga cada jugador tiene como máximo un dueño.
CREATE TABLE squad_player (
    id          BIGSERIAL PRIMARY KEY,
    league_id   BIGINT         NOT NULL REFERENCES league (id) ON DELETE CASCADE,
    member_id   BIGINT         NOT NULL REFERENCES league_member (id) ON DELETE CASCADE,
    player_id   BIGINT         NOT NULL REFERENCES player (id),
    starter     BOOLEAN        NOT NULL DEFAULT FALSE,
    price_paid  NUMERIC(14, 2) NOT NULL DEFAULT 0,
    acquired_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    UNIQUE (league_id, player_id)
);
CREATE INDEX idx_squad_player_member ON squad_player (member_id);

CREATE TABLE market_listing (
    id        BIGSERIAL PRIMARY KEY,
    league_id BIGINT NOT NULL REFERENCES league (id) ON DELETE CASCADE,
    player_id BIGINT NOT NULL REFERENCES player (id),
    cycle     INT    NOT NULL,
    UNIQUE (league_id, player_id, cycle)
);

-- seller_id NULL = puja al mercado; con valor = oferta directa al dueño del jugador.
CREATE TABLE offer (
    id          BIGSERIAL PRIMARY KEY,
    league_id   BIGINT         NOT NULL REFERENCES league (id) ON DELETE CASCADE,
    player_id   BIGINT         NOT NULL REFERENCES player (id),
    bidder_id   BIGINT         NOT NULL REFERENCES league_member (id) ON DELETE CASCADE,
    seller_id   BIGINT         REFERENCES league_member (id) ON DELETE CASCADE,
    amount      NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    status      VARCHAR(12)    NOT NULL DEFAULT 'PENDIENTE'
        CHECK (status IN ('PENDIENTE', 'ACEPTADA', 'RECHAZADA', 'CADUCADA', 'PERDIDA', 'ANULADA', 'RETIRADA')),
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ
);
CREATE INDEX idx_offer_league_status ON offer (league_id, status);

CREATE TABLE member_matchday_score (
    id        BIGSERIAL PRIMARY KEY,
    member_id BIGINT        NOT NULL REFERENCES league_member (id) ON DELETE CASCADE,
    matchday  INT           NOT NULL,
    points    NUMERIC(8, 2) NOT NULL,
    UNIQUE (member_id, matchday)
);
