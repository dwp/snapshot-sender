package app.utils.logging

/**
This file had simple json logging utils extending SLF4J in a standard way.
It brings all our changes into one code module and one test file, and allows us to make a simple override in the
resources/logback.xml file so that all vagaries are in code and unit-testable; one "just" does this:

<appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
<encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
<layout class="app.utils.logging.LoggerLayoutAppender" />
</encoder>
</appender>

See http://logback.qos.ch/manual/layouts.html for examples.
 */

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.CoreConstants
import ch.qos.logback.core.LayoutBase
import org.apache.commons.text.StringEscapeUtils
import org.slf4j.Logger
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.*

private val UNSET_TEXT = "NOT_SET"
private val defaultFormat = makeUtcDateFormat() // 2001-07-04T12:08:56.235

private var hostname = InetAddress.getLocalHost().hostName
private var environment = System.getProperty("environment", UNSET_TEXT)
private var application = System.getProperty("application", UNSET_TEXT)
private var app_version = System.getProperty("app_version", UNSET_TEXT)
private var component = System.getProperty("component", UNSET_TEXT)
private var correlation_id = System.getProperty("correlation_id", UNSET_TEXT)
private var staticData = makeLoggerStaticDataTuples()

class LogConfiguration {
    companion object {
        var start_time_milliseconds = System.currentTimeMillis()
    }
}

fun makeUtcDateFormat(): SimpleDateFormat {
    // 2001-07-04T12:08:56.235
    val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
    df.timeZone = TimeZone.getTimeZone("UTC")
    return df
}

fun makeLoggerStaticDataTuples(): String {
    return "\"hostname\":\"$hostname\", " +
        "\"environment\":\"$environment\", " +
        "\"application\":\"$application\", " +
        "\"app_version\":\"$app_version\", " +
        "\"component\":\"$component\", " +
        "\"correlation_id\":\"$correlation_id\""
}

fun resetLoggerStaticFieldsForTests() {
    hostname = InetAddress.getLocalHost().hostName
    environment = System.getProperty("environment", UNSET_TEXT)
    application = System.getProperty("application", UNSET_TEXT)
    app_version = System.getProperty("app_version", UNSET_TEXT)
    component = System.getProperty("component", UNSET_TEXT)
    correlation_id = System.getProperty("correlation_id", UNSET_TEXT)
    staticData = makeLoggerStaticDataTuples()
}

fun overrideLoggerStaticFieldsForTests(host: String, env: String, app: String, version: String, comp: String, start_milliseconds: String, id: String) {
    hostname = host
    environment = env
    application = app
    app_version = version
    component = comp
    LogConfiguration.start_time_milliseconds = start_milliseconds.toLong()
    correlation_id = id
    staticData = makeLoggerStaticDataTuples()
}

fun logDebug(logger: Logger, message: String, vararg tuples: String) {
    if (logger.isDebugEnabled) {
        val semiFormatted = semiFormattedTuples(message, *tuples)
        logger.debug(semiFormatted)
    }
}

fun logInfo(logger: Logger, message: String, vararg tuples: String) {
    if (logger.isInfoEnabled) {
        val semiFormatted = semiFormattedTuples(message, *tuples)
        logger.info(semiFormatted)
    }
}

fun logWarn(logger: Logger, message: String, vararg tuples: String) {
    if (logger.isWarnEnabled) {
        val semiFormatted = semiFormattedTuples(message, *tuples)
        logger.warn(semiFormatted)
    }
}

fun logError(logger: Logger, message: String, vararg tuples: String) {
    if (logger.isErrorEnabled) {
        val semiFormatted = semiFormattedTuples(message, *tuples)
        logger.error(semiFormatted)
    }
}

fun logError(logger: Logger, message: String, error: Throwable, vararg tuples: String) {
    if (logger.isErrorEnabled) {
        val semiFormatted = semiFormattedTuples(message, *tuples)
        logger.error(semiFormatted, error)
    }
}

fun semiFormattedTuples(message: String, vararg tuples: String): String {
    val semiFormatted = StringBuilder(StringEscapeUtils.escapeJson(message))
    if (tuples.isEmpty()) {
        return semiFormatted.toString()
    }
    if (tuples.size % 2 != 0) {
        throw IllegalArgumentException("Must have matched key-value pairs but had ${tuples.size} argument(s)")
    }
    for (i in tuples.indices step 2) {
        val key = tuples[i]
        val value = tuples[i + 1]
        val escapedValue = StringEscapeUtils.escapeJson(value)
        semiFormatted.append("\", \"")
        semiFormatted.append(key)
        semiFormatted.append("\":\"")
        semiFormatted.append(escapedValue)
    }
    return semiFormatted.toString()
}

fun formattedTimestamp(epochTime: Long): String {
    try {
        synchronized(defaultFormat) {
            val netDate = Date(epochTime)
            return defaultFormat.format(netDate)
        }
    }
    catch (e: Exception) {
        throw e
    }
}

fun flattenMultipleLines(text: String?): String {
    if (text == null) {
        return "null"
    }
    return try {
        text.replace("\n", " | ").replace("\t", " ")
    }
    catch (ex: java.lang.Exception) {
        text
    }
}

fun inlineStackTrace(text: String): String {
    return try {
        StringEscapeUtils.escapeJson(flattenMultipleLines(text))
    }
    catch (ex: java.lang.Exception) {
        text
    }
}

fun throwableProxyEventToString(event: ILoggingEvent): String {
    val throwableProxy = event.throwableProxy
    return if (throwableProxy != null) {
        val stackTrace = ThrowableProxyUtil.asString(throwableProxy)
        val oneLineTrace = inlineStackTrace(stackTrace)
        "\"exception\":\"$oneLineTrace\", "
    }
    else {
        ""
    }
}

fun getDurationInMilliseconds(epochTime: Long): String {
    val elapsedMilliseconds = epochTime - LogConfiguration.start_time_milliseconds
    return elapsedMilliseconds.toString()
}

class LoggerLayoutAppender : LayoutBase<ILoggingEvent>() {

    override fun doLayout(event: ILoggingEvent?): String {
        if (event == null) {
            return ""
        }
        val dateTime = formattedTimestamp(event.timeStamp)
        val builder = StringBuilder()
        builder.append("{ ")
        builder.append("\"timestamp\":\"")
        builder.append(dateTime)
        builder.append("\", \"log_level\":\"")
        builder.append(event.level)
        builder.append("\", \"message\":\"")
        builder.append(flattenMultipleLines(event.formattedMessage))
        builder.append("\", ")
        builder.append(throwableProxyEventToString(event))
        builder.append("\"thread\":\"")
        builder.append(event.threadName)
        builder.append("\", \"logger\":\"")
        builder.append(event.loggerName)
        builder.append("\", \"duration_in_milliseconds\":\"")
        builder.append(getDurationInMilliseconds(event.timeStamp))
        builder.append("\", ")
        builder.append(staticData)
        builder.append(" }")
        builder.append(CoreConstants.LINE_SEPARATOR)
        return builder.toString()
    }
}
