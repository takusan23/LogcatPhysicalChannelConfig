package io.github.takusan23.logcatphysicalchannelconfig

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.takusan23.logcatphysicalchannelconfig.ui.theme.LogcatPhysicalChannelConfigTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter

// adb shell pm grant io.github.takusan23.logcatphysicalchannelconfig android.permission.READ_LOGS

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LogcatPhysicalChannelConfigTheme {
                MainScreen()
            }
        }
    }
}

// TODO Android 15 / 16 のみ動作確認済み
// https://cs.android.com/android/platform/superproject/main/+/main:frameworks/opt/telephony/src/java/com/android/internal/telephony/NetworkTypeController.java;drc=4b1786fab7e144d982b86c4f1ce00a9982a036a1;l=1340
private val PHYSICAL_CHANNEL_CONFIG_UPDATE_LOG_REGEX = "Physical channel configs updated: anchorNrCell=(.*?), nrBandwidths=(.*?), nrBands=(.*?), configs=(.*)".toRegex()

// TODO Android 15 / 16 のみ動作確認済み
// https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/telephony/java/android/telephony/PhysicalChannelConfig.java;drc=876a342027a418e9d95f12a6a5d9baf2c6d93c4f;l=464
private val PHYSICAL_CHANNEL_CONFIG_UPDATE_CONFIGS_LOG_REGEX = "\\{mConnectionStatus=(.*?),mCellBandwidthDownlinkKhz=(.*?),mCellBandwidthUplinkKhz=(.*?),mNetworkType=(.*?),mFrequencyRange=(.*?),mDownlinkChannelNumber=(.*?),mUplinkChannelNumber=(.*?),mContextIds=(.*?),mPhysicalCellId=(.*?),mBand=(.*?),mDownlinkFrequency=(.*?),mUplinkFrequency=(.*?)\\}".toRegex()

private data class PhysicalChannelConfigUpdateLog(
    val mLastAnchorNrCellId: String?,
    val mRatchetedNrBandwidths: String?,
    val mRatchetedNrBands: String?,
    val mPhysicalChannelConfigs: String?
)

private data class PhysicalChannelConfigLog(
    val mConnectionStatus: String?,
    val mCellBandwidthDownlinkKhz: String?,
    val mCellBandwidthUplinkKhz: String?,
    val mNetworkType: String?,
    val mFrequencyRange: String?,
    val mDownlinkChannelNumber: String?,
    val mUplinkChannelNumber: String?,
    val mContextIds: String?,
    val mPhysicalCellId: String?,
    val mBand: String?,
    val mDownlinkFrequency: String?,
    val mUplinkFrequency: String?
)

@Composable
private fun MainScreen() {
    val physicalChannelConfig = remember { mutableStateOf(emptyList<String>()) }
    val latestPhysicalChannelConfigUpdateLog = remember { mutableStateOf<PhysicalChannelConfigUpdateLog?>(null) }
    val latestPhysicalChannelConfigLogList = remember { mutableStateOf(emptyList<PhysicalChannelConfigLog>()) }

    LaunchedEffect(Unit) {
        LogcatTool.listenLogcat()
            .filter { it.message.contains("Physical channel configs updated", ignoreCase = true) }
            .collectLatest { logCatData ->

                when {
                    Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                        // Android 14 以前。直で取れる
                        val configsLogRegexResultList = logCatData.message.let { nonnull -> PHYSICAL_CHANNEL_CONFIG_UPDATE_CONFIGS_LOG_REGEX.findAll(nonnull) }?.toList()
                        latestPhysicalChannelConfigLogList.value = configsLogRegexResultList?.map { result ->
                            val groupValues = result.groupValues
                            PhysicalChannelConfigLog(
                                mConnectionStatus = groupValues.getOrNull(1),
                                mCellBandwidthDownlinkKhz = groupValues.getOrNull(2),
                                mCellBandwidthUplinkKhz = groupValues.getOrNull(3),
                                mNetworkType = groupValues.getOrNull(4),
                                mFrequencyRange = groupValues.getOrNull(5),
                                mDownlinkChannelNumber = groupValues.getOrNull(6),
                                mUplinkChannelNumber = groupValues.getOrNull(7),
                                mContextIds = groupValues.getOrNull(8),
                                mPhysicalCellId = groupValues.getOrNull(9),
                                mBand = groupValues.getOrNull(10),
                                mDownlinkFrequency = groupValues.getOrNull(11),
                                mUplinkFrequency = groupValues.getOrNull(12)
                            )
                        } ?: emptyList()
                    }

                    Build.VERSION.SDK_INT <= Build.VERSION_CODES.VANILLA_ICE_CREAM -> {
                        // Android 15 以降。一個間に挟まった

                        // ログから正規表現で取り出す
                        val updateLogRegexResult = PHYSICAL_CHANNEL_CONFIG_UPDATE_LOG_REGEX.find(logCatData.message)?.groupValues
                        latestPhysicalChannelConfigUpdateLog.value = PhysicalChannelConfigUpdateLog(
                            mLastAnchorNrCellId = updateLogRegexResult?.getOrNull(1),
                            mRatchetedNrBandwidths = updateLogRegexResult?.getOrNull(2),
                            mRatchetedNrBands = updateLogRegexResult?.getOrNull(3),
                            mPhysicalChannelConfigs = updateLogRegexResult?.getOrNull(4)
                        )

                        // configs が取れていれば
                        // 複数の PhysicalChannelConfig（キャリアアグリゲーション） が入ってるので findAll()
                        val configsLogRegexResultList = latestPhysicalChannelConfigUpdateLog.value?.mPhysicalChannelConfigs?.let { nonnull -> PHYSICAL_CHANNEL_CONFIG_UPDATE_CONFIGS_LOG_REGEX.findAll(nonnull) }?.toList()
                        latestPhysicalChannelConfigLogList.value = configsLogRegexResultList?.map { result ->
                            val groupValues = result.groupValues
                            PhysicalChannelConfigLog(
                                mConnectionStatus = groupValues.getOrNull(1),
                                mCellBandwidthDownlinkKhz = groupValues.getOrNull(2),
                                mCellBandwidthUplinkKhz = groupValues.getOrNull(3),
                                mNetworkType = groupValues.getOrNull(4),
                                mFrequencyRange = groupValues.getOrNull(5),
                                mDownlinkChannelNumber = groupValues.getOrNull(6),
                                mUplinkChannelNumber = groupValues.getOrNull(7),
                                mContextIds = groupValues.getOrNull(8),
                                mPhysicalCellId = groupValues.getOrNull(9),
                                mBand = groupValues.getOrNull(10),
                                mDownlinkFrequency = groupValues.getOrNull(11),
                                mUplinkFrequency = groupValues.getOrNull(12)
                            )
                        } ?: emptyList()
                    }
                }


                // 履歴追加
                physicalChannelConfig.value = listOf(logCatData.message) + physicalChannelConfig.value
            }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        LazyColumn(contentPadding = innerPadding) {

            item {
                Text(
                    "最新のログ",
                    fontSize = 24.sp
                )
                Text(
                    text = """
                    anchorNrCell = ${latestPhysicalChannelConfigUpdateLog.value?.mLastAnchorNrCellId}
                    nrBandwidth = ${latestPhysicalChannelConfigUpdateLog.value?.mRatchetedNrBandwidths}
                    nrBands = ${latestPhysicalChannelConfigUpdateLog.value?.mRatchetedNrBands}
                """.trimIndent()
                )
            }

            item {
                val checked = remember { mutableStateOf(false) }

                Text(
                    modifier = Modifier.padding(top = 10.dp),
                    text = "物理チャンネル構成",
                    fontSize = 24.sp
                )

                Row(
                    modifier = Modifier.toggleable(value = checked.value, role = Role.Switch, onValueChange = { checked.value = it }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "詳細表示")
                    Switch(checked = checked.value, onCheckedChange = null)
                }

                if (!checked.value) {
                    Text("Gen \t (Pri|Sec)Cell \t Band/BandWidth \t PCI")
                    latestPhysicalChannelConfigLogList.value.forEach {
                        Text("${it.mNetworkType} \t ${it.mConnectionStatus} \t ${it.mBand}/${it.mCellBandwidthDownlinkKhz} \t ${it.mPhysicalCellId}")
                    }
                } else {
                    latestPhysicalChannelConfigLogList.value.forEach {
                        Text(
                            text = """
                            mConnectionStatus = ${it.mConnectionStatus}
                            mCellBandwidthDownlinkKhz = ${it.mCellBandwidthDownlinkKhz}
                            mCellBandwidthUplinkKhz = ${it.mCellBandwidthUplinkKhz}
                            mNetworkType = ${it.mNetworkType}
                            mFrequencyRange = ${it.mFrequencyRange}
                            mDownlinkChannelNumber = ${it.mDownlinkChannelNumber}
                            mUplinkChannelNumber = ${it.mUplinkChannelNumber}
                            mContextIds = ${it.mContextIds}
                            mPhysicalCellId = ${it.mPhysicalCellId}
                            mBand = ${it.mBand}
                            mDownlinkFrequency = ${it.mDownlinkFrequency}
                            mUplinkFrequency = ${it.mUplinkFrequency}
                            """.trimIndent()
                        )
                        HorizontalDivider()
                    }
                }
            }

            item {
                Text(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .padding(top = 10.dp),
                    text = "履歴",
                    fontSize = 24.sp
                )
            }
            items(physicalChannelConfig.value) {
                Text(it)
                HorizontalDivider()
            }
        }
    }
}
