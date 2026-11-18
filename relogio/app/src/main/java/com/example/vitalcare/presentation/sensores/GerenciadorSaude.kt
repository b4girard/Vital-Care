package com.example.vitalcare.presentation.sensores

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.health.services.client.HealthServices
import androidx.health.services.client.HealthServicesClient
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.PassiveListenerCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.PassiveListenerConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt


class GerenciadorSaude(
    private val context: Context
) {


    private val healthClient: HealthServicesClient =
        HealthServices.getClient(context)

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val prefs =
        context.getSharedPreferences("vitalcare_prefs", Context.MODE_PRIVATE)


    var frequenciaAtual by mutableStateOf("--")
        private set

    var statusAtual by mutableStateOf("--")
        private set

    var horarioAtual by mutableStateOf("--:--")
        private set

    // Ritmo cardíaco (indício de arritmia, só medido em repouso)
    // "--", "Regular", "Irregular", "Taquicardia" ou "Bradicardia"
    var ritmoAtual by mutableStateOf("--")
        private set


    // Saturação (inserida manualmente, medida no app do Mobvoi)
    var saturacaoAtual by mutableStateOf("--")
        private set

    var statusSaturacao by mutableStateOf("--")
        private set

    var horarioSaturacao by mutableStateOf("--:--")
        private set


    // Estresse (calculado a partir da variabilidade do BPM)
    var estresseAtual by mutableStateOf("--")
        private set

    var statusEstresse by mutableStateOf("--")
        private set

    // Mantido só para a MainActivity continuar compilando. No cálculo
    // antigo não há explicação, então fica vazio e a tela não mostra nada.
    var explicacaoEstresse by mutableStateOf("")
        private set

    var horarioEstresse by mutableStateOf("--:--")
        private set

    // janela deslizante com os últimos BPMs para calcular a variabilidade
    private val historicoBpm = mutableListOf<Int>()
    private val tamanhoJanela = 15


    // Passos
    var passosAtual by mutableStateOf("--")
        private set

    var horarioPassos by mutableStateOf("--:--")
        private set


    // ------------------------------------------------------------
    // Análise de ritmo (arritmia) + detecção de movimento
    // ------------------------------------------------------------

    // O analisador não é thread-safe; os callbacks (BPM, passos,
    // acelerômetro) podem chegar de threads diferentes, então toda
    // mexida nele passa por esta trava.
    private val trava = Any()

    private val analisador = AnalisadorCardiaco(
        prefs.getFloat(
            CHAVE_BASELINE,
            AnalisadorCardiaco.BASELINE_PADRAO.toFloat()
        ).toDouble()
    )

    // última vez que o corpo se mexeu (passos ou acelerômetro)
    private var ultimoMovimentoMs: Long = Long.MIN_VALUE / 2

    // total de passos da leitura anterior (pra saber se aumentou)
    private var ultimoTotalPassos: Long? = null

    // janela de módulos do acelerômetro pra medir "quanto está balançando"
    private val amostrasAcel = ArrayDeque<Pair<Long, Double>>()

    private var ultimoSalvoBaselineMs = 0L


    // ------------------------------------------------------------
    // Limites de saturação definidos pelo cuidador (vêm da API).
    // IMPORTANTE: precisam ficar ACIMA do bloco init, porque o
    // restaurarSaturacao() (chamado no init) já usa esses valores.
    // Padrão = os limites antigos (95 / 91) até o relógio buscar os
    // do cuidador.
    // ------------------------------------------------------------

    private var limiteNormalMin = prefs.getInt(CHAVE_SPO2_NORMAL, 95)
    private var limiteAtencaoMin = prefs.getInt(CHAVE_SPO2_ATENCAO, 91)
    private var ultimaBuscaConfigMs = 0L


    // Guardamos as referências dos callbacks para poder cancelar
    // o registro depois (evita vazamento de memória e registro duplicado
    // se a Activity for recriada).
    private var callbackCardiaco: MeasureCallback? = null
    private var callbackPassivo: PassiveListenerCallback? = null

    private val listenerMovimento = object : SensorEventListener {

        override fun onSensorChanged(evento: SensorEvent) {
            processarAcelerometro(evento)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private var monitoramentoIniciado = false


    init {
        restaurarSaturacao()
    }


    fun iniciarMonitoramento() {

        // evita registrar os sensores mais de uma vez (ex: onStart chamado
        // de novo depois de onStop, ou permissão concedida + onResume)
        if (monitoramentoIniciado) {
            return
        }

        monitoramentoIniciado = true

        verificarSensoresDisponiveis()

        monitorarFrequenciaCardiaca()

        iniciarMonitoramentoPassos()

        iniciarMonitoramentoMovimento()

        // busca os limites de saturação definidos pelo cuidador
        verificarConfig()

    }


    // ============================================================
    // PARA O MONITORAMENTO (chamar em onStop/onDestroy da Activity)
    // ============================================================

    fun pararMonitoramento() {

        callbackCardiaco?.let {
            healthClient.measureClient.unregisterMeasureCallbackAsync(
                DataType.HEART_RATE_BPM,
                it
            )
        }

        callbackPassivo?.let {
            healthClient.passiveMonitoringClient.clearPassiveListenerCallbackAsync()
        }

        sensorManager.unregisterListener(listenerMovimento)

        salvarBaseline()

        monitoramentoIniciado = false
    }



    private fun verificarSensoresDisponiveis() {

        // IMPORTANTE: NÃO chamar getCapabilitiesAsync() de novo dentro do
        // listener e travar a thread principal com .get() nele — isso
        // causa deadlock (a chamada nova depende da thread principal pra
        // terminar, mas a thread principal está presa esperando ela).
        // Em vez disso, usamos o MESMO future que já terminou: chamar
        // .get() nele não bloqueia porque ele já está concluído quando
        // o listener dispara.

        val future = healthClient.measureClient.getCapabilitiesAsync()

        future.addListener({

            try {

                val capacidades = future.get()

                capacidades.supportedDataTypesMeasure.forEach { sensor ->

                    Log.d(
                        "VitalCare",
                        "Sensor disponível: $sensor"
                    )

                }

            } catch (e: Exception) {

                Log.e(
                    "VitalCare",
                    "Erro ao verificar sensores disponíveis",
                    e
                )
            }

        }, context.mainExecutor)

    }



    private fun monitorarFrequenciaCardiaca() {


        val callback = object : MeasureCallback {


            override fun onDataReceived(
                data: DataPointContainer
            ) {


                val dados =
                    data.getData(DataType.HEART_RATE_BPM)


                dados.forEach { ponto ->


                    val bpm =
                        ponto.value.toInt()


                    frequenciaAtual =
                        "$bpm BPM"


                    statusAtual = when {

                        bpm < 60 ->
                            "Baixa"

                        bpm <= 100 ->
                            "Normal"

                        else ->
                            "Alta"

                    }


                    horarioAtual = horarioAgora()


                    // atualiza os limites de saturação do cuidador
                    // (só busca de novo a cada 10 min)
                    verificarConfig()

                    // calcula o estresse toda vez que chega um BPM novo
                    // (cálculo antigo: variabilidade do BPM)
                    atualizarEstresse(bpm)

                    // ritmo/arritmia: só vale em repouso (ver analisarRitmo)
                    analisarRitmo(bpm)

                    EnviadorApi.enviar(EnviadorApi.TIPO_BATIMENTOS, bpm.toDouble())


                    Log.d(
                        "VitalCare",
                        "Batimento: $frequenciaAtual"
                    )


                }

            }



            override fun onAvailabilityChanged(
                dataType: DeltaDataType<*, *>,
                availability: Availability
            ) {

                Log.d(
                    "VitalCare",
                    "Sensor cardíaco: $availability"
                )

            }


        }



        callbackCardiaco = callback

        healthClient.measureClient.registerMeasureCallback(
            DataType.HEART_RATE_BPM,
            callback
        )


    }


    // ============================================================
    // ESTRESSE (cálculo antigo: variabilidade do BPM)
    // ============================================================
    //
    // Índice simples: BPM médio elevado + baixa variabilidade = mais
    // estresse. Não considera movimento, então exercício também pode
    // elevar o índice.
    // ============================================================

    private fun atualizarEstresse(bpmAtual: Int) {

        historicoBpm.add(bpmAtual)

        if (historicoBpm.size > tamanhoJanela) {
            historicoBpm.removeAt(0)
        }

        // precisa de um mínimo de amostras pra calcular algo confiável
        if (historicoBpm.size < 5) {
            estresseAtual = "Calculando..."
            statusEstresse = "--"
            return
        }

        val media = historicoBpm.average()

        val variancia = historicoBpm
            .map { (it - media) * (it - media) }
            .average()

        val desvioPadrao = sqrt(variancia)

        // índice simples: BPM médio elevado + baixa variabilidade = mais estresse
        val indice = (media - 60) - (desvioPadrao * 2)

        estresseAtual = String.format(
            "%.0f",
            indice.coerceIn(0.0, 100.0)
        )

        statusEstresse = when {
            indice < 20 -> "Baixo"
            indice < 45 -> "Moderado"
            else -> "Alto"
        }

        horarioEstresse = horarioAgora()

        EnviadorApi.enviar(EnviadorApi.TIPO_ESTRESSE, indice.coerceIn(0.0, 100.0))

        Log.d(
            "VitalCare",
            "Estresse: $estresseAtual ($statusEstresse)"
        )

    }


    // ============================================================
    // RITMO / ARRITMIA (BPM cruzado com movimento)
    // ============================================================
    //
    // O AnalisadorCardiaco só avalia o ritmo com o corpo em repouso.
    // Aqui usamos apenas o campo "ritmo" do resultado; o estresse dele
    // é ignorado (o estresse agora vem de atualizarEstresse).
    // ============================================================

    private fun analisarRitmo(bpm: Int) {

        // ignora leituras impossíveis (sensor sem contato, por exemplo)
        if (bpm < BPM_VALIDO_MIN || bpm > BPM_VALIDO_MAX) {
            return
        }

        val agora = SystemClock.elapsedRealtime()

        val resultado = synchronized(trava) {

            val movendo = agora - ultimoMovimentoMs < JANELA_MOVENDO_MS

            analisador.processar(agora, bpm, movendo)
        }

        ritmoAtual = resultado.ritmo

        if (agora - ultimoSalvoBaselineMs > INTERVALO_SALVAR_BASELINE_MS) {
            ultimoSalvoBaselineMs = agora
            salvarBaseline()
        }

        Log.d(
            "VitalCare",
            "Ritmo: ${resultado.ritmo}"
        )

    }


    // ------------------------------------------------------------
    // MOVIMENTO (passos + acelerômetro)
    // ------------------------------------------------------------

    private fun registrarMovimento(agoraMs: Long) {

        synchronized(trava) {

            ultimoMovimentoMs = agoraMs

            analisador.registrarMovimento(agoraMs)
        }
    }


    private fun iniciarMonitoramentoMovimento() {

        // O contador de passos chega com atraso (e não pega bicicleta,
        // musculação etc.), então o acelerômetro complementa. Ele não
        // precisa de permissão.

        val acelerometro =
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (acelerometro == null) {

            Log.w(
                "VitalCare",
                "Sem acelerômetro: movimento será detectado só pelos passos"
            )

            return
        }

        sensorManager.registerListener(
            listenerMovimento,
            acelerometro,
            SensorManager.SENSOR_DELAY_UI
        )
    }


    private fun processarAcelerometro(evento: SensorEvent) {

        val x = evento.values[0]
        val y = evento.values[1]
        val z = evento.values[2]

        // o módulo não depende da orientação do pulso
        val modulo = sqrt((x * x + y * y + z * z).toDouble())

        val agora = SystemClock.elapsedRealtime()

        amostrasAcel.addLast(agora to modulo)

        while (amostrasAcel.isNotEmpty() &&
            agora - amostrasAcel.first().first > JANELA_ACEL_MS
        ) {
            amostrasAcel.removeFirst()
        }

        if (amostrasAcel.size < MIN_AMOSTRAS_ACEL) {
            return
        }

        // desvio padrão do módulo: parado ≈ 0, andando/correndo alto
        val valores = amostrasAcel.map { it.second }
        val media = valores.average()
        val desvio = sqrt(
            valores.map { (it - media) * (it - media) }.average()
        )

        if (desvio >= LIMIAR_MOVIMENTO) {
            registrarMovimento(agora)
        }
    }


    // ============================================================
    // SATURAÇÃO DE OXIGÊNIO (SpO2) — ENTRADA MANUAL
    // ============================================================
    //
    // O Health Services (androidx.health.services.client) NÃO expõe
    // SpO2 para apps de terceiros. Então a pessoa mede no app de saúde
    // do Mobvoi/TicWatch e digita o valor aqui. O valor é guardado no
    // relógio e enviado pra API (TIPO_SATURACAO) pelo mesmo caminho dos
    // outros dados.
    //
    // A classificação (Normal / Atenção / Baixa) usa os limites
    // definidos pelo cuidador no app mobile (buscados da API).
    // ============================================================

    fun registrarSaturacaoManual(valor: Int) {

        val v = valor.coerceIn(SPO2_MIN, SPO2_MAX)

        val agora = System.currentTimeMillis()

        saturacaoAtual = "$v%"

        statusSaturacao = classificarSaturacao(v)

        horarioSaturacao = formatarHorario(agora)

        prefs.edit()
            .putInt(CHAVE_SPO2_VALOR, v)
            .putLong(CHAVE_SPO2_MS, agora)
            .apply()

        EnviadorApi.enviar(EnviadorApi.TIPO_SATURACAO, v.toDouble())

        Log.d("VitalCare", "SpO2 manual: $v% ($statusSaturacao)")
    }

    /** Última saturação digitada (ou o padrão, se nunca houve). */
    fun saturacaoSugerida(): Int =
        prefs.getInt(CHAVE_SPO2_VALOR, SPO2_PADRAO)

    private fun restaurarSaturacao() {

        if (!prefs.contains(CHAVE_SPO2_VALOR)) {
            return
        }

        val v = prefs.getInt(CHAVE_SPO2_VALOR, SPO2_PADRAO)
        val ms = prefs.getLong(CHAVE_SPO2_MS, 0L)

        saturacaoAtual = "$v%"
        statusSaturacao = classificarSaturacao(v)
        horarioSaturacao = if (ms > 0L) formatarHorario(ms) else "--:--"
    }

    /**
     * Classifica com os limites do cuidador:
     *   valor >= limiteNormalMin  -> Normal
     *   valor >= limiteAtencaoMin -> Atenção
     *   abaixo disso              -> Baixa
     */
    private fun classificarSaturacao(valor: Int): String = when {

        valor >= limiteNormalMin -> "Normal"

        valor >= limiteAtencaoMin -> "Atenção"

        else -> "Baixa"
    }


    // ------------------------------------------------------------
    // CONFIG DO CUIDADOR (limites de saturação vindos da API)
    // ------------------------------------------------------------

    private fun atualizarConfig() {

        EnviadorApi.buscarConfig { normal, atencao ->

            limiteNormalMin = normal
            limiteAtencaoMin = atencao

            // guarda no relógio pra funcionar mesmo sem internet
            prefs.edit()
                .putInt(CHAVE_SPO2_NORMAL, normal)
                .putInt(CHAVE_SPO2_ATENCAO, atencao)
                .apply()

            // reclassifica a última medição com os limites novos
            if (prefs.contains(CHAVE_SPO2_VALOR)) {
                statusSaturacao = classificarSaturacao(
                    prefs.getInt(CHAVE_SPO2_VALOR, SPO2_PADRAO)
                )
            }

            Log.d(
                "VitalCare",
                "Config do cuidador: normal>=$normal, atenção>=$atencao"
            )
        }
    }

    /** Busca a config na primeira vez e depois só a cada INTERVALO_CONFIG_MS. */
    private fun verificarConfig() {

        val agora = SystemClock.elapsedRealtime()

        if (ultimaBuscaConfigMs == 0L ||
            agora - ultimaBuscaConfigMs > INTERVALO_CONFIG_MS
        ) {
            ultimaBuscaConfigMs = agora
            atualizarConfig()
        }
    }


    // ------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------

    private fun salvarBaseline() {

        val baseline = synchronized(trava) { analisador.baseline }

        prefs.edit()
            .putFloat(CHAVE_BASELINE, baseline.toFloat())
            .apply()
    }

    private fun horarioAgora(): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    /** "HH:mm" se for de hoje, "dd/MM HH:mm" se for de outro dia. */
    private fun formatarHorario(ms: Long): String {

        val dia = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

        val mesmoDia = dia.format(Date(ms)) == dia.format(Date())

        val padrao = if (mesmoDia) "HH:mm" else "dd/MM HH:mm"

        return SimpleDateFormat(padrao, Locale.getDefault()).format(Date(ms))
    }


    private fun iniciarMonitoramentoPassos() {

        val callback = object : PassiveListenerCallback {

            override fun onNewDataPointsReceived(
                dataPoints: DataPointContainer
            ) {

                val dadosPassos =
                    dataPoints.getData(DataType.STEPS_DAILY)

                dadosPassos.forEach { ponto ->

                    val totalPassos = ponto.value

                    passosAtual = "$totalPassos passos"

                    // passos aumentaram desde a última leitura = o corpo
                    // se mexeu (o total zera na virada do dia, então só
                    // conta quando sobe)
                    val total = totalPassos.toLong()
                    val anterior = ultimoTotalPassos

                    if (anterior != null && total > anterior) {
                        registrarMovimento(SystemClock.elapsedRealtime())
                    }

                    ultimoTotalPassos = total

                    EnviadorApi.enviar(EnviadorApi.TIPO_PASSOS, totalPassos.toDouble())

                    horarioPassos = horarioAgora()

                    Log.d(
                        "VitalCare",
                        "Passos hoje: $totalPassos"
                    )

                }

            }

        }

        callbackPassivo = callback

        val config = PassiveListenerConfig.builder()
            .setDataTypes(setOf(DataType.STEPS_DAILY))
            .build()

        healthClient.passiveMonitoringClient
            .setPassiveListenerCallback(config, callback)

    }


    companion object {

        private const val CHAVE_BASELINE = "baseline_bpm"
        private const val CHAVE_SPO2_VALOR = "spo2_valor"
        private const val CHAVE_SPO2_MS = "spo2_ms"

        // limites de saturação do cuidador (guardados no relógio)
        private const val CHAVE_SPO2_NORMAL = "spo2_normal_min"
        private const val CHAVE_SPO2_ATENCAO = "spo2_atencao_min"

        const val SPO2_MIN = 70
        const val SPO2_MAX = 100
        const val SPO2_PADRAO = 95

        // leituras fora disso são ruído do sensor
        private const val BPM_VALIDO_MIN = 30
        private const val BPM_VALIDO_MAX = 230

        private const val INTERVALO_SALVAR_BASELINE_MS = 300_000L

        // de quanto em quanto tempo busca a config do cuidador (10 min)
        private const val INTERVALO_CONFIG_MS = 600_000L

        // --- valores ajustáveis (calibrar com o relógio real) ---

        // quanto tempo depois do último movimento ainda conta como "movendo"
        private const val JANELA_MOVENDO_MS = 4_000L

        // acelerômetro: janela de análise e mínimo de amostras
        private const val JANELA_ACEL_MS = 3_000L
        private const val MIN_AMOSTRAS_ACEL = 10

        private const val LIMIAR_MOVIMENTO = 1.5
    }

}