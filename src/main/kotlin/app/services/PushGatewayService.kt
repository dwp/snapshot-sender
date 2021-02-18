package app.services

interface PushGatewayService {
    fun pushMetrics()
    fun pushFinalMetrics()
    fun deleteMetrics()
}
