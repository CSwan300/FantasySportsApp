import scala.io.{Source, StdIn}
import scala.util.Try

/**
 * Fantasy Sports Analyser for coursework 2
 * Reads CSV data into immutable structures and provides menu-driven analysis.
 * Follows functional principles: pure logic, immutability, and isolated IO.
 */

object App {

  // Single player entry from the data file
  final case class PlayerRecord(name: String, weeklyScores: Vector[Int])

  // Full application dataset with helper methods
  final case class PlayerDatabase(players: Map[String, PlayerRecord]) {
    def isEmpty: Boolean = players.isEmpty
    def size: Int = players.size

    // Get score count from the first available player
    def maxWeeks: Int =
      players.values.headOption.map(_.weeklyScores.length).getOrElse(0)

    def playerNames: Vector[String] = players.keys.toVector.sorted
  }

  // Container for team-based calculation results
  final case class TeamAnalysisResult(
                                       perPlayerTotals: Map[String, Int],
                                       missingPlayers: List[String],
                                       teamTotal: Int
                                     )

  private val dataPath = "src/data/data.txt"
  private val ColW = 26 // Table column width for names
  private val NumW = 10 // Table column width for scores

  val database: PlayerDatabase = loadData(dataPath)

  // Converts a CSV line (Name, Score1, Score2) into a PlayerRecord
  def parseLine(line: String): Option[PlayerRecord] = {
    val parts = line.split(",").toList.map(_.trim)

    parts match {
      case name :: scoreStrings if name.nonEmpty && scoreStrings.length == 20 =>
        val parsedScores = scoreStrings.map(score => Try(score.toInt).toOption)
        if (parsedScores.forall(_.isDefined)) {
          Some(PlayerRecord(name, parsedScores.flatten.toVector))
        } else None
      case _ => None
    }
  }

  // Reads file and builds the PlayerDatabase map
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
        println(s"[ERROR] Could not read $path. Using empty dataset.")
        PlayerDatabase(Map.empty)
      }

  // Logic: Current week is defined as the last entry in the scores list
  def currentWeekScores(db: PlayerDatabase): Map[String, Int] =
    db.players.view.mapValues(_.weeklyScores.last).toMap

  // Logic: 1-based week input (User) to 0-based index (Vector)
  def scoresForWeek(db: PlayerDatabase, week: Int): Map[String, Int] =
    db.players.view.mapValues(_.weeklyScores(week - 1)).toMap

  def minMaxScores(db: PlayerDatabase): Map[String, (Int, Int)] =
    db.players.view
      .mapValues(p => (p.weeklyScores.min, p.weeklyScores.max))
      .toMap

  def highTotalPlayers(db: PlayerDatabase, threshold: Int = 500): Map[String, Int] =
    db.players.view
      .mapValues(_.weeklyScores.sum)
      .toMap
      .filter { case (_, total) => total > threshold }

  def averageScore(scores: Vector[Int]): Double =
    if (scores.isEmpty) 0.0 else scores.sum.toDouble / scores.length

  // Calculates cumulative points for a specific subset of players
  def teamStats(names: List[String], week: Int, db: PlayerDatabase): TeamAnalysisResult = {
    val found = names.filter(db.players.contains)
    val missing = names.filterNot(db.players.contains)

    val totals = found.map { name =>
      name -> db.players(name).weeklyScores.take(week).sum
    }.toMap

    TeamAnalysisResult(totals, missing, totals.values.sum)
  }

  // Standardizes names for easier search (lowercase, no spaces/underscores)
  def normaliseName(name: String): String =
    name.toLowerCase.replace("_", "").replace(" ", "")

  // Partial substring matching for user input
  def findMatchingPlayers(input: String, db: PlayerDatabase): Vector[String] = {
    val target = normaliseName(input)
    db.playerNames.filter(name => normaliseName(name).contains(target))
  }

  // Formatting helpers
  def padR(text: String, width: Int = ColW): String = text.padTo(width, ' ')
  def padL(text: String, width: Int = NumW): String =
    (" " * (width - text.length.min(width))) + text.take(width)

  def numFmt(n: Int): String = padL(n.toString)
  def numFmt(d: Double): String = padL(f"$d%.2f")

  def banner(title: String): Unit = {
    val line = "-" * (ColW + NumW * 2 + 4)
    println(s"\n$line\n  $title\n$line")
  }

  def printMenu(): Unit = {
    println("\n          FANTASY SPORTS ANALYSER           ")
    println("----------------------------------------------")
    println(" 1. Current week scores")
    println(" 2. Highest and lowest per player")
    println(" 3. Players with total points > 500")
    println(" 4. Compare two players' averages")
    println(" 5. Team analysis up to a chosen week")
    println(" 6. Scores for a selected week")
    println(" 0. Quit\n")
    print("Choice: ")
  }

  // Input validation loop for week numbers
  def readWeekNumber(maxWeek: Int): Int = {
    print(s"  Enter week number (1-$maxWeek): ")
    Try(StdIn.readLine().trim.toInt).toOption match {
      case Some(v) if v >= 1 && v <= maxWeek => v
      case _ =>
        println(s"  [!] Invalid input. Enter 1 to $maxWeek")
        readWeekNumber(maxWeek)
    }
  }

  // Flexible player search: auto-selects if one match, asks if multiple
  def readPlayer(prompt: String, db: PlayerDatabase): String = {
    print(prompt)
    val input = StdIn.readLine().trim
    val matches = findMatchingPlayers(input, db)

    matches match {
      case Vector() =>
        println("  [!] No player found. Try again.")
        readPlayer(prompt, db)
      case Vector(single) =>
        println(s"  Selected: $single")
        single
      case many =>
        println(s"  [!] Multiple matches: ${many.mkString(", ")}")
        readPlayer(prompt, db)
    }
  }

  // Reads a comma-separated list and resolves them to database names
  def readPlayerNames(db: PlayerDatabase): List[String] = {
    println("  Enter player names (comma separated):")
    print("  > ")
    StdIn.readLine().split(",").toList.map(_.trim).filter(_.nonEmpty).flatMap { raw =>
      findMatchingPlayers(raw, db) match {
        case Vector()       => println(s"  [!] '$raw' ignored (not found)"); None
        case Vector(single) => Some(single)
        case many           => println(s"  [!] '$raw' ambiguous, ignored"); None
      }
    }
  }

  //  Menu Handlers (UI Logic) 

  def handleCurrentWeek(backend: PlayerDatabase => Map[String, Int])(db: PlayerDatabase): Unit = {
    banner("Current Week Scores")
    println(s"  ${padR("Player")}${padL("Points")}\n  " + "-" * (ColW + NumW))
    backend(db).toSeq.sortBy(-_._2).foreach { case (n, p) => println(s"  ${padR(n)}${numFmt(p)}") }
  }

  def handleSelectedWeek(db: PlayerDatabase): Unit = {
    banner("Scores For A Selected Week")
    val week = readWeekNumber(db.maxWeeks)
    println(s"\n  Scores for week $week:")
    println(s"  ${padR("Player")}${padL("Points")}\n  " + "-" * (ColW + NumW))
    scoresForWeek(db, week).toSeq.sortBy(-_._2).foreach { case (n, p) => println(s"  ${padR(n)}${numFmt(p)}") }
  }

  def handleMinMax(backend: PlayerDatabase => Map[String, (Int, Int)])(db: PlayerDatabase): Unit = {
    banner("Highest And Lowest Points")
    println(s"  ${padR("Player")}${padL("Lowest")}${padL("Highest")}\n  " + "-" * (ColW + NumW * 2))
    backend(db).toSeq.sortBy(_._1).foreach { case (n, (l, h)) => println(s"  ${padR(n)}${numFmt(l)}${numFmt(h)}") }
  }

  def handleHighTotals(backend: PlayerDatabase => Map[String, Int])(db: PlayerDatabase): Unit = {
    banner("Players With Total Points > 500")
    val results = backend(db).toSeq.sortBy(-_._2)
    if (results.isEmpty) println("  No players exceed 500 points.")
    else {
      println(s"  ${padR("Player")}${padL("Total")}\n  " + "-" * (ColW + NumW))
      results.foreach { case (n, t) => println(s"  ${padR(n)}${numFmt(t)}") }
    }
  }

  def handleAverageCompare(db: PlayerDatabase): Unit = {
    banner("Compare Averages")
    val p1 = readPlayer("  First player: ", db)
    val p2 = readPlayer("  Second player: ", db)
    val avg1 = averageScore(db.players(p1).weeklyScores)
    val avg2 = averageScore(db.players(p2).weeklyScores)

    println(s"\n  ${padR(p1)} avg = ${numFmt(avg1)}")
    println(s"  ${padR(p2)} avg = ${numFmt(avg2)}")

    val diff = math.abs(avg1 - avg2)
    val winner = if (avg1 >= avg2) p1 else p2
    val loser = if (avg1 >= avg2) p2 else p1
    println(f"\n  $winner leads $loser by $diff%.2f points.")
  }

  def handleTeamAnalysis(db: PlayerDatabase): Unit = {
    banner("Team Analysis")
    val names = readPlayerNames(db)
    val week = readWeekNumber(db.maxWeeks)
    val result = teamStats(names, week, db)

    if (result.missingPlayers.nonEmpty)
      println(s"\n  [!] Missing players: ${result.missingPlayers.mkString(", ")}")

    if (result.perPlayerTotals.nonEmpty) {
      println(s"\n  Cumulative points to week $week:")
      println(s"  ${padR("Player")}${padL("Total")}\n  " + "-" * (ColW + NumW))
      result.perPlayerTotals.toSeq.sortBy(-_._2).foreach { case (n, t) => println(s"  ${padR(n)}${numFmt(t)}") }
      println("  " + "-" * (ColW + NumW) + s"\n  ${padR("TEAM TOTAL")}${numFmt(result.teamTotal)}")
    }
  }

  // Maps menu numbers to specific handler functions
  def handleChoice(choice: String, db: PlayerDatabase): Boolean =
    choice match {
      case "1" => handleCurrentWeek(currentWeekScores)(db); true
      case "2" => handleMinMax(minMaxScores)(db); true
      case "3" => handleHighTotals(db => highTotalPlayers(db, 500))(db); true
      case "4" => handleAverageCompare(db); true
      case "5" => handleTeamAnalysis(db); true
      case "6" => handleSelectedWeek(db); true
      case "0" => println("\nQuitting"); false
      case _   => println(s"\n  [!] '$choice' is invalid."); true
    }

  @annotation.tailrec
  def run(db: PlayerDatabase): Unit = {
    printMenu()
    val choice = Try(StdIn.readLine().trim).getOrElse("0")
    if (handleChoice(choice, db)) run(db)
  }

  def main(args: Array[String]): Unit = {
    println(s"Loading '$dataPath'")
    if (database.isEmpty) println("No data. Check file path.")
    else {
      println(s"Loaded ${database.size} players.")
      run(database)
    }
  }
}