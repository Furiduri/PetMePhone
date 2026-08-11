package com.gcatcode.petmephone

import com.gcatcode.petmephone.feature.overlay.character.ValidatedImport

/**
 * Where the app can be.
 *
 * [MenuDestination] is the subset the side menu offers. Preview is deliberately not one: it is a
 * step inside the import flow that only exists once there is something to preview, and a menu entry
 * that is unreachable most of the time is worse than no entry.
 *
 * Nothing here is listed before it works. The task list, statistics and journal that slices 3–7
 * plan are absent rather than disabled, because a menu row that opens nothing is a promise the app
 * cannot keep.
 */
sealed interface Destination {
    data object Pet : Destination, MenuDestination {
        override val label = "Pet"
    }

    data object Characters : Destination, MenuDestination {
        override val label = "Characters"
    }

    data object Import : Destination, MenuDestination {
        override val label = "Import"
    }

    data object Permission : Destination, MenuDestination {
        override val label = "Permission"
    }

    data class Preview(val import: ValidatedImport) : Destination
}

/** A destination the side menu can navigate to. */
sealed interface MenuDestination {
    val label: String
}

/** Menu order, top to bottom. Pet is first because it is where the app opens. */
val MENU_DESTINATIONS: List<Destination> = listOf(
    Destination.Pet,
    Destination.Characters,
    Destination.Import,
    Destination.Permission,
)
