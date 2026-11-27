package com.medlens.ai.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class InAppVoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null

    fun startRecording(): Result<File> {
        stopAndDiscard()
        return runCatching {
            val dir = File(context.cacheDir, "voice").apply { mkdirs() }
            val outFile = File(dir, "voice_${System.currentTimeMillis()}.m4a")

            @Suppress("DEPRECATION")
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(32000)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            currentFile = outFile
            outFile
        }.onFailure {
            stopAndDiscard()
        }
    }

    fun stopAndGetFile(): File? {
        val rec = recorder ?: return null
        val file = currentFile
        recorder = null
        currentFile = null

        return runCatching {
            rec.stop()
            rec.release()
            file?.takeIf { it.exists() && it.length() > 512L }
        }.getOrElse {
            runCatching { rec.release() }
            file?.delete()
            null
        }
    }

    fun stopAndDiscard() {
        val rec = recorder
        val file = currentFile
        recorder = null
        currentFile = null
        if (rec != null) {
            runCatching { rec.stop() }
            runCatching { rec.release() }
        }
        file?.delete()
    }
}
