import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AppSpec extends AnyFlatSpec with Matchers with OptionValues {
// uses private data just for the tests
  private val alphaScores =
    Vector(10, 20, 30, 40, 50, 10, 20, 30, 40, 50, 10, 20, 30, 40, 50, 10, 20, 30, 40, 50)
  private val betaScores = Vector.fill(20)(5)
  private val gammaScores = Vector(100, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 200)

  private val alphaRecord = App.PlayerRecord("Alpha", alphaScores)
  private val betaRecord = App.PlayerRecord("Beta", betaScores)
  private val gammaRecord = App.PlayerRecord("Gamma", gammaScores)

  private val db = App.PlayerDatabase(Map(
    "Alpha" -> alphaRecord,
    "Beta"  -> betaRecord,
    "Gamma" -> gammaRecord
  ))

  private val emptyDb = App.PlayerDatabase(Map.empty)

  "parseLine" should "parse a valid line with 20 integer scores" in {
    val line = "TestPlayer," + (1 to 20).mkString(",")
    val result = App.parseLine(line)

    result shouldBe defined
    result.value.name shouldBe "TestPlayer"
    result.value.weeklyScores shouldBe (1 to 20).toVector
  }

  it should "trim whitespace from the name and scores" in {
    val scores = List.fill(20)(5)
    val line = "  SpacedName  ," + scores.mkString(" , ")
    val result = App.parseLine(line)

    result shouldBe defined
    result.value.name shouldBe "SpacedName"
    result.value.weeklyScores shouldBe Vector.fill(20)(5)
  }

  it should "return None when there are fewer than 20 scores" in {
    App.parseLine("ShortPlayer,1,2,3") shouldBe None
  }

  it should "return None when there are more than 20 scores" in {
    val line = "LongPlayer," + (1 to 21).mkString(",")
    App.parseLine(line) shouldBe None
  }

  it should "return None when a score is not an integer" in {
    val scores = (1 to 20).toList.updated(2, "abc")
    val line = "BadPlayer," + scores.mkString(",")
    App.parseLine(line) shouldBe None
  }

  it should "return None when the name is empty" in {
    val line = "," + (1 to 20).mkString(",")
    App.parseLine(line) shouldBe None
  }

  it should "return None for a blank line" in {
    App.parseLine("") shouldBe None
  }

  "PlayerDatabase.isEmpty" should "return false for a populated database" in {
    db.isEmpty shouldBe false
  }

  it should "return true for an empty database" in {
    emptyDb.isEmpty shouldBe true
  }

  "PlayerDatabase.size" should "return 3 for the fixture database" in {
    db.size shouldBe 3
  }

  "PlayerDatabase.maxWeeks" should "return 20 for the fixture database" in {
    db.maxWeeks shouldBe 20
  }

  it should "return 0 for an empty database" in {
    emptyDb.maxWeeks shouldBe 0
  }

  "PlayerDatabase.playerNames" should "return names in alphabetical order" in {
    db.playerNames shouldBe Vector("Alpha", "Beta", "Gamma")
  }

  "currentWeekScores" should "return the last score for each player" in {
    val result = App.currentWeekScores(db)

    result("Alpha") shouldBe 50
    result("Beta") shouldBe 5
    result("Gamma") shouldBe 200
  }

  it should "contain one entry per player" in {
    App.currentWeekScores(db).size shouldBe 3
  }

  "scoresForWeek" should "return week-1 scores for all players" in {
    val result = App.scoresForWeek(db, 1)

    result("Alpha") shouldBe 10
    result("Beta") shouldBe 5
    result("Gamma") shouldBe 100
  }

  it should "return week-20 scores for all players" in {
    val result = App.scoresForWeek(db, 20)

    result("Alpha") shouldBe 50
    result("Beta") shouldBe 5
    result("Gamma") shouldBe 200
  }

  it should "return the correct score for week 5" in {
    val result = App.scoresForWeek(db, 5)

    result("Alpha") shouldBe 50
    result("Beta") shouldBe 5
    result("Gamma") shouldBe 1
  }

  "minMaxScores" should "return the correct min and max for Alpha" in {
    App.minMaxScores(db)("Alpha") shouldBe (10, 50)
  }

  it should "return the correct min and max for Beta" in {
    App.minMaxScores(db)("Beta") shouldBe (5, 5)
  }

  it should "return the correct min and max for Gamma" in {
    App.minMaxScores(db)("Gamma") shouldBe (1, 200)
  }

  it should "return one entry per player" in {
    App.minMaxScores(db).size shouldBe 3
  }

  "highTotalPlayers" should "include only players above the threshold" in {
    val result = App.highTotalPlayers(db, 500)

    result.keys should contain only "Alpha"
    result("Alpha") shouldBe 600
  }

  it should "return an empty map when nobody exceeds the threshold" in {
    App.highTotalPlayers(db, 1000) shouldBe Map.empty
  }

  it should "include all players when the threshold is 0" in {
    App.highTotalPlayers(db, 0).size shouldBe 3
  }

  it should "default to a threshold of 500" in {
    val result = App.highTotalPlayers(db)

    result.keys should contain only "Alpha"
  }

  it should "exclude players whose total is exactly the threshold" in {
    App.highTotalPlayers(db, 600) shouldBe Map.empty
  }

  "averageScore" should "compute the average for Alpha" in {
    App.averageScore(alphaScores) shouldBe 30.0 +- 0.001
  }

  it should "compute the average for Beta" in {
    App.averageScore(betaScores) shouldBe 5.0 +- 0.001
  }

  it should "compute the average for Gamma" in {
    App.averageScore(gammaScores) shouldBe 15.9 +- 0.001
  }

  it should "return 0.0 for an empty vector" in {
    App.averageScore(Vector.empty[Int]) shouldBe 0.0 +- 0.001
  }

  it should "return the only value for a single-element vector" in {
    App.averageScore(Vector(42)) shouldBe 42.0 +- 0.001
  }

  "teamStats" should "compute totals correctly for Alpha and Beta up to week 5" in {
    val result = App.teamStats(List("Alpha", "Beta"), 5, db)

    result.perPlayerTotals("Alpha") shouldBe 150
    result.perPlayerTotals("Beta") shouldBe 25
    result.teamTotal shouldBe 175
    result.missingPlayers shouldBe Nil
  }

  it should "compute totals correctly up to week 1" in {
    val result = App.teamStats(List("Alpha", "Gamma"), 1, db)

    result.perPlayerTotals("Alpha") shouldBe 10
    result.perPlayerTotals("Gamma") shouldBe 100
    result.teamTotal shouldBe 110
  }

  it should "compute full-season totals up to week 20" in {
    val result = App.teamStats(List("Alpha"), 20, db)

    result.perPlayerTotals("Alpha") shouldBe 600
    result.teamTotal shouldBe 600
  }

  it should "record missing players" in {
    val result = App.teamStats(List("Alpha", "Unknown"), 3, db)

    result.missingPlayers shouldBe List("Unknown")
    result.perPlayerTotals.keys should contain only "Alpha"
  }

  it should "return all names as missing when none are found" in {
    val result = App.teamStats(List("X", "Y"), 5, db)

    result.perPlayerTotals shouldBe Map.empty
    result.teamTotal shouldBe 0
    result.missingPlayers should contain allOf ("X", "Y")
  }

  it should "handle an empty name list" in {
    val result = App.teamStats(Nil, 10, db)

    result.perPlayerTotals shouldBe Map.empty
    result.missingPlayers shouldBe Nil
    result.teamTotal shouldBe 0
  }

  "normaliseName" should "convert to lowercase" in {
    App.normaliseName("ALPHA") shouldBe "alpha"
  }

  it should "remove underscores" in {
    App.normaliseName("Player_A") shouldBe "playera"
  }

  it should "remove spaces" in {
    App.normaliseName("Player A") shouldBe "playera"
  }

  it should "handle mixed case with spaces and underscores" in {
    App.normaliseName("The _PLAYER_ Name") shouldBe "theplayername"
  }

  it should "return an empty string for empty input" in {
    App.normaliseName("") shouldBe ""
  }

  "findMatchingPlayers" should "find an exact match" in {
    App.findMatchingPlayers("Alpha", db) shouldBe Vector("Alpha")
  }

  it should "find a partial match case-insensitively" in {
    App.findMatchingPlayers("alph", db) shouldBe Vector("Alpha")
  }

  it should "return an empty vector when nothing matches" in {
    App.findMatchingPlayers("zzz", db) shouldBe Vector.empty
  }

  it should "return multiple matches when the input is ambiguous" in {
    val multiDb = App.PlayerDatabase(Map(
      "Alpha_One" -> App.PlayerRecord("Alpha_One", Vector.fill(20)(1)),
      "Alpha_Two" -> App.PlayerRecord("Alpha_Two", Vector.fill(20)(2)),
      "Beta"      -> App.PlayerRecord("Beta", Vector.fill(20)(3))
    ))

    val result = App.findMatchingPlayers("alpha", multiDb)
    result should contain allOf ("Alpha_One", "Alpha_Two")
    result should not contain "Beta"
  }

  it should "match regardless of underscores or spaces" in {
    val spaceDb = App.PlayerDatabase(Map(
      "Alpha One" -> App.PlayerRecord("Alpha One", Vector.fill(20)(1))
    ))

    App.findMatchingPlayers("Alpha_One", spaceDb) shouldBe Vector("Alpha One")
  }

  "padR" should "pad a short string to the default width" in {
    val result = App.padR("Hi")

    result.length shouldBe 26
    result shouldBe "Hi" + " " * 24
  }

  it should "not shorten a string that is already longer than the width" in {
    val result = App.padR("A" * 30, 26)

    result.length shouldBe 30
  }

  "padL" should "left-pad a short string to the default width" in {
    val result = App.padL("42")

    result.length shouldBe 10
    result shouldBe "        42"
  }

  it should "return the string unchanged when it already matches the width" in {
    App.padL("1234567890") shouldBe "1234567890"
  }

  "numFmt(Int)" should "format an integer right-aligned in width 10" in {
    val result = App.numFmt(7)

    result.length shouldBe 10
    result.trim shouldBe "7"
  }

  "numFmt(Double)" should "format a double to two decimal places" in {
    val result = App.numFmt(30.0)

    result.length shouldBe 10
    result.trim shouldBe "30.00"
  }

  it should "format a double that already has two decimal places" in {
    val result = App.numFmt(15.9)

    result.trim shouldBe "15.90"
  }
}