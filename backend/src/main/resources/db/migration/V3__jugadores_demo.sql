-- Datos de DEMO para desarrollo (valores inventados). Sustituir por datos reales de una API.
INSERT INTO player (name, position, team_id, market_value) VALUES
    ('Thibaut Courtois', 'PORTERO',        (SELECT id FROM team WHERE short_name = 'RMA'), 20000000),
    ('Jan Oblak',        'PORTERO',        (SELECT id FROM team WHERE short_name = 'ATM'), 18000000),
    ('Antonio Rüdiger',  'DEFENSA',        (SELECT id FROM team WHERE short_name = 'RMA'), 25000000),
    ('Ronald Araújo',    'DEFENSA',        (SELECT id FROM team WHERE short_name = 'BAR'), 50000000),
    ('Pedri',            'CENTROCAMPISTA', (SELECT id FROM team WHERE short_name = 'BAR'), 90000000),
    ('Jude Bellingham',  'CENTROCAMPISTA', (SELECT id FROM team WHERE short_name = 'RMA'), 130000000),
    ('Lamine Yamal',     'DELANTERO',      (SELECT id FROM team WHERE short_name = 'BAR'), 180000000),
    ('Vinícius Júnior',  'DELANTERO',      (SELECT id FROM team WHERE short_name = 'RMA'), 150000000);
