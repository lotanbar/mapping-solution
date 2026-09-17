package com.mappingsolution.ui.image

import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ZipImageFetcherTest {

    @Test
    fun loadsImageEntryFromZipWithSpacesInPath() = runBlocking {
        val dir = Files.createTempDirectory("zip images").toFile()
        try {
            val zip = File(dir, "My Group.zip")
            ZipOutputStream(zip.outputStream()).use { out ->
                out.putNextEntry(ZipEntry("images/pin.png"))
                ImageIO.write(BufferedImage(12, 8, BufferedImage.TYPE_INT_ARGB), "png", out)
                out.closeEntry()
            }
            val context = PlatformContext.INSTANCE
            val request = ImageRequest.Builder(context).data(ZipImageFetcher.uriFor(zip.absolutePath, "pin.png")).build()

            val result = AppImageLoader.create(context).execute(request)

            assertIs<SuccessResult>(result)
            assertEquals(12, result.image.width)
        } finally {
            dir.deleteRecursively()
        }
    }
}
