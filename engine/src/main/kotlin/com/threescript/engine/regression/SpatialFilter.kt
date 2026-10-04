package com.threescript.engine.regression

import com.threescript.shared.model.VectorPoint

/**
 * SpatialFilter — optional linear regression blend for score points.
 *
 * FILTER FORMULA (per axis, per scene):
 *   score_filtered = score_raw + α × (score_predicted − score_raw)
 *
 * Where:
 *   α (alpha)         = blend factor (default 0.35)
 *   score_raw         = raw value from AI scoring
 *   score_predicted   = OLS regression predicted value at scene t
 *
 * Effect:
 *   α = 0.0  → pure raw AI scores (unfiltered)
 *   α = 0.35 → light smoothing, preserves dramatic shape, removes LLM noise
 *   α = 1.0  → pure regression line (maximum smoothing)
 *
 * Each axis (X, Y, Z) is filtered independently so that their individual
 * dramatic arcs are preserved while noise is reduced.
 */
object SpatialFilter {

    /**
     * Apply regression blending to a list of raw vector points.
     *
     * @param rawPoints  Unfiltered vector points (xRaw/yRaw/zRaw populated)
     * @param alpha      Blend factor in [0.0, 1.0]. Default = 0.35
     * @return New list of [VectorPoint]s with x/y/z set to filtered values
     */
    fun filter(rawPoints: List<VectorPoint>, alpha: Double = 0.35): List<VectorPoint> {
        if (rawPoints.size < 2) {
            // A single scene has no regression baseline, so return its raw score.
            return rawPoints.map { it.copy(x = it.xRaw, y = it.yRaw, z = it.zRaw) }
        }

        val xs = rawPoints.map { it.xRaw }
        val ys = rawPoints.map { it.yRaw }
        val zs = rawPoints.map { it.zRaw }

        val xFit = LinearRegression.fit(xs)
        val yFit = LinearRegression.fit(ys)
        val zFit = LinearRegression.fit(zs)

        return rawPoints.mapIndexed { i, pt ->
            val xFiltered = blend(pt.xRaw, xFit.predicted[i], alpha)
            val yFiltered = blend(pt.yRaw, yFit.predicted[i], alpha)
            val zFiltered = blend(pt.zRaw, zFit.predicted[i], alpha)
            pt.copy(x = xFiltered, y = yFiltered, z = zFiltered)
        }
    }

    // score_raw + α × (score_pred − score_raw)
    private fun blend(raw: Double, predicted: Double, alpha: Double): Double =
        (raw + alpha * (predicted - raw)).coerceIn(0.0, 10.0)
}
