package com.chargecompass.core.catalog

import com.chargecompass.core.model.VehicleModel

class VehicleCatalog(models: List<VehicleModel>) {
    val all: List<VehicleModel> = models.toList()

    /** 접두어가 여러 개 맞으면 가장 긴 접두어의 차종. 에뮬레이터 VIN은 "invalid"라 null이 된다. */
    fun byVin(vin: String?): VehicleModel? {
        if (vin == null) return null
        return all.flatMap { m -> m.vinPrefixes.filter { vin.startsWith(it) }.map { it.length to m } }
            .maxByOrNull { it.first }?.second
    }

    fun byId(id: String): VehicleModel? = all.firstOrNull { it.id == id }
}
