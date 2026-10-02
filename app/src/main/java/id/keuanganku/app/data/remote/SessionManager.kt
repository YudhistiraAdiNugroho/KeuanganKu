package id.keuanganku.app.data.remote

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionStore by preferencesDataStore("session")

/** Menyimpan token Supabase secara lokal (DataStore Preferences). */
class SessionManager(private val context: Context) {
    companion object {
        private val ACCESS = stringPreferencesKey("access_token")
        private val REFRESH = stringPreferencesKey("refresh_token")
        private val USER_ID = stringPreferencesKey("user_id")
        private val USER_EMAIL = stringPreferencesKey("user_email")
        private val USER_NAME = stringPreferencesKey("user_name")
    }

    val accessToken: Flow<String?> = context.sessionStore.data.map { it[ACCESS] }
    val isLoggedIn: Flow<Boolean> = accessToken.map { !it.isNullOrBlank() }

    suspend fun save(access: String, refresh: String, userId: String, email: String, name: String) {
        context.sessionStore.edit {
            it[ACCESS] = access
            it[REFRESH] = refresh
            it[USER_ID] = userId
            it[USER_EMAIL] = email
            it[USER_NAME] = name
        }
    }

    suspend fun updateTokens(access: String, refresh: String) {
        context.sessionStore.edit { it[ACCESS] = access; it[REFRESH] = refresh }
    }

    suspend fun getAccess(): String? = context.sessionStore.data.map { it[ACCESS] }.first()
    suspend fun getRefresh(): String? = context.sessionStore.data.map { it[REFRESH] }.first()
    suspend fun getUserId(): String? = context.sessionStore.data.map { it[USER_ID] }.first()
    suspend fun getUserName(): String? = context.sessionStore.data.map { it[USER_NAME] }.first()

    suspend fun clear() { context.sessionStore.edit { it.clear() } }
}
