package rl.pacman.training
import java.nio.file.{Files, Paths}
import java.time.Instant

import rl.core._
import rl.pacman.core.PacmanProblem.{AgentState, GameState, Move, initialState}

import scala.collection.mutable
import scala.scalajs.niocharset.StandardCharsets

object PacmanTraining extends App {

  private val initialAgentData: QLearning[AgentState, Move] =
    QLearning(α = 0.5, γ = 0.9, ε = 0.4, Q = Map.empty)

  private val env: Environment[GameState, Move]                       = implicitly
  private val stateConversion: StateConversion[GameState, AgentState] = implicitly
  private val agentBehaviour: AgentBehaviour[QLearning[AgentState, Move], AgentState, Move] =
    implicitly

  private var t: Long                               = 0
  private var episodes: Long                        = 0
  private var episodeLength                         = 0
  private var longestEpisode                        = 0
  private var wins: Long                            = 0
  private var losses: Long                          = 0
  private val recentResults: mutable.Queue[Boolean] = new mutable.Queue[Boolean]()
  private val MaxQueueSize                          = 10000

  private var agentData            = initialAgentData
  private var gameState: GameState = initialState

  val trainingDir = Paths.get(s"pacman-training/${Instant.now()}")
  Files.createDirectories(trainingDir)
  Files.write(
    trainingDir.resolve("parameters.txt"),
    s"alpha = ${initialAgentData.α}, gamma = ${initialAgentData.γ}, epsilon = ${initialAgentData.ε}"
      .getBytes(StandardCharsets.UTF_8)
  )

  private def step(): Unit = {
    val currentState    = stateConversion.convertState(gameState)
    val possibleActions = env.possibleActions(gameState)
    val (nextAction, updateAgent) =
      agentBehaviour.chooseAction(agentData, currentState, possibleActions)
    val (nextState, reward) = env.step(gameState, nextAction)

    agentData = updateAgent(ActionResult(reward, stateConversion.convertState(nextState)))
    gameState = nextState

    episodeLength += 1
    t += 1

    if (env.isTerminal(gameState)) {
      episodes += 1
      longestEpisode = longestEpisode max episodeLength

      val won = gameState.food.isEmpty
      if (won) {
        wins += 1
      } else {
        losses += 1
      }
      recentResults.enqueue(won)
      if (recentResults.size > MaxQueueSize) {
        recentResults.dequeue()
      }

      gameState = initialState
      episodeLength = 0
    }
  }

  private def report(): Unit = {
    println(s"t = $t")
    println(s"Completed episodes = $episodes")
    println(s"Longest episode so far = $longestEpisode")
    println(s"Wins = $wins")
    println(s"Losses = $losses")
    println(s"Won ${recentResults.count(identity)} of the last 10,000 games")
    println(s"State space size = ${agentData.Q.size}")
    println()

    if (t % 50000000 == 0) {
      saveQValues()
    }
  }

  private def saveQValues(): Unit = {
    print("Saving Q values to file... ")
    val list: List[QKeyValue] = agentData.Q.map { case (k, v) => QKeyValue(k, v) }.toList

    import io.circe.syntax._
    val json = list.asJson

    Files.write(
      trainingDir.resolve(s"Q-after-$t-steps.json"),
      json.noSpaces.getBytes(StandardCharsets.UTF_8)
    )
    println("Done.")
  }

  while (true) {
    step()

    if (t % 10000000 == 0) {
      report()
    }
  }

}
