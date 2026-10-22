package rs.chimera.android.ui.metacubex.adapter

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.ui.metacubex.activity.MetaMainActivity
import rs.chimera.android.ui.metacubex.design.ProxyCardView
import rs.chimera.android.ui.metacubex.design.ProxyDisplayEntry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ProxyAdapterImmediateRebindInstrumentedTest {
    @Test
    fun visibleRowsReflectSelectionBeforeSubmittedListDiffCommits() {
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

                // ListAdapter dispatches its diff on the main thread, so it cannot commit
                // until this callback returns. The attached card views must update now.
                assertEquals(oldEntries, adapter.currentList)
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
            scenario.onActivity {
                adapter.submitList(reorderedEntries, reorderedListCommitted::countDown)
                assertEquals(newEntries, adapter.currentList)
                adapter.rebindVisibleItems(recyclerView, reorderedEntries)

                val visibleRows = (0 until recyclerView.childCount).mapNotNull { childIndex ->
                    val child = recyclerView.getChildAt(childIndex) as? ProxyCardView ?: return@mapNotNull null
                    recyclerView.getChildViewHolder(child).bindingAdapterPosition to child.entry?.name
                }.sortedBy { it.first }
                assertEquals(listOf("old-node", "new-node"), visibleRows.map { it.second })
            }
            assertTrue("The reordered rows should eventually commit", reorderedListCommitted.await(5, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
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
        type = "Vless",
        delayText = "",
        delayMillis = null,
        isSelected = isSelected,
    )
}
