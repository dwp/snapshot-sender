package app.batch

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
    fun willWriteSuccessIndicatorOnSuccessfulCompletionAndAllFilesSent() {
        val jobExecution = mock<JobExecution> {
            on { exitStatus } doReturn ExitStatus.COMPLETED
        }
        given(exportStatusService.setSentStatus()).willReturn(true)
        jobCompletionNotificationListener.afterJob(jobExecution)
        verify(successService, times(1)).postSuccessIndicator()
    }

    @Test
    fun willNotWriteSuccessIndicatorOnSuccessfulCompletionAndNotAllFilesSent() {
        val jobExecution = mock<JobExecution> {
            on { exitStatus } doReturn ExitStatus.COMPLETED
        }
        given(exportStatusService.setSentStatus()).willReturn(false)
        jobCompletionNotificationListener.afterJob(jobExecution)
        verifyZeroInteractions(successService)
    }

    @Test
    fun willNotWriteSuccessIndicatorOnUnsuccessfulCompletion() {
        val jobExecution = mock<JobExecution> {
            on { exitStatus } doReturn ExitStatus.FAILED
        }
        jobCompletionNotificationListener.afterJob(jobExecution)
        verifyZeroInteractions(successService)
    }
}
