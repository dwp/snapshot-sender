package app.utils

import app.exceptions.MetadataException
import org.junit.Assert.assertEquals
import org.junit.Test


class TextParsingUtilityTest {

    @Test
    fun shouldParseNameWithNoHyphens() {
        verifyParsed("core", "addressDeclaration", "db.core.addressDeclaration-001-002-000001.txt.gz")
    }

    @Test
    fun shouldParseNameWithNoHyphensNoPrefix() {
        verifyParsed("core", "addressDeclaration", "core.addressDeclaration-045-050-000001.txt.gz")
    }

    @Test
    fun shouldParseNameWithHyphensInDatabase() {
        verifyParsed(
            DATABASE_WITH_HYPHEN, "addressDeclaration",
            "db.core-with-hyphen.addressDeclaration-045-050-000001.txt.gz")
    }

    @Test
    fun shouldParseNameWithHyphensInDatabaseNoPrefix() {
        verifyParsed(
            DATABASE_WITH_HYPHEN, "addressDeclaration",
            "core-with-hyphen.addressDeclaration-045-050-000001.txt.gz")
    }


    @Test
    fun shouldParseNameWithHyphensInDatabaseAndCollection() {
        verifyParsed(
            DATABASE_WITH_HYPHEN, "address-declaration-has-hyphen",
            "db.core-with-hyphen.address-declaration-has-hyphen-045-050-000001.txt.gz")
    }

    @Test
    fun shouldParseNameWithHyphensInDatabaseAndCollectionNoPrefix() {
        verifyParsed(
            DATABASE_WITH_HYPHEN, "address-declaration-has-hyphen",
            "core-with-hyphen.address-declaration-has-hyphen-045-050-000001.txt.gz")
    }

    @Test(expected = MetadataException::class)
    fun shouldRejectNonMatchingFilename() {
        parse("dbcoreaddressDeclaration-000001")
    }


    @Test(expected = MetadataException::class)
    fun shouldRejectNonMatchingFilenameNoDashBeforeFinalNumber() {
        parse("db.core.address-045-05001.txt")
    }

    @Test(expected = MetadataException::class)
    fun shouldRejectNonMatchingFilenameNoExtension() {
        parse("bad_filename-045-050-000001")
    }

    @Test(expected = MetadataException::class)
    fun shouldRejectNonMatchingFilenameNoHyphens() {
        parse("db.type.nonum.txt.gz")
    }

    private fun verifyParsed(expectedDatabase: String, expectedCollection: String, filename: String) {
        parse(filename).let { (actualDatabase, actualCollection) ->
            assertEquals(expectedDatabase, actualDatabase)
            assertEquals(expectedCollection, actualCollection)
        }
    }

    private fun parse(filename: String) = TextParsingUtility.databaseAndCollection(filename)

    companion object {
        private const val DATABASE_WITH_HYPHEN = "core-with-hyphen"
    }
}
