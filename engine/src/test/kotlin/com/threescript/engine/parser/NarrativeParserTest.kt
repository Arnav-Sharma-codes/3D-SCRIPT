package com.threescript.engine.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NarrativeParserTest {

    @Test
    fun testMessySluglinesAndVanNormalization() {
        val script = """
            1 EXT. KIEV OPERA HOUSE - NIGHT 1
            The orchestra tunes up under the glittering chandeliers. Armed men in camouflage cut the power cables.
            
            2 /EXT. VAN - NIGHT 2
            The Protagonist sits beside his Ukrainian SWAT contact, checking his Beretta.
            
            3 INT. / EXT. HIGHWAY - DAY
            Cars speed across the bridge.
        """.trimIndent()

        val scenes = NarrativeParser.parse(script)
        assertEquals(3, scenes.size, "Should identify all 3 scenes despite numbered headings and split prefixes")

        assertEquals("EXT. KIEV OPERA HOUSE - NIGHT", scenes[0].title)
        assertTrue(scenes[0].rawText.contains("orchestra tunes up"))

        assertEquals("INT./EXT. VAN - NIGHT", scenes[1].title, "Should fix /EXT. VAN to INT./EXT. VAN")
        assertTrue(scenes[1].rawText.contains("The Protagonist sits beside"))

        assertEquals("INT./EXT. HIGHWAY - DAY", scenes[2].title, "Should normalize INT. / EXT. to INT./EXT.")
    }

    @Test
    fun testPrologueNotDropped() {
        val script = """
            An ominous drone reverberates across a black screen.
            Suddenly, a packed auditorium comes into view as hundreds of classical music lovers take their seats.
            
            1 INT. OPERA HOUSE - NIGHT
            The conductor raises his baton.
        """.trimIndent()

        val scenes = NarrativeParser.parse(script)
        assertEquals(2, scenes.size, "Prologue should be captured as Scene 1")
        assertEquals(1, scenes[0].index)
        assertTrue(scenes[0].rawText.contains("packed auditorium"))
        assertEquals(2, scenes[1].index)
        assertEquals("INT. OPERA HOUSE - NIGHT", scenes[1].title)
    }

    @Test
    fun testCleanSceneTitleHelper() {
        assertEquals("INT./EXT. VAN", NarrativeParser.cleanSceneTitle("/EXT. VAN"))
        assertEquals("INT./EXT. VAN - NIGHT", NarrativeParser.cleanSceneTitle("2 /EXT. VAN - NIGHT 2"))
        assertEquals("EXT. KIEV OPERA HOUSE - NIGHT", NarrativeParser.cleanSceneTitle("1 EXT. KIEV OPERA HOUSE - NIGHT 1"))
        assertEquals("INT./EXT. HIGHWAY", NarrativeParser.cleanSceneTitle("INT. / EXT. HIGHWAY"))
    }
}
