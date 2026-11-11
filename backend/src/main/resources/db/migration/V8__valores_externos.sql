-- Últimos valores de mercado importados de una fuente externa (Biwenger). Se guardan para poder volver a
-- aplicarlos a los jugadores que se crean después, al importar estadísticas de nuevas jornadas.
CREATE TABLE external_value (
    id        BIGSERIAL PRIMARY KEY,
    name      VARCHAR(120) NOT NULL,
    slug      VARCHAR(160) NOT NULL,
    team_name VARCHAR(80)  NOT NULL,
    position  VARCHAR(20)  NOT NULL CHECK (position IN ('PORTERO', 'DEFENSA', 'CENTROCAMPISTA', 'DELANTERO')),
    value     BIGINT       NOT NULL CHECK (value > 0),
    status    VARCHAR(20)  NOT NULL DEFAULT 'DISPONIBLE' CHECK (status IN ('DISPONIBLE', 'LESIONADO', 'SANCIONADO', 'DUDA'))
);
