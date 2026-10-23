package rs.chimera.android.ui.metacubex.adapter

import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.ui.metacubex.design.ProxyDisplayEntry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ProxyAdapterOptionsInstrumentedTest {
    @Test
    fun unchangedDisplayOptionsDoNotRebindRows() {
        val adapter = ProxyAdapter(onItemClick = {})
        val changedRanges = mutableListOf<Pair<Int, Int>>()
        val observer = object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                changedRanges += positionStart to itemCount
            }
        }
        val initialListCommitted = CountDownLatch(1)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            adapter.registerAdapterDataObserver(observer)
            adapter.submitList(
                listOf(
                    ProxyDisplayEntry(
                        name = "node-a",
                        subtitle = "Vless",
                        delayText = "",
                        delayMillis = null,
                        isSelected = true,
                    ),
                ),
                initialListCommitted::countDown,
            )
        }
        assertTrue("The initial adapter list should commit", initialListCommitted.await(5, TimeUnit.SECONDS))

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            adapter.columns = 2
            adapter.selectable = false
        }
        assertEquals(emptyList<Pair<Int, Int>>(), changedRanges)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            adapter.columns = 3
            adapter.selectable = true
        }
        assertEquals(listOf(0 to 1, 0 to 1), changedRanges)
    }
}
