package com.threescript.shared.model

import kotlinx.serialization.Serializable

/**
 * A single parsed scene token from a screenplay or narrative text.
 *
 * @property index     1-based scene number
 * @property title     Slugline or derived heading (e.g. "INT. OFFICE - DAY")
 * @property rawText   Full raw text of the scene block
 * @property wordCount Approximate word count of rawText
 */
@Serializable
data class SceneBlock(
    val index: Int,
    val title: String,
    val rawText: String,
    val wordCount: Int = rawText.split(Regex("\\s+")).size,
)
