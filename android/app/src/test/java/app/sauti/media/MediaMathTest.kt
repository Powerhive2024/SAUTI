package app.sauti.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaMathTest {
    @Test fun smallImagesStayTheSameSize() = assertEquals(640 to 480, MediaMath.fit(640, 480))

    @Test fun bigImagesShrinkToLongestSide768() {
        assertEquals(768 to 576, MediaMath.fit(4000, 3000))
        assertEquals(432 to 768, MediaMath.fit(2160, 3840))
    }

    @Test fun badSizesDoNotCrash() = assertEquals(1 to 1, MediaMath.fit(0, 0))

    @Test fun sampleSizeKeepsAtLeastTheTarget() {
        assertEquals(1, MediaMath.sampleSize(1000, 800))
        assertEquals(4, MediaMath.sampleSize(4000, 3000))
        assertTrue(4000 / MediaMath.sampleSize(4000, 3000) >= MediaMath.MAX_SIDE)
    }

    @Test fun framesAreSpreadThroughTheClip() {
        assertEquals(listOf(1_000_000L, 3_000_000L, 5_000_000L, 7_000_000L), MediaMath.frameTimesUs(8_000, 4))
        assertEquals(listOf(0L), MediaMath.frameTimesUs(0, 4))
        assertTrue(MediaMath.frameTimesUs(5_000, 0).isEmpty())
    }

    @Test fun aVideoFitsInOneMessage() = assertTrue(MediaMath.VIDEO_FRAMES <= MediaMath.MAX_IMAGES)
}
