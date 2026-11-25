package com.example.cornercivilization.p37f776dc

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.pico.spatial.ui.platform.resize.SpatialContainerUtils
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/** Verifies maximum startup and the public XY resize helper; restores launch size. */
@RunWith(AndroidJUnit4::class)
class VolumeResizeJourneyTest {
    @Test fun maximumCanResizeContentAndReturnToMaximum() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val maximumPx = (2700 * context.resources.displayMetrics.density).roundToInt()
        val smallerPx = (1800 * context.resources.displayMetrics.density).roundToInt()
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var activity: Activity? = null
        repeat(100) {
            instrumentation.runOnMainSync {
                activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .firstOrNull { it.window.decorView.width >= 2400 }
            }
            if (activity == null) SystemClock.sleep(100)
        }
        val windowActivity = requireNotNull(activity) { "Spatial window activity did not become ready" }
        fun resize(width: Int, height: Int): Boolean {
            var accepted = false
            instrumentation.runOnMainSync {
                accepted = SpatialContainerUtils.resizeCurrentWindowContainer(windowActivity, width, height)
            }
            return accepted
        }
        fun awaitWidth(expected: Int): Boolean {
            repeat(100) {
                var actual = 0
                instrumentation.runOnMainSync { actual = windowActivity.window.decorView.width }
                if (actual == expected) {
                    Log.i("VolumeResizeJourney", "windowWidth=$actual")
                    return true
                }
                SystemClock.sleep(100)
            }
            return false
        }
        var renderedHalfDepth = 0f
        fun awaitRenderedSize(width: Int, height: Int, depth: Int): Boolean {
            val dimensions = Regex("DisplayInfo:\\{[^}]*w:(\\d+), h:(\\d+), d:(\\d+),[^\\n]*sfBoundsComp:\\{w:([0-9.]+) h:([0-9.]+) d:([0-9.]+)\\}")
            var previous = ""
            repeat(100) {
                val dump = ParcelFileDescriptor.AutoCloseInputStream(
                    instrumentation.uiAutomation.executeShellCommand("dumpsys SurfaceFlinger")
                ).bufferedReader().use { it.readText() }
                val matches = dimensions.findAll(dump).toList()
                val sizes = matches.map { m -> m.groupValues.subList(1, 4).map(String::toInt) }
                val current = sizes.joinToString()
                if (current != previous) {
                    Log.i("VolumeResizeJourney", "observedRenderedSizes=$current")
                    previous = current
                }
                val matchIndex = sizes.indexOfFirst {
                    kotlin.math.abs(it[0] - width) <= 1 && kotlin.math.abs(it[1] - height) <= 1 &&
                        kotlin.math.abs(it[2] - depth) <= 1
                }
                if (matchIndex >= 0) {
                    renderedHalfDepth = matches[matchIndex].groupValues[6].toFloat() / 2f
                    Log.i("VolumeResizeJourney", "renderedSize=${sizes[matchIndex]} requestedDepth=$depth tolerancePx=1")
                    Log.i("VolumeResizeJourney", "renderedPhysicalMetres=${matches[matchIndex].groupValues.subList(4, 7)}")
                    return true
                }
                SystemClock.sleep(100)
            }
            return false
        }
        fun awaitSceneFit(width: Int, depthHalf: Float): Boolean {
            // Wait for assets and the final native depth, not just the 2D window.
            val fit = Regex("fit revision=(\\d+) x \\d+.*safeHalf=Vector3\\(x=[^,]+, y=[^,]+, z=([^)]+)\\)")
            repeat(100) {
                val logs = ParcelFileDescriptor.AutoCloseInputStream(
                    instrumentation.uiAutomation.executeShellCommand(
                        "logcat -d --pid=${Process.myPid()} -s PrimitivePreview:I -v raw"
                    )
                ).bufferedReader().use { it.readText() }
                val lastFit = fit.findAll(logs).lastOrNull()
                if (lastFit != null && lastFit.groupValues[1].toInt() == width &&
                    kotlin.math.abs(lastFit.groupValues[2].toFloat() - depthHalf) < .001f) {
                    Log.i("VolumeResizeJourney", "sceneFit width=$width depthHalf=$depthHalf")
                    return true
                }
                SystemClock.sleep(200)
            }
            return false
        }
        try {
            assertTrue("Window did not launch at 2700dp maximum", awaitWidth(maximumPx))
            assertTrue("Maximum rendered depth incorrect", awaitRenderedSize(maximumPx, maximumPx, maximumPx))
            assertTrue("Initial scene did not fit maximum depth", awaitSceneFit(maximumPx, renderedHalfDepth))
            assertTrue("Public resize request rejected", resize(smallerPx, smallerPx))
            assertTrue("Window could not shrink to 1800dp", awaitWidth(smallerPx))
            assertTrue("Rendered volume did not follow smaller cube", awaitRenderedSize(smallerPx, smallerPx, smallerPx))
            assertTrue("Scene did not refit to smaller cube", awaitSceneFit(smallerPx, renderedHalfDepth))
            SystemClock.sleep(2000)
        } finally {
            resize(maximumPx, maximumPx)
        }
        assertTrue("Window did not return to maximum", awaitWidth(maximumPx))
        assertTrue("Rendered depth did not return to maximum", awaitRenderedSize(maximumPx, maximumPx, maximumPx))
        assertTrue("Scene did not refit to maximum depth", awaitSceneFit(maximumPx, renderedHalfDepth))
    }
}
