import scala.io.{Source, StdIn}
import scala.util.Try

/**
 * Fantasy Sports Analyser application.
 *
 * This application reads player data from a comma-separated text file,
 * stores it in a custom immutable data structure, and provides several
 * menu-driven analyses.
 *
 * The program follows functional programming principles by:
 * -using immutable case classes and immutable collections
 * -using pure functions for parsing and analysis
 * -isolating input and output to the application boundary
 */
object App {

  /**
   * Represents one player entry from the data file.
   *
   * Each line of the input file is converted into exactly one PlayerRecord.
   *
   * @param name the player's name
   * @param weeklyScores the weekly scores for the player, most recent last
   */
  final case class PlayerRecord(name: String, weeklyScores: Vector[Int])

  /**
   * Represents the full application dataset.
   *
   * The map key is the player name, allowing easy access to the player's scores.
   *
   * @param players map of player name to player record
   */
  final case class PlayerDatabase(players: Map[String, PlayerRecord]) {

    /**
     * Returns true if the database is empty.
     *
     * @return true if there are no players
     */
    def isEmpty: Boolean = players.isEmpty

    /**
     * Returns the number of players in the database.
     *
     * @return the number of players
     */
    def size: Int = players.size

    /**
     * Returns the number of weeks stored per player.
     *
     * @return the number of weekly scores, or 0 if empty
     */
    def maxWeeks: Int =
      players.values.headOption.map(_.weeklyScores.length).getOrElse(0)

    /**
     * Returns all player names in alphabetical order.
     *
     * @return the sorted player names
     */
    def playerNames: Vector[String] =
      players.keys.toVector.sorted
  }

  /**
   * Represents the result of the team analysis.
   *
   * @param perPlayerTotals cumulative total for each valid player
   * @param missingPlayers names not found in the database
   * @param teamTotal total score for all valid selected players
   */
  final case class TeamAnalysisResult(
                                       perPlayerTotals: Map[String, Int],
                                       missingPlayers: List[String],
                                       teamTotal: Int
                                     )

  /**
   * Path to the input file.
   */
  private val dataPath = "src/data/data.txt"

  /**
   * Width of text columns in formatted output.
   */
  private val ColW = 26

  /**
   * Width of numeric columns in formatted output.
   */
  private val NumW = 10

  /**
   * Main application dataset loaded from file.
   */
  val database: PlayerDatabase = loadData(dataPath)

  /**
   * Parses one line of the input file into a PlayerRecord.
   *
   * Expected format:
   * Player_A,18,24,28,20,...
   *
   * @param line one line from the file
   * @return an optional PlayerRecord if parsing succeeds
   */
  def parseLine(line: String): Option[PlayerRecord] = {
    val parts = line.split(",").toList.map(_.trim)

    parts match {
      case name :: scoreStrings if name.nonEmpty && scoreStrings.length == 20 =>
        val parsedScores = scoreStrings.map(score => Try(score.toInt).toOption)
        if (parsedScores.forall(_.isDefined)) {
          Some(PlayerRecord(name, parsedScores.flatten.toVector))
        } else {
          None
        }

      case _ => None
    }
  }

  /**
   * Loads the full player dataset from a file.
   *
   * Each valid line is converted into one PlayerRecord and then inserted
   * into the player map using the player name as the key.
   *
   * @param path the file path
   * @return the loaded player database
   */
  def loadData(path: String): PlayerDatabase =
    Try(Source.fromFile(path)).toOption
      .map { source =>
        try {
          val records = source.getLines().flatMap(parseLine).toVector
          val playerMap = records.map(record => record.name -> record).toMap
          PlayerDatabase(playerMap)
        } finally {
          source.close()
        }
      }
      .getOrElse {
        println(s"[ERROR] Could not read file: $path. Using empty dataset")
        PlayerDatabase(Map.empty)
      }

  /**
   * Returns the current week's score for each player.
   *
   * The coursework defines the current week as the last value in each
   * player's list of weekly scores.
   *
   * @param db the player database
   * @return a map of player name to current week's score
   */
  def currentWeekScores(db: PlayerDatabase): Map[String, Int] =
    db.players.view.mapValues(_.weeklyScores.last).toMap

  /**
   * Returns the scores for all players for a selected week.
   *
   * The user inputs weeks using 1-based numbering, so week 1 maps to index 0.
   *
   * @param db the player database
   * @param week the selected week number
   * @return a map of player name to score for the selected week
   */
  def scoresForWeek(db: PlayerDatabase, week: Int): Map[String, Int] =
    db.players.view.mapValues(_.weeklyScores(week -1)).toMap

  /**
   * Returns the minimum and maximum weekly score for each player.
   *
   * @param db the player database
   * @return a map of player name to (minimumScore, maximumScore)
   */
  def minMaxScores(db: PlayerDatabase): Map[String, (Int, Int)] =
    db.players.view
      .mapValues(player => (player.weeklyScores.min, player.weeklyScores.max))
      .toMap

  /**
   * Returns players whose total score is greater than the threshold.
   *
   * @param db the player database
   * @param threshold the threshold score
   * @return a map of player name to total score
   */
  def highTotalPlayers(db: PlayerDatabase, threshold: Int = 500): Map[String, Int] =
    db.players.view
      .mapValues(_.weeklyScores.sum)
      .toMap
      .filter { case (_, total) => total > threshold }

  /**
   * Returns the average score for one player's weekly scores.
   *
   * @param scores the weekly scores
   * @return the average score
   */
  def averageScore(scores: Vector[Int]): Double =
    if (scores.isEmpty) 0.0 else scores.sum.toDouble / scores.length

  /**
   * Computes cumulative team totals up to and including a selected week.
   *
   * @param names selected player names
   * @param week selected week number
   * @param db the player database
   * @return the team analysis result
   */
  def teamStats(names: List[String], week: Int, db: PlayerDatabase): TeamAnalysisResult = {
    val found = names.filter(db.players.contains)
    val missing = names.filterNot(db.players.contains)

    val totals = found.map { name =>
      name -> db.players(name).weeklyScores.take(week).sum
    }.toMap

    TeamAnalysisResult(
      perPlayerTotals = totals,
      missingPlayers = missing,
      teamTotal = totals.values.sum
    )
  }

  /**
   * Normalises a player name for flexible matching.
   *
   * This removes spaces and underscores and converts the text to lowercase.
   *
   * @param name the raw player name
   * @return the normalised name
   */
  def normaliseName(name: String): String =
    name.toLowerCase.replace("_", "").replace(" ", "")

  /**
   * Finds all player names that match the user's input.
   *
   * A match is found if the normalised stored name contains the
   * normalised user input as a substring.
   *
   * @param input the user's input
   * @param db the player database
   * @return matching player names
   */
  def findMatchingPlayers(input: String, db: PlayerDatabase): Vector[String] = {
    val target = normaliseName(input)
    db.playerNames.filter(name => normaliseName(name).contains(target))
  }

  /**
   * Pads text on the right.
   *
   * @param text the text to pad
   * @param width the output width
   * @return the padded text
   */
  def padR(text: String, width: Int = ColW): String =
    text.padTo(width, ' ')

  /**
   * Pads text on the left.
   *
   * @param text the text to pad
   * @param width the output width
   * @return the padded text
   */
  def padL(text: String, width: Int = NumW): String =
    (" " * (width -text.length.min(width))) + text.take(width)

  /**
   * Formats an integer for display.
   *
   * @param n the integer value
   * @return the formatted text
   */
  def numFmt(n: Int): String =
    padL(n.toString)

  /**
   * Formats a double for display to two decimal places.
   *
   * @param d the double value
   * @return the formatted text
   */
  def numFmt(d: Double): String =
    padL(f"$d%.2f")

  /**
   * Prints a banner title.
   *
   * @param title the banner title
   */
  def banner(title: String): Unit = {
    val line = "-" * (ColW + NumW * 2 + 4)
    println()
    println(line)
    println(s"  $title")
    println(line)
  }

  /**
   * Prints the application menu.
   */
  def printMenu(): Unit = {
    println()
    println("          FANTASY SPORTS ANALYSER           ")
    println("--------------------------------------------")
    println(" 1. Current week scores                     ")
    println(" 2. Highest and lowest per player           ")
    println(" 3. Players with total points > 500         ")
    println(" 4. Compare two players' averages           ")
    println(" 5. Team analysis up to a chosen week       ")
    println(" 6. Scores for a selected week              ")
    println(" 0. Quit                                    ")
    println("")
    print("Choice: ")
  }

  /**
   * Reads a valid week number from user input.
   *
   * @param maxWeek the maximum allowed week
   * @return a valid week number
   */
  def readWeekNumber(maxWeek: Int): Int = {
    print(s"  Enter week number (1-$maxWeek): ")

    Try(StdIn.readLine().trim.toInt).toOption match {
      case Some(value) if value >= 1 && value <= maxWeek => value
      case _ =>
        println(s"  [Danger] Please enter a whole number between 1 and $maxWeek")
        readWeekNumber(maxWeek)
    }
  }

  /**
   * Reads a player name from user input using flexible matching.
   *
   * The user does not need to type the exact full name. Matching is:
   * -case-insensitive
   * -tolerant of spaces and underscores
   * -based on partial text
   *
   * If there is exactly one match, it is selected automatically.
   * If there are several matches, suggestions are shown.
   *
   * @param prompt the input prompt
   * @param db the player database
   * @return the selected player name
   */
  def readPlayer(prompt: String, db: PlayerDatabase): String = {
    print(prompt)
    val input = StdIn.readLine().trim
    val matches = findMatchingPlayers(input, db)

    matches match {
      case Vector() =>
        println("  [Danger] No matching player found. Try again")
        readPlayer(prompt, db)

      case Vector(singleMatch) =>
        println(s"  Selected: $singleMatch")
        singleMatch

      case many =>
        println(s"  [Danger] Multiple matches found: ${many.mkString(", ")}")
        println("  Please type a bit more of the name")
        readPlayer(prompt, db)
    }
  }

  /**
   * Reads a comma-separated list of player names from user input.
   *
   * Each entered name is matched flexibly against the player list.
   *
   * @param db the player database
   * @return the selected player names
   */
  def readPlayerNames(db: PlayerDatabase): List[String] = {
    println("  Enter player names separated by commas:")
    print("  > ")

    StdIn.readLine()
      .split(",")
      .toList
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap { rawInput =>
        val matches = findMatchingPlayers(rawInput, db)

        matches match {
          case Vector() =>
            println(s"  [Danger] No match for '$rawInput' -ignored")
            None

          case Vector(singleMatch) =>
            println(s"  Selected: $singleMatch")
            Some(singleMatch)

          case many =>
            println(s"  [Danger] '$rawInput' matches multiple players: ${many.mkString(", ")}")
            println("  Please enter that player more specifically next time")
            None
        }
      }
  }

  /**
   * Frontend handler for analysis 1.
   *
   * Displays the current week's score for each player.
   *
   * @param backend the pure backend function
   * @param db the player database
   */
  def handleCurrentWeek(
                         backend: PlayerDatabase => Map[String, Int]
                       )(db: PlayerDatabase): Unit = {
    banner("Current Week Scores")
    println(s"  ${padR("Player")}${padL("Points")}")
    println("  " + "-" * (ColW + NumW))

    backend(db).toSeq.sortBy(-_._2).foreach { case (name, points) =>
      println(s"  ${padR(name)}${numFmt(points)}")
    }
  }

  /**
   * Frontend handler for the extra selected-week view.
   *
   * @param db the player database
   */
  def handleSelectedWeek(db: PlayerDatabase): Unit = {
    banner("Scores For A Selected Week")
    val week = readWeekNumber(db.maxWeeks)

    println()
    println(s"  Scores for week $week:")
    println(s"  ${padR("Player")}${padL("Points")}")
    println("  " + "-" * (ColW + NumW))

    scoresForWeek(db, week).toSeq.sortBy(-_._2).foreach { case (name, points) =>
      println(s"  ${padR(name)}${numFmt(points)}")
    }
  }

  /**
   * Frontend handler for analysis 2.
   *
   * @param backend the pure backend function
   * @param db the player database
   */
  def handleMinMax(
                    backend: PlayerDatabase => Map[String, (Int, Int)]
                  )(db: PlayerDatabase): Unit = {
    banner("Highest And Lowest Points Per Player")
    println(s"  ${padR("Player")}${padL("Lowest")}${padL("Highest")}")
    println("  " + "-" * (ColW + NumW * 2))

    backend(db).toSeq.sortBy(_._1).foreach { case (name, (low, high)) =>
      println(s"  ${padR(name)}${numFmt(low)}${numFmt(high)}")
    }
  }

  /**
   * Frontend handler for analysis 3.
   *
   * @param backend the pure backend function
   * @param db the player database
   */
  def handleHighTotals(
                        backend: PlayerDatabase => Map[String, Int]
                      )(db: PlayerDatabase): Unit = {
    banner("Players With Total Points Greater Than 500")
    val results = backend(db).toSeq.sortBy(-_._2)

    if (results.isEmpty) {
      println("  No players exceed 500 total points")
    } else {
      println(s"  ${padR("Player")}${padL("Total")}")
      println("  " + "-" * (ColW + NumW))
      results.foreach { case (name, total) =>
        println(s"  ${padR(name)}${numFmt(total)}")
      }
    }
  }

  /**
   * Frontend handler for analysis 4.
   *
   * @param db the player database
   */
  def handleAverageCompare(db: PlayerDatabase): Unit = {
    banner("Compare Average Points For Two Players")
    println("  Available players:")
    db.playerNames.foreach(name => println(s"    $name"))
    println()

    val player1 = readPlayer("  Enter first player name : ", db)
    val player2 = readPlayer("  Enter second player name: ", db)

    val avg1 = averageScore(db.players(player1).weeklyScores)
    val avg2 = averageScore(db.players(player2).weeklyScores)

    println()
    println(s"  ${padR(player1)} avg = ${numFmt(avg1)}")
    println(s"  ${padR(player2)} avg = ${numFmt(avg2)}")
    println()

    val difference = math.abs(avg1 -avg2)
    val (winner, loser) =
      if (avg1 >= avg2) (player1, player2) else (player2, player1)

    println(f"  $winner leads $loser by $difference%.2f points on average")
  }

  /**
   * Frontend handler for analysis 5.
   *
   * @param db the player database
   */
  def handleTeamAnalysis(db: PlayerDatabase): Unit = {
    banner("Team Analysis -Cumulative Points Up To A Chosen Week")

    val names = readPlayerNames(db)
    val week = readWeekNumber(db.maxWeeks)
    val result = teamStats(names, week, db)

    if (result.missingPlayers.nonEmpty) {
      println(s"\n  [WARNING] Players not found (ignored): ${result.missingPlayers.mkString(", ")}")
    }

    if (result.perPlayerTotals.isEmpty) {
      println("  No valid players found. Returning to menu")
    } else {
      println(s"\n  Cumulative points up to and including week $week:")
      println(s"  ${padR("Player")}${padL("Total")}")
      println("  " + "-" * (ColW + NumW))

      result.perPlayerTotals.toSeq.sortBy(-_._2).foreach { case (name, total) =>
        println(s"  ${padR(name)}${numFmt(total)}")
      }

      println("  " + "-" * (ColW + NumW))
      println(s"  ${padR("TEAM TOTAL")}${numFmt(result.teamTotal)}")
    }
  }

  /**
   * Handles one menu choice.
   *
   * This composes the frontend handlers with their backend functions,
   * keeping the data-processing functions pure and the user interaction separate.
   *
   * @param choice the selected menu option
   * @param db the player database
   * @return true if the application should continue
   */
  def handleChoice(choice: String, db: PlayerDatabase): Boolean =
    choice match {
      case "1" => handleCurrentWeek(currentWeekScores)(db); true
      case "2" => handleMinMax(minMaxScores)(db); true
      case "3" => handleHighTotals(db => highTotalPlayers(db, 500))(db); true
      case "4" => handleAverageCompare(db); true
      case "5" => handleTeamAnalysis(db); true
      case "6" => handleSelectedWeek(db); true
      case "0" =>
        println("\n  Quitting")
        false
      case _ =>
        println(s"\n  [!] '$choice' is not a valid option. Please enter 0-6")
        true
    }

  /**
   * Runs the application loop.
   *
   * @param db the player database
   */
  @annotation.tailrec
  def run(db: PlayerDatabase): Unit = {
    printMenu()
    val choice = Try(StdIn.readLine().trim).getOrElse("0")
    if (handleChoice(choice, db)) run(db)
  }

  /**
   * Main entry point.
   *
   * @param args command-line arguments
   */
  def main(args: Array[String]): Unit = {
    println(s"Loading data from '$dataPath' ..")

    if (database.isEmpty) {
      println("No data loaded. Please check the file path. Exiting")
    } else {
      println(s"Successfully loaded ${database.size} players")
      run(database)
    }
  }
}