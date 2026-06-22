package com.offlinevault

import android.app.Application
import com.offlinevault.security.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OfflineVaultApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applicationScope.launch {
            SessionManager.unlocked.filter { it }.collect {
                withContext(Dispatchers.IO) {
                    try {
                        container.vaultRepository.migrateLegacyMetadata()
                        container.passwordRepository.migrateLegacyMetadata()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // A damaged legacy row must not terminate the application-wide collector.
                    }
                }
            }
        }
    }
}
