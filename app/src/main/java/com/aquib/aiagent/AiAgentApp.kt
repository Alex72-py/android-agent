package com.aquib.aiagent

import android.app.Application
import com.aquib.aiagent.util.NotificationHelper

class AiAgentApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }
}
