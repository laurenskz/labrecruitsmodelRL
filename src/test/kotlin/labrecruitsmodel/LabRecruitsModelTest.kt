package labrecruitsmodel

import agents.LabRecruitsTestAgent
import agents.TestSettings
import environments.LabRecruitsConfig
import environments.LabRecruitsEnvironment
import eu.iv4xr.framework.model.distribution.Distribution
import eu.iv4xr.framework.model.rl.*
import eu.iv4xr.framework.model.rl.algorithms.MCPolicyGradient
import eu.iv4xr.framework.model.rl.algorithms.QLearningAlg
import eu.iv4xr.framework.model.rl.approximation.ActionRepeatingFactory
import eu.iv4xr.framework.model.rl.policies.*
import eu.iv4xr.framework.model.rl.valuefunctions.*
import game.LabRecruitsTestServer
import nl.uu.cs.aplib.AplibEDSL
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import world.BeliefState
import kotlin.concurrent.thread
import kotlin.math.pow
import kotlin.random.Random

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class LabRecruitsModelTest {

    var labRecruitsTestServer: LabRecruitsTestServer? = null

    @BeforeAll
    fun start() {
        TestSettings.USE_GRAPHICS = true
        val labRecruitesExeRootDir = System.getProperty("user.dir")
        Runtime.getRuntime().addShutdownHook(thread(start = false) { close() });
        labRecruitsTestServer = TestSettings.start_LabRecruitsTestServer(labRecruitesExeRootDir)
    }

    @AfterAll
    fun close() {
        labRecruitsTestServer?.close()
    }

    @Test
    fun test() {

        // Create an environment
        val config = LabRecruitsConfig("samiratest")
        val g = AplibEDSL.goal("the-goal").toSolve { happiness: Int -> happiness >= 100 }.lift()
        g.maxbudget(10.0)
        config.light_intensity = 0.95f
        val environment = LabRecruitsEnvironment(config)
        val state = BeliefState()
        val lagent = LabRecruitsTestAgent("agent1")
        lagent
            .attachState(state)
            .attachEnvironment(environment)
        val lrConfig = LRLevelParser.parse(config)
        val model = LabRecruitsModel(lrConfig, lagent)
        val mdp = createRlMDP(model, g)
        val policy = doQLearning(mdp).train(mdp)
        val agent = pomd(lrConfig, model, mdp, policy)
        agent.attachState(state)
        agent.setGoal(g)
        println(agent.beliefState.supportWithDensities())
        while (agent.goal.status.inProgress()) {
            agent.update()
        }
    }

    private fun pomd(
        config: LabRecruitsLevelConfig,
        model: LabRecruitsModel,
        mdp: MDP<StateWithGoalProgress<LabRecruitsState>, LabRecruitsAction>,
        policy: Policy<StateWithGoalProgress<LabRecruitsState>, LabRecruitsAction>
    ): POMDAgent<LRObservation, LabRecruitsAction, StateWithGoalProgress<LabRecruitsState>> {
        val observer = LRPOMDObserver(config)
        val pomd2 = POMD2(observer, mdp)
        val utils = LRPOMDUtensils(config, PolicyMapper(policy, pomd2), model)
        return POMDAgent(utils, pomd2)

    }

    class PolicyMapper<S, A : Identifiable>(val policy: Policy<S, A>, val mdp: MDP<Distribution<S>, A>) :
        Policy<Distribution<S>, A> {
        override fun action(state: Distribution<S>): Distribution<A> {
            return state.chain {
                policy.action(it)
            }
        }
    }

    private fun doQLearning(mdp: MDP<StateWithGoalProgress<LabRecruitsState>, LabRecruitsAction>): QLearningAlg<StateWithGoalProgress<LabRecruitsState>, LabRecruitsAction> {
        val qFunction: TrainableQFunction<StateWithGoalProgress<LabRecruitsState>, LabRecruitsAction> =
            DownSampledQFunction(QTable(0.05f)) {
                SmallLabRecruitsState(it.state.entityStates)
            }
        return QLearningAlg(qFunction, 0.95f, mdp, Random, 50000, 0.5)
    }

    private fun employMC(
        config: LabRecruitsLevelConfig,
        mdp: MDP<LabRecruitsState, LabRecruitsAction>
    ): MCPolicyGradient<LabRecruitsState, LabRecruitsAction> {
        val factory = LabRecruitsState.factory(config)
        val actionFactory = ActionRepeatingFactory(factory, mdp.allPossibleActions().toList())
        val policy = SoftmaxPolicy(actionFactory, mdp, 0.1)
        val valueFunction = LinearStateValueFunction(factory, 0.005)
        val visit = LinearStateValueFunction(factory, 1.0)
        val icm = CountBasedICMModule<LabRecruitsState, LabRecruitsAction>(visit) { 1.0 / (it + 1).pow(1) }
        return MCPolicyGradient(policy, icm, valueFunction, visit, 0.1, 1000, 0.95, Random)
    }

}