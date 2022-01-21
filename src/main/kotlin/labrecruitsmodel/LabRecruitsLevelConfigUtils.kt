package labrecruitsmodel


class Grid<T>(val rows: Int, val cols: Int, val initial: T) {
    private val tiles = List(rows) { MutableList(cols) { initial } }

    fun get(pos: LRPos) = tiles[pos.second][pos.first]

    fun set(pos: LRPos, t: T) {
        tiles[pos.second][pos.first] = t
    }

    private fun seq(): Sequence<Pair<LRPos, T>> {
        return sequence {
            for (x in 0 until cols) {
                for (y in 0 until rows) {
                    val pos = x to y
                    yield(pos to get(pos))
                }
            }
        }
    }

    fun pos(predicate: (T) -> Boolean): LRPos = seq().first { predicate(it.second) }.first

    override fun toString(): String {
        return tiles.joinToString("\n", transform = {
            it.toString()
        })
    }
}

enum class LRStaticEntity {
    WALL, FLOOR, EMPTY
}

sealed class LabrecruitsEntity
abstract class LREntityWithId() : LabrecruitsEntity() {
    abstract val id: String
}

object LREmpty : LabrecruitsEntity()

data class LRDoor(override val id: String) : LREntityWithId()
data class LRAgent(override val id: String) : LREntityWithId()
data class LRButton(override val id: String) : LREntityWithId()
data class LRGoal(override val id: String) : LREntityWithId()

data class LabRecruitsLevelConfig(
    val actuators: Map<String, Set<String>>,
    val staticLayout: Grid<LRStaticEntity>,
    val dynamicLayout: Grid<LabrecruitsEntity>,
    val allEntities: Set<LabrecruitsEntity>
) {
    fun isDoor(entityId: String): Boolean {
        return allEntities.any { it is LRDoor && it.id == entityId }
    }

    fun isButton(entityId: String): Boolean {
        return allEntities.any { it is LRButton && it.id == entityId }
    }

    fun entityLoc(entity: LREntityWithId): LRPos {
        return dynamicLayout.pos { it == entity }
    }

    fun isGoal(entityId: String): Boolean {
        return allEntities.any { it is LRGoal && it.id == entityId }

    }
}