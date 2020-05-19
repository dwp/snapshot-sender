package app.batch

import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.services.s3.model.S3ObjectInputStream
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.apache.http.client.methods.HttpGet
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class S3UtilsTest {

    @Test
    fun testContentsCopiedFaithfully() {
        val expected = "EXPECTED TEXT"
        val inputStream = S3ObjectInputStream(ByteArrayInputStream(expected.toByteArray()), HttpGet())
        val s3Object = mock<S3Object> {
            on { objectContent } doReturn inputStream
        }
        val bufferedStream = S3Utils().objectContents(s3Object)
        assertEquals(expected, String(bufferedStream))
    }

    @Test
    fun testNullArgTolerated() {
        val expected = ""
        val bufferedStream = S3Utils().objectContents(null)
        assertEquals(expected, String(bufferedStream))
    }

    @Test
    fun should_calculate_finished_status_file_name_from_htme_file_name() {
        val s3utils: S3Utils = S3Utils()
        val htmeFileName = "business-data-export/JobNumber/1990-01-31/myfilename.00001.txt.bz2.enc"
        val htmeRootFolder = "business-data-export"
        val statusFolder = "business-sender-status"

        val actual = s3utils.getFinishedStatusKeyName(htmeFileName, htmeRootFolder, statusFolder)
        assertEquals("business-sender-status/JobNumber/1990-01-31/myfilename.00001.txt.bz2.enc.finished", actual)
    }

}
