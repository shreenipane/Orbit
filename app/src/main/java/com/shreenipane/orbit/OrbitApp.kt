package com.shreenipane.orbit

import android.app.Application
import com.shreenipane.orbit.data.Notifier
import com.shreenipane.orbit.data.Repository
import com.shreenipane.orbit.work.Work

class OrbitApp : Application() {
    val repository by lazy { Repository(this) }

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        Work.ensurePeriodic(this, repository.settings)
    }
}
