package rs.chimera.android.ui.metacubex.adapter

import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.R
import rs.chimera.android.ui.metacubex.design.ProxyDisplayEntry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ProxyAdapterRecycledViewPoolInstrumentedTest {
    @Test
    fun holderReusedFromAnotherSelectorUsesTheCurrentAdapterCallback() {
        val clicked = mutableListOf<String>()
        val firstListCommitted = CountDownLatch(1)
        val secondListCommitted = CountDownLatch(1)
        lateinit var parent: FrameLayout
        lateinit var firstAdapter: ProxyAdapter
        lateinit var secondAdapter: ProxyAdapter

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            parent = newParent()
            firstAdapter = ProxyAdapter { clicked += "first:$it" }
            secondAdapter = ProxyAdapter { clicked += "second:$it" }
            firstAdapter.submitList(listOf(entry("shared-node")), firstListCommitted::countDown)
            secondAdapter.submitList(listOf(entry("shared-node")), secondListCommitted::countDown)
        }
        assertTrue("The first adapter list should commit", firstListCommitted.await(5, TimeUnit.SECONDS))
        assertTrue("The second adapter list should commit", secondListCommitted.await(5, TimeUnit.SECONDS))

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            firstAdapter.selectable = true
            val holder = firstAdapter.createViewHolder(parent, 0)
            firstAdapter.bindViewHolder(holder, 0)

            val sharedPool = RecyclerView.RecycledViewPool()
            sharedPool.putRecycledView(holder)
            val reusedHolder = sharedPool.getRecycledView(0) as? ProxyAdapter.ViewHolder
            assertTrue("The selector holder should be available in the shared pool", reusedHolder != null)

            secondAdapter.selectable = true
            secondAdapter.bindViewHolder(reusedHolder!!, 0)
            assertTrue(reusedHolder.itemView.performClick())
        }

        assertEquals(listOf("second:shared-node"), clicked)
    }

    @Test
    fun nonSelectorAdapterDisablesReusedSelectableHolder() {
        val clicked = mutableListOf<String>()
        val firstListCommitted = CountDownLatch(1)
        val secondListCommitted = CountDownLatch(1)
        lateinit var parent: FrameLayout
        lateinit var firstAdapter: ProxyAdapter
        lateinit var secondAdapter: ProxyAdapter

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            parent = newParent()
            firstAdapter = ProxyAdapter { clicked += "first:$it" }
            secondAdapter = ProxyAdapter { clicked += "second:$it" }
            firstAdapter.submitList(listOf(entry("shared-node")), firstListCommitted::countDown)
            secondAdapter.submitList(listOf(entry("shared-node")), secondListCommitted::countDown)
        }
        assertTrue(firstListCommitted.await(5, TimeUnit.SECONDS))
        assertTrue(secondListCommitted.await(5, TimeUnit.SECONDS))

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            firstAdapter.selectable = true
            val holder = firstAdapter.createViewHolder(parent, 0)
            firstAdapter.bindViewHolder(holder, 0)

            val sharedPool = RecyclerView.RecycledViewPool()
            sharedPool.putRecycledView(holder)
            val reusedHolder = sharedPool.getRecycledView(0) as ProxyAdapter.ViewHolder

            secondAdapter.selectable = false
            secondAdapter.bindViewHolder(reusedHolder, 0)
            assertFalse(reusedHolder.itemView.performClick())
        }

        assertEquals(emptyList<String>(), clicked)
    }

    private fun newParent(): FrameLayout = FrameLayout(
        ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext,
            R.style.Theme_Chimera_MetaCubeX,
        ),
    )

    private fun entry(name: String) = ProxyDisplayEntry(
        name = name,
        subtitle = "Vless",
        delayText = "",
        delayMillis = null,
        isSelected = false,
    )
}
