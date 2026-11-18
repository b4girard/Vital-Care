package com.example.vitalcare.presentation.sensores

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object EnviadorApi {

    const val URL_BASE = "https://printers-straight-vienna-bradley.trycloudflare.com"

    const val ID_DEPENDENTE = 13

    private const val CHAVE = "vitalcare-demo-2026"

    const val TIPO_BATIMENTOS = 1
    const val TIPO_SATURACAO = 2
    const val TIPO_ESTRESSE = 3
    const val TIPO_PASSOS = 4

    var statusEnvio by mutableStateOf("Envio: aguardando")

    private val ultimoEnvio = mutableMapOf<Int, Long>()
    private const val INTERVALO_MS = 5000L

    // ------------------------------------------------------------
    // ENVIO DE DADOS (batimentos, saturação, estresse, passos)
    // ------------------------------------------------------------

    fun enviar(tipo: Int, valor: Double) {
        val agora = System.currentTimeMillis()
        synchronized(ultimoEnvio) {
            if (agora - (ultimoEnvio[tipo] ?: 0L) < INTERVALO_MS) return
            ultimoEnvio[tipo] = agora
        }

        statusEnvio = "Enviando..."

        Thread {
            try {
                val corpo = JSONObject().apply {
                    put("valor", valor)
                    put("idDependente", ID_DEPENDENTE)
                    put("idTipoMedicao", tipo)
                }

                val conexao = (URL("$URL_BASE/relogio/dados").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 8000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Relogio-Key", CHAVE)
                }

                conexao.outputStream.use { it.write(corpo.toString().toByteArray()) }

                val codigo = conexao.responseCode
                statusEnvio = "HTTP $codigo"
                Log.d("VitalCare", "POST tipo=$tipo valor=$valor -> HTTP $codigo")
                if (codigo >= 400) {
                    val erro = conexao.errorStream?.bufferedReader()?.readText()
                    Log.e("VitalCare", "Erro da API: $erro")
                }
                conexao.disconnect()
            } catch (e: Exception) {
                statusEnvio = "ERRO: ${e.javaClass.simpleName}"
                Log.e("VitalCare", "Falha ao enviar para a API", e)
            }
        }.start()
    }

    // ------------------------------------------------------------
    // EMERGÊNCIA (botão SOS)
    // ------------------------------------------------------------
    //
    // Não passa pela trava de 5 s do enviar(): um alerta não pode ser
    // descartado em silêncio. O estado fica visível pra tela mostrar
    // "enviando", "enviado" ou "erro".
    // ------------------------------------------------------------

    enum class EstadoEmergencia { PARADO, ENVIANDO, ENVIADO, ERRO }

    var statusEmergencia by mutableStateOf(EstadoEmergencia.PARADO)
        private set

    fun resetarEmergencia() {
        statusEmergencia = EstadoEmergencia.PARADO
    }

    fun enviarEmergencia() {

        // evita toque duplo enquanto já está enviando
        if (statusEmergencia == EstadoEmergencia.ENVIANDO) return

        statusEmergencia = EstadoEmergencia.ENVIANDO

        Thread {
            try {
                val corpo = JSONObject().apply {
                    put("idDependente", ID_DEPENDENTE)
                    put("origem", "RELOGIO")
                    put("momento", System.currentTimeMillis())
                }

                val conexao = (URL("$URL_BASE/relogio/emergencia").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 8000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Relogio-Key", CHAVE)
                }

                conexao.outputStream.use { it.write(corpo.toString().toByteArray()) }

                val codigo = conexao.responseCode
                Log.d("VitalCare", "POST emergencia -> HTTP $codigo")

                if (codigo >= 400) {
                    val erro = conexao.errorStream?.bufferedReader()?.readText()
                    Log.e("VitalCare", "Erro da API (emergencia): $erro")
                }

                conexao.disconnect()

                statusEmergencia =
                    if (codigo in 200..299) EstadoEmergencia.ENVIADO
                    else EstadoEmergencia.ERRO

            } catch (e: Exception) {
                Log.e("VitalCare", "Falha ao enviar emergência", e)
                statusEmergencia = EstadoEmergencia.ERRO
            }
        }.start()
    }

    // ------------------------------------------------------------
    // CONFIG DEFINIDA PELO CUIDADOR (limites de saturação)
    // ------------------------------------------------------------

    /**
     * Busca os limites de saturação definidos pelo cuidador no app mobile.
     * Se der erro, não chama o callback (o relógio segue com os limites
     * que já tinha salvos).
     */
    fun buscarConfig(onResultado: (normalMin: Int, atencaoMin: Int) -> Unit) {

        Thread {
            try {
                val conexao = (URL("$URL_BASE/relogio/config?idDependente=$ID_DEPENDENTE")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("X-Relogio-Key", CHAVE)
                }

                val codigo = conexao.responseCode

                if (codigo in 200..299) {
                    val texto = conexao.inputStream.bufferedReader().readText()
                    val json = JSONObject(texto)

                    onResultado(
                        json.getInt("saturacaoNormalMin"),
                        json.getInt("saturacaoAtencaoMin")
                    )
                } else {
                    Log.e("VitalCare", "Config: HTTP $codigo")
                }

                conexao.disconnect()

            } catch (e: Exception) {
                Log.e("VitalCare", "Falha ao buscar config", e)
            }
        }.start()
    }
}