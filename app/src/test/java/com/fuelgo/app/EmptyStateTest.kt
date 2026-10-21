package com.fuelgo.app

import com.fuelgo.app.ui.EmptyStateAction
import com.fuelgo.app.ui.LastOutcome
import com.fuelgo.app.ui.emptyStateFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The four cold-start states (Fix 4) must map distinctly and stably. */
class EmptyStateTest {

    @Test
    fun `state a - permission missing - grant action`() {
        val ui = emptyStateFor(hasPermission = false, hasFix = false, loading = false, outcome = null)
        assertEquals(com.fuelgo.app.ui.EmptyStateKind.PERMISSION_MISSING, ui?.kind)
        assertEquals(EmptyStateAction.GRANT, ui?.primaryAction)
    }

    @Test
    fun `state b - permission granted no fix loading - getting your location`() {
        val loading = emptyStateFor(hasPermission = true, hasFix = false, loading = true, outcome = null)
        assertEquals(com.fuelgo.app.ui.EmptyStateKind.WAITING_LOCATION, loading?.kind)
        val idle = emptyStateFor(hasPermission = true, hasFix = false, loading = false, outcome = null)
        assertEquals(com.fuelgo.app.ui.EmptyStateKind.WAITING_LOCATION, idle?.kind)
        // Failed refresh without a fix still reads as a location problem.
        val failed = emptyStateFor(hasPermission = true, hasFix = false, loading = false, outcome = LastOutcome.PROVIDERS_FAILED)
        assertEquals(com.fuelgo.app.ui.EmptyStateKind.WAITING_LOCATION, failed?.kind)
        assertEquals(EmptyStateAction.RETRY, failed?.primaryAction)
    }

    @Test
    fun `state c - fix available providers failed - retry action`() {
        val ui = emptyStateFor(hasPermission = true, hasFix = true, loading = false, outcome = LastOutcome.PROVIDERS_FAILED)
        assertEquals(com.fuelgo.app.ui.EmptyStateKind.PROVIDERS_FAILED, ui?.kind)
        assertEquals(EmptyStateAction.RETRY, ui?.primaryAction)
    }

    @Test
    fun `state d - providers ok zero stations - honest notice with retry`() {
        val ui = emptyStateFor(hasPermission = true, hasFix = true, loading = false, outcome = LastOutcome.OK_EMPTY)
        assertEquals(com.fuelgo.app.ui.EmptyStateKind.NO_STATIONS_FOUND, ui?.kind)
        assertEquals(
            "No fuel stations found within 10 km.",
            ui?.title,
        )
    }

    @Test
    fun `permission not yet resolved shows nothing`() {
        assertNull(emptyStateFor(hasPermission = null, hasFix = false, loading = false, outcome = null))
    }

    @Test
    fun `data present shows nothing`() {
        // (Loaded via the regular list — no empty state at all.)
        assertNull(emptyStateFor(hasPermission = true, hasFix = true, loading = false, outcome = LastOutcome.OK_DATA))
    }
}
