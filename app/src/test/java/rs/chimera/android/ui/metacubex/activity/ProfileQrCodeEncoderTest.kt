package rs.chimera.android.ui.metacubex.activity

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileQrCodeEncoderTest {
    @Test
    fun encodesRemoteUrlThatCanBeDecodedBack() {
        val url = "https://example.com/profile?token=test-only"
        val matrix = ProfileQrCodeEncoder.encode(url)
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) {
                0xFF000000.toInt()
            } else {
                0xFFFFFFFF.toInt()
            }
        }
        val source = RGBLuminanceSource(matrix.width, matrix.height, pixels)
        val bitmap = BinaryBitmap(HybridBinarizer(source))

        assertEquals(url, QRCodeReader().decode(bitmap).text)
    }
}
