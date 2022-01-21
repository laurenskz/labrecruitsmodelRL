package labrecruitsmodel

import agents.LabRecruitsTestAgent
import eu.iv4xr.framework.model.rl.approximation.ActionRepeatingFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class LabRecruitsLogicKtTest {

    @Test
    fun testPointsBetween() {
        positionsBetween(0 to 0, 9 to 9).forEach {
            println(it)
        }
    }

    @Test
    fun testFactory() {
        val config = LabRecruitsLevelConfig(
            mapOf(), Grid(10, 10, LRStaticEntity.FLOOR), Grid(10, 10, LREmpty),
            setOf(
                LRDoor("door1"),
                LRDoor("door2"),
                LRDoor("door3"),
                LRButton("button1"),
                LRButton("button2"),
                LRButton("button3"),
                LRGoal("goal1")
            )
        )
        val fac = LabRecruitsState.factory(config)
        println(
            fac.floatFeatures(
                LabRecruitsState(
                    10, 0,
                    mapOf(
                        "door1" to DoorState.OPEN,
                        "door2" to DoorState.CLOSED,
                        "door3" to DoorState.UNCERTAIN
                    ),
                    setOf("door1", "goal1"),
                    0 to 0
                )
            ).toList()
        )
        val model = LabRecruitsModel(config, LabRecruitsTestAgent("Bob"))
        val repeating = ActionRepeatingFactory(fac, model.possibleActions().toList())
        println(
            repeating.floatFeatures(
                LabRecruitsState(
                    10, 0,
                    mapOf(
                        "door1" to DoorState.OPEN,
                        "door2" to DoorState.CLOSED,
                        "door3" to DoorState.UNCERTAIN
                    ),
                    setOf("door1", "goal1"),
                    0 to 0
                ) to LRInteract("button1")
            ).toList()
        )
    }
}