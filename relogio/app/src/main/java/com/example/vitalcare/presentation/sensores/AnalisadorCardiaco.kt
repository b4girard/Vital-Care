package com.example.vitalcare.presentation.sensores

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Lógica de estresse e de ritmo cardíaco, separada do Android pra ficar
 * fácil de testar e de ajustar.
 *
 * IDEIAS PRINCIPAIS
 *
 * 1) Estresse só é avaliado quando o corpo está em REPOUSO.
 *    Batimento alto durante/logo depois de exercício é esforço físico, não
 *    estresse. Por isso:
 *      - se está se mexendo            -> não calcula ("Em atividade")
 *      - até 2 min depois de parar     -> não calcula ("Recuperando")
 *      - depois disso                  -> calcula
 *
 * 2) O estresse é comparado com o repouso DA PRÓPRIA PESSOA (baseline),
 *    não com um número fixo (60 bpm). Quem tem repouso de 55 e quem tem
 *    repouso de 80 não são medidos pela mesma régua.
 *
 * 3) A variabilidade do BPM entra com peso pequeno. O relógio só entrega o
 *    BPM já suavizado (não entrega o intervalo entre batidas, que é o que
 *    se usa pra medir HRV de verdade), então é um indício fraco.
 *
 * LIMITAÇÃO IMPORTANTE (arritmia)
 *    Só com BPM NÃO dá pra diagnosticar arritmia (ex.: fibrilação atrial
 *    exige ECG ou intervalos RR). O que este código faz é DETECTAR INDÍCIOS
 *    em repouso (FC muito alta, muito baixa ou instável) e avisar. Não é
 *    diagnóstico médico.
 */
class AnalisadorCardiaco(
    baselineInicial: Double = BASELINE_PADRAO
) {

    data class Resultado(
        /** 0..100, ou null enquanto ainda não houve nenhum cálculo válido */
        val estresse: Int?,
        /** "Baixo", "Moderado", "Alto", "Em atividade", "Recuperando" ou "Calculando..." */
        val statusEstresse: String,
        /** "--", "Regular", "Irregular", "Taquicardia" ou "Bradicardia" */
        val ritmo: String,
        /** true quando o estresse acabou de ser recalculado com dados válidos */
        val estresseAtualizado: Boolean
    )

    private class Amostra(val tempoMs: Long, val bpm: Int)

    /** Repouso habitual da pessoa (bpm). Exposto pra poder ser salvo/restaurado. */
    var baseline: Double = baselineInicial.coerceIn(BASELINE_MIN, BASELINE_MAX)
        private set

    private val janela = ArrayDeque<Amostra>()

    private var ultimoMovimentoMs: Long = Long.MIN_VALUE / 2
    private var estresseSuavizado: Double? = null

    // controle do alerta de ritmo (com "histerese" pra não piscar)
    private var ritmoExibido = RITMO_SEM_DADOS
    private var candidatoRitmo = RITMO_REGULAR
    private var candidatoDesdeMs = 0L

    // ------------------------------------------------------------------

    /** Avisa que houve movimento (acelerômetro, passos...). */
    fun registrarMovimento(agoraMs: Long) {
        ultimoMovimentoMs = agoraMs
        janela.clear()
    }

    /**
     * Processa uma leitura de BPM.
     * @param movendo true se o acelerômetro indica movimento neste instante
     */
    fun processar(agoraMs: Long, bpm: Int, movendo: Boolean): Resultado {

        if (movendo) {
            registrarMovimento(agoraMs)
            return resultado("Em atividade", atualizado = false)
        }

        val calmoHaMs = agoraMs - ultimoMovimentoMs

        if (calmoHaMs < TEMPO_RECUPERACAO_MS) {
            return resultado("Recuperando", atualizado = false)
        }

        // --- a partir daqui só entram leituras de repouso ---

        janela.addLast(Amostra(agoraMs, bpm))
        while (janela.isNotEmpty() &&
            agoraMs - janela.first().tempoMs > JANELA_MS
        ) {
            janela.removeFirst()
        }

        if (janela.size < MIN_AMOSTRAS) {
            return resultado("Calculando...", atualizado = false)
        }

        val valores = janela.map { it.bpm.toDouble() }
        val media = valores.average()

        atualizarBaseline(media)
        atualizarRitmo(agoraMs, valores, media)

        val novoEstresse = calcularEstresse(valores, media)

        val suave = estresseSuavizado
            ?.let { it * 0.7 + novoEstresse * 0.3 }
            ?: novoEstresse

        estresseSuavizado = suave

        val status = when {
            suave < 30 -> "Baixo"
            suave < 60 -> "Moderado"
            else -> "Alto"
        }

        return resultado(status, atualizado = true)
    }

    // ------------------------------------------------------------------

    private fun resultado(status: String, atualizado: Boolean) = Resultado(
        estresse = estresseSuavizado?.toInt()?.coerceIn(0, 100),
        statusEstresse = status,
        ritmo = ritmoExibido,
        estresseAtualizado = atualizado
    )

    private fun calcularEstresse(valores: List<Double>, media: Double): Double {

        // (a) quanto o BPM em repouso está acima do repouso habitual.
        //     +25 bpm acima do baseline, parado, já é o máximo.
        val elevacao = ((media - baseline) / 25.0).coerceIn(0.0, 1.0) * 100.0

        // (b) variabilidade: média da diferença entre leituras seguidas.
        //     Pouca variação em repouso = sistema mais "travado" = indício
        //     (fraco) de estresse.
        var soma = 0.0
        for (i in 1 until valores.size) {
            soma += abs(valores[i] - valores[i - 1])
        }
        val variacaoMedia = soma / (valores.size - 1)
        val pouca = (1.0 - variacaoMedia / 2.0).coerceIn(0.0, 1.0) * 100.0

        return elevacao * 0.75 + pouca * 0.25
    }

    /**
     * O baseline desce rápido (um repouso mais baixo é sempre mais confiável)
     * e sobe devagar (pra uma pessoa estressada o dia todo não "normalizar"
     * o próprio estresse).
     */
    private fun atualizarBaseline(mediaRepouso: Double) {
        val alfa = when {
            mediaRepouso < baseline -> 0.10
            mediaRepouso < baseline + 10 -> 0.005
            else -> 0.0
        }
        baseline = (baseline + (mediaRepouso - baseline) * alfa)
            .coerceIn(BASELINE_MIN, BASELINE_MAX)
    }

    private fun atualizarRitmo(
        agoraMs: Long,
        valores: List<Double>,
        media: Double
    ) {
        val observado = when {
            media > LIMITE_TAQUICARDIA -> RITMO_TAQUICARDIA
            media < LIMITE_BRADICARDIA -> RITMO_BRADICARDIA
            parecerIrregular(valores, media) -> RITMO_IRREGULAR
            else -> RITMO_REGULAR
        }

        if (observado != candidatoRitmo) {
            candidatoRitmo = observado
            candidatoDesdeMs = agoraMs
        }

        // só muda o que é exibido se o novo estado se mantiver por 60 s
        if (candidatoRitmo != ritmoExibido &&
            agoraMs - candidatoDesdeMs >= CONFIRMACAO_RITMO_MS
        ) {
            ritmoExibido = candidatoRitmo
        }
    }

    private fun parecerIrregular(valores: List<Double>, media: Double): Boolean {

        var saltos = 0
        for (i in 1 until valores.size) {
            if (abs(valores[i] - valores[i - 1]) >= SALTO_BPM) saltos++
        }
        val fracaoSaltos = saltos.toDouble() / (valores.size - 1)

        val variancia = valores.map { (it - media) * (it - media) }.average()
        val coefVariacao = sqrt(variancia) / media

        return fracaoSaltos >= FRACAO_SALTOS_IRREGULAR &&
                coefVariacao >= CV_IRREGULAR
    }

    companion object {
        const val RITMO_SEM_DADOS = "--"
        const val RITMO_REGULAR = "Regular"
        const val RITMO_IRREGULAR = "Irregular"
        const val RITMO_TAQUICARDIA = "Taquicardia"
        const val RITMO_BRADICARDIA = "Bradicardia"

        const val BASELINE_PADRAO = 70.0
        private const val BASELINE_MIN = 45.0
        private const val BASELINE_MAX = 100.0

        // --- valores ajustáveis (calibrar com dados reais do relógio) ---
        private const val TEMPO_RECUPERACAO_MS = 120_000L
        private const val JANELA_MS = 120_000L
        private const val MIN_AMOSTRAS = 40
        private const val CONFIRMACAO_RITMO_MS = 60_000L

        private const val LIMITE_TAQUICARDIA = 110.0
        private const val LIMITE_BRADICARDIA = 45.0
        private const val SALTO_BPM = 6.0
        private const val FRACAO_SALTOS_IRREGULAR = 0.20
        private const val CV_IRREGULAR = 0.10
    }
}