package app.configuration

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Metrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.exporter.PushGateway
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.annotation.PostConstruct

@Configuration
class MetricsConfiguration {

    @Bean
    fun successPostFileCounter(): Counter =
        counter("snapshot_sender_files_posted_successfully", "Total number of files successfully posted to NiFi.", "file_name")

    @Bean
    fun retriedPostFilesCounter(): Counter =
        counter("snapshot_sender_files_retried_post", "Total number of files retried posting to NiFi.", "file_name")

    @Bean
    fun rejectedFilesCounter(): Counter =
        counter("snapshot_sender_rejected_files", "Number of files rejected.", "file_name")

    @Bean
    fun blockedTopicFileCounter(): Counter =
        counter("snapshot_sender_blocked_topic_files", "Number of files blocked due to topic being blocked.", "file_name")

    @Bean
    fun s3ItemsCounter(): Counter =
        counter("snapshot_sender_items_read_from_s3", "Number of items read from s3 for incoming prefix.", "s3_prefix")

    @Bean
    fun sentNonEmptyCollectionCounter(): Counter =
        counter("snapshot_sender_completed_non_empty_collections", "Number of completed collections with files to send.")

    @Bean
    fun sentEmptyCollectionCounter(): Counter =
        counter("snapshot_sender_completed_empty_collections", "Number of completed collections with no files to send.")

    @Bean
    fun filesSentIncrementedCounter(): Counter =
        counter("snapshot_sender_incremented_files_sent", "Number of times files sent was incremented in status table.")

    @Bean
    fun successfulFullRunCounter(): Counter =
        counter("snapshot_sender_successful_runs", "Number of failed full successful runs.")

    @Bean
    fun failedFullRunCounter(): Counter =
        counter("snapshot_sender_failed_runs", "Number of full failed runs.")

    @Bean
    fun keysDecryptedCounter(): Counter =
        counter("snapshot_sender_dks_keys_decrypted", "Number of successful DKS key decryptions.")

    @Bean
    fun keyDecryptionRetriesCounter(): Counter =
        counter("snapshot_sender_dks_key_decryption_retries", "Number of DKS key decryption calls retried.")

    @Bean
    fun monitoringMessagesSentCounter(): Counter =
        counter("snapshot_sender_monitoring_messages_sent", "Number of monitoring messages successfully sent.", "severity", "notification_type")

    @Bean
    fun successFilesSentCounter(): Counter =
        counter("snapshot_sender_success_files_sent", "Number of success files sent.")

    @Bean
    fun successFilesRetriedCounter(): Counter =
        counter("snapshot_sender_success_file_sending_retries", "Number of success file sends retried.")

    @Bean
    fun runningApplicationsGuage(): Gauge =
        gauge("snapshot_sender_running_applications", "Number of running applications.")

    @Bean
    fun pushGateway(): PushGateway = PushGateway("$pushgatewayHost:$pushgatewayPort")


    @PostConstruct
    fun init() {
        Metrics.globalRegistry.add(PrometheusMeterRegistry(PrometheusConfig.DEFAULT, CollectorRegistry.defaultRegistry, Clock.SYSTEM))
    }

    @Synchronized
    private fun counter(name: String, help: String, vararg labels: String): Counter =
            with (Counter.build()) {
                name(name)
                labelNames(*labels)
                help(help)
                register()
            }

    @Synchronized
    private fun guage(name: String, help: String, vararg labels: String): Gauge =
            with (Gauge.build()) {
                name(name)
                labelNames(*labels)
                help(help)
                register()
            }

    @Value("\${pushgateway.host}")
    private lateinit var pushgatewayHost: String

    @Value("\${pushgateway.port:9091}")
    private lateinit var pushgatewayPort: String
}
