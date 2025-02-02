package io.github.takusan23.logcatphysicalchannelconfig

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.yield
import java.io.InputStreamReader

object LogcatTool {

    fun listenLogcat() = flow<LogCatData> {
        val process = Runtime.getRuntime().exec(arrayOf("logcat", "-b", "radio"))
        try {
            InputStreamReader(process.inputStream).buffered().use { bufferedReader ->
                var output: String? = null
                while (bufferedReader.readLine()?.also { output = it } != null) {
                    yield()
                    val data = output?.split(" ")?.let {
                        LogCatData(it[0], it[1], it.drop(6).joinToString(separator = " "))
                    } ?: continue
                    emit(data)
                }
            }
        } finally {
            process.destroy()
        }
    }.flowOn(Dispatchers.IO)

    data class LogCatData(
        val date: String,
        val time: String,
        val message: String,
    )
}