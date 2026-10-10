package com.example.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.SelfControlApp
import com.example.service.vpn.SelfControlVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            CoroutineScope(Dispatchers.IO).launch {
                val masterProtection = SelfControlApp.repository.preferences.masterProtection.first()
                val vpnEnabled = SelfControlApp.repository.preferences.vpnEnabled.first()

                if (masterProtection && vpnEnabled) {
                    SelfControlVpnService.startVpn(context)
                }
            }
        }
    }
}
