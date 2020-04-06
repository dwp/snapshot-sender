package app.configuration

import app.domain.DecryptedStream
import app.domain.EncryptedStream
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.step.tasklet.TaskletStep
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemReader
import org.springframework.batch.item.ItemWriter
import org.springframework.batch.item.support.CompositeItemProcessor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.retry.policy.SimpleRetryPolicy

@Configuration
@EnableBatchProcessing
@Profile("batchRun")
class JobConfiguration {

    @Bean
    fun importUserJob(step: Step) =
        jobBuilderFactory.get("snapshotSenderJob")
            .incrementer(RunIdIncrementer())
            .flow(step)
            .end()
            .build()

    @Bean
    fun step() = stepBuilderFactory.get("step")
            .chunk<EncryptedStream, DecryptedStream>(chunkSize.toInt())
            .reader(itemReader)
            .processor(itemProcessor())
            .writer(itemWriter)
            .faultTolerant()
            .retry(Exception::class.java)
            .retryPolicy(SimpleRetryPolicy().apply {
                maxAttempts = maxRetries.toInt()
            })
            .taskExecutor(taskExecutor())
            .build()

    @Bean
    fun taskExecutor() = SimpleAsyncTaskExecutor("snapshot_sender").apply {
        concurrencyLimit = Integer.parseInt(threadCount)
    }

    fun itemProcessor(): ItemProcessor<EncryptedStream, DecryptedStream> =
        CompositeItemProcessor<EncryptedStream, DecryptedStream>().apply {
            setDelegates(listOf(finishedFilterProcessor, bufferingProcessor, datakeyProcessor, decryptionProcessor))
        }

    @Value("\${thread.count:10}")
    lateinit var threadCount: String

    @Autowired
    lateinit var itemReader: ItemReader<EncryptedStream>

    @Autowired
    @Qualifier("filter")
    lateinit var finishedFilterProcessor: ItemProcessor<EncryptedStream, EncryptedStream>

    @Autowired
    @Qualifier("datakey")
    lateinit var datakeyProcessor: ItemProcessor<EncryptedStream, EncryptedStream>

    @Autowired
    @Qualifier("buffer")
    lateinit var bufferingProcessor: ItemProcessor<EncryptedStream, EncryptedStream>

    @Autowired
    lateinit var decryptionProcessor: ItemProcessor<EncryptedStream, DecryptedStream>

    @Autowired
    lateinit var itemWriter: ItemWriter<DecryptedStream>

    @Autowired
    lateinit var jobBuilderFactory: JobBuilderFactory

    @Autowired
    lateinit var stepBuilderFactory: StepBuilderFactory


    @Value("\${s3.max.retries:100}")
    private lateinit var maxRetries: String

    @Value("\${chunk.size:1}")
    private lateinit var chunkSize: String
}
