package app.batch

import app.services.*
import com.nhaarman.mockitokotlin2.*
import org.junit.Before
import org.junit.Test
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.JobExecution
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor
import org.springframework.test.util.ReflectionTestUtils

class JobCompletionNotificationListenerTest {

    @Before
    fun before() {
        reset(successService)
        reset(snsService)
        reset(pushgatewayService)
        reset(postProcessor)
        System.setProperty("topic_name", "db.core.toDo")
    }

    @Test
    fun willNotWriteSuccessIndicatorOnSuccessfulCompletionAndFilesSent() {
        val exportStatusService = mock<ExportStatusService> {
            on { setCollectionStatus() } doReturn CollectionStatus.SENT
            on { sendingCompletionStatus() } doReturn SendingCompletionStatus.COMPLETED_SUCCESSFULLY
        }
        val jobCompletionNotificationListener = jobCompletionNotificationListener(exportStatusService)
        val jobExecution = mock<JobExecution> {
            on { exitStatus } doReturn ExitStatus.COMPLETED
        }
        jobCompletionNotificationListener.afterJob(jobExecution)
        verifyZeroInteractions(successService)
        verify(exportStatusService, times(1)).setCollectionStatus()
        verify(exportStatusService, times(1)).sendingCompletionStatus()
        verifyNoMoreInteractions(exportStatusService)
        verify(pushgatewayService, times(1)).pushFinalMetrics()
        verifyNoMoreInteractions(pushgatewayService)
    }

    @Test
    fun willNotWriteSuccessIndicatorOnSuccessfulCompletionAndNotAllFilesSent() {
        val exportStatusService = mock<ExportStatusService> {
            on { setCollectionStatus() } doReturn CollectionStatus.IN_PROGRESS
            on { sendingCompletionStatus() } doReturn SendingCompletionStatus.COMPLETED_SUCCESSFULLY
        }
        val jobCompletionNotificationListener = jobCompletionNotificationListener(exportStatusService)
        val jobExecution = mock<JobExecution> {
            on { exitStatus } doReturn ExitStatus.COMPLETED
        }
        jobCompletionNotificationListener.afterJob(jobExecution)
        verifyZeroInteractions(successService)
        verify(exportStatusService, times(1)).setCollectionStatus()
        verify(exportStatusService, times(1)).sendingCompletionStatus()
        verifyNoMoreInteractions(exportStatusService)
        verify(pushgatewayService, times(1)).pushFinalMetrics()
        verifyNoMoreInteractions(pushgatewayService)
    }

    @Test
    fun willCallSetStatusOnSuccessfulCompletionAndNoFilesExported() {
        val exportStatusService = mock<ExportStatusService> {
            on { setCollectionStatus() } doReturn CollectionStatus.NO_FILES_EXPORTED
            on { sendingCompletionStatus() } doReturn SendingCompletionStatus.COMPLETED_SUCCESSFULLY
        }
        val jobCompletionNotificationListener = jobCompletionNotificationListener(exportStatusService)
        val jobExecution = mock<JobExecution> {
            on { exitStatus } doReturn ExitStatus.COMPLETED
        }
        jobCompletionNotificationListener.afterJob(jobExecution)
        verify(successService, times(1)).postSuccessIndicator()
        verify(exportStatusService, times(1)).setCollectionStatus()
        verify(exportStatusService, times(1)).sendingCompletionStatus()
        verifyNoMoreInteractions(exportStatusService)
        verify(pushgatewayService, times(1)).pushFinalMetrics()
        verifyNoMoreInteractions(pushgatewayService)
    }

    @Test
    fun willWriteSuccessIndicatorOnSendSuccessIndicatorFlag() {
        val exportStatusService = mock<ExportStatusService>()
        val jobCompletionNotificationListener = jobCompletionNotificationListener(exportStatusService, "true")
        val jobExecution = mock<JobExecution> {
            on { exitStatus } doReturn ExitStatus.COMPLETED
        }
        jobCompletionNotificationListener.afterJob(jobExecution)
        verify(successService, times(1)).postSuccessIndicator()
        verifyZeroInteractions(exportStatusService)
        verify(pushgatewayService, times(1)).pushFinalMetrics()
        verifyNoMoreInteractions(pushgatewayService)
    }

    @Test
    fun willNotWriteSuccessIndicatorOrSendSnsMonitoringMessageOnUnsuccessfulCompletion() {
        val exportStatusService = mock<ExportStatusService>()
        val jobCompletionNotificationListener = jobCompletionNotificationListener(exportStatusService)
        val jobExecution = mock<JobExecution> {
            on { exitStatus } doReturn ExitStatus.FAILED
        }
        jobCompletionNotificationListener.afterJob(jobExecution)
        verifyZeroInteractions(successService)
        verifyZeroInteractions(exportStatusService)
        verifyZeroInteractions(snsService)
        verify(pushgatewayService, times(1)).pushFinalMetrics()
        verifyNoMoreInteractions(pushgatewayService)
    }

    @Test
    fun willSendSuccessSnsMonitoringMessageOnSuccessfulCompletionAndNotSendSuccessIndicator() {
        val exportStatusService = mock<ExportStatusService> {
            on { sendingCompletionStatus() } doReturn SendingCompletionStatus.COMPLETED_SUCCESSFULLY
        }
        val jobCompletionNotificationListener = jobCompletionNotificationListener(exportStatusService)
        val jobExecution = mock<JobExecution> {
            on { exitStatus } doReturn ExitStatus.COMPLETED
        }
        jobCompletionNotificationListener.afterJob(jobExecution)
        verify(exportStatusService, times(1)).sendingCompletionStatus()
        verify(snsService, times(1)).sendMonitoringMessage(SendingCompletionStatus.COMPLETED_SUCCESSFULLY)
        verifyNoMoreInteractions(snsService)
        verify(pushgatewayService, times(1)).pushFinalMetrics()
        verifyNoMoreInteractions(pushgatewayService)
    }

    @Test
    fun willSendFailureSnsMonitoringMessageOnSuccessfulCompletionAndNotSendSuccessIndicator() {
        val exportStatusService = mock<ExportStatusService> {
            on { sendingCompletionStatus() } doReturn SendingCompletionStatus.COMPLETED_UNSUCCESSFULLY
        }
        val jobCompletionNotificationListener = jobCompletionNotificationListener(exportStatusService)
        val jobExecution = mock<JobExecution> {
            on { exitStatus } doReturn ExitStatus.COMPLETED
        }
        jobCompletionNotificationListener.afterJob(jobExecution)
        verify(exportStatusService, times(1)).sendingCompletionStatus()
        verify(snsService, times(1)).sendMonitoringMessage(SendingCompletionStatus.COMPLETED_UNSUCCESSFULLY)
        verifyNoMoreInteractions(snsService)
        verify(pushgatewayService, times(1)).pushFinalMetrics()
        verifyNoMoreInteractions(pushgatewayService)
    }

    @Test
    fun willNotSendSnsMonitoringMessageOnNotCompletedStatus() {
        val exportStatusService = mock<ExportStatusService> {
            on { sendingCompletionStatus() } doReturn SendingCompletionStatus.NOT_COMPLETED
        }
        val jobCompletionNotificationListener = jobCompletionNotificationListener(exportStatusService)
        val jobExecution = mock<JobExecution> {
            on { exitStatus } doReturn ExitStatus.COMPLETED
        }
        jobCompletionNotificationListener.afterJob(jobExecution)
        verify(exportStatusService, times(1)).sendingCompletionStatus()
        verifyZeroInteractions(snsService)
        verify(pushgatewayService, times(1)).pushFinalMetrics()
        verifyNoMoreInteractions(pushgatewayService)
    }

    @Test
    fun willNotSendSnsMonitoringMessageOnSendSuccessIndicator() {
        val exportStatusService = mock<ExportStatusService> {
            on { sendingCompletionStatus() } doReturn SendingCompletionStatus.COMPLETED_SUCCESSFULLY
        }
        val jobCompletionNotificationListener = jobCompletionNotificationListener(exportStatusService, "true")
        val jobExecution = mock<JobExecution> {
            on { exitStatus } doReturn ExitStatus.COMPLETED
        }
        jobCompletionNotificationListener.afterJob(jobExecution)
        verifyZeroInteractions(snsService)
        verify(pushgatewayService, times(1)).pushFinalMetrics()
        verifyNoMoreInteractions(pushgatewayService)
    }

    @Test
    fun willNotSendSnsMonitoringMessageForNifiHeartbeat() {
        val exportStatusService = mock<ExportStatusService> {
            on { sendingCompletionStatus() } doReturn SendingCompletionStatus.COMPLETED_SUCCESSFULLY
        }
        val jobCompletionNotificationListener = jobCompletionNotificationListener(
            exportStatusService, 
            "false", 
            "NIFI_HEARTBEAT"
        )
        val jobExecution = mock<JobExecution> {
            on { exitStatus } doReturn ExitStatus.COMPLETED
        }
        jobCompletionNotificationListener.afterJob(jobExecution)
        verify(exportStatusService, times(1)).sendingCompletionStatus()
        verifyZeroInteractions(snsService)
        verify(pushgatewayService, times(1)).pushFinalMetrics()
        verifyNoMoreInteractions(pushgatewayService)
    }

    private fun jobCompletionNotificationListener(exportStatusService: ExportStatusService,
    sendSuccessIndicator: String = "false", exportDate: String = "2020-25-12"): JobCompletionNotificationListener =
        JobCompletionNotificationListener(successService, exportStatusService,
        snsService, pushgatewayService).apply {
            ReflectionTestUtils.setField(this, "sendSuccessIndicator", sendSuccessIndicator)
            ReflectionTestUtils.setField(this, "exportDate", exportDate)
        }

    private val successService = mock<SuccessService>()

    private val snsService = mock<SnsService>()
    private val pushgatewayService = mock<PushGatewayService>()
    private val postProcessor = mock<ScheduledAnnotationBeanPostProcessor>()
}
