package com.heixss.cubewars.domain.model

/** Axis-aligned bounding box anchored at its bottom-left corner. */
data class Aabb(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    val left: Float get() = x
    val right: Float get() = x + width
    val bottom: Float get() = y
    val top: Float get() = y + height
    val centerX: Float get() = x + width / 2f
    val centerY: Float get() = y + height / 2f
    val center: Vec2 get() = Vec2(centerX, centerY)

    fun overlaps(other: Aabb): Boolean =
        left < other.right && right > other.left && bottom < other.top && top > other.bottom

    companion object {
        /** Builds a box of [size] x [size] whose bottom-left corner sits at [position]. */
        fun square(position: Vec2, size: Float): Aabb = Aabb(position.x, position.y, size, size)

        /** Builds a box of [size] x [size] centred on [center]. */
        fun centeredSquare(center: Vec2, size: Float): Aabb =
            Aabb(center.x - size / 2f, center.y - size / 2f, size, size)
    }
}
