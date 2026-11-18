package com.example.vitalcare.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Text

import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales

import com.example.vitalcare.presentation.sensores.EnviadorApi
import com.example.vitalcare.presentation.sensores.GerenciadorSaude
import com.example.vitalcare.presentation.theme.VitalCareTheme

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale




class MainActivity : ComponentActivity() {

    private lateinit var gerenciadorSaude: GerenciadorSaude

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)


        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        gerenciadorSaude = GerenciadorSaude(this)

        // TESTE: força um envio assim que o app abre (independe do sensor)
        EnviadorApi.enviar(1, 77.0)

        //permissoes--------------------------------------------------------

        val permissoesNecessarias = arrayOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.ACTIVITY_RECOGNITION
        )

        val permissoesFaltando = permissoesNecessarias.filter {

            ActivityCompat.checkSelfPermission(
                this,
                it
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (permissoesFaltando.isNotEmpty()) {

            ActivityCompat.requestPermissions(
                this,
                permissoesFaltando.toTypedArray(),
                100
            )

        } else {

            gerenciadorSaude.iniciarMonitoramento()
        }

        setContent {

            WearApp(
                gerenciadorSaude = gerenciadorSaude
            )
        }
    }


    //resultado das permissoes

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode == 100 &&
            grantResults.isNotEmpty() &&
            grantResults.all {
                it == PackageManager.PERMISSION_GRANTED
            }
        ) {

            gerenciadorSaude.iniciarMonitoramento()
        }
    }




    override fun onDestroy() {

        gerenciadorSaude.pararMonitoramento()

        super.onDestroy()
    }
}

@Composable
fun WearApp(
    gerenciadorSaude: GerenciadorSaude
) {

    VitalCareTheme {

        //
        // PAGER HORIZONTAL
        //
        //
        // 0 = Início
        // 1 = Frequência Cardíaca
        // 2 = Saturação
        // 3 = Estresse
        // 4 = Passos
        // 5 = Emergência (SOS)

        val pagerState = rememberPagerState(
            initialPage = 0,
            pageCount = {
                6
            }
        )

        AppScaffold {

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { pagina ->

                when (pagina) {


                    0 -> {

                        TelaInicio()
                    }


                    1 -> {

                        TelaCardiaco(
                            gerenciadorSaude = gerenciadorSaude
                        )
                    }


                    // ------------------------------------------------
                    // PÁGINA 3 — SATURAÇÃO
                    // ------------------------------------------------

                    2 -> {

                        TelaSaturacao(
                            gerenciadorSaude = gerenciadorSaude
                        )
                    }


                    // ------------------------------------------------
                    // PÁGINA 4 — ESTRESSE
                    // ------------------------------------------------

                    3 -> {

                        TelaEstresse(
                            gerenciadorSaude = gerenciadorSaude
                        )
                    }


                    // ------------------------------------------------
                    // PÁGINA 5 — PASSOS
                    // ------------------------------------------------

                    4 -> {

                        TelaPassos(
                            gerenciadorSaude = gerenciadorSaude
                        )
                    }


                    // ------------------------------------------------
                    // PÁGINA 6 — EMERGÊNCIA (SOS)
                    // ------------------------------------------------

                    5 -> {

                        TelaEmergencia()
                    }
                }
            }
        }
    }
}


@Composable
fun TelaInicio() {

    // ------------------------------------------------------------
    // HORÁRIO ATUAL
    // ------------------------------------------------------------

    var horario by remember {

        mutableStateOf(
            SimpleDateFormat(
                "HH:mm",
                Locale.getDefault()
            ).format(Date())
        )
    }


    //vai atualizar hotario

    LaunchedEffect(Unit) {

        while (true) {

            horario = SimpleDateFormat(
                "HH:mm",
                Locale.getDefault()
            ).format(Date())

            kotlinx.coroutines.delay(1000)
        }
    }


    // ------------------------------------------------------------
    // INTERFACE
    // ------------------------------------------------------------

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),

        horizontalAlignment = Alignment.CenterHorizontally,

        verticalArrangement = Arrangement.Center
    ) {

        // --------------------------------------------------------
        // HORÁRIO
        // --------------------------------------------------------

        Text(
            text = horario,

            fontSize = 48.sp,

            fontWeight = FontWeight.ExtraBold,

            color = Color.White
        )

        Spacer(
            modifier = Modifier.height(4.dp)
        )


        // --------------------------------------------------------
        // NOME DO APP
        // --------------------------------------------------------

        Text(
            text = "VitalCare",

            fontSize = 20.sp,

            fontWeight = FontWeight.ExtraBold,

            color = Color.White
        )


        // --------------------------------------------------------
        // SUBTÍTULO
        // --------------------------------------------------------

        Text(
            text = "Monitoramento",

            fontSize = 12.sp,

            fontWeight = FontWeight.Medium,

            color = Color(0xFF00BFA5)
        )

        // STATUS DO ENVIO (diagnóstico)
        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = EnviadorApi.statusEnvio,
            fontSize = 11.sp,
            color = Color.Yellow
        )
    }
}


// bpm

@Composable
fun TelaCardiaco(
    gerenciadorSaude: GerenciadorSaude
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),

        horizontalAlignment = Alignment.CenterHorizontally,

        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Frequência Cardíaca",

            fontSize = 18.sp,

            fontWeight = FontWeight.Bold,

            color = Color.White
        )

        Spacer(
            modifier = Modifier.height(14.dp)
        )

        Text(
            text = gerenciadorSaude.frequenciaAtual,

            fontSize = 30.sp,

            fontWeight = FontWeight.ExtraBold,

            color = Color.White
        )

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Text(
            text = gerenciadorSaude.statusAtual,

            fontSize = 15.sp,

            color = Color(0xFF00BFA5)
        )

        // ritmo (indício de arritmia, só avaliado em repouso)

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        val ritmo = gerenciadorSaude.ritmoAtual

        Text(
            text = if (ritmo == "--") "Ritmo: aguardando repouso" else "Ritmo: $ritmo",

            fontSize = 13.sp,

            textAlign = TextAlign.Center,

            color = corPorRitmo(ritmo)
        )

        if (ritmo == "Irregular" || ritmo == "Taquicardia" || ritmo == "Bradicardia") {

            Spacer(
                modifier = Modifier.height(2.dp)
            )

            Text(
                text = "Indício em repouso, não é diagnóstico",

                fontSize = 11.sp,

                textAlign = TextAlign.Center,

                color = Color.Gray
            )
        }

        Spacer(
            modifier = Modifier.height(14.dp)
        )

        Text(
            text = "Última atualização",

            fontSize = 12.sp,

            fontWeight = FontWeight.Bold,

            color = Color.White
        )

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        Text(
            text = gerenciadorSaude.horarioAtual,

            fontSize = 12.sp,

            color = Color.White
        )
    }
}


// saturacao (medida no app do Mobvoi e inserida manualmente)

@Composable
fun TelaSaturacao(
    gerenciadorSaude: GerenciadorSaude
) {

    // valor que está sendo ajustado (começa na última medição salva)
    var entrada by remember {
        mutableStateOf(gerenciadorSaude.saturacaoSugerida())
    }

    // depois de salvar, o botão fica travado por alguns segundos
    var salvo by remember { mutableStateOf(false) }

    LaunchedEffect(salvo) {

        if (salvo) {

            kotlinx.coroutines.delay(6000)

            salvo = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),

        horizontalAlignment = Alignment.CenterHorizontally,

        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Saturação O₂",

            fontSize = 18.sp,

            fontWeight = FontWeight.Bold,

            color = Color.White
        )

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Text(
            text = gerenciadorSaude.saturacaoAtual,

            fontSize = 30.sp,

            fontWeight = FontWeight.ExtraBold,

            color = Color.White
        )

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        Text(
            text = gerenciadorSaude.statusSaturacao,

            fontSize = 15.sp,

            color = corPorStatusSaturacao(
                gerenciadorSaude.statusSaturacao
            )
        )

        if (gerenciadorSaude.statusSaturacao == "Baixa") {

            Spacer(
                modifier = Modifier.height(2.dp)
            )

            Text(
                text = "Refaça a medição e procure ajuda se persistir",

                fontSize = 11.sp,

                textAlign = TextAlign.Center,

                color = Color(0xFFFFB300)
            )
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = "Última atualização",

            fontSize = 12.sp,

            fontWeight = FontWeight.Bold,

            color = Color.White
        )

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        Text(
            text = gerenciadorSaude.horarioSaturacao,

            fontSize = 12.sp,

            color = Color.White
        )

        // --------------------------------------------------------
        // ENTRADA MANUAL
        // --------------------------------------------------------

        Spacer(
            modifier = Modifier.height(18.dp)
        )

        Text(
            text = "Inserir medição do Mobvoi",

            fontSize = 12.sp,

            fontWeight = FontWeight.Bold,

            textAlign = TextAlign.Center,

            color = Color(0xFF00BFA5)
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,

            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            BotaoRedondo("−") {

                if (entrada > GerenciadorSaude.SPO2_MIN) entrada--
            }

            Text(
                text = "$entrada%",

                fontSize = 26.sp,

                fontWeight = FontWeight.ExtraBold,

                color = Color.White
            )

            BotaoRedondo("+") {

                if (entrada < GerenciadorSaude.SPO2_MAX) entrada++
            }
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (salvo) Color(0xFF3A4A66) else Color(0xFF00BFA5)
                )
                .clickable(enabled = !salvo) {

                    gerenciadorSaude.registrarSaturacaoManual(entrada)

                    salvo = true
                }
                .padding(horizontal = 28.dp, vertical = 10.dp),

            contentAlignment = Alignment.Center
        ) {

            Text(
                text = if (salvo) "Salvo!" else "Salvar",

                fontSize = 14.sp,

                fontWeight = FontWeight.Bold,

                color = Color.White
            )
        }
    }
}


// botão redondo simples (+ / −) usado na entrada manual

@Composable
fun BotaoRedondo(
    texto: String,
    onClick: () -> Unit
) {

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0xFF136F68))
            .clickable(onClick = onClick),

        contentAlignment = Alignment.Center
    ) {

        Text(
            text = texto,

            fontSize = 22.sp,

            fontWeight = FontWeight.Bold,

            color = Color.White
        )
    }
}


// estresse

@Composable
fun TelaEstresse(
    gerenciadorSaude: GerenciadorSaude
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),

        horizontalAlignment = Alignment.CenterHorizontally,

        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Nível de Estresse",

            fontSize = 18.sp,

            fontWeight = FontWeight.Bold,

            color = Color.White
        )

        Spacer(
            modifier = Modifier.height(14.dp)
        )

        Text(
            text = gerenciadorSaude.estresseAtual,

            fontSize = 30.sp,

            fontWeight = FontWeight.ExtraBold,

            color = Color.White
        )

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Text(
            text = gerenciadorSaude.statusEstresse,

            fontSize = 15.sp,

            color = corPorStatusEstresse(
                gerenciadorSaude.statusEstresse
            )
        )

        if (gerenciadorSaude.explicacaoEstresse.isNotEmpty()) {

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = gerenciadorSaude.explicacaoEstresse,

                fontSize = 11.sp,

                textAlign = TextAlign.Center,

                color = Color.Gray
            )
        }

        Spacer(
            modifier = Modifier.height(14.dp)
        )

        Text(
            text = "Última atualização",

            fontSize = 12.sp,

            fontWeight = FontWeight.Bold,

            color = Color.White
        )

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        Text(
            text = gerenciadorSaude.horarioEstresse,

            fontSize = 12.sp,

            color = Color.White
        )
    }
}


// ============================================================
// TELA — PASSOS
// ============================================================

@Composable
fun TelaPassos(
    gerenciadorSaude: GerenciadorSaude
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),

        horizontalAlignment = Alignment.CenterHorizontally,

        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Passos",

            fontSize = 18.sp,

            fontWeight = FontWeight.Bold,

            color = Color.White
        )

        Spacer(
            modifier = Modifier.height(14.dp)
        )

        Text(
            text = gerenciadorSaude.passosAtual,

            fontSize = 30.sp,

            fontWeight = FontWeight.ExtraBold,

            color = Color.White
        )

        Spacer(
            modifier = Modifier.height(14.dp)
        )

        Text(
            text = "Última atualização",

            fontSize = 12.sp,

            fontWeight = FontWeight.Bold,

            color = Color.White
        )

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        Text(
            text = gerenciadorSaude.horarioPassos,

            fontSize = 12.sp,

            color = Color.White
        )
    }
}


// ============================================================
// TELA — EMERGÊNCIA (SOS)
// ============================================================
//
// Dois passos pra evitar alerta sem querer: toca em SOS e depois
// confirma. Se não confirmar em 5 s, cancela sozinho.
// ============================================================

@Composable
fun TelaEmergencia() {

    var confirmando by remember { mutableStateOf(false) }

    val estado = EnviadorApi.statusEmergencia

    // desiste sozinho se não confirmar
    LaunchedEffect(confirmando) {

        if (confirmando) {

            kotlinx.coroutines.delay(5000)

            confirmando = false
        }
    }

    // some a mensagem de "enviado" depois de um tempo
    LaunchedEffect(estado) {

        if (estado == EnviadorApi.EstadoEmergencia.ENVIADO) {

            kotlinx.coroutines.delay(10000)

            EnviadorApi.resetarEmergencia()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),

        horizontalAlignment = Alignment.CenterHorizontally,

        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Emergência",

            fontSize = 18.sp,

            fontWeight = FontWeight.Bold,

            color = Color.White
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        when (estado) {

            EnviadorApi.EstadoEmergencia.ENVIANDO -> {

                Text(
                    text = "Enviando alerta...",

                    fontSize = 14.sp,

                    color = Color(0xFFFFB300)
                )
            }

            EnviadorApi.EstadoEmergencia.ENVIADO -> {

                Text(
                    text = "Alerta enviado!\nSeu cuidador foi avisado.",

                    fontSize = 14.sp,

                    textAlign = TextAlign.Center,

                    color = Color(0xFF00BFA5)
                )
            }

            else -> {

                if (estado == EnviadorApi.EstadoEmergencia.ERRO) {

                    Text(
                        text = "Não foi possível enviar.\nTente de novo.",

                        fontSize = 12.sp,

                        textAlign = TextAlign.Center,

                        color = Color(0xFFE53935)
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )
                }

                if (!confirmando) {

                    // botão SOS
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935))
                            .clickable { confirmando = true },

                        contentAlignment = Alignment.Center
                    ) {

                        Text(
                            text = "SOS",

                            fontSize = 24.sp,

                            fontWeight = FontWeight.ExtraBold,

                            color = Color.White
                        )
                    }

                } else {

                    Text(
                        text = "Avisar o cuidador?",

                        fontSize = 13.sp,

                        textAlign = TextAlign.Center,

                        color = Color.White
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF3A4A66))
                                .clickable { confirmando = false }
                                .padding(horizontal = 14.dp, vertical = 10.dp),

                            contentAlignment = Alignment.Center
                        ) {

                            Text(
                                text = "Não",

                                fontSize = 13.sp,

                                fontWeight = FontWeight.Bold,

                                color = Color.White
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFE53935))
                                .clickable {

                                    confirmando = false

                                    EnviadorApi.enviarEmergencia()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),

                            contentAlignment = Alignment.Center
                        ) {

                            Text(
                                text = "Enviar",

                                fontSize = 13.sp,

                                fontWeight = FontWeight.Bold,

                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}


// ============================================================
// COR DO STATUS DE ESTRESSE
// ============================================================

fun corPorStatusEstresse(
    status: String
): Color {

    return when (status) {

        "Baixo" -> Color(0xFF00BFA5)

        "Moderado" -> Color(0xFFFFB300)

        "Alto" -> Color(0xFFE53935)

        // não está em repouso: não é estresse, é esforço
        "Em atividade", "Recuperando" -> Color(0xFF64B5F6)

        else -> Color.Gray
    }
}


fun corPorStatusSaturacao(
    status: String
): Color {

    return when (status) {

        "Normal" -> Color(0xFF00BFA5)

        "Atenção" -> Color(0xFFFFB300)

        "Baixa" -> Color(0xFFE53935)

        else -> Color.Gray
    }
}


fun corPorRitmo(
    ritmo: String
): Color {

    return when (ritmo) {

        "Regular" -> Color(0xFF00BFA5)

        "Irregular" -> Color(0xFFFFB300)

        "Taquicardia", "Bradicardia" -> Color(0xFFE53935)

        else -> Color.Gray
    }
}


@WearPreviewDevices
@WearPreviewFontScales
@Composable

fun DefaultPreview() {


}