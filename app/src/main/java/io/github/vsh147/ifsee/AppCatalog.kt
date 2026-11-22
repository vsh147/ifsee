package io.github.vsh147.ifsee

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(val label: String, val packageName: String)

object AppCatalog {

    private val cacheSize = (Runtime.getRuntime().maxMemory() / 8 / 1024)
        .toInt()
        .coerceAtLeast(256 * 1024)
    private val iconCache = LruCache<String, Drawable>(cacheSize)

    @Volatile
    private var launcherApps: Map<String, ApplicationInfo> = emptyMap()

    fun cachedIcon(packageName: String): Drawable? =
        iconCache.get(packageName)?.constantState?.newDrawable()

    suspend fun loadInstalledApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
        loadAppsBlocking(context.applicationContext)
    }

    suspend fun findApp(context: Context, packageName: String): AppInfo = withContext(Dispatchers.IO) {
        loadAppsBlocking(context.applicationContext)
            .firstOrNull { it.packageName == packageName }
            ?: AppInfo(packageName, packageName)
    }

    suspend fun loadIcon(context: Context, packageName: String): Drawable? = withContext(Dispatchers.IO) {
        cachedIcon(packageName)?.let { return@withContext it }

        val appContext = context.applicationContext
        val pm = appContext.packageManager
        val drawable = try {
            val appInfo = launcherApps[packageName]
            if (appInfo != null) {
                pm.getApplicationIcon(appInfo)
            } else {
                pm.getApplicationIcon(packageName)
            }
        } catch (e: Exception) {
            null
        }

        if (drawable != null) iconCache.put(packageName, drawable)
        drawable?.constantState?.newDrawable() ?: drawable
    }

    private fun loadAppsBlocking(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = mutableMapOf<String, ApplicationInfo>()

        val result = pm.queryIntentActivities(intent, 0)
            .mapNotNull { resolve ->
                val appInfo = resolve.activityInfo.applicationInfo ?: return@mapNotNull null
                val packageName = appInfo.packageName ?: return@mapNotNull null
                apps.putIfAbsent(packageName, appInfo)
                AppInfo(resolve.loadLabel(pm).toString(), packageName)
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

        launcherApps = apps
        return result
    }
}
