package com.mitrustbuster.hook

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Method

/**
 * MiTrustBuster - LSPosed Module
 *
 * References HyperCeiler's DisableMrm implementation.
 * Uses DexKit to locate the MRM initialization method in com.xiaomi.trustservice
 * by matching string patterns "MiTrustService/statusEventHandle" and
 * "try init mrmd Service", then hooks it to return false,
 * preventing the mrmd daemon from starting.
 *
 * Key fix: Uses XSharedPreferences (not AndroidAppHelper) to read the module's
 * toggle setting from within the hooked trustservice process.
 */
class MainHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "MiTrustBuster"
        private const val TARGET_PACKAGE = "com.xiaomi.trustservice"
        private const val PREF_NAME = "mitrustbuster_prefs"
        private const val PREF_DISABLE_MRM = "disable_mrm"
        private const val MODULE_PACKAGE = "com.mitrustbuster"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Only hook into the target trustservice package
        if (lpparam.packageName != TARGET_PACKAGE) return

        XposedBridge.log("$TAG: Loaded in ${lpparam.packageName}")

        // Use XSharedPreferences to read module's settings from the hooked process
        val xsp = XSharedPreferences(MODULE_PACKAGE, PREF_NAME)
        xsp.makeWorldReadable()
        xsp.reload()

        val enabled = xsp.getBoolean(PREF_DISABLE_MRM, false)
        if (!enabled) {
            XposedBridge.log("$TAG: Module is DISABLED in settings, skipping hook")
            return
        }

        XposedBridge.log("$TAG: Module is ENABLED, starting DexKit search...")

        // Use DexKit to find the MRM init method (same strategy as HyperCeiler)
        val targetMethod = findMrmInitMethod(lpparam)

        if (targetMethod != null) {
            hookMrmInitMethod(targetMethod)
        } else {
            XposedBridge.log("$TAG: DexKit found no method, trying fallback...")
            tryFallbackHook(lpparam)
        }
    }

    /**
     * Use DexKit to find the MRM initialization method.
     * Exact same strategy as HyperCeiler's DisableMrm:
     *   - String match: "MiTrustService/statusEventHandle"
     *   - String match: "try init mrmd Service"
     *   - Return type: boolean
     */
    private fun findMrmInitMethod(lpparam: XC_LoadPackage.LoadPackageParam): Method? {
        return try {
            val apkPath = lpparam.appInfo.sourceDir
            XposedBridge.log("$TAG: DexKit scanning APK: $apkPath")

            DexKitBridge.create(apkPath).use { bridge ->
                val result = bridge.findMethod(
                    FindMethod.create()
                        .matcher(
                            MethodMatcher.create()
                                .usingStrings("MiTrustService/statusEventHandle", "try init mrmd Service")
                                .returnType(Boolean::class.java)
                        )
                )

                val dexMethod = result.singleOrNull()
                if (dexMethod != null) {
                    val javaMethod = dexMethod.getMethodInstance(lpparam.classLoader)
                    XposedBridge.log("$TAG: DexKit FOUND method: ${javaMethod.declaringClass.name}.${javaMethod.name}")
                    javaMethod
                } else {
                    XposedBridge.log("$TAG: DexKit found ${result.size} methods (expected 1)")
                    null
                }
            }
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: DexKit search failed: ${e.message}")
            null
        }
    }

    /**
     * Hook the found method to always return false before it executes.
     * This tells the trustservice app that MRM init "failed",
     * so mrmd daemon never starts.
     */
    private fun hookMrmInitMethod(method: Method) {
        try {
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = false
                    XposedBridge.log("$TAG: BLOCKED MRM init -> returned false")
                }
            })
            XposedBridge.log("$TAG: Successfully hooked: ${method.declaringClass.name}.${method.name}")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Failed to hook method: ${e.message}")
        }
    }

    /**
     * Fallback: Scan known class names for boolean init/mrm methods.
     * This is less reliable than DexKit but may catch some cases.
     */
    private fun tryFallbackHook(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader
        val candidateClasses = listOf(
            "com.xiaomi.trustservice.mrm.MrmService",
            "com.xiaomi.trustservice.mrm.MrmInitializer",
            "com.xiaomi.trustservice.TrustServiceManager"
        )

        for (className in candidateClasses) {
            try {
                val clazz = XposedHelpers.findClass(className, classLoader)
                XposedBridge.log("$TAG: Fallback found class: $className")
                hookAllBooleanInitMethods(clazz)
            } catch (_: ClassNotFoundException) {
                // Skip
            }
        }
    }

    private fun hookAllBooleanInitMethods(clazz: Class<*>) {
        for (method in clazz.declaredMethods) {
            if (method.returnType == Boolean::class.javaPrimitiveType) {
                val name = method.name.lowercase()
                if (name.contains("init") || name.contains("mrm") ||
                    name.contains("mrmd") || name.contains("start")) {
                    try {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                param.result = false
                                XposedBridge.log("$TAG: Fallback blocked ${clazz.name}.${method.name}")
                            }
                        })
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }
}
