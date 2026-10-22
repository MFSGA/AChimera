package rs.chimera.android.ui.metacubex.activity

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

internal object ProfileQrCodeEncoder {
    fun encode(contents: String): BitMatrix = QRCodeWriter().encode(
        contents,
        BarcodeFormat.QR_CODE,
        SIZE,
        SIZE,
        mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"),
    )

    private const val SIZE = 768
}
