package com.shreenipane.orbit.data

/** All VIT campuses run the same VTOP software on different hosts. */
enum class Campus(val id: String, val label: String, val baseUrl: String) {
    VELLORE("vellore", "Vellore", "https://vtop.vit.ac.in"),
    CHENNAI("chennai", "Chennai", "https://vtopcc.vit.ac.in"),
    AP("ap", "Amaravati (AP)", "https://vtop.vitap.ac.in"),
    BHOPAL("bhopal", "Bhopal", "https://vtop.vitbhopal.ac.in");

    val loginUrl get() = "$baseUrl/vtop/open/page"

    companion object {
        fun byId(id: String?) = entries.firstOrNull { it.id == id } ?: VELLORE
    }
}
