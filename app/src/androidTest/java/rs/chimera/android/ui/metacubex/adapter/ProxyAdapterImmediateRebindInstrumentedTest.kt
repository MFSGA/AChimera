package rs.chimera.android.ui.metacubex.adapter

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.ui.metacubex.activity.MetaMainActivity
import rs.chimera.android.ui.metacubex.design.ProxyCardView
import rs.chimera.android.ui.metacubex.design.ProxyDisplayEntry
import rs.chimera.android.ui.metacubex.design.ProxySelectionColorTransition
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ProxyAdapterImmediateRebindInstrumentedTest {
    @Test
    fun visibleRowsReflectSelectionAndSortWithoutDiffingHolderMoves() {
        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            val initialListCommitted = CountDownLatch(1)
            val updatedListCommitted = CountDownLatch(1)
            lateinit var adapter: ProxyAdapter
            lateinit var recyclerView: RecyclerView
            val oldEntries = listOf(entry("old-node", isSelected = true), entry("new-node"))
            val newEntries = listOf(entry("old-node"), entry("new-node", isSelected = true))

            scenario.onActivity { activity ->
                recyclerView = RecyclerView(activity).apply {
                    layoutManager = GridLayoutManager(activity, 2)
                    itemAnimator = null
                    this.adapter = ProxyAdapter(onItemClick = {}).also {
                        it.submitList(oldEntries, initialListCommitted::countDown)
                    }
                    activity.setContentView(this)
                }
            }
            assertTrue("The initial rows should commit", initialListCommitted.await(5, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity {
                assertEquals(2, recyclerView.childCount)
                adapter = recyclerView.adapter as ProxyAdapter
                adapter.submitList(newEntries, updatedListCommitted::countDown)

                // Proxy pages follow CMFA's position-based refresh, not an asynchronous diff.
                assertEquals(newEntries, adapter.currentList)
                adapter.rebindVisibleItems(recyclerView, newEntries)

                val selectedRows = (0 until recyclerView.childCount).mapNotNull { childIndex ->
                    (recyclerView.getChildAt(childIndex) as? ProxyCardView)?.entry
                        ?.takeIf(ProxyDisplayEntry::isSelected)
                        ?.name
                }
                assertEquals(listOf("new-node"), selectedRows)
            }

            assertTrue("The submitted rows should eventually commit", updatedListCommitted.await(5, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                val selectedRows = (0 until recyclerView.childCount).mapNotNull { childIndex ->
                    (recyclerView.getChildAt(childIndex) as? ProxyCardView)?.entry
                        ?.takeIf(ProxyDisplayEntry::isSelected)
                        ?.name
                }
                assertEquals(listOf("new-node"), selectedRows)
            }

            val reorderedListCommitted = CountDownLatch(1)
            val reorderedEntries = listOf(entry("new-node", isSelected = true), entry("old-node"))
            val movedRanges = mutableListOf<Pair<Int, Int>>()
            val changedRanges = mutableListOf<Pair<Int, Int>>()
            lateinit var selectedTransition: ProxySelectionColorTransition
            var selectedColor = 0
            var selectionAnimationFrameTime = 0L
            scenario.onActivity {
                val selectedCard = (0 until recyclerView.childCount)
                    .mapNotNull { recyclerView.getChildAt(it) as? ProxyCardView }
                    .single { it.entry?.name == "new-node" }
                selectedTransition = selectedCard.selectionTransition()
                selectedColor = selectedCard.backgroundColor(newEntries[1])
                val unselectedColor = selectedCard.backgroundColor(newEntries[1].copy(isSelected = false))
                selectionAnimationFrameTime = System.currentTimeMillis() + 5_000L
                selectedTransition.snap(unselectedColor, selectionAnimationFrameTime)
                assertTrue(
                    selectedTransition.bind(
                        previous = newEntries[1].copy(isSelected = false),
                        current = newEntries[1],
                        target = selectedColor,
                        frameTime = selectionAnimationFrameTime,
                    ),
                )
                assertTrue(
                    selectedTransition.advance(selectedColor, selectionAnimationFrameTime + 64L).animating,
                )
                adapter.registerAdapterDataObserver(
                    object : RecyclerView.AdapterDataObserver() {
                        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                            movedRanges += fromPosition to toPosition
                        }

                        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                            changedRanges += positionStart to itemCount
                        }
                    },
                )
                adapter.submitList(reorderedEntries, reorderedListCommitted::countDown)
                assertEquals(reorderedEntries, adapter.currentList)
                assertEquals(emptyList<Pair<Int, Int>>(), movedRanges)
                assertEquals(listOf(0 to 2), changedRanges)
                adapter.rebindVisibleItems(recyclerView, reorderedEntries)

                val visibleRows = (0 until recyclerView.childCount).mapNotNull { childIndex ->
                    val child = recyclerView.getChildAt(childIndex) as? ProxyCardView ?: return@mapNotNull null
                    recyclerView.getChildViewHolder(child).bindingAdapterPosition to child.entry?.name
                }.sortedBy { it.first }
                assertEquals(listOf("old-node", "new-node"), visibleRows.map { it.second })
                val selectedRows = (0 until recyclerView.childCount).mapNotNull { childIndex ->
                    (recyclerView.getChildAt(childIndex) as? ProxyCardView)?.entry
                        ?.takeIf(ProxyDisplayEntry::isSelected)
                        ?.name
                }
                assertEquals(listOf("new-node"), selectedRows)
            }
            assertTrue("The reordered rows should eventually commit", reorderedListCommitted.await(5, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                val selectedCard = (0 until recyclerView.childCount)
                    .mapNotNull { recyclerView.getChildAt(it) as? ProxyCardView }
                    .single { it.entry?.name == "new-node" }
                assertSame(selectedTransition, selectedCard.selectionTransition())
                assertNotEquals(selectedColor, selectedTransitionColor(selectedTransition))
                val visibleRows = (0 until recyclerView.childCount).mapNotNull { childIndex ->
                    val child = recyclerView.getChildAt(childIndex) as? ProxyCardView ?: return@mapNotNull null
                    recyclerView.getChildViewHolder(child).bindingAdapterPosition to child.entry?.name
                }.sortedBy { it.first }
                assertEquals(listOf("new-node", "old-node"), visibleRows.map { it.second })
            }
        }
    }

    private fun entry(name: String, isSelected: Boolean = false) = ProxyDisplayEntry(
        name = name,
        subtitle = "Vless",
        delayText = "",
        delayMillis = null,
        isSelected = isSelected,
    )

    private fun ProxyCardView.selectionTransition(): ProxySelectionColorTransition =
        ProxyCardView::class.java.getDeclaredField("selectionColorTransition")
            .apply { isAccessible = true }
            .get(this) as ProxySelectionColorTransition

    private fun ProxyCardView.backgroundColor(entry: ProxyDisplayEntry): Int =
        ProxyCardView::class.java.getDeclaredMethod("backgroundColor", ProxyDisplayEntry::class.java)
            .apply { isAccessible = true }
            .invoke(this, entry) as Int

    private fun selectedTransitionColor(transition: ProxySelectionColorTransition): Int =
        ProxySelectionColorTransition::class.java.getDeclaredField("color")
            .apply { isAccessible = true }
            .get(transition) as Int
}
