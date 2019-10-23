package app.configuration

import app.domain.DecryptedStream
import app.domain.EncryptedStream
import app.exceptions.DataKeyDecryptionException
import app.exceptions.WriterException
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.launch.support.RunIdIncrementer
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
    fun step() =
            stepBuilderFactory.get("step")
                    .chunk<EncryptedStream, DecryptedStream>(10)
                    .reader(itemReader)
                    .faultTolerant()
                    .skip(DataKeyDecryptionException::class.java)
                    .skip(WriterException::class.java)
                    .skipLimit(Integer.MAX_VALUE)
                    .processor(itemProcessor())
                    .writer(itemWriter)
                    .taskExecutor(taskExecutor())
                    .build()

    @Bean
    fun taskExecutor() = SimpleAsyncTaskExecutor("snapshot_sender").apply {
        concurrencyLimit = Integer.parseInt(threadCount)
    }

    fun itemProcessor(): ItemProcessor<EncryptedStream, DecryptedStream> =
        CompositeItemProcessor<EncryptedStream, DecryptedStream>().apply {
            setDelegates(listOf(finishedFilterProcessor, datakeyProcessor, decryptionProcessor))
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
    lateinit var decryptionProcessor: ItemProcessor<EncryptedStream, DecryptedStream>

    @Autowired
    lateinit var itemWriter: ItemWriter<DecryptedStream>

    @Autowired
    lateinit var jobBuilderFactory: JobBuilderFactory

    @Autowired
    lateinit var stepBuilderFactory: StepBuilderFactory
}
