package com.lumina.player

import android.app.Application
import com.lumina.player.data.HistoryStore
import com.lumina.player.data.MediaRepository
import com.lumina.player.data.SettingsStore
import com.lumina.player.playback.PlayerEngine

class LuminaApp : Application() {

    lateinit var settings: SettingsStore
        private set
    lateinit var history: HistoryStore
        private set
    lateinit var repository: MediaRepository
        private set

    /** libVLC yerel kütüphaneleri ağırdır; motor ilk ihtiyaçta (ana iş parçacığında) kurulur. */
    val engine: PlayerEngine by lazy { PlayerEngine(this, settings, history) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsStore(this)
        history = HistoryStore(this)
        repository = MediaRepository(this, settings)
    }

    companion object {
        lateinit var instance: LuminaApp
            private set
    }
}
