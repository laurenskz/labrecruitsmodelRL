package labrecruitsmodel

import environments.LabRecruitsConfig
import java.io.File

object LRLevelParser {

    fun parse(config: LabRecruitsConfig): LabRecruitsLevelConfig {
        return parse(File(config.level_path).readText())
    }

    fun parse(string: String): LabRecruitsLevelConfig {
        val entities = mutableSetOf<LabrecruitsEntity>()
        val parts = string.split("|")
        val actuators = mutableMapOf<String, Set<String>>()
        parts[0].trim().lines()
            .forEach {
                val thing = it.trim().split(",")
                actuators[thing[0]] = thing.subList(1, thing.size).filter { !it.isBlank() }.toSet()
            }
        val lines = parts[1].lines()
        val rows = lines.size
        val cols = lines.first().split(",").size
        val staticGrid = Grid(rows = rows, cols = cols, LRStaticEntity.EMPTY)
        val dynamicGrid = Grid<LabrecruitsEntity>(rows = rows, cols = cols, LREmpty)
        parts[1].lines().forEachIndexed { y, line ->
            line.split(",").forEachIndexed { x, s ->
                s.split(":").forEach {
                    parseDynamic(it)?.also {
                        dynamicGrid.set(x to y, it)
                        entities.add(it)
                    }
                    parseStatic(it)?.also { staticGrid.set(x to y, it) }
                }
            }
        }

        return LabRecruitsLevelConfig(actuators, staticGrid, dynamicGrid, entities)
    }

    private fun parseDynamic(s: String): LabrecruitsEntity? = when {
        s.startsWith("a") -> LRAgent(s.substringAfter("^"))
        s.startsWith("d") -> LRDoor(s.substringAfter("^"))
        s.startsWith("b") -> LRButton(s.substringAfter("^"))
        s.startsWith("g") -> LRGoal(s.substringAfter("^"))
        else -> null
    }

    private fun parseStatic(s: String): LRStaticEntity? = when (s) {
        "f" -> LRStaticEntity.FLOOR
        "w" -> LRStaticEntity.WALL
        else -> null
    }
}