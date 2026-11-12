package com.onlyfinal.alarmasfl

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object SflWork {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SflWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("sfl_price_check", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
