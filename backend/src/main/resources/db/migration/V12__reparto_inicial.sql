-- Modalidad de reparto inicial e importes que elige quien crea la liga.

-- La modalidad y los importes se guardan en la liga, y no solo se usan al crearla, porque quien se une
-- después tiene que recibir exactamente el mismo trato: misma modalidad, mismo presupuesto y mismo tope.
--   ALEATORIO: 11 jugadores al azar sin mirar el precio (lo de siempre) y el presupuesto elegido.
--   SIN_JUGADORES: plantilla vacía; hay que fichar todo en el mercado.
--   ALEATORIO_CON_TOPE: 11 jugadores al azar cuya suma de valores no pasa de initial_squad_cap, con un
--   presupuesto normalmente más bajo.
-- initial_budget manda sobre app.league.initial-budget, que solo es el valor que se propone al crear la liga.
-- El DEFAULT de 40000000 es lo que repartían las ligas que ya existen.
ALTER TABLE league
    ADD COLUMN initial_squad_mode VARCHAR(20)     NOT NULL DEFAULT 'ALEATORIO'
        CHECK (initial_squad_mode IN ('ALEATORIO', 'SIN_JUGADORES', 'ALEATORIO_CON_TOPE')),
    ADD COLUMN initial_budget     NUMERIC(14, 2)  NOT NULL DEFAULT 40000000 CHECK (initial_budget >= 0),
    ADD COLUMN initial_squad_cap  NUMERIC(14, 2)  CHECK (initial_squad_cap > 0);

-- El tope solo tiene sentido en ALEATORIO_CON_TOPE: en esa modalidad es obligatorio y en las otras dos se
-- rechaza (la API devuelve 400), así que nunca puede quedar un tope que no se aplique.
ALTER TABLE league
    ADD CONSTRAINT chk_league_squad_cap
        CHECK ((initial_squad_mode = 'ALEATORIO_CON_TOPE') = (initial_squad_cap IS NOT NULL));
