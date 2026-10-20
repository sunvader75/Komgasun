package com.sunvader.komgasun

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.sunvader.komgasun.data.local.PreferencesManager
import com.sunvader.komgasun.data.model.ServerConfig
import com.sunvader.komgasun.data.remote.KomgaApiClient
import com.sunvader.komgasun.data.repository.KomgaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class KomgasunApp : Application(), ImageLoaderFactory {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val cachedServerConfig = AtomicReference<ServerConfig?>(null)

    lateinit var preferencesManager: PreferencesManager
        private set
    lateinit var apiClient: KomgaApiClient
        private set
    lateinit var repository: KomgaRepository
        private set

    /**
     * Inicializa las dependencias compartidas y observa la configuración durante la vida del proceso.
     * La referencia atómica permite al interceptor de imágenes consultar la última configuración
     * recibida sin bloquear su hilo ni leer las preferencias en cada petición.
     */
    override fun onCreate() {
        super.onCreate()
        instance = this

        preferencesManager = PreferencesManager(this)
        apiClient = KomgaApiClient()
        repository = KomgaRepository(preferencesManager, apiClient)

        applicationScope.launch {
            preferencesManager.serverConfigFlow.collectLatest { config ->
                cachedServerConfig.set(config)
            }
        }
    }

    /**
     * Crea el cargador de Coil con tiempos de conexión/lectura de 30/60 segundos y fundido de imágenes.
     * Añade autenticación básica desde la configuración en memoria solo si la petición no incluye
     * Authorization. No filtra por destino: esta política se aplica a las peticiones del cargador.
     */
    override fun newImageLoader(): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val config = cachedServerConfig.get()

                val requestBuilder = originalRequest.newBuilder()
                if (config != null && originalRequest.header("Authorization") == null) {
                    val credential = Credentials.basic(config.username, config.password)
                    requestBuilder.header("Authorization", credential)
                }

                chain.proceed(requestBuilder.build())
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .crossfade(true)
            .build()
    }

    companion object {
        lateinit var instance: KomgasunApp
            private set
    }
}
