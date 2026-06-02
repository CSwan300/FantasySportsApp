CREATE DATABASE fantasy_db;

\c fantasy_db;

CREATE TABLE players (
                         player_id SERIAL PRIMARY KEY,
                         name VARCHAR(100) NOT NULL UNIQUE,
                         weekly_scores INT[] NOT NULL CHECK (cardinality(weekly_scores) = 20)
);

CREATE INDEX idx_players_normalized_name
    ON players (lower(replace(replace(name, '_', ''), ' ', '')));

-- Add some sample data so the app doesn't fall back to the text file
INSERT INTO players (name, weekly_scores)
VALUES ('Cristiano Ronaldo', '{10,15,20,5,8,12,30,22,18,14,25,11,9,16,21,24,13,19,20,28}'),
       ('Lionel Messi', '{12,18,15,9,7,14,28,25,20,11,22,13,10,18,24,22,15,17,22,26}');