package com.sonora.music

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import androidx.compose.runtime.mutableStateOf

/** Quanto "pesante" può essere la grafica. */
enum class PerfMode(val label: String) { AUTO("Automatico"), FULL("Completa"), LITE("Leggera") }

/**
 * Riconosce i dispositivi entry level o datati da RAM, processore, versione di Android e scheda video,
 * e spegne gli effetti pesanti (sfocature, animazioni infinite).
 */
object Perf {
    /** true = grafica leggera. */
    val lite = mutableStateOf(false)
    val mode = mutableStateOf(PerfMode.AUTO)
    var summary: String = ""
        private set

    private val SLOW_GPU = listOf(
        "mali-4", "mali-t6", "mali-t7", "mali-t8", "mali-g31", "mali-g51", "mali-g52", "mali-g57 mc1",
        "adreno (tm) 2", "adreno (tm) 3", "adreno (tm) 4", "adreno (tm) 5", "adreno (tm) 61",
        "powervr", "videocore", "vivante", "tegra", "sgx"
    )
    private val SLOW_SOC = listOf(
        "mt67", "mt65", "mt81", "helio a", "helio p2", "helio g25", "helio g35", "helio g37", "sc98", "sc77",
        "unisoc", "spreadtrum", "msm89", "msm86", "sdm4", "sdm6", "exynos 7", "exynos 8", "exynos 850", "kirin 6", "kirin 7"
    )

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("sonora", Context.MODE_PRIVATE)
        mode.value = PerfMode.values().getOrElse(prefs.getInt("perf_mode", 0)) { PerfMode.AUTO }
        val auto = detect(context)
        lite.value = when (mode.value) {
            PerfMode.AUTO -> auto
            PerfMode.FULL -> false
            PerfMode.LITE -> true
        }
    }

    fun setMode(context: Context, m: PerfMode) {
        context.getSharedPreferences("sonora", Context.MODE_PRIVATE).edit().putInt("perf_mode", m.ordinal).apply()
        init(context)
    }

    private fun detect(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val ramGb = info.totalMem / (1024.0 * 1024 * 1024)
        val cores = Runtime.getRuntime().availableProcessors()
        val soc = (if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else "") +
            " ${Build.HARDWARE} ${Build.BOARD}"
        val gpu = gpuName().lowercase()

        val reasons = mutableListOf<String>()
        if (am.isLowRamDevice) reasons += "poca RAM"
        if (ramGb < 3.3) reasons += "RAM %.1f GB".format(ramGb)
        if (cores <= 4) reasons += "$cores core"
        if (Build.VERSION.SDK_INT <= 28) reasons += "Android ${Build.VERSION.RELEASE}"
        if (SLOW_SOC.any { soc.lowercase().contains(it) }) reasons += "processore base"
        if (gpu.isNotEmpty() && SLOW_GPU.any { gpu.contains(it) }) reasons += "scheda video base"
        summary = "RAM %.1f GB · %d core · GPU %s".format(ramGb, cores, gpu.ifEmpty { "?" })
        return reasons.size >= 2 || "poca RAM" in reasons || "scheda video base" in reasons
    }

    /** Nome della GPU (stringa del driver OpenGL), letto con un contesto minuscolo. */
    private fun gpuName(): String = try {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val v = IntArray(2)
        EGL14.eglInitialize(display, v, 0, v, 1)
        val cfgs = arrayOfNulls<EGLConfig>(1)
        val n = IntArray(1)
        EGL14.eglChooseConfig(display, intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE), 0, cfgs, 0, 1, n, 0)
        val ctx = EGL14.eglCreateContext(display, cfgs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        val surf = EGL14.eglCreatePbufferSurface(display, cfgs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(display, surf, surf, ctx)
        val name = GLES20.glGetString(GLES20.GL_RENDERER) ?: ""
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, surf)
        EGL14.eglDestroyContext(display, ctx)
        EGL14.eglTerminate(display)
        name
    } catch (e: Throwable) {
        ""
    }
}
