package app.batch

import app.services.*
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.listener.JobExecutionListenerSupport
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import uk.gov.dwp.dataworks.logging.DataworksLogger
import io.prometheus.client.Gauge
import io.prometheus.client.Counter
import uk.gov.dwp.dataworks.logging.LogFields

@Component
class JobCompletionNotificationListener(private val successService: SuccessService,
                                        private val exportStatusService: ExportStatusService,
                                        private val snsService: SnsService,
                                        private val pushGatewayService: PushGatewayService,
                                        private val runningApplicationsGauge: Gauge,
                                        private val failedFilesCounter: Counter,
                                        private val failedSuccessFilesCounter: Counter,
                                        private val failedCollectionsCounter: Counter):
        JobExecutionListenerSupport() {

    override fun beforeJob(jobExecution: JobExecution) {
        LogFields.put("S3_PREFIX", "s3_prefix", s3PrefixFolder)
        runningApplicationsGauge.inc()
        pushGatewayService.pushMetrics()
    }

    override fun afterJob(jobExecution: JobExecution) {
        try {
            if (jobExecution.exitStatus.equals(ExitStatus.COMPLETED)) {
                if (sendSuccessIndicator.toBoolean()) {
                    successService.postSuccessIndicator()
                } else {
                    val status = exportStatusService.setCollectionStatus()
                    if (status == CollectionStatus.NO_FILES_EXPORTED) {
                        successService.postSuccessIndicator()
                    }
                    sendMonitoringSnsMessage()
                }
            }
            else {
                logger.error("Not setting status or sending success indicator",
                        "job_exit_status" to "${jobExecution.exitStatus}")

                if (sendSuccessIndicator.toBoolean()) {
                    failedSuccessFilesCounter.inc() 
                } else {
                    failedFilesCounter.inc()
                }
            }
        } finally {
            runningApplicationsGauge.dec()
            pushGatewayService.pushFinalMetrics()
        }
    }

    private fun sendMonitoringSnsMessage() {
        if (exportDate == "NIFI_HEARTBEAT") {
            logger.info("Not sending monitoring message for nifi heartbeat",
                    "export_date" to exportDate)
        } else {
            when (val completionStatus = exportStatusService.sendingCompletionStatus()) {
                SendingCompletionStatus.COMPLETED_SUCCESSFULLY -> {
                    snsService.sendMonitoringMessage(completionStatus)
                }
                SendingCompletionStatus.COMPLETED_UNSUCCESSFULLY -> {
                    snsService.sendMonitoringMessage(completionStatus)
                    failedCollectionsCounter.inc()
                }
            }
        }
    }

    @Value("\${send.success.indicator:false}")
    private lateinit var sendSuccessIndicator: String

    @Value("\${export.date}")
    private lateinit var exportDate: String

    @Value("\${s3.prefix.folder}")
    lateinit var s3PrefixFolder: String

    companion object {
        val logger = DataworksLogger.getLogger(JobCompletionNotificationListener::class.toString())
    }
}
