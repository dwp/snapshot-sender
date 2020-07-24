package app.batch

import app.services.ExportStatusService
import app.services.SuccessService
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.listener.JobExecutionListenerSupport
import org.springframework.stereotype.Component
import uk.gov.dwp.dataworks.logging.DataworksLogger

@Component
class JobCompletionNotificationListener(private val successService: SuccessService,
                                        private val exportStatusService: ExportStatusService):
        JobExecutionListenerSupport() {

    override fun afterJob(jobExecution: JobExecution) {
        if (jobExecution.exitStatus.equals(ExitStatus.COMPLETED)) {
            if (exportStatusService.setSentStatus()) {
                successService.postSuccessIndicator()
            }
        }
        else {
            logger.error("Not setting status or sending success indicator",
                    "job_exit_status" to "${jobExecution.exitStatus}")
        }
    }

    companion object {
        val logger = DataworksLogger.getLogger(JobCompletionNotificationListener::class.toString())
    }
}
