package rs.chimera.android.ui.metacubex.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.QRResult.QRError
import io.github.g00fy2.quickie.QRResult.QRMissingPermission
import io.github.g00fy2.quickie.QRResult.QRSuccess
import io.github.g00fy2.quickie.QRResult.QRUserCanceled
import io.github.g00fy2.quickie.ScanQRCode
import kotlinx.coroutines.launch
import rs.chimera.android.R
import rs.chimera.android.ui.metacubex.design.MetaNewProfileDesign

class MetaNewProfileActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_SOURCE = "rs.chimera.android.ui.metacubex.profile_source"
        const val EXTRA_QR_URL = "rs.chimera.android.ui.metacubex.scanned_profile_url"
        const val SOURCE_FILE = "file"
        const val SOURCE_URL = "url"
        const val SOURCE_QR = "qr"
    }

    private lateinit var design: MetaNewProfileDesign
    private val scanLauncher = registerForActivityResult(ScanQRCode(), ::handleScanResult)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Chimera_MetaCubeX)

        design = MetaNewProfileDesign(this)
        setContentView(design.root)

        lifecycleScope.launch {
            for (request in design.requests) {
                when (request) {
                    MetaNewProfileDesign.Request.NavigateBack -> finish()
                    MetaNewProfileDesign.Request.ImportFile -> returnSource(SOURCE_FILE)
                    MetaNewProfileDesign.Request.ImportUrl -> returnSource(SOURCE_URL)
                    MetaNewProfileDesign.Request.ImportQrCode -> scanLauncher.launch(null)
                }
            }
        }
    }

    private fun handleScanResult(result: QRResult) {
        when (result) {
            is QRSuccess -> {
                val url = profileUrlFromQr(result.content.rawValue, result.content.rawBytes)
                if (url == null) {
                    lifecycleScope.launch {
                        design.showToast(getString(R.string.profile_qr_empty_result))
                    }
                } else {
                    setResult(
                        RESULT_OK,
                        Intent()
                            .putExtra(EXTRA_SOURCE, SOURCE_QR)
                            .putExtra(EXTRA_QR_URL, url),
                    )
                    finish()
                }
            }
            QRUserCanceled -> Unit
            QRMissingPermission -> lifecycleScope.launch {
                design.showToast(getString(R.string.profile_qr_no_camera_permission))
            }
            is QRError -> lifecycleScope.launch {
                design.showToast(getString(R.string.profile_qr_scan_failed))
            }
        }
    }

    private fun returnSource(source: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_SOURCE, source))
        finish()
    }
}

internal fun profileUrlFromQr(rawValue: String?, rawBytes: ByteArray?): String? =
    rawValue?.takeIf(String::isNotBlank)
        ?: rawBytes?.let { String(it) }?.takeIf(String::isNotBlank)
