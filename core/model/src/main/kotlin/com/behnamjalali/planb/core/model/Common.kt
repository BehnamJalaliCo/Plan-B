package com.behnamjalali.planb.core.model

/** Persistent records use auto-generated Long identifiers; 0 means "not yet stored". */
typealias EntityId = Long

const val NEW_ID: EntityId = 0L

/**
 * Controlled accent palette. Stored by [key] so the display colors can evolve
 * (and adapt to dark mode) without migrating user data.
 */
enum class AccentColor(val key: String) {
    LAVENDER("lavender"),
    MINT("mint"),
    PEACH("peach"),
    POWDER_BLUE("powder_blue"),
    ROSE("rose"),
    SAND("sand"),
    SAGE("sage"),
    SLATE("slate"),
    ;

    companion object {
        fun fromKey(key: String?): AccentColor = entries.firstOrNull { it.key == key } ?: LAVENDER
    }
}

/** Curated icon set; stored by key so icons stay replaceable. */
enum class PlannerIcon(val key: String) {
    FOLDER("folder"),
    BRIEFCASE("briefcase"),
    BOOK("book"),
    HOME("home"),
    HEART("heart"),
    STAR("star"),
    ROCKET("rocket"),
    LIGHTBULB("lightbulb"),
    SCHOOL("school"),
    FITNESS("fitness"),
    WATER("water"),
    MEDITATION("meditation"),
    RUN("run"),
    SLEEP("sleep"),
    CODE("code"),
    PALETTE("palette"),
    MUSIC("music"),
    TRAVEL("travel"),
    MONEY("money"),
    LEAF("leaf"),
    ;

    companion object {
        fun fromKey(key: String?): PlannerIcon = entries.firstOrNull { it.key == key } ?: FOLDER
    }
}

enum class Priority(val weight: Int) {
    NONE(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    ;

    companion object {
        fun fromWeight(weight: Int): Priority = entries.firstOrNull { it.weight == weight } ?: NONE
    }
}
