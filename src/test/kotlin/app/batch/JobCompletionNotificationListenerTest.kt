package app.batch

import app.services.CollectionStatus
import app.services.ExportStatusService
import app.services.SuccessService
import app.services.SendingCompletionStatus
import app.services.SnsService
import com.nhaarman.mockitokotlin2.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.JobExecution
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.util.ReflectionTestUtils

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [JobCompletionNotificationListener::class])
class JobCompletionNotificationListenerTest {

    @SpyBean
    @Autowired
    private lateinit var jobCompletionNotificationListener: JobCompletionNotificationListener

    @MockBean
    private lateinit var exportStatusService: ExportStatusService

    @MockBean
    private lateinit var successService: SuccessService

    @MockBean
    private lateinit var snsService: SnsService

    @Before
    fun setUp() {
        System.setProperty("topic_name", "db.core.toDo")
    }

    @Test
    fun willNotWriteSuccessIndicatorOnSuccessfulCompletionAndFilesSent() {
        val jobExecution = mock<JobExecution> {
            on { exitStatus } doReturn ExitStatus.COMPLETED
        }
        given(exportStatusService.setCollectionStatus()).willReturn(CollectionStatus.SENT)
        given(exportStatusService.sendingCompletionStatus()).willReturn(SendingCompletionStatus.COMPLETED_SUCCESSFULLY)
        jobCompletionNotificationListener.afterJob(jobExecution)
        verifyZeroInteractions(successService)
        verify(exportStatusService, times(1)).setCollectionStatus()
        verifyNoMoreInteractions(exportStatusService)
    }

    @Test
    fun willNotWriteSuccessIndicatorOnSuccessfulCompletionAndNotAllFilesSent() {
        val jobExecution = mock<JobExecution> {
            on { exitStatus } doReturn ExitStatus.COMPLETED
        }
        given(exportStatusService.setCollectionStatus()).willReturn(CollectionStatus.IN_PROGRESS)
        given(exportStatusService.sendingCompletionStatus()).willReturn(SendingCompletionStatus.COMPLETED_SUCCESSFULLY)
        jobCompletionNotificationListener.afterJob(jobExecution)
        verifyZeroInteractions(successService)
        verify(exportStatusService, times(1)).setCollectionStatus()
        verifyNoMoreInteractions(exportStatusService)
    }

    @Test
    fun willCallSetStatusOnSuccessfulCompletionAndNoFilesExported() {
        val jobExecution = mock<JobExecution> {
            on { exitStatus } doReturn ExitStatus.COMPLETED
        }
        given(exportStatusService.setCollectionStatus()).willReturn(CollectionStatus.NO_FILES_EXPORTED)
        given(exportStatusService.sendingCompletionStatus()).willReturn(SendingCompletionStatus.COMPLETED_SUCCESSFULLY)
        jobCompletionNotificationListener.afterJob(jobExecution)
        verify(successService, times(1)).postSuccessIndicator()
        verify(exportStatusService, times(1)).setCollectionStatus()
        verifyNoMoreInteractions(exportStatusService)
    }

    @Test
    fun willWriteSuccessIndicatorOnSendSuccessIndicatorFlag() {
        ReflectionTestUtils.setField(jobCompletionNotificationListener, "sendSuccessIndicator", "true")
        val jobExecution = mock<JobExecution> {
            on { exitStatus } doReturn ExitStatus.COMPLETED
        }
        jobCompletionNotificationListener.afterJob(jobExecution)
        verify(successService, times(1)).postSuccessIndicator()
        verifyZeroInteractions(exportStatusService)
        ReflectionTestUtils.setField(jobCompletionNotificationListener, "sendSuccessIndicator", "false")
    }

    @Test
    fun willNotWriteSuccessIndicatorOrSendSnsMonitoringMessageOnUnsuccessfulCompletion() {
        val jobExecution = mock<JobExecution> {
            on { exitStatus } doReturn ExitStatus.FAILED
        }
        jobCompletionNotificationListener.afterJob(jobExecution)
        verifyZeroInteractions(successService)
        verifyZeroInteractions(exportStatusService)
        verifyZeroInteractions(snsService)
    }

    @Test
    fun willSendSuccessSnsMonitoringMessageOnSuccessfulCompletionAndNotSendSuccessIndicator() {
        val jobExecution = mock<JobExecution> {
            on { exitStatus } doReturn ExitStatus.COMPLETED
        }
        given(exportStatusService.sendingCompletionStatus()).willReturn(SendingCompletionStatus.COMPLETED_SUCCESSFULLY)
        jobCompletionNotificationListener.afterJob(jobExecution)
        verify(exportStatusService, times(1)).sendingCompletionStatus()
        verify(snsService, times(1)).sendMonitoringMessage(SendingCompletionStatus.COMPLETED_SUCCESSFULLY)
        verifyNoMoreInteractions(snsService)
    }

    @Test
    fun willSendFailureSnsMonitoringMessageOnSuccessfulCompletionAndNotSendSuccessIndicator() {
        val jobExecution = mock<JobExecution> {
            on { exitStatus } doReturn ExitStatus.COMPLETED
        }
        given(exportStatusService.sendingCompletionStatus()).willReturn(SendingCompletionStatus.COMPLETED_UNSUCCESSFULLY)
        jobCompletionNotificationListener.afterJob(jobExecution)
        verify(exportStatusService, times(1)).sendingCompletionStatus()
        verify(snsService, times(1)).sendMonitoringMessage(SendingCompletionStatus.COMPLETED_UNSUCCESSFULLY)
        verifyNoMoreInteractions(snsService)
    }

    @Test
    fun willNotSendSnsMonitoringMessageOnNotCompletedStatus() {
        val jobExecution = mock<JobExecution> {
            on { exitStatus } doReturn ExitStatus.COMPLETED
        }
        given(exportStatusService.sendingCompletionStatus()).willReturn(SendingCompletionStatus.NOT_COMPLETED)
        jobCompletionNotificationListener.afterJob(jobExecution)
        verify(exportStatusService, times(1)).sendingCompletionStatus()
        verifyZeroInteractions(snsService)
    }

    @Test
    fun willNotSendSnsMonitoringMessageOnSendSuccessIndicator() {
        ReflectionTestUtils.setField(jobCompletionNotificationListener, "sendSuccessIndicator", "true")
        val jobExecution = mock<JobExecution> {
            on { exitStatus } doReturn ExitStatus.COMPLETED
        }
        given(exportStatusService.sendingCompletionStatus()).willReturn(SendingCompletionStatus.COMPLETED_SUCCESSFULLY)
        jobCompletionNotificationListener.afterJob(jobExecution)
        verifyZeroInteractions(snsService)
        ReflectionTestUtils.setField(jobCompletionNotificationListener, "sendSuccessIndicator", "false")
    }
}
