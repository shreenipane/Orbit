package com.shreenipane.orbit.data

/** Orbit is built for VIT Vellore only. */
enum class Campus(val id: String, val label: String, val baseUrl: String) {
    VELLORE("vellore", "Vellore", "https://vtop.vit.ac.in");

    val loginUrl get() = "$baseUrl/vtop/open/page"

    companion object {
        fun byId(id: String?) = entries.firstOrNull { it.id == id } ?: VELLORE
    }
}
