package com.poyal.perilog

import com.poyal.perilog.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class GuideTest {
    @Test fun allScreenHelpTargetsExistAndContainInstructions() {
        val ids=guideTopics.map {it.id}
        assertEquals(ids.size,ids.distinct().size)
        listOf("updates","settings/display","settings/lock","settings/notifications","settings/backup","settings/transfer","settings/protection","settings/reset","settings/basis","settings/palette","home","edit","records","recordTable","stats","stock","settings","about","products","product/id","templates","template/id",
            "appointments","appointment/id","appointmentDetail/id","appointmentStock/id","departments","department/id","careTemplates","care/id","contacts","contact/id","contacts/order","widgets",
            "requests","request/id","requestDetail/id","requestReceive/id","stockHistory/id","count/id","adjustment/id").forEach {route->
            assertTrue("Missing help for $route",guideForRoute(route) in ids)
        }
        listOf("settings/backup","settings/transfer","settings/protection","settings/reset").forEach {assertEquals("backup",guideForRoute(it))}
        assertEquals("updates",guideForRoute("updates"))
        assertEquals("calendar",guideForRoute("settings/calendar"))
        assertTrue("request-patterns" in ids && "manual" in ids && "backup" in ids)
        guideTopics.forEach { topic->assertTrue(topic.title.isNotBlank() && topic.steps.isNotEmpty());topic.steps.forEach {assertTrue(it.title.isNotBlank() && it.text.isNotBlank())} }
    }
    @Test fun offlineScreenshotsMatchTheActualDocumentedPngBytes() {
        val root=if(File("src/main/assets/guide").exists())File(".")else File("app")
        val docs=File(root,"../docs/screenshots")
        val names=guideTopics.flatMap {it.steps}.mapNotNull {it.image}.distinct()
        assertTrue(names.isNotEmpty())
        names.forEach {name->
            val asset=File(root,"src/main/assets/guide/$name.png")
            assertTrue("Missing bundled screenshot $name",asset.isFile)
            assertArrayEquals("Screenshot must be a real capture: $name",File(docs,"$name.png").readBytes(),asset.readBytes())
        }
    }
}
