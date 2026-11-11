-- Modos de avance de jornada y penalización por puestos vacíos de la alineación.

-- POR_TIEMPO: la jornada se juega cuando vence next_advance_at (comportamiento anterior).
-- POR_CONFIRMACION: se juega en cuanto todos los miembros confirman su alineación y, como tope,
-- cuando vence next_advance_at (que pasa a ser la fecha límite). El plazo sigue siendo
-- matchday_interval_seconds, así que no hace falta una columna nueva para la fecha límite.
ALTER TABLE league
    ADD COLUMN advance_mode VARCHAR(20) NOT NULL DEFAULT 'POR_TIEMPO'
        CHECK (advance_mode IN ('POR_TIEMPO', 'POR_CONFIRMACION'));

-- Jornada para la que el miembro ya ha confirmado su alineación. Se compara con current_matchday + 1,
-- así que al avanzar la liga todas las confirmaciones quedan obsoletas solas, sin tener que borrarlas.
ALTER TABLE league_member
    ADD COLUMN lineup_confirmed_matchday INT NOT NULL DEFAULT 0;

-- Cuántos de los 11 puestos de la táctica se quedaron sin jugador en esa jornada y cuántos puntos
-- costó (se guarda para poder explicarlo en la clasificación; ya va descontado de points).
ALTER TABLE member_matchday_score
    ADD COLUMN empty_slots        INT           NOT NULL DEFAULT 0,
    ADD COLUMN empty_slot_penalty NUMERIC(8, 2) NOT NULL DEFAULT 0;
