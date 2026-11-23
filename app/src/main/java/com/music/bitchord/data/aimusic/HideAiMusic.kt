package com.music.bitchord.data.aimusic

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Keeps [AiMusicFilter] in step with the "Hide AI music" switch: on, it reads
 * the kept lists and fetches any that is a day old ([AiMusicLists.refresh]);
 * off, it takes the lists out of use and fetches nothing.
 */
object HideAiMusic {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var filesDir: File? = null
    private var job: Job? = null

    fun init(context: Context, enabled: Boolean) {
        filesDir = context.applicationContext.filesDir
        apply(enabled)
    }

    @Synchronized
    fun apply(enabled: Boolean) {
        AiMusicFilter.enabled = enabled
        job?.cancel()
        job = null
        if (!enabled) {
            AiMusicLists.clear()
            return
        }
        val dir = filesDir ?: return
        job = scope.launch { AiMusicLists.refresh(dir) }
    }
}
