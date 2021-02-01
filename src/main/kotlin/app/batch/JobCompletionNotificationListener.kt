package app.batch

import app.services.CollectionStatus
import app.services.ExportStatusService
import app.services.SuccessService
import app.services.SendingCompletionStatus
import app.services.SnsService
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.listener.JobExecutionListenerSupport
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import uk.gov.dwp.dataworks.logging.DataworksLogger

@Component
class JobCompletionNotificationListener(private val successService: SuccessService,
                                        private val exportStatusService: ExportStatusService,
                                        private val snsService: SnsService):
        JobExecutionListenerSupport() {

    override fun afterJob(jobExecution: JobExecution) {
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
        }
    }

    private fun sendMonitoringSnsMessage() {
        when (val completionStatus = exportStatusService.sendingCompletionStatus()) {
            SendingCompletionStatus.COMPLETED_SUCCESSFULLY -> {
                snsService.sendMonitoringMessage(completionStatus)
            }
            SendingCompletionStatus.COMPLETED_UNSUCCESSFULLY -> {
                snsService.sendMonitoringMessage(completionStatus)
            }
        }
    }

    @Value("\${send.success.indicator:false}")
    private lateinit var sendSuccessIndicator: String

    companion object {
        val logger = DataworksLogger.getLogger(JobCompletionNotificationListener::class.toString())
    }
}
