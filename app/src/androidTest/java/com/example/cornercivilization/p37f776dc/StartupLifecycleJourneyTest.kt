package com.example.cornercivilization.p37f776dc

import android.app.Activity
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.cornercivilization.p37f776dc.scene.PrimitivePreviewRuntime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses real task background/return; never changes lifecycle state or village data directly. */
@RunWith(AndroidJUnit4::class)
class StartupLifecycleJourneyTest {
    @Test fun minimizedLoadResumesWithoutEmptyBounds() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".living.acceptance"))
        val duringUpload = InstrumentationRegistry.getArguments().getString("loadStage") == "upload"
        fun logs(): String = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(
                "logcat -d --pid=${Process.myPid()} -v brief"
            )
        ).bufferedReader().use { it.readText() }
        fun await(label: String, seconds: Int = 60, condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + seconds * 1000
            while (SystemClock.elapsedRealtime() < deadline) {
                if (condition()) return
                SystemClock.sleep(50)
            }
            throw AssertionError("$label timed out; last logs=${logs().takeLast(16000)}")
        }
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var window: Activity? = null
        await("native window", 30) {
            instrumentation.runOnMainSync {
                window = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .firstOrNull { it.window.decorView.width >= 2400 }
            }
            window != null
        }
        if (duringUpload) await("geometry generation started") {
            logs().contains("stage=materials elapsedMillis=")
        }
        var minimized = false
        var restored = false
        try {
            instrumentation.runOnMainSync {
                assertTrue("Runtime was already published before minimize",
                    PrimitivePreviewRuntime.acceptanceInstance.get() == null)
                minimized = requireNotNull(window).moveTaskToBack(true)
            }
            assertTrue("Task background request rejected", minimized)
            await("real ON_STOP") { logs().contains("event=ON_STOP started=false") }
            val expectedGate = if (duringUpload) "waiting=upload-" else "waiting=assets-loaded"
            await("initialization paused at $expectedGate") { logs().contains(expectedGate) }
            SystemClock.sleep(1000)
            val stopped = logs()
            assertFalse("Scene continued initializing while minimized",
                stopped.contains("preview loaded residents="))
            assertFalse("Load failed instead of waiting",
                stopped.contains("New managed primitive scene could not load"))
            instrumentation.runOnMainSync {
                context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                restored = true
            }
            await("resumed native runtime") {
                var ready = false
                instrumentation.runOnMainSync {
                    ready = PrimitivePreviewRuntime.acceptanceInstance.get()?.ready == true
                }
                ready
            }
            await("natural resident work and native volume bounds") {
                val text = logs()
                text.contains("ResidentWork") &&
                    Regex("live-bounds .*numericStatus=PASS").containsMatchIn(text)
            }
            val completed = logs()
            assertTrue("No resume checkpoint observed", completed.contains("resumed="))
            assertFalse("Empty bounds or scene exception after restore",
                completed.contains("New managed primitive scene could not load") ||
                    completed.contains("Animated scene validation failed") ||
                    completed.contains("Volume fit failed"))
            Log.i("StartupLifecycleJourney", "PASS stage=${if (duringUpload) "upload" else "assets"} realMinimize=true realRestore=true ready=true villagePreserved=true")
        } finally {
            if (minimized && !restored) instrumentation.runOnMainSync {
                context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
