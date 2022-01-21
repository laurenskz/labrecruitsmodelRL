package labrecruitsmodel

import eu.iv4xr.framework.model.distribution.Distribution
import eu.iv4xr.framework.model.distribution.always
import eu.iv4xr.framework.model.distribution.foldD
import eu.iv4xr.framework.model.rl.POMDAgentUtensils
import eu.iv4xr.framework.model.rl.POMDObservationFunction
import eu.iv4xr.framework.model.rl.Policy
import eu.iv4xr.framework.model.rl.StateWithGoalProgress
import nl.uu.cs.aplib.mainConcepts.SimpleState
import world.BeliefState
import kotlin.math.roundToInt

class LRPOMDObserver(private val config: LabRecruitsLevelConfig) :
    POMDObservationFunction<StateWithGoalProgress<LabRecruitsState>, LabRecruitsAction, LRObservation> {


    override fun observe(
        sp: StateWithGoalProgress<LabRecruitsState>,
        a: LabRecruitsAction
    ): Distribution<LRObservation> {
        val doorIds = config.allEntities.filterIsInstance<LRDoor>().map { it.id }
        val doorDists = doorIds.map { door -> doorVisible(door, sp.state, config).map { door to it } }.toList()
//        val doorDists = doorIds.map { door -> flip(0.5).map { door to it } }
        val doorsToShow = doorDists.foldD(always(emptySet<String>())) { acc, (id, b) -> if (b) acc + id else acc }
        val states = doorsToShow.map { LRObservation(showDoors(sp.state, it)) }
        return when (a) {
            LRExplore -> TODO()
            is LRInteract -> states
            is LRRefresh -> TODO()
        }
    }

    private fun observationFor(state: LabRecruitsState) =
        LRObservation(showDoors(state, visibleDoors(state, config)))

    private fun showDoors(s: LabRecruitsState, doors: Set<String>) =
        s.entityStates.filterKeys { it in doors }

    override fun observeState(s: StateWithGoalProgress<LabRecruitsState>): LRObservation {
        return observationFor(s.state)
    }
}

class LRPOMDUtensils(
    private val config: LabRecruitsLevelConfig,
    override val policy: Policy<Distribution<StateWithGoalProgress<LabRecruitsState>>, LabRecruitsAction>,
    val model: LabRecruitsModel
) :
    POMDAgentUtensils<LRObservation, LabRecruitsAction, StateWithGoalProgress<LabRecruitsState>> {
    override fun observeEnv(state: SimpleState): LRObservation {
        val beliefState = (state as? BeliefState) ?: error("Not a good state unfortunately")
        val wm = beliefState.worldmodel
        val x = wm.position.x.roundToInt()
        val y = wm.position.z.roundToInt()
        val mock = LabRecruitsState(10, 0, emptyMap(), emptySet(), x to y)
        val ids = visibleDoors(mock, config)
        val doorStates = wm.elements.filter { it.key in ids }.map {
            val blocking = wm.isBlocking(it.value)
            it.key to
                    if (blocking) DoorState.CLOSED
                    else DoorState.OPEN
        }.toMap()
        return LRObservation(doorStates)
    }

    override fun initialBelief(o: LRObservation): Distribution<StateWithGoalProgress<LabRecruitsState>> {
        return model.initialState().map {
            StateWithGoalProgress(listOf(false), it.copy(entityStates = it.entityStates + o.doors))
        }
    }

    override fun executeAction(a: LabRecruitsAction, state: SimpleState): Any {
        return model.executeAction(a, state)
    }
}
