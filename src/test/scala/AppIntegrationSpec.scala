import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, PrintStream}

class AppIntegrationSpec extends AnyFlatSpec with Matchers {

  private val alphaScores = Vector(
    10, 20, 30, 40, 50,
    10, 20, 30, 40, 50,
    10, 20, 30, 40, 50,
    10, 20, 30, 40, 50
  )

  private val betaScores = Vector.fill(20)(5)

  private val gammaScores = Vector(
    100, 1, 1, 1, 1,
    1, 1, 1, 1, 1,
    1, 1, 1, 1, 1,
    1, 1, 1, 1, 200
  )

  private val db = App.PlayerDatabase(Map(
    "Alpha" -> App.PlayerRecord("Alpha", alphaScores),
    "Beta"  -> App.PlayerRecord("Beta",  betaScores),
    "Gamma" -> App.PlayerRecord("Gamma", gammaScores)
  ))

  private def captureOutput(block: => Unit): String = {
    val buffer      = new ByteArrayOutputStream()
    val printer     = new PrintStream(buffer)
    val originalOut = System.out
    System.setOut(printer)
    try {
      Console.withOut(printer)(block)
    } finally {
      printer.flush()
      System.setOut(originalOut)
    }
    buffer.toString("UTF-8")
  }

  // Uses Console.withIn so that scala.StdIn.readLine() sees the simulated input
  private def withInput[A](input: String)(block: => A): A = {
    val stream = new ByteArrayInputStream((input + "\n").getBytes("UTF-8"))
    Console.withIn(stream)(block)
  }

  private def lineContaining(output: String, text: String): String =
    output.linesIterator.find(_.contains(text)).getOrElse("")

  
  // Menu option 1 – Current week scores
  

  "handleCurrentWeek" should "show the latest score for each player" in {
    val output = captureOutput {
      App.handleCurrentWeek(App.currentWeekScores)(db)
    }

    output should include ("Current Week Scores")
    output should include ("Alpha")
    output should include ("Beta")
    output should include ("Gamma")
    output should include ("200")
    output should include ("50")

    lineContaining(output, "Beta").trim should endWith ("5")
  }

  it should "list players in descending score order" in {
    val output = captureOutput {
      App.handleCurrentWeek(App.currentWeekScores)(db)
    }

    output.indexOf("Gamma") should be < output.indexOf("Alpha")
    output.indexOf("Alpha") should be < output.indexOf("Beta")
  }

  
  // Menu option 6 – Scores for a selected week
  

  "handleSelectedWeek" should "display week 1 scores when the user enters 1" in {
    val output = captureOutput {
      withInput("1") {
        App.handleSelectedWeek(db)
      }
    }

    output should include ("Scores For A Selected Week")
    output should include ("Scores for week 1")
    output should include ("Alpha")
    output should include ("Beta")
    output should include ("Gamma")
    output should include ("100")
    output should include ("10")
  }

  it should "display week 10 scores correctly" in {
    val output = captureOutput {
      withInput("10") {
        App.handleSelectedWeek(db)
      }
    }

    output should include ("Scores for week 10")
    lineContaining(output, "Alpha") should include ("50")
  }

  
  // Menu option 2 – Highest and lowest per player
  

  "handleMinMax" should "display correct min and max values for each player" in {
    val output = captureOutput {
      App.handleMinMax(App.minMaxScores)(db)
    }

    output should include ("Highest And Lowest Points Per Player")
    output should include ("Alpha")
    output should include ("Beta")
    output should include ("Gamma")

    lineContaining(output, "Alpha") should include ("10")
    lineContaining(output, "Alpha") should include ("50")
    lineContaining(output, "Beta")  should include ("5")
    lineContaining(output, "Gamma") should include ("1")
    lineContaining(output, "Gamma") should include ("200")
  }

  
  // Menu option 3 – Players with total > 500
  

  "handleHighTotals" should "show only Alpha above the threshold of 500" in {
    val output = captureOutput {
      App.handleHighTotals(db => App.highTotalPlayers(db, 500))(db)
    }

    output should include ("Players With Total Points Greater Than 500")
    output should include ("Alpha")
    output should include ("600")
    output should not include "Beta"
    output should not include "Gamma"
  }

  it should "show the no-players message when nobody exceeds the threshold" in {
    val lowDb = App.PlayerDatabase(Map(
      "Low" -> App.PlayerRecord("Low", Vector.fill(20)(1))
    ))

    val output = captureOutput {
      App.handleHighTotals(db => App.highTotalPlayers(db, 500))(lowDb)
    }

    output should include ("No players exceed 500 total points")
  }

  
  // Menu option 4 – Compare two players' averages
  

  "handleAverageCompare" should "display the correct averages and winner for Alpha vs Beta" in {
    val output = captureOutput {
      withInput("alpha\nbeta") {
        App.handleAverageCompare(db)
      }
    }

    output should include ("Compare Average Points For Two Players")
    output should include ("30.00")
    output should include ("5.00")
    output should include ("Alpha")
    output should include ("25.00")
  }

  it should "display the correct averages and winner for Alpha vs Gamma" in {
    val output = captureOutput {
      withInput("alpha\ngamma") {
        App.handleAverageCompare(db)
      }
    }

    output should include ("30.00")
    output should include ("15.90")
    output should include ("14.10")
  }

  it should "show zero difference when comparing a player with themselves" in {
    val output = captureOutput {
      withInput("alpha\nalpha") {
        App.handleAverageCompare(db)
      }
    }

    output should include ("0.00")
  }

  
  // Menu option 5 – Team analysis
  

  "handleTeamAnalysis" should "show correct totals for Alpha and Beta up to week 5" in {
    val output = captureOutput {
      withInput("alpha, beta\n5") {
        App.handleTeamAnalysis(db)
      }
    }

    output should include ("Team Analysis")
    output should include ("Cumulative points up to and including week 5")
    output should include ("Alpha")
    output should include ("Beta")
    output should include ("150")
    output should include ("25")
    output should include ("175")
  }

  it should "warn about missing players but still show valid totals" in {
    val output = captureOutput {
      withInput("alpha, nobody\n3") {
        App.handleTeamAnalysis(db)
      }
    }

    output should include("[Danger]")
    output should include("nobody")
    // Alpha weeks 1-3: 10+20+30 = 60
    output should include("60")
  }

  it should "show the no-valid-players message when all names are unknown" in {
    val output = captureOutput {
      withInput("x, y\n1") {
        App.handleTeamAnalysis(db)
      }
    }

    output should include ("No valid players found")
  }

  
  // handleChoice dispatch
  

  "handleChoice" should "return true for choices 1 to 3 which need no stdin" in {
    captureOutput { App.handleChoice("1", db) shouldBe true }
    captureOutput { App.handleChoice("2", db) shouldBe true }
    captureOutput { App.handleChoice("3", db) shouldBe true }
  }

  it should "return true for choice 4 which reads two player names from stdin" in {
    captureOutput {
      withInput("alpha\nbeta") {
        App.handleChoice("4", db) shouldBe true
      }
    }
  }

  it should "return true for choice 5 which reads player names and a week number from stdin" in {
    captureOutput {
      withInput("alpha, beta\n5") {
        App.handleChoice("5", db) shouldBe true
      }
    }
  }

  it should "return true for choice 6 which reads a week number from stdin" in {
    captureOutput {
      withInput("3") {
        App.handleChoice("6", db) shouldBe true
      }
    }
  }

  it should "return false for choice 0" in {
    captureOutput { App.handleChoice("0", db) shouldBe false }
  }

  it should "return true and print an error for an invalid choice" in {
    val output = captureOutput { App.handleChoice("99", db) }
    App.handleChoice("99", db) shouldBe true
    output should include ("not a valid option")
  }

  
  // Week number input validation
  

  "readWeekNumber" should "re-prompt until a valid week number is entered" in {
    val output = captureOutput {
      withInput("0\n21\nabc\n3") {
        App.handleSelectedWeek(db)
      }
    }

    output should include ("Please enter a whole number")
    output should include ("Scores for week 3")
    output should include ("30")
  }
}