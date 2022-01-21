package labrecruitsmodel

import agents.LabRecruitsTestAgent
import agents.tactics.GoalLib
import eu.iv4xr.framework.model.ProbabilisticModel
import eu.iv4xr.framework.model.distribution.*
import eu.iv4xr.framework.model.rl.*
import eu.iv4xr.framework.utils.cons
import nl.uu.cs.aplib.AplibEDSL
import nl.uu.cs.aplib.mainConcepts.SimpleState
import world.BeliefState
import kotlin.math.roundToInt



class LabRecruitsModel(private val config: LabRecruitsLevelConfig, private val agent: LabRecruitsTestAgent) :
    ProbabilisticModel<LabRecruitsState, LabRecruitsAction> {
    override fun possibleStates(): Sequence<LabRecruitsState> {
        TODO("Not yet implemented")
    }

    override fun possibleActions(state: LabRecruitsState): Sequence<LabRecruitsAction> {
        return sequence {
//            yield(LRExplore)
            yieldAll(state.knownEntities.filter { config.isButton(it) || config.isGoal(it) }.map { LRInteract(it) })
//            yieldAll(state.knownEntities.filter { config.isDoor(it) }.map { LRRefresh(it) })
        }
    }

    private fun goalForAction(action: LabRecruitsAction) = when (action) {
        LRExplore -> TODO()
        is LRInteract -> GoalLib.entityInteracted(action.entityId)
        is LRRefresh -> GoalLib.entityStateRefreshed(action.entityId)
    }

    override fun executeAction(action: LabRecruitsAction, state: SimpleState): Any {
        var goal = goalForAction(action)
        if (action is LRInteract && config.isDoor(action.entityId)) {
            val visible = visibleDoors(parseBeliefState(state), config)
            val goals = goal cons visible.map { GoalLib.entityStateRefreshed(it) }
            goal = AplibEDSL.SEQ(*goals.toTypedArray())
        }

        agent.setGoal(goal)
        while (goal.status.inProgress()) {
            agent.update()
            Thread.sleep(50)
        }
        if (action is LRInteract && config.isButton(action.entityId)) {
            (state as BeliefState).lastInteraction = state.worldmodel.timestamp
        }
        state.updateState(agent.id)
        return (state as BeliefState).worldmodel().score
    }

    override fun convertState(state: SimpleState): LabRecruitsState {
        TODO("POMD model")
    }

    fun parseBeliefState(state: SimpleState): LabRecruitsState {
        val beliefState = (state as? BeliefState) ?: error("Not a good state unfortunately")
        val wm = beliefState.worldmodel
        val x = wm.position.x.roundToInt()
        val y = wm.position.z.roundToInt()

        val pos = x to y
        val doors =
            config.allEntities.filterIsInstance(LRDoor::class.java).associate { it.id to DoorState.UNCERTAIN }
        val entities = wm.elements.filter { it.key in doors && it.value.timestamp >= beliefState.lastInteraction }.map {
            val blocking = wm.isBlocking(it.value)
            it.key to
                    if (blocking) DoorState.CLOSED
                    else DoorState.OPEN
        }.toMap()
        val finalDoors = doors + entities
        val explored = explore(LabRecruitsState(10, 0, finalDoors, emptySet(), pos), config)
        return explored
    }

    override fun isTerminal(state: LabRecruitsState): Boolean {
        return state.health <= 0 || state.points >= 100
    }

    private fun updateSingleDoorMap(
        id: String,
        state: LabRecruitsState,
        map: MutableMap<String, DoorState>
    ) {
        val current = state.entityStates[id] ?: return
        val new = when (current) {
            DoorState.CLOSED -> DoorState.OPEN
            DoorState.UNCERTAIN -> DoorState.UNCERTAIN
            DoorState.OPEN -> DoorState.CLOSED
        }
        map[id] = new
    }

    private fun updateDoors(id: Set<String>, state: LabRecruitsState): LabRecruitsState {
        val map = mutableMapOf<String, DoorState>()
        id.forEach { updateSingleDoorMap(it, state, map) }
        return state.copy(entityStates = state.entityStates + map)
    }

    override fun transition(current: LabRecruitsState, action: LabRecruitsAction): Distribution<LabRecruitsState> {
        if (action is LRInteract && action.entityId !in current.knownEntities) {
            val visited = mutableSetOf<LRPos>()
            val knownEntities = mutableSetOf<String>()
            dfs(current, config, visited, knownEntities)
            val newPos = Distributions.uniform(visited)
            return newPos.map { explore(current.copy(pos = it), config) }
        }
        if (action is LRInteract) {
            val maybeGoal =
                config.allEntities.filterIsInstance(LRGoal::class.java).firstOrNull { it.id == action.entityId }
            if (maybeGoal != null) {
                if (maybeGoal.id in explore(current, config).knownEntities) {
                    return always(current.copy(points = current.points + 100))
                }
            }
            config.actuators[action.entityId]?.also {
                val updatedDoors = updateDoors(it, current)
                return squaresAround(action.entityId, config).map {
                    explore(updatedDoors.copy(pos = it), config)
                }
            }
        }
        if (action is LRRefresh) {
            if (
                config.isDoor(action.entityId) &&
                action.entityId in explore(current, config).knownEntities &&
                current.entityStates[action.entityId] == DoorState.UNCERTAIN
            ) {
                val newDoor = if_(flip(0.5), DoorState.CLOSED, DoorState.OPEN)
                return newDoor.map {
                    val entityMap = mapOf(action.entityId to it)
                    explore(current.copy(entityStates = current.entityStates + entityMap), config)
                }
            }
        }
        return always(current)
    }

    override fun proposal(
        current: LabRecruitsState,
        action: LabRecruitsAction,
        result: LabRecruitsState
    ): Distribution<out Any> {
        return always(result.points)
    }

    override fun possibleActions(): Sequence<LabRecruitsAction> {
        return sequence {
//            yield(LRExplore)
            yieldAll(config.allEntities.mapNotNull { (it as? LREntityWithId)?.id }.map { LRInteract(it) })
//            yieldAll(config.allEntities.mapNotNull { (it as? LREntityWithId)?.id }.map { LRRefresh(it) })
        }
    }

    override fun initialState(): Distribution<LabRecruitsState> {
        val pos = config.dynamicLayout.pos { it is LRAgent }
        val entityStates = config.allEntities.mapNotNull {
            if (it is LRDoor) {
                it.id to DoorState.UNCERTAIN
            } else
                null
        }.toMap()
        return doorDist(entityStates).map {
            explore(
                LabRecruitsState(
                    10,
                    0,
                    it,
                    emptySet(),
                    pos
                ), config
            )
        }
    }
}
