package com.fuelgo.app.domain.model

/** Fuel types the user can pre-configure. */
enum class FuelType(val displayName: String, val osmKey: String) {
    DIESEL("Diesel", "fuel:diesel"),
    PETROL("Petrol", "fuel:petrol"),
    CNG("CNG", "fuel:cng");

    companion object {
        fun fromName(name: String?): FuelType =
            entries.firstOrNull { it.name == name } ?: DIESEL
    }
}
