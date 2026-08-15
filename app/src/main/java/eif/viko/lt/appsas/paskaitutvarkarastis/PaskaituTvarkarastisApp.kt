package eif.viko.lt.appsas.paskaitutvarkarastis

import android.app.Application
import eif.viko.lt.appsas.paskaitutvarkarastis.glance.TimetableSyncWorker

class PaskaituTvarkarastisApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // KEEP, so an already-scheduled sync survives a restart instead of resetting its period.
        TimetableSyncWorker.enqueue(this)
    }
}
