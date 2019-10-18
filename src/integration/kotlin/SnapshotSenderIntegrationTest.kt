import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import org.apache.http.client.fluent.Request
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SnapshotSenderIntegrationTest : StringSpec() {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(SnapshotSenderIntegrationTest::class.toString())
    }

    /*
    s3.prefix.folder: "test/output/"
    s3.htme.root.folder: "test"
    s3.status.folder: "status"
    s3.service.endpoint: "http://s3-dummy:4572"
    nifi.root.folder: "/data/output"
    nifi.timestamp: "10"
    nifi.file.names: "/data/output/db.core.addressDeclaration/db.core.addressDeclaration-000001.txt.bz2"
    nifi.file.linecounts: "7"
     */

    init {
        "Verify for every source collection a finished file was written to s3" {
            logger.info("Hello Mum 1")

            //s3 in:  test/output/db.core.addressDeclaration-000001.txt.bz2.enc
            //s3 out: test/status/db.core.addressDeclaration-000001.txt.bz2.enc.finished

            // fetch http://s3-dummy:4572/demobucket/status/output/db.core.addressDeclaration-000001.txt.bz2.enc.finished
            // fetch http://s3-dummy:4572/demobucket/status/output/db.core.addressDeclaration-000001.txt.bz2.enc.finished
            //content = "Finished test/output/db.core.addressDeclaration-000001.txt.bz2.enc"
            val results = Request.Get("http://s3-dummy:4572/demobucket")
                .connectTimeout(1000)
                .socketTimeout(1000)
                .execute().returnContent().asString()
            logger.info(results)
        }

        "Verify for every source collection an output file was sent to nifi as bz2 with valid json lines at expected timestamp" {
            logger.info("Hello Mum 1")

            //s3 in:    test/output/db.core.addressDeclaration-000001.txt.bz2.enc
            //nifi out: /data/output/db.core.addressDeclaration/db.core.addressDeclaration-000001.txt.bz2

            //     command: "-file /data/output/db.core.addressDeclaration/db.core.addressDeclaration-000001.txt.bz2 \
            //              -timestamp 10 \

        }

        "Verify addressDeclaration output file in nifi has 7 lines" {
            logger.info("Hello Mum 2")
            //     command: "-file /data/output/db.core.addressDeclaration/db.core.addressDeclaration-000001.txt.bz2 \
            //              -linecount 7"
        }
    }
}
