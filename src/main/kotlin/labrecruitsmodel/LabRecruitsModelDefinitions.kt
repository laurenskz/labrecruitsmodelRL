package labrecruitsmodel

import burlap.mdp.core.action.Action
import eu.iv4xr.framework.model.rl.BurlapAction
import eu.iv4xr.framework.model.rl.approximation.*
import eu.iv4xr.framework.model.rl.burlapadaptors.DataClassHashableState

typealias LRPos = Pair<Int, Int>

enum class DoorState {
    OPEN, CLOSED, UNCERTAIN

}


data class LabRecruitsState(
    val health: Int,
    val points: Int,
//    val entityBeliefs: Map<String, DoorState>,
    val entityStates: Map<String, DoorState>,
    val knownEntities: Set<String>,
    val pos: LRPos
) : DataClassHashableState() {

    companion object {
        fun factory(doorIds: List<String>, allEntities: List<String>) = CompositeFeature<LabRecruitsState>(
            listOf(
                RepeatedFeature(
                    doorIds.size,
                    OneHot(DoorState.values().toList())
                ).from { lr -> doorIds.map { lr.entityStates[it]!! } },
                RepeatedFeature(
                    allEntities.size,
                    BoolFeature
                ).from { lr -> allEntities.map { it in lr.knownEntities } }
            )
        )

        fun factory(config: LabRecruitsLevelConfig): CompositeFeature<LabRecruitsState> {
            val doorIds = config.allEntities.filterIsInstance<LRDoor>().map { it.id }
            val allEntities = config.allEntities.filterIsInstance<LREntityWithId>().map { it.id }
            return factory(doorIds, allEntities)
        }
    }

    override fun toString(): String {
        return "LRState{pos=$pos,entityStates=$entityStates}"
    }
}

data class SmallLabRecruitsState(
//    val pos: LRPos,
    val entityStates: Map<String, DoorState>,
//    val knownEntities: Set<String>
) : DataClassHashableState()


sealed class LabRecruitsAction : BurlapAction
object LRExplore : LabRecruitsAction() {
    override fun actionName(): String {
        return "Explore"
    }

    override fun copy(): Action {
        return this
    }
}

data class LRInteract(val entityId: String) : LabRecruitsAction() {
    override fun actionName(): String {
        return "Interact:$entityId"
    }

    override fun copy(): Action {
        return this.copy(entityId = entityId)
    }
}

data class LRRefresh(val entityId: String) : LabRecruitsAction() {
    override fun actionName(): String {
        return "Refresh:$entityId"
    }

    override fun copy(): Action {
        return this.copy(entityId = entityId)
    }
}

data class LRObservation(
    val doors: Map<String, DoorState>,
)