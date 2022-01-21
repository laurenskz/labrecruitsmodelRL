package labrecruitsmodel

import eu.iv4xr.framework.model.distribution.*
import eu.iv4xr.framework.utils.cons
import kotlin.math.pow
import kotlin.math.sqrt

fun actions(pos: LRPos): List<LRPos> {
    val (x, y) = pos
    return listOf(
        x - 1 to y,
        x + 1 to y,
        x to y - 1,
        x to y + 1
    )
}


fun updateKnownDoors(state: LabRecruitsState, config: LabRecruitsLevelConfig): Distribution<LabRecruitsState> {
    val maps = state.knownEntities.filter { config.isDoor(it) }.map { entity ->
        if (state.entityStates[entity] == DoorState.UNCERTAIN) {
            if_(flip(0.5), mapOf(entity to DoorState.CLOSED), mapOf(entity to DoorState.OPEN))
        } else always(mapOf(entity to state.entityStates[entity]!!))
    }
    val final = maps.foldD(always(mapOf<String, DoorState>())) { a, c ->
        a + c
    }
    return final.map { state.copy(entityStates = state.entityStates + it) }
}

fun squaresAround(entityId: String, config: LabRecruitsLevelConfig): Distribution<LRPos> {
    val loc = entityLocation(config, entityId)
    val availableAround = actions(loc).filter {
        config.staticLayout.get(it) == LRStaticEntity.FLOOR
    }
    return Distributions.uniform(loc cons availableAround)
}

private fun entityLocation(config: LabRecruitsLevelConfig, entityId: String): LRPos {
    return config.dynamicLayout.pos { (it as? LREntityWithId)?.id == entityId }
}


fun doorDist(doors: Map<String, DoorState>): Distribution<Map<String, DoorState>> {
    val states = doors
        .mapValues {
            if (it.value == DoorState.UNCERTAIN) if_(
                flip(0.5),
                it.key to DoorState.OPEN,
                it.key to DoorState.CLOSED
            ) else always(
                it.key to it.value
            )
        }.values.toList()
    return states.foldD(always(mapOf())) { a, e -> a + e }
}

fun visibleDoors(
    state: LabRecruitsState,
    config: LabRecruitsLevelConfig
): Set<String> {
    val doors = config.allEntities.filterIsInstance(LRDoor::class.java)
    return doors.filter {
        val doorPos = config.entityLoc(it)
        clearLineOfSight(state.pos, doorPos, state, config)
    }.map { it.id }.toSet()
}

fun doorVisible(id: String, state: LabRecruitsState, config: LabRecruitsLevelConfig): Distribution<Boolean> {
    val door = config.allEntities.first { it is LRDoor && it.id == id }
    val doorPos = config.dynamicLayout.pos { it == door }
    if (dist(state.pos, doorPos) > 10) return always(false)
    val blockedPositions = positionsBetween(state.pos, doorPos).count {
        it != doorPos && !clearDoorSight(it, state, config)
    }
    return when (blockedPositions) {
        0 -> always(true)
//        1 -> flip(0.3)
//        2 -> flip(0.2)
//        3 -> flip(0.1)
        else -> flip(0.001)
    }
}

fun dist(a: LRPos, b: LRPos): Double {
    return sqrt((a.first - b.first).toDouble().pow(2) + (a.second - b.second).toDouble().pow(2))
}

fun clearLineOfSight(
    a: LRPos,
    b: LRPos,
    state: LabRecruitsState,
    config: LabRecruitsLevelConfig,
    viewDist: Int = 10
): Boolean {
    return dist(a, b) < viewDist && positionsBetween(a, b).all {
        it == b || clearDoorSight(it, state, config)

    }
}

fun clearDoorSight(pos: LRPos, state: LabRecruitsState, config: LabRecruitsLevelConfig): Boolean {
    return (config.staticLayout.get(pos) == LRStaticEntity.FLOOR
            && !isBlockingDoor(pos, state, config))
}

fun isBlockingDoor(
    pos: LRPos, state: LabRecruitsState,
    config: LabRecruitsLevelConfig
): Boolean {
    val entity = config.dynamicLayout.get(pos)
    return entity is LRDoor && (state.entityStates[entity.id] != DoorState.OPEN)
}

fun positionsBetween(a: LRPos, b: LRPos): Sequence<LRPos> {
    val (x1, y1) = a
    val (x2, y2) = b
    return sequence<LRPos> {
        var i: Int;               // loop counter
        var ystep: Int
        var xstep: Int;    // the step on y and x axis
        var error: Int;           // the error accumulated during the increment
        var errorprev: Int;       // *vision the previous value of the error variable
        var y: Int = y1
        var x: Int = x1;  // the line povars: Int
        var ddy: Int
        var ddx: Int;        // compulsory variables: the double values of dy and dx
        var dx: Int = x2 - x1;
        var dy: Int = y2 - y1;
        yield(y1 to x1);  // first point
        // NB the last point can't be here, because of its previous point (which has to be verified)
        if (dy < 0) {
            ystep = -1;
            dy = -dy;
        } else
            ystep = 1;
        if (dx < 0) {
            xstep = -1;
            dx = -dx;
        } else
            xstep = 1;
        ddy = 2 * dy;  // work with double values for full precision
        ddx = 2 * dx;
        if (ddx >= ddy) {  // first octant (0 <= slope <= 1)
            error = dx;  // start in the middle of the square
            errorprev = error

            // compulsory initialization (even for errorprev, needed when dx==dy)
            for (i in 0 until dx) {  // do not use the first point (already done)
                x += xstep;
                error += ddy;
                if (error > ddx) {  // increment y if AFTER the middle ( > )
                    y += ystep;
                    error -= ddx;
                    // three cases (octant == right->right-top for directions below):
                    if (error + errorprev < ddx)  // bottom square also
                        yield(y - ystep to x);
                    else if (error + errorprev > ddx)  // left square also
                        yield(y to x - xstep);
                    else {  // corner: bottom and left squares also
                        yield(y - ystep to x);
                        yield(y to x - xstep);
                    }
                }
                yield(y to x);
                errorprev = error;
            }
        } else {  // the same as above
            error = dy;
            errorprev = error
            for (i in 0 until dy) {
                y += ystep;
                error += ddx;
                if (error > ddy) {
                    x += xstep;
                    error -= ddy;
                    if (error + errorprev < ddy)
                        yield(y to x - xstep);
                    else if (error + errorprev > ddy)
                        yield(y - ystep to x);
                    else {
                        yield(y to x - xstep);
                        yield(y - ystep to x);
                    }
                }
                yield(y to x);
                errorprev = error;
            }
        }

    }.map {
        it.second to it.first
    }
}

fun labBeliefToReal(labRecruitsState: LabRecruitsState): Distribution<LabRecruitsState> {
    return doorDist(labRecruitsState.entityStates).map {
        labRecruitsState.copy(entityStates = labRecruitsState.entityStates + it)
    }
}

fun explore(labRecruitsState: LabRecruitsState, labRecruitsLevelConfig: LabRecruitsLevelConfig): LabRecruitsState {
    val visited = mutableSetOf<LRPos>()
    val knownEntities = mutableSetOf<String>()
    dfs(labRecruitsState, labRecruitsLevelConfig, visited, knownEntities)
    return labRecruitsState.copy(
        knownEntities = knownEntities
    )
}

fun dfs(
    labRecruitsState: LabRecruitsState,
    labRecruitsLevelConfig: LabRecruitsLevelConfig,
    visited: MutableSet<LRPos>,
    knownEntities: MutableSet<String>
) {
    visited.add(labRecruitsState.pos)
    for (pos in actions(labRecruitsState.pos)) {
        if (pos in visited) continue
        val entity = labRecruitsLevelConfig.dynamicLayout.get(pos)
        if (entity is LREntityWithId) {
            knownEntities.add(entity.id)
        }
        if (entity is LRDoor) {
            if (labRecruitsState.entityStates[entity.id] != DoorState.OPEN) {
                continue
            }
        }
        if (labRecruitsLevelConfig.staticLayout.get(pos) != LRStaticEntity.FLOOR) {
            continue
        }
        dfs(labRecruitsState.copy(pos = pos), labRecruitsLevelConfig, visited, knownEntities)
    }

}