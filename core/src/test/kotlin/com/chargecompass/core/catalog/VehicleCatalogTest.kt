package com.chargecompass.core.catalog

import com.chargecompass.core.model.Connector
import com.chargecompass.core.model.VehicleModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VehicleCatalogTest {
    private fun model(id: String, vararg prefixes: String) = VehicleModel(
        id = id, name = id, batteryKwh = 77.4, baseEfficiencyKmPerKwh = 5.0,
        connectors = setOf(Connector.DC_COMBO), vinPrefixes = prefixes.toList(),
    )

    private val ioniq5 = model("ioniq5", "KMHK3")
    private val ioniq5Long = model("ioniq5-long", "KMHK38")
    private val catalog = VehicleCatalog(listOf(ioniq5, ioniq5Long, model("ev6", "KNAC8")))

    @Test
    fun `VIN 접두어로 차종을 찾는다`() {
        assertEquals("ev6", catalog.byVin("KNAC81ABCDE123456")?.id)
    }

    @Test
    fun `접두어가 겹치면 더 긴 접두어가 이긴다`() {
        assertEquals("ioniq5-long", catalog.byVin("KMHK381ABCDE12345")?.id)
        assertEquals("ioniq5", catalog.byVin("KMHK371ABCDE12345")?.id)
    }

    @Test
    fun `VIN이 없거나 맞는 접두어가 없으면 null`() {
        assertNull(catalog.byVin(null))
        assertNull(catalog.byVin("\"invalid\""))
        assertNull(catalog.byVin("WBA12345678901234"))
    }

    @Test
    fun `id로 차종을 찾는다`() {
        assertEquals(ioniq5, catalog.byId("ioniq5"))
        assertNull(catalog.byId("none"))
        assertEquals(3, catalog.all.size)
    }
}
