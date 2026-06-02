# Fantasy Sports Analyser

A robust Scala 3 backend data-processing engine designed to ingest, parse, and analyze multi-week player performance metrics. The application uses pure functional programming paradigms, immutable data structures, and monadic exception handling to deliver memory-safe, predictable analytical execution. It supports local development, containerized orchestration with Docker, and automated integration validation through GitHub Actions.

## Features

- **Functional ingestion pipeline:** Parses multi-week structured metrics defensively using Scala `Try` and `Option` monads.
- **Relational data layer:** Integrates local persistence through a PostgreSQL database initialized with repeatable schema definitions.
- **Fault-tolerant string normalization:** Implements a flexible command-line parsing algorithm that handles case insensitivity, character substitutions, and partial string matching.
- **Advanced performance analytics:**
  - Evaluates current-week performance vectors.
  - Computes historical bounds, including peak and trough metrics for each player.
  - Applies volumetric filtering for aggregated scoring thresholds above 500 points.
  - Performs comparative statistical evaluation using rolling mean calculations.
  - Supports time-series team accumulation analysis up to a specified target week.

## Architecture and Project Structure

```text
Prog3cw2/
├── .github/
│   └── workflows/
│       └── test.yml             # GitHub Actions CI/CD pipeline
├── src/
│   ├── data/
│   │   └── data.txt             # Flat-file backup data resource
│   ├── main/
│   │   └── scala/
│   │       └── App.scala        # Main application entry point
│   └── test/
│       └── scala/
│           └── AppSpec.scala    # ScalaTest suite
├── init.sql                     # PostgreSQL schema setup script
├── docker-compose.yml           # Containerized ecosystem orchestration
├── Dockerfile                   # Multi-stage container build instructions
├── build.sbt                    # sbt build configuration
└── README.md
```

## Prerequisites

- Scala 3.3.x
- sbt 1.10.x or higher
- Java Development Kit (JDK) 17 or 21
- Docker Desktop, required for containerized runtime execution

## Data File Specification

The core application dataset is seeded from structured tabular records stored in `src/data/data.txt`. Each line must follow this format:

```text
PlayerName,score1,score2,score3,...,score20
```

Example:

```text
Robert_Burns,18,24,28,20,32,38,50,26,34,42,55,28,36,40,34,50,52,46,42,72
```

- Column index `0` stores the unique player identifier.
- The remaining columns must contain exactly 20 numeric weekly performance values.

## Deployment and Execution Guide

### Method 1: Local Terminal Runtime

To compile and run the console application directly on the host machine:

1. Open a terminal in the project root directory.
2. Run the application with sbt:

```bash
sbt run
```

### Method 2: Docker Compose Runtime

The container stack provisions an isolated environment containing both the Scala application and a PostgreSQL database instance.

1. Ensure Docker Desktop is running.
2. Build and launch the full environment:

```bash
docker compose up --build
```

This setup mounts the required volumes, initializes the database schema from `init.sql`, applies environment configuration, and exposes the interactive CLI through the active terminal session.

### Method 3: IntelliJ IDEA

1. Import the repository root as an sbt project.
2. Confirm the project SDK is set to JDK 17 or JDK 21.
3. Open `src/main/scala/App.scala`, then run the `App` object.

## Continuous Integration Configuration

The repository includes an automated GitHub Actions workflow defined in `.github/workflows/test.yml`.

On every push and pull request targeting the `master` branch, the workflow provisions a PostgreSQL service container, resolves dependencies through sbt, executes the ScalaTest and JUnit suites, and uploads execution logs as workflow artifacts.

## Fault-Tolerant CLI Input Engine

The application includes a custom normalization layer that improves record matching during interactive CLI input.

It supports:

- Case normalization for lowercase and mixed-case queries.
- Character sanitization across underscores and spaces.
- Partial trailing-pattern matching for incomplete inputs.

Examples:

- Entering `sean` resolves to `Sean_Connery`.
- Entering `graham bell` resolves to `Alexander_Graham_Bell`.
- If multiple records match the same pattern, the CLI prompts for more specific input.

## Troubleshooting

### Persistence Failure

- Ensure the Docker containers have fully started before running database-dependent operations.
- Verify that the `DB_URL` value matches the active local connection configuration.

### Application Initialization Crash

- Confirm that `src/data/data.txt` exists and follows the required format.
- Ensure the application is started from the project root so relative file paths resolve correctly.

## Author

**Campbell Swan**  
BSc (Hons) Software Development Student  
Glasgow Caledonian University
