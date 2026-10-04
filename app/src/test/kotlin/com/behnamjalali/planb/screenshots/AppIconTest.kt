package com.behnamjalali.planb.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.R
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the adaptive launcher icon (as a launcher masks it) and the full-bleed 512×512
 * store icon used for the Cafe Bazaar listing, so both always match the shipped artwork.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class)
class AppIconTest {
    private val resources get() = ApplicationProvider.getApplicationContext<android.app.Application>().resources

    @Test
    fun launcherIcon() {
        val icon = ResourcesCompat.getDrawable(resources, R.mipmap.ic_launcher, null) as AdaptiveIconDrawable
        val bitmap = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 432, 432)
        icon.draw(Canvas(bitmap))
        bitmap.captureRoboImage(File(screenshotRoot, "icon/launcher_icon.png").path)
    }

    @Test
    fun monochromeIcon() {
        val mono = ResourcesCompat.getDrawable(resources, R.drawable.ic_launcher_monochrome, null)!!
        val bitmap = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(0xFFE8E2F4.toInt())
            mono.setTint(0xFF3A3178.toInt())
            mono.setBounds(0, 0, 432, 432)
            mono.draw(this)
        }
        bitmap.captureRoboImage(File(screenshotRoot, "icon/launcher_icon_themed.png").path)
    }

    @Test
    fun storeIcon() {
        // The visible launcher area is the inner 72dp of the 108dp layers; render that region at 512px.
        val size = 512
        val full = size * 108 / 72
        val offset = (full - size) / 2
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        for (layer in listOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground)) {
            ResourcesCompat.getDrawable(resources, layer, null)!!.apply {
                setBounds(-offset, -offset, full - offset, full - offset)
                draw(canvas)
            }
        }
        bitmap.captureRoboImage(File(screenshotRoot.parentFile.parentFile, "store/cafebazaar/graphics/icon-512.png").path)
    }
}
