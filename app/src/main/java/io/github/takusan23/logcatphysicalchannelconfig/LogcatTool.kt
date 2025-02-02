package io.github.takusan23.logcatphysicalchannelconfig

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.io.InputStreamReader

object LogcatTool {

    fun listenLogcat() = callbackFlow<LogCatData> {
        val process = Runtime.getRuntime().exec(arrayOf("logcat", "-b", "radio"))
        InputStreamReader(process.inputStream).buffered().use { bufferedReader ->
            var output: String? = null
            while (bufferedReader.readLine()?.also { output = it } != null) {
                val data = output?.split(" ")?.let {
                    LogCatData(it[0], it[1], it.drop(6).joinToString(separator = " "))
                } ?: continue
                trySend(data)
            }
        }
        awaitClose { process.destroy() }
    }.flowOn(Dispatchers.IO)

    data class LogCatData(
        val date: String,
        val time: String,
        val message: String,
    )
}