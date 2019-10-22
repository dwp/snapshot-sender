package app.batch

import org.junit.Assert.assertEquals
import org.junit.Test

class S3UtilsTest {

    @Test
    fun should_calculate_finished_status_file_name_from_htme_file_name() {
        var s3utils: S3Utils = S3Utils()
        val htmeFileName = "business-data-export/JobNumber/1990-01-31/myfilename.00001.txt.bz2.enc"
        val htmeRootFolder = "business-data-export"
        val statusFolder = "business-sender-status"

        val actual = s3utils.getFinishedStatusKeyName(htmeFileName, htmeRootFolder, statusFolder)
        assertEquals("business-sender-status/JobNumber/1990-01-31/myfilename.00001.txt.bz2.enc.finished", actual)
    }

}
