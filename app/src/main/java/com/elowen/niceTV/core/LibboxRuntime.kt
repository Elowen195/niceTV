package com.elowen.niceTV.core

import android.content.Context
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import java.io.File

object LibboxRuntime {
    @Volatile
    private var isSetup: Boolean = false

    fun ensureSetup(context: Context) {
        if (isSetup) return
        synchronized(this) {
            if (isSetup) return

            val appContext = context.applicationContext
            val baseDir = File(appContext.filesDir, "libbox")
            val workDir = File(baseDir, "work")
            val tempDir = File(appContext.cacheDir, "libbox-temp")

            prepareDir(baseDir)
            prepareDir(workDir)
            prepareDir(tempDir)

            val options = SetupOptions().apply {
                basePath = baseDir.absolutePath
                workingPath = workDir.absolutePath
                tempPath = tempDir.absolutePath
                username = ""
            }

            Libbox.setup(options)
            isSetup = true
        }
    }

    private fun prepareDir(dir: File) {
        if (dir.exists()) {
            if (!dir.isDirectory) {
                throw IllegalStateException("Path exists but is not a directory: ${dir.absolutePath}")
            }
            return
        }
        if (!dir.mkdirs()) {
            throw IllegalStateException("Failed to create directory: ${dir.absolutePath}")
        }
    }
}
