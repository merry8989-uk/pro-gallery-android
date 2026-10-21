package com.progallery.app.ui.screens.webdav

import androidx.lifecycle.ViewModel
import com.progallery.app.data.webdav.WebDavCredentialStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Base64
import javax.inject.Inject

/** The header a player needs, or none when the server takes no sign-in. */
data class WebDavAuth(val header: String?)

/**
 * Resolves the sign-in for one server, so the player can fetch a file from it.
 *
 * The header is built here and handed straight to the player: the password is never put
 * in a navigation argument, where it would end up in a saved state bundle.
 */
@HiltViewModel
class WebDavPlayerViewModel @Inject constructor(
    private val store: WebDavCredentialStore,
) : ViewModel() {

    /** Null means the server is no longer saved. */
    suspend fun auth(serverId: String): WebDavAuth? {
        val credentials = store.credentials(serverId) ?: return null
        val user = credentials.server.username
        val password = credentials.password
        if (user.isEmpty() && password.isEmpty()) return WebDavAuth(null)
        val raw = "$user:$password".toByteArray(Charsets.UTF_8)
        return WebDavAuth("Basic " + Base64.getEncoder().encodeToString(raw))
    }
}
