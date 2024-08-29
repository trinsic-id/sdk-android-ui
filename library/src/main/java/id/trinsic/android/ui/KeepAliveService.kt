package id.trinsic.android.ui

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

// I am not sure what this service is for -- I have only seen people assert _that_ this should be used with Custom Tabs, but no explanation anywhere for _why_.
// This is a cargo cult.
class KeepAliveService : Service() {
    companion object {
        val binder = Binder()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }
}