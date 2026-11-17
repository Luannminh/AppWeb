package com.twchat.app.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/**
 * Tiny wrapper around [MediaRecorder] that records AAC audio into an .m4a file
 * inside the app cache. Recording needs the RECORD_AUDIO permission.
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var target: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    fun elapsedMillis(): Long =
        if (recorder == null) 0L else (SystemClock.elapsedRealtime() - startedAt)

    fun start(): Boolean {
        cancel()
        val file = File(context.cacheDir, "voice-${System.currentTimeMillis()}.m4a")
        val media = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return try {
            media.setAudioSource(MediaRecorder.AudioSource.MIC)
            media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            media.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            media.setAudioSamplingRate(44_100)
            media.setAudioEncodingBitRate(96_000)
            media.setOutputFile(file.absolutePath)
            media.prepare()
            media.start()
            recorder = media
            target = file
            startedAt = SystemClock.elapsedRealtime()
            true
        } catch (error: Throwable) {
            runCatching { media.release() }
            recorder = null
            target = null
            runCatching { file.delete() }
            false
        }
    }

    /** Stops recording and returns the file plus its length in milliseconds. */
    fun stop(): Pair<File, Int>? {
        val media = recorder ?: return null
        val file = target
        val duration = elapsedMillis().toInt()
        recorder = null
        target = null
        val ok = runCatching { media.stop() }.isSuccess
        runCatching { media.release() }
        if (!ok || file == null || !file.exists()) {
            file?.let { runCatching { it.delete() } }
            return null
        }
        return file to duration
    }

    fun cancel() {
        val media = recorder
        recorder = null
        val file = target
        target = null
        if (media != null) {
            runCatching { media.stop() }
            runCatching { media.release() }
        }
        file?.let { runCatching { it.delete() } }
    }
}
