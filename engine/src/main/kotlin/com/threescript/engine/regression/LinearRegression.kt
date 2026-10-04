package com.threescript.engine.regression

import com.threescript.shared.model.AxisStats
import kotlin.math.sqrt

/**
 * LinearRegression — Ordinary Least Squares (OLS) single-variable regressor.
 *
 * Fits the model:  score(t) = β₀ + β₁·t
 * where t = scene index (1, 2, ..., n).
 *
 * This is a pure Kotlin implementation with zero external math dependencies,
 * ensuring complete engine portability and auditability.
 */
object LinearRegression {

    /**
     * Fit an OLS line to a sequence of (t, y) pairs.
     *
     * @param values  Ordered list of axis scores (one per scene, t is implicit 1..n)
     * @return [RegressionResult] containing β₀, β₁ and predicted values
     */
    fun fit(values: List<Double>): RegressionResult {
        val n = values.size
        require(n >= 2) { "Need at least 2 data points for regression" }

        val ts = (1..n).map { it.toDouble() }  // t = 1, 2, ..., n

        val tMean = ts.mean()
        val yMean = values.mean()

        // β₁ = Σ((t - t̄)(y - ȳ)) / Σ((t - t̄)²)
        var numerator   = 0.0
        var denominator = 0.0
        for (i in ts.indices) {
            val dt = ts[i] - tMean
            numerator   += dt * (values[i] - yMean)
            denominator += dt * dt
        }

        val beta1 = if (denominator == 0.0) 0.0 else numerator / denominator
        val beta0 = yMean - beta1 * tMean

        val predicted = ts.map { t -> (beta0 + beta1 * t).coerceIn(0.0, 10.0) }

        // R² for diagnostics
        val ssTot = values.sumOf { y -> (y - yMean) * (y - yMean) }
        val ssRes = values.indices.sumOf { i -> (values[i] - predicted[i]).let { it * it } }
        val rSquared = if (ssTot == 0.0) 1.0 else 1.0 - ssRes / ssTot

        return RegressionResult(
            beta0 = beta0,
            beta1 = beta1,
            predicted = predicted,
            rSquared = rSquared,
            rmse = sqrt(ssRes / n),
        )
    }

    /**
     * Build [AxisStats] from a list of values using OLS.
     */
    fun axisStats(values: List<Double>): AxisStats {
        val fit = if (values.size >= 2) fit(values) else
            RegressionResult(values.firstOrNull() ?: 0.0, 0.0, values, 1.0, 0.0)
        return AxisStats(
            min = values.minOrNull() ?: 0.0,
            max = values.maxOrNull() ?: 0.0,
            mean = values.mean(),
            regressionSlope = fit.beta1,
            regressionIntercept = fit.beta0,
        )
    }

    private fun List<Double>.mean(): Double = if (isEmpty()) 0.0 else sum() / size
}

/**
 * Result of an OLS fit on a single axis.
 *
 * @property beta0      Intercept (y-value when t = 0)
 * @property beta1      Slope (positive = rising arc, negative = falling)
 * @property predicted  Predicted value at each scene position
 * @property rSquared   Coefficient of determination (0–1)
 * @property rmse       Root mean squared error
 */
data class RegressionResult(
    val beta0: Double,
    val beta1: Double,
    val predicted: List<Double>,
    val rSquared: Double,
    val rmse: Double,
)
