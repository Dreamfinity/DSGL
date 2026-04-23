package org.dreamfinity.dsgl.core.dom.layout

import kotlin.math.cos
import kotlin.math.sin

/**
 * 2D affine transform:
 * x' = a*x + c*y + tx
 * y' = b*x + d*y + ty
 */
data class AffineTransform2D(
    val a: Float,
    val b: Float,
    val c: Float,
    val d: Float,
    val tx: Float,
    val ty: Float,
) {
    fun transform(x: Float, y: Float): Pair<Float, Float> =
        Pair(
            a * x + c * y + tx,
            b * x + d * y + ty,
        )

    fun times(other: AffineTransform2D): AffineTransform2D =
        AffineTransform2D(
            a = a * other.a + c * other.b,
            b = b * other.a + d * other.b,
            c = a * other.c + c * other.d,
            d = b * other.c + d * other.d,
            tx = a * other.tx + c * other.ty + tx,
            ty = b * other.tx + d * other.ty + ty,
        )

    fun inverseOrNull(): AffineTransform2D? {
        val det = a * d - b * c
        if (det == 0f) return null
        val invDet = 1f / det
        val na = d * invDet
        val nb = -b * invDet
        val nc = -c * invDet
        val nd = a * invDet
        val ntx = -(na * tx + nc * ty)
        val nty = -(nb * tx + nd * ty)
        return AffineTransform2D(na, nb, nc, nd, ntx, nty)
    }

    companion object {
        val IDENTITY: AffineTransform2D =
            AffineTransform2D(
                a = 1f,
                b = 0f,
                c = 0f,
                d = 1f,
                tx = 0f,
                ty = 0f,
            )

        fun translation(x: Float, y: Float): AffineTransform2D = AffineTransform2D(1f, 0f, 0f, 1f, x, y)

        fun scale(x: Float, y: Float): AffineTransform2D = AffineTransform2D(x, 0f, 0f, y, 0f, 0f)

        fun rotation(deg: Float): AffineTransform2D {
            val rad = Math.toRadians(deg.toDouble())
            val cos = cos(rad).toFloat()
            val sin = sin(rad).toFloat()
            return AffineTransform2D(cos, sin, -sin, cos, 0f, 0f)
        }
    }
}
