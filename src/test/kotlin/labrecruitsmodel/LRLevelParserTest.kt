package labrecruitsmodel

import org.junit.Test
import org.junit.jupiter.api.Assertions.*

internal class LRLevelParserTest {


    @Test
    fun testParse() {
        val parsed = LRLevelParser.parse(
            "button0,door0,\n" +
                    "|w,w,w\n" +
                    "w,f:b^button0,w\n" +
                    "w,f:a^agent0,w\n" +
                    "w,f:d>s^door0,w\n" +
                    "w,f:g^flag0,w\n" +
                    "w,w,w"
        )
        println(parsed.actuators)
        println(parsed.dynamicLayout)
        println(parsed.staticLayout)
    }
}