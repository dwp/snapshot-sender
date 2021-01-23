package app.utils

import app.exceptions.MetadataException

object TextParsingUtility {

    fun databaseAndCollection(filename: String) =
        inputFilenameRe.find(filename)?.let(MatchResult::destructured) ?:
        throw MetadataException("Rejecting '$filename': does not match '${inputFilenameRe.pattern}'")

    private val inputFilenameRe = Regex("""^(?:\w+\.)?(?<database>[\w-]+)\.(?<collection>[\w-]+)-\d{3}-\d{3}-\d+\.\w+\.\w+$""")
}
