import java.sql.DriverManager
import scala.io.{Source, StdIn}
import scala.util.Try

/**
 * Fantasy Sports Analytics Application.
 * * This system processes historical player performance metrics by fetching 
 * datasets dynamically from a PostgreSQL database server. If the server 
 * is down or the database is unpopulated, it gracefully falls back to a 
 * local comma-separated text file database.
 */
object App {

  // Domain Data Models

  final case class PlayerRecord(name: String, weeklyScores: Vector[Int])

  final case class PlayerDatabase(players: Map[String, PlayerRecord]) {
    def isEmpty: Boolean = players.isEmpty
    def maxWeeks: Int = players.values.headOption.map(_.weeklyScores.length).getOrElse(0)
    def playerNames: Vector[String] = players.keys.toVector.sorted
  }

  final case class TeamAnalysisResult(perPlayerTotals: Map[String, Int], missingPlayers: List[String], teamTotal: Int)

  // UI Configuration Constants
  private val dataPath = "src/data/data.txt"
  private val ColW = 26
  private val NumW = 10

  // Database Initialization Sequence

  val database: PlayerDatabase = loadDataFromPostgres().filter(!_.isEmpty).getOrElse {
    println("\n[Warning] PostgreSQL unavailable or empty. Falling back to local text file...")
    loadData(dataPath)
  }

  private def loadDataFromPostgres(): Try[PlayerDatabase] = Try {
    val connection = DriverManager.getConnection("jdbc:postgresql://postgres-db:5432/fantasy_db", "postgres", "mysecretpassword")
    try {
      val resultSet = connection.createStatement().executeQuery("SELECT name, weekly_scores FROM players")
      val records = Iterator.continually(resultSet.next()).takeWhile(identity).map { _ =>
        val name = resultSet.getString("name")
        val arrayObj = resultSet.getArray("weekly_scores").getArray.asInstanceOf[Array[Object]]
        PlayerRecord(name, arrayObj.map(_.asInstanceOf[java.lang.Integer].toInt).toVector)
      }.toVector
      PlayerDatabase(records.map(r => r.name -> r).toMap)
    } finally connection.close()
  }

  def parseLine(line: String): Option[PlayerRecord] = {
    val parts = line.split(",").toList.map(_.trim)
    parts match {
      case name :: scoreStrings if name.nonEmpty && scoreStrings.length == 20 =>
        val parsedScores = scoreStrings.flatMap(s => Try(s.toInt).toOption)
        if (parsedScores.length == 20) Some(PlayerRecord(name, parsedScores.toVector)) else None
      case _ => None
    }
  }

  def loadData(path: String): PlayerDatabase = {
    val db = Try(Source.fromFile(path)).map { source =>
      try PlayerDatabase(source.getLines().flatMap(parseLine).map(r => r.name -> r).toMap) finally source.close()
    }.getOrElse(PlayerDatabase(Map.empty))
    if (!db.isEmpty) println("\n[Info] Successfully connected and loaded data from local fallback file.")
    db
  }

  def computeCumulativeScores(scores: Vector[Int]): Vector[Int] = {
    @annotation.tailrec
    def process(remaining: List[Int], currentSum: Int, acc: Vector[Int]): Vector[Int] = remaining match {
      case Nil          => acc
      case head :: tail => val newSum = currentSum + head; process(tail, newSum, acc :+ newSum)
    }
    process(scores.toList, 0, Vector.empty)
  }

  // Fuzzy String Matching Metrics
  
  def normaliseName(name: String): String = name.toLowerCase.replace("_", "").replace(" ", "")

  def findMatchingPlayers(input: String, db: PlayerDatabase): Vector[String] = {
    val target = normaliseName(input)
    if (target.isEmpty) Vector.empty
    else {
      val exactMatches = db.playerNames.filter(name => normaliseName(name).contains(target))
      if (exactMatches.nonEmpty) exactMatches
      else db.playerNames.filter { name =>
        val normalDbName = normaliseName(name)
        val sharedChars = target.intersect(normalDbName).length
        val lengthDelta = math.abs(target.length - normalDbName.length)
        sharedChars >= (target.length - 2) && lengthDelta <= 2
      }
    }
  }

  // Pure Business Logic Calculations (Restored for Test Suite Compatibility)

  def averageScore(scores: Vector[Int]): Double =
    if (scores.isEmpty) 0.0 else scores.sum.toDouble / scores.length

  def teamStats(names: List[String], week: Int, db: PlayerDatabase): TeamAnalysisResult = {
    val found = names.filter(db.players.contains)
    val missing = names.filterNot(db.players.contains)
    val totals = found.map(n => n -> computeCumulativeScores(db.players(n).weeklyScores).lift(week - 1).getOrElse(0)).toMap
    TeamAnalysisResult(totals, missing, totals.values.sum)
  }

  // Functional String Layout Utilities
  
  def padR(text: String, width: Int = ColW): String = text.padTo(width, ' ')
  def padL(text: String, width: Int = NumW): String = (" " * (width - text.length.min(width))) + text.take(width)
  def numFmt(n: Any): String = n match {
    case d: Double => padL(f"$d%.2f")
    case other     => padL(other.toString)
  }

  def banner(title: String): Unit = {
    val line = "-" * (ColW + NumW * 2 + 4)
    println(s"\n$line\n  $title\n$line")
  }

  // Analytical View Handlers

  def handleCurrentWeek(db: PlayerDatabase): Unit = {
    banner("Current Week Scores")
    println(s"  ${padR("Player")}${padL("Points")}")
    db.players.view.mapValues(_.weeklyScores.last).toSeq.sortBy(-_._2).foreach { case (n, p) => println(s"  ${padR(n)}${numFmt(p)}") }
  }

  def handleSelectedWeek(db: PlayerDatabase): Unit = {
    banner("Scores For A Selected Week")
    val week = readWeekNumber(db.maxWeeks)
    println(s"\n  Scores for week $week:\n  ${padR("Player")}${padL("Points")}")
    db.players.view.mapValues(_.weeklyScores(week - 1)).toSeq.sortBy(-_._2).foreach { case (n, p) => println(s"  ${padR(n)}${numFmt(p)}") }
  }

  def handleMinMax(db: PlayerDatabase): Unit = {
    banner("Highest And Lowest Points Per Player")
    println(s"  ${padR("Player")}${padL("Lowest")}${padL("Highest")}")
    db.players.view.mapValues(p => (p.weeklyScores.min, p.weeklyScores.max)).toSeq.sortBy(_._1).foreach { case (n, (l, h)) => println(s"  ${padR(n)}${numFmt(l)}${numFmt(h)}") }
  }

  def handleHighTotals(db: PlayerDatabase): Unit = {
    banner("Players With Total Points Greater Than 500")
    val results = db.players.view.mapValues(p => computeCumulativeScores(p.weeklyScores).last).toMap.filter(_._2 > 500).toSeq.sortBy(-_._2)
    if (results.isEmpty) println("  No players exceed 500 total points")
    else {
      println(s"  ${padR("Player")}${padL("Total")}\n  " + "-" * (ColW + NumW))
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
    val (w, l) = if (avg1 >= avg2) (p1, p2) else (p2, p1)
    println(f"\n  $w leads $l by ${math.abs(avg1 - avg2)}%.2f points on average")
  }

  def handleTeamAnalysis(db: PlayerDatabase): Unit = {
    banner("Team Analysis - Cumulative Points Up To A Chosen Week")
    val names = readPlayerNames(db)
    val week = readWeekNumber(db.maxWeeks)
    val result = teamStats(names, week, db)

    if (result.missingPlayers.nonEmpty) println(s"\n  [Danger] Players not found: ${result.missingPlayers.mkString(", ")}")
    if (result.perPlayerTotals.isEmpty) println("  No valid players found")
    else {
      println(s"\n  Cumulative points up to and including week $week:\n  ${padR("Player")}${padL("Total")}\n  " + "-" * (ColW + NumW))
      result.perPlayerTotals.toSeq.sortBy(-_._2).foreach { case (n, t) => println(s"  ${padR(n)}${numFmt(t)}") }
      println("  " + "-" * (ColW + NumW) + s"\n  ${padR("TEAM TOTAL")}${numFmt(result.teamTotal)}")
    }
  }

  // Monadic User Read Loops via Tail-Recursion

  def readWeekNumber(maxWeek: Int): Int = {
    print(s"  Enter week number (1-$maxWeek): ")
    Try(StdIn.readLine().trim.toInt).toOption match {
      case Some(v) if v >= 1 && v <= maxWeek => v
      case _ => println(s"  [Danger] Please enter a whole number between 1 and $maxWeek"); readWeekNumber(maxWeek)
    }
  }

  def readPlayer(prompt: String, db: PlayerDatabase): String = {
    print(prompt)
    findMatchingPlayers(StdIn.readLine().trim, db) match {
      case Vector()  => println("  [Danger] No matching player found. Try again"); readPlayer(prompt, db)
      case Vector(s) => println(s"  Selected: $s"); s
      case many      => println(s"  [Danger] Ambiguous identity. Options found: ${many.mkString(", ")}"); readPlayer(prompt, db)
    }
  }

  def readPlayerNames(db: PlayerDatabase): List[String] = {
    println("  Enter player names separated by commas:\n  > ")
    StdIn.readLine().split(",").toList.map(_.trim).filter(_.nonEmpty).flatMap { raw =>
      findMatchingPlayers(raw, db) match {
        case Vector()  => println(s"  [Danger] No match for '$raw' - ignored"); None
        case Vector(s) => println(s"  Selected: $s"); Some(s)
        case many      => println(s"  [Danger] '$raw' matches multiple entities or string is ambiguous"); None
      }
    }
  }

  // Application Runtimes
  
  def main(args: Array[String]): Unit = { if (!database.isEmpty) run(database) }

  @annotation.tailrec
  def run(db: PlayerDatabase): Unit = {
    println("\n1. Current | 2. MinMax | 3. High Totals | 4. Compare | 5. Team | 6. Week | 0. Quit\nChoice: ")
    val active = Try(StdIn.readLine().trim).getOrElse("0") match {
      case "1" => handleCurrentWeek(db); true
      case "2" => handleMinMax(db); true
      case "3" => handleHighTotals(db); true
      case "4" => handleAverageCompare(db); true
      case "5" => handleTeamAnalysis(db); true
      case "6" => handleSelectedWeek(db); true
      case "0" => false
      case userChoice => println(s"\n  [!] '$userChoice' is not a valid option. Please enter 0-6"); true
    }
    if (active) run(db)
  }
}
