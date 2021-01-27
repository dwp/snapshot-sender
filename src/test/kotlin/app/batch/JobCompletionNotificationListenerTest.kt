package app.batch

import app.services.CollectionStatus
import app.services.ExportStatusService
import app.services.SuccessService
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
    fun willNotWriteSuccessIndicatorOnUnsuccessfulCompletion() {
        val jobExecution = mock<JobExecution> {
            on { exitStatus } doReturn ExitStatus.FAILED
        }
        jobCompletionNotificationListener.afterJob(jobExecution)
        verifyZeroInteractions(successService)
        verifyZeroInteractions(exportStatusService)
    }
}
