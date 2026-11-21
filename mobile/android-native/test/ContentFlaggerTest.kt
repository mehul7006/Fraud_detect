package com.fraudshield.app

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Roadmap 5.6: fixtures are built in memory so no binary files sit in the repo. */
class ContentFlaggerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun pdf(body: String) = "%PDF-1.4\n1 0 obj\n<< $body >>\nendobj\ntrailer\n%%EOF".toByteArray(Charsets.ISO_8859_1)

    private fun zip(vararg names: String): ByteArray {
        val bo = ByteArrayOutputStream()
        ZipOutputStream(bo).use { z -> names.forEach { z.putNextEntry(ZipEntry(it)); z.write("x".toByteArray()); z.closeEntry() } }
        return bo.toByteArray()
    }

    @Test fun cleanPdfHasNoFlags() = assertTrue(ContentFlagger.flagsOf(pdf("/Type /Catalog /Pages 2 0 R")).isEmpty())

    @Test fun pdfWithJavaScript() = assertEquals(listOf("PDF_JS"), ContentFlagger.flagsOf(pdf("/Type /Action /S /JavaScript /JS (app.alert(1))")))

    @Test fun pdfWithShortJsKey() = assertEquals(listOf("PDF_JS"), ContentFlagger.flagsOf(pdf("/S /JavaScript")))

    @Test fun pdfWithLaunch() = assertTrue(ContentFlagger.flagsOf(pdf("/Type /Action /S /Launch /F (cmd.exe)")).contains("PDF_LAUNCH"))

    @Test fun pdfWithOpenAction() = assertEquals(listOf("PDF_OPENACTION"), ContentFlagger.flagsOf(pdf("/OpenAction 5 0 R")))

    @Test fun jsonLikeNameIsNotJavaScript() = assertTrue(ContentFlagger.flagsOf(pdf("/JSONData 1 /JSX 2")).isEmpty())

    @Test fun docxWithoutMacroIsClean() = assertTrue(ContentFlagger.flagsOf(zip("word/document.xml", "[Content_Types].xml")).isEmpty())

    @Test fun docmWithVbaProjectIsFlagged() = assertEquals(listOf("OFFICE_MACRO"), ContentFlagger.flagsOf(zip("word/document.xml", "word/vbaProject.bin")))

    @Test fun oldDocWithVbaStreamIsFlagged() {
        val ole = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()) +
            ByteArray(100) + "VBA".toByteArray(Charsets.UTF_16LE) + ByteArray(50)
        assertEquals(listOf("OFFICE_MACRO"), ContentFlagger.flagsOf(ole))
    }

    @Test fun plainTextAndEmptyHaveNoFlags() {
        assertTrue(ContentFlagger.flagsOf("hello /JavaScript".toByteArray()).isEmpty())
        assertTrue(ContentFlagger.flagsOf(ByteArray(0)).isEmpty())
    }

    @Test fun onlyTheFirst64KbAreRead() {
        val big = ByteArray(ContentFlagger.LIMIT + 100)
        val head = "%PDF-1.4\n".toByteArray()
        head.copyInto(big)
        "/Launch".toByteArray().copyInto(big, ContentFlagger.LIMIT + 10) // beyond the limit
        val f = tmp.newFile("big.pdf"); f.writeBytes(big)
        assertTrue(ContentFlagger.flags(f).isEmpty())
    }

    @Test fun flagsFromAFileOnDisk() {
        val f = tmp.newFile("x.pdf"); f.writeBytes(pdf("/S /Launch"))
        assertEquals(listOf("PDF_LAUNCH"), ContentFlagger.flags(f))
    }
}
