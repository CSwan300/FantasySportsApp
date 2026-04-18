# Fantasy Sports Analyser

A Scala console application that reads player scores from a comma-separated text file and runs several analyses from a menu. The program uses a custom immutable data structure and functional programming techniques to process the data.

## Features

- Loads player data from `src/data/data.txt`.
- Displays the current week's scores for each player.
- Shows the highest and lowest scores per player.
- Lists players with total points above 500.
- Compares two players by average score.
- Calculates team totals up to a chosen week.
- Accepts flexible player-name input, including partial names and different capitalisation.

## Project structure

```text
Prog3cw2-build/
├── src/
│   ├── data/
│   │   └── data.txt
│   └── main/
│       └── scala/
│           └── App.scala
├── build.sbt
└── README.md
```

## Requirements

- Scala 3
- sbt
- A terminal or IDE such as IntelliJ IDEA

## Data file format

Each line in `src/data/data.txt` must contain:

```text
PlayerName,score1,score2,score3,...,score20
```

Example:

```text
Robert_Burns,18,24,28,20,32,38,50,26,34,42,55,28,36,40,34,50,52,46,42,72
```

- The player name is the first value on the line.
- The next 20 values are weekly scores.
- The final score is the current week's score.
- Each line must contain exactly 20 numeric values after the name.

## How to run

### Using sbt in a terminal

1. Open a terminal in the project root.
2. Run the application:

```bash
sbt run
```

3. If sbt asks which main class to run, select:

```text
App
```

### Using IntelliJ IDEA

1. Open the project in IntelliJ IDEA.
2. Make sure `src/data/data.txt` exists in the project.
3. Open `App.scala`.
4. Run the `main` method or the `App` object.

## Important note about the data path

The program expects the file at:

```text
src/data/data.txt
```

If you move the file, update the `dataPath` value in the code so it points to the correct location.

## Menu options

When the program starts, it shows this menu:

- `1` Current week scores.
- `2` Highest and lowest per player.
- `3` Players with total points > 500.
- `4` Compare two players' averages.
- `5` Team analysis up to a chosen week.
- `0` Quit.

## Flexible name input

When the program asks for a player name, you do not need to type it exactly as stored in the file.

The program will:
- ignore capitalisation,
- ignore spaces and underscores,
- match partial names,
- auto-select a player if only one match is found.

Examples:
- `sean` matches `Sean_Connery`
- `alexander` may match multiple players
- `graham bell` matches `Alexander_Graham_Bell`

If multiple matches are found, the program asks you for a specific name.

## Troubleshooting

### "Could not read file"

This usually means one of these is wrong:

- The file is not in `src/data/data.txt`.
- The working directory is not the project root.
- The file name is spelled incorrectly.

### Program exits immediately

This usually means the file could not be loaded, so the database is empty.

### Input not recognised

For player names, try entering part of the name instead of the full exact version.

## Example usage

```text
Loading data from 'src/data/data.txt' ...
Successfully loaded 20 players.

╔══════════════════════════════════════════════╗
║           FANTASY SPORTS ANALYSER           ║
╠══════════════════════════════════════════════╣
║  1. Current week scores                     ║
║  2. Highest and lowest per player           ║
║  3. Players with total points > 500         ║
║  4. Compare two players' averages           ║
║  5. Team analysis up to a chosen week       ║
║  0. Quit                                    ║
╚══════════════════════════════════════════════╝
```

## Author
**Campbell Swan**
> Coursework project for programming3
> :).