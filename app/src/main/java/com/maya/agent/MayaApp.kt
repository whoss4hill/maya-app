package com.maya.agent

import android.app.Application
import android.content.Context
import com.maya.agent.data.MemoryRepository
import com.maya.agent.data.auth.AuthRepository
import com.maya.agent.data.vault.VaultRepository
import com.maya.agent.voice.VoiceController

/** Simple manual service locator - no DI framework needed at this size. */
class MayaApp : Application() {

    lateinit var auth: AuthRepository
        private set
    lateinit var memories: MemoryRepository
        private set
    lateinit var vault: VaultRepository
        private set
    lateinit var voice: VoiceController
        private set

    override fun onCreate() {
        super.onCreate()
        auth = AuthRepository(this)
        memories = MemoryRepository(auth)
        vault = VaultRepository(this)
        voice = VoiceController(this)
        voice.initTts()
    }

    companion object {
        fun from(context: Context): MayaApp = context.applicationContext as MayaApp
    }
}
