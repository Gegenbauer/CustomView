package me.gegenbauer.customview.entrance

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ManifestDemoCollector(private val context: Context) : DemoCollector {

    private val packageManager: PackageManager
        get() = context.packageManager

    override suspend fun collect(): List<Demo> {
        return withContext(Dispatchers.IO) {
            packageManager.getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES).run {
                activities?.mapNotNull { it.demoInfo }
            } ?: emptyList()
        }
    }

    private val ActivityInfo.demoInfo: Demo?
        get() {
            val metadata = packageManager.getActivityInfo(ComponentName(context, name), PackageManager.GET_META_DATA).metaData
            metadata?.getString(META_DATA_KEY_DEMO_CATEGORY)?.let { category ->
                metadata.getString(META_DATA_KEY_DEMO_NAME)?.let {name ->
                    return Demo(name, category, this.name)
                }
            }
            return null
        }

    companion object {
        private const val META_DATA_KEY_DEMO_CATEGORY = "demoCategory"
        private const val META_DATA_KEY_DEMO_NAME = "demoName"
    }
}