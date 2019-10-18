import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class SnapshotSenderIntegrationTest : StringSpec() {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(SnapshotSenderIntegrationTest::class.toString())
    }

    init {
        "Verify for every source collection a finished file was written to s3" {
            logger.info("Hello Mum 1")

            //s3 in:  test/output/db.core.addressDeclaration-000001.txt.bz2.enc
            //s3 out: test/status/db.core.addressDeclaration-000001.txt.bz2.enc.finished

            // fetch http://s3-dummy:4572/demobucket/status/output/db.core.addressDeclaration-000001.txt.bz2.enc.finished
            // fetch http://s3-dummy:4572/demobucket/status/output/db.core.addressDeclaration-000001.txt.bz2.enc.finished
            //content = "Finished test/output/db.core.addressDeclaration-000001.txt.bz2.enc"
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
