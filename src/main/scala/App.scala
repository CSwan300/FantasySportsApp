import java.sql.DriverManager
import scala.io.{Source, StdIn}
import scala.util.Try

/**
 * Fantasy Sports Analyser & Calculation Engine (Production Grade)
 * Seamlessly integrates PostgreSQL container persistence with fully optimized, 
 * unmutated functional data transformations, tail-recursive calculation layers,
 * and dynamic programming fuzzy logic error resolution.
 */
object App {

  // Stores one player's name and weekly scores
  final case class PlayerRecord(name: String, weeklyScores: Vector[Int])

  // Stores all loaded player data in a immutable name-to-record based map
  final case class PlayerDatabase(players: Map[String, PlayerRecord]) {
    def isEmpty: Boolean = players.isEmpty
    def size: Int = players.size
    def maxWeeks: Int = players.values.headOption.map(_.weeklyScores.length).getOrElse(0)
    def playerNames: Vector[String] = players.keys.toVector.sorted
  }

  // Stores analysis results for a group of players
  final case class TeamAnalysisResult(
                                       perPlayerTotals: Map[String, Int],
                                       missingPlayers: List[String],
                                       teamTotal: Int
                                     )

  private val dataPath = "src/data/data.txt"
  private val ColW = 26
  private val NumW = 10

  // Dual-source data loader wrapper with a resilient failover mechanism
  val database: PlayerDatabase = loadDataFromPostgres() match {
    case scala.util.Success(db) if !db.isEmpty =>
      println("\n[Info] Successfully connected and loaded data from PostgreSQL database.")
      db
    case _ =>
      println("\n[Warning] PostgreSQL unavailable or empty. Falling back to local text file...")
      loadData(dataPath)
  }

  /**
   * Attempts to load dataset directly out of a PostgreSQL database instance
   */
  private def loadDataFromPostgres(): Try[PlayerDatabase] = Try {
    // Docker containerized network routing properties
    val url = "jdbc:postgresql://postgres-db:5432/fantasy_db"
    val username = "postgres"
    val password = "mysecretpassword"

    val connection = DriverManager.getConnection(url, username, password)
    try {
      val statement = connection.createStatement()
      val resultSet = statement.executeQuery("SELECT name, weekly_scores FROM players")

      val records = Iterator.continually(resultSet.next())
        .takeWhile(identity)
        .map { _ =>
          val name = resultSet.getString("name")
          // Retrieve the raw SQL array and safely cast it into a boxed Integer array
          val arrayObj = resultSet.getArray("weekly_scores").getArray.asInstanceOf[Array[java.lang.Integer]]
          val scores = arrayObj.map(_.toInt).toVector
          PlayerRecord(name, scores)
        }.toVector

      PlayerDatabase(records.map(r => r.name -> r).toMap)
    } finally {
      connection.close()
    }
  }

  // Converts a CSV line into a PlayerRecord if it contains exactly 20 scores
  def parseLine(line: String): Option[PlayerRecord] = {
    val parts = line.split(",").toList.map(_.trim)
    parts match {
      case name :: scoreStrings if name.nonEmpty && scoreStrings.length == 20 =>
        val parsedScores = scoreStrings.map(score => Try(score.toInt).toOption)
        if (parsedScores.forall(_.isDefined)) Some(PlayerRecord(name, parsedScores.flatten.toVector))
        else None
      case _ => None
    }
  }

  // Reads the data file and builds the player database (Fallback schema mechanism)
  def loadData(path: String): PlayerDatabase =
    Try(Source.fromFile(path)).toOption.map { source =>
      try {
        val records = source.getLines().flatMap(parseLine).toVector
        PlayerDatabase(records.map(r => r.name -> r).toMap)
      } finally {
        source.close()
      }
    }.getOrElse(PlayerDatabase(Map.empty))

  // --- CV OPTIMIZATION 1: TAIL-RECURSIVE MEMORY MANAGE ENGINE ---

  /**
   * Computes historical cumulative running totals using stack-safe operations.
   * Compiles down via TCO into a flat iterative loop executing in constant O(1) stack space,
   * shielding the data layer from StackOverflowErrors over large historical sequence sets.
   */
  def computeCumulativeScores(scores: Vector[Int]): Vector[Int] = {
    @annotation.tailrec
    def process(remaining: List[Int], currentSum: Int, acc: Vector[Int]): Vector[Int] = {
      remaining match {
        case Nil          => acc
        case head :: tail => 
          val newSum = currentSum + head
          process(tail, newSum, acc :+ newSum)
      }
    }
    process(scores.toList, 0, Vector.empty)
  }

  // --- CV OPTIMIZATION 2: FUZZY LOGIC RESOLUTION ALGORITHM ---

  // Standardization for flexible name character matching
  def normaliseName(name: String): String = name.toLowerCase.replace("_", "").replace(" ", "")

  /**
   * Dynamic programming calculation of the Levenshtein Distance matrix metric.
   * Maps single-character edit costs to evaluate structural string proximity.
   */
  def calculateLevenshteinDistance(s1: String, s2: String): Int = {
    val memo = Array.tabulate(s1.length + 1, s2.length + 1) { (i, j) =>
      if (i == 0) j else if (j == 0) i else 0
    }
    for (i <- 1 to s1.length; j <- 1 to s2.length) {
      if (s1(i - 1) == s2(j - 1)) memo(i)(j) = memo(i - 1)(j - 1)
      else memo(i)(j) = 1 + math.min(memo(i - 1)(j), math.min(memo(i)(j - 1), memo(i - 1)(j - 1)))
    }
    memo(s1.length)(s2.length)
  }

  /**
   * Resilient input resolution engine leveraging proximity matching rules.
   * Falls back to matrix distance evaluation if structural sub-containment matches fail.
   */
  def findMatchingPlayers(input: String, db: PlayerDatabase): Vector[String] = {
    val target = normaliseName(input)
    if (target.isEmpty) Vector.empty
    else {
      val exactMatches = db.playerNames.filter(name => normaliseName(name).contains(target))
      if (exactMatches.nonEmpty) exactMatches
      else db.playerNames.filter(name => calculateLevenshteinDistance(normaliseName(name), target) <= 2)
    }
  }

  // --- Core Functional Transformations ---

  def currentWeekScores(db: PlayerDatabase): Map[String, Int] =
    db.players.view.mapValues(_.weeklyScores.last).toMap

  def scoresForWeek(db: PlayerDatabase, week: Int): Map[String, Int] =
    db.players.view.mapValues(_.weeklyScores(week - 1)).toMap

  def minMaxScores(db: PlayerDatabase): Map[String, (Int, Int)] =
    db.players.view.mapValues(p => (p.weeklyScores.min, p.weeklyScores.max)).toMap

  def highTotalPlayers(db: PlayerDatabase, threshold: Int = 500): Map[String, Int] =
    db.players.view.mapValues(p => computeCumulativeScores(p.weeklyScores).last).toMap.filter(_._2 > threshold)

  def averageScore(scores: Vector[Int]): Double =
    if (scores.isEmpty) 0.0 else scores.sum.toDouble / scores.length

  def teamStats(names: List[String], week: Int, db: PlayerDatabase): TeamAnalysisResult = {
    val found = names.filter(db.players.contains)
    val missing = names.filterNot(db.players.contains)
    val totals = found.map(n => n -> computeCumulativeScores(db.players(n).weeklyScores).lift(week - 1).getOrElse(0)).toMap
    TeamAnalysisResult(totals, missing, totals.values.sum)
  }

  // --- UI and Terminal Formatting Helpers ---

  def padR(text: String, width: Int = ColW): String = text.padTo(width, ' ')
  def padL(text: String, width: Int = NumW): String =
    (" " * (width - text.length.min(width))) + text.take(width)

  def numFmt(n: Int): String = padL(n.toString)
  def numFmt(d: Double): String = padL(f"$d%.2f")

  def banner(title: String): Unit = {
    val line = "-" * (ColW + NumW * 2 + 4)
    println(s"\n$line\n  $title\n$line")
  }

  // --- Interactive Menu Operation Routing Handlers ---

  def handleCurrentWeek(backend: PlayerDatabase => Map[String, Int])(db: PlayerDatabase): Unit = {
    banner("Current Week Scores")
    println(s"  ${padR("Player")}${padL("Points")}")
    backend(db).toSeq.sortBy(-_._2).foreach { case (n, p) => println(s"  ${padR(n)}${numFmt(p)}") }
  }

  def handleSelectedWeek(db: PlayerDatabase): Unit = {
    banner("Scores For A Selected Week")
    val week = readWeekNumber(db.maxWeeks)
    println(s"\n  Scores for week $week:")
    println(s"  ${padR("Player")}${padL("Points")}")
    scoresForWeek(db, week).toSeq.sortBy(-_._2).foreach { case (n, p) => println(s"  ${padR(n)}${numFmt(p)}") }
  }

  def handleMinMax(backend: PlayerDatabase => Map[String, (Int, Int)])(db: PlayerDatabase): Unit = {
    banner("Highest And Lowest Points Per Player")
    println(s"  ${padR("Player")}${padL("Lowest")}${padL("Highest")}")
    backend(db).toSeq.sortBy(_._1).foreach { case (n, (l, h)) => println(s"  ${padR(n)}${numFmt(l)}${numFmt(h)}") }
  }

  def handleHighTotals(backend: PlayerDatabase => Map[String, Int])(db: PlayerDatabase): Unit = {
    banner("Players With Total Points Greater Than 500")
    val results = backend(db).toSeq.sortBy(-_._2)
    if (results.isEmpty) println("  No players exceed 500 total points")
    else {
      println(s"  ${padR("Player")}${padL("Total")}")
      println("  " + "-" * (ColW + NumW))
      results.foreach { case (n, t) => println(s"  ${padR(n)}${numFmt(t)}") }
    }
  }

  def handleAverageCompare(db: PlayerDatabase): Unit = {
    banner("Compare Average Points For Two Players")
    val p1 = readPlayer("  Enter first player name : ", db)
    val p2 = readPlayer("  Enter second player name: ", db)
    val avg1 = averageScore(db.players(p1).weeklyScores)
    val avg2 = averageScore(db.players(p2).weeklyScores)
    println(s"\n  ${padR(p1)} avg = ${numFmt(avg1)}\n  ${padR(p2)} avg = ${numFmt(avg2)}")
    val diff = math.abs(avg1 - avg2)
    val (w, l) = if (avg1 >= avg2) (p1, p2) else (p2, p1)
    println(f"\n  $w leads $l by $diff%.2f points on average")
  }

  def handleTeamAnalysis(db: PlayerDatabase): Unit = {
    banner("Team Analysis - Cumulative Points Up To A Chosen Week")
    val names = readPlayerNames(db)
    val week = readWeekNumber(db.maxWeeks)
    val result = teamStats(names, week, db)
    if (result.missingPlayers.nonEmpty) println(s"\n  [Danger] Players not found: ${result.missingPlayers.mkString(", ")}")
    if (result.perPlayerTotals.isEmpty) println("  No valid players found")
    else {
      println(s"\n  Cumulative points up to and including week $week:")
      println(s"  ${padR("Player")}${padL("Total")}")
      println("  " + "-" * (ColW + NumW))
      result.perPlayerTotals.toSeq.sortBy(-_._2).foreach { case (n, t) => println(s"  ${padR(n)}${numFmt(t)}") }
      println("  " + "-" * (ColW + NumW))
      println(s"  ${padR("TEAM TOTAL")}${numFmt(result.teamTotal)}")
    }
  }

  def readWeekNumber(maxWeek: Int): Int = {
    print(s"  Enter week number (1-$maxWeek): ")
    Try(StdIn.readLine().trim.toInt).toOption match {
      case Some(v) if v >= 1 && v <= maxWeek => v
      case _ =>
        println(s"  [Danger] Please enter a whole number between 1 and $maxWeek")
        readWeekNumber(maxWeek)
    }
  }

  def readPlayer(prompt: String, db: PlayerDatabase): String = {
    print(prompt)
    val input = StdIn.readLine().trim
    val matches = findMatchingPlayers(input, db)
    matches match {
      case Vector()  => println("  [Danger] No matching player found. Try again"); readPlayer(prompt, db)
      case Vector(s) => println(s"  Selected: $s"); s
      case many      => println(s"  [Danger] Ambiguous identity. Options found: ${many.mkString(", ")}"); readPlayer(prompt, db)
    }
  }

  def readPlayerNames(db: PlayerDatabase): List[String] = {
    println("  Enter player names separated by commas:")
    print("  > ")
    StdIn.readLine().split(",").toList.map(_.trim).filter(_.nonEmpty).flatMap { raw =>
      findMatchingPlayers(raw, db) match {
        case Vector()  => println(s"  [Danger] No match for '$raw' - ignored"); None
        case Vector(s) => println(s"  Selected: $s"); Some(s)
        case many      => println(s"  [Danger] '$raw' matches multiple entities or string is ambiguous"); None
      }
    }
  }

  def handleChoice(choice: String, db: PlayerDatabase): Boolean = choice match {
    case "1" => handleCurrentWeek(currentWeekScores)(db); true
    case "2" => handleMinMax(minMaxScores)(db); true
    case "3" => handleHighTotals(db => highTotalPlayers(db, 500))(db); true
    case "4" => handleAverageCompare(db); true
    case "5" => handleTeamAnalysis(db); true
    case "6" => handleSelectedWeek(db); true
    case "0" => false
    case _   => println(s"\n  [!] '$choice' is not a valid option. Please enter 0-6"); true
  }

  def main(args: Array[String]): Unit = { if (!database.isEmpty) run(database) }

  // Constant-space tail-recursive terminal interface execution loop
  @annotation.tailrec
  def run(db: PlayerDatabase): Unit = {
    println("\n1. Current | 2. MinMax | 3. High Totals | 4. Compare | 5. Team | 6. Week | 0. Quit")
    print("Choice: ")
    val choice = Try(StdIn.readLine().trim).getOrElse("0")
    if (handleChoice(choice, db)) run(db)
  }
}     }
    }
  }

  // Main navigation logic
  def handleChoice(choice: String, db: PlayerDatabase): Boolean = choice match {
    case "1" => handleCurrentWeek(currentWeekScores)(db); true
    case "2" => handleMinMax(minMaxScores)(db); true
    case "3" => handleHighTotals(db => highTotalPlayers(db, 500))(db); true
    case "4" => handleAverageCompare(db); true
    case "5" => handleTeamAnalysis(db); true
    case "6" => handleSelectedWeek(db); true
    case "0" => false
    case _   => println(s"\n  [!] '$choice' is not a valid option. Please enter 0-6"); true
  }

  def main(args: Array[String]): Unit = { if (!database.isEmpty) run(database) }

  @annotation.tailrec
  def run(db: PlayerDatabase): Unit = {
    println("\n1. Current | 2. MinMax | 3. High Totals | 4. Compare | 5. Team | 6. Week | 0. Quit")
    print("Choice: ")
    val choice = Try(StdIn.readLine().trim).getOrElse("0")
    if (handleChoice(choice, db)) run(db)
  }
}
