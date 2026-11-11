-- Identificadores de API-Football para emparejar datos importados (reimportar no duplica).
ALTER TABLE team    ADD COLUMN external_id BIGINT UNIQUE;
ALTER TABLE player  ADD COLUMN external_id BIGINT UNIQUE;
ALTER TABLE matches ADD COLUMN external_id BIGINT UNIQUE;
ALTER TABLE matches ADD COLUMN season INT NOT NULL DEFAULT 2024;

-- Limpieza de los datos de demo (V2 y V3): los equipos y jugadores reales se crean al importar.
-- Solo se borra lo que no tiene datos colgando, para no romper claves foráneas.
DELETE FROM player
WHERE external_id IS NULL
  AND id NOT IN (SELECT player_id FROM player_match_stats);

DELETE FROM team
WHERE external_id IS NULL
  AND id NOT IN (SELECT team_id FROM player)
  AND id NOT IN (SELECT home_team_id FROM matches)
  AND id NOT IN (SELECT away_team_id FROM matches);
