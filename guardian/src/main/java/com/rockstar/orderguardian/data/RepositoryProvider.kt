package com.rockstar.orderguardian.data

import android.content.Context
import com.rockstar.orderguardian.BuildConfig
import com.rockstar.orderguardian.shared.ApprovalRepository

/** Single place that decides which approval backend this build uses. */
object RepositoryProvider {

    @Volatile
    private var instance: ApprovalRepository? = null

    fun get(context: Context): ApprovalRepository {
        instance?.let { return it }
        synchronized(this) {
            instance?.let { return it }
            val created: ApprovalRepository = if (BuildConfig.FIREBASE_CONFIGURED) {
                runCatching { FirestoreApprovalRepository() }
                    .getOrElse { LocalApprovalRepository(context.applicationContext) }
            } else {
                LocalApprovalRepository(context.applicationContext)
            }
            instance = created
            return created
        }
    }

    /** True when requests can actually leave this phone (Firebase configured at build time). */
    val isLiveMode: Boolean get() = BuildConfig.FIREBASE_CONFIGURED
}
