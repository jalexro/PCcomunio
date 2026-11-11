-- Opciones de la liga que edita su dueño: lo que se abona por punto y el premio por posición en cada jornada.
-- position_bonuses es una lista de importes enteros separados por comas: el primero es para el 1.º, el segundo para el 2.º...
ALTER TABLE league
    ADD COLUMN point_value      NUMERIC(14, 2) NOT NULL DEFAULT 100000 CHECK (point_value >= 0),
    ADD COLUMN position_bonuses VARCHAR(400)   NOT NULL DEFAULT '3000000,2000000,1000000';

-- Cuánto se abonó a cada miembro en cada jornada y por qué.
ALTER TABLE member_matchday_score
    ADD COLUMN position       INT,
    ADD COLUMN points_payout  NUMERIC(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN position_bonus NUMERIC(14, 2) NOT NULL DEFAULT 0;
