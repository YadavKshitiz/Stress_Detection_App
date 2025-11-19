// corret code and real one
package com.example.calmiapp

import android.Manifest
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import com.example.calmiapp.ui.icons.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.File

// ---------------- API CONFIG ----------------
const val BASE_URL = "http://10.12.159.222:8080"

// --------------------------------------------
interface CalmiApi {
    @Multipart
    @POST("/detectStress")
    suspend fun detectStress(
        @Part image: MultipartBody.Part?,
        @Part audio: MultipartBody.Part?,
        @Part("answers") answers: RequestBody
    ): ResponseBody

    @POST("/chat")
    suspend fun chatWithBot(@Body request: Map<String, String>): ResponseBody
}

fun provideCalmiApi(): CalmiApi {
    val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
    val client = OkHttpClient.Builder().addInterceptor(logger).build()

    val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    return retrofit.create(CalmiApi::class.java)
}

// ---------------- MAIN ACTIVITY ----------------

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: CalmiViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = CalmiViewModel(applicationContext)
        setContent { CalmiApp(viewModel) }
    }
}

// ---------------- VIEWMODEL ----------------

class CalmiViewModel(private val context: Context) : ViewModel() {

    var darkThemeEnabled by mutableStateOf(false)
        private set

    var capturedImageUri by mutableStateOf<Uri?>(null)
    var audioFilePath by mutableStateOf<String?>(null)

    val answers = mutableStateMapOf<String, String>()

    var chatMessages = mutableStateListOf<ChatMessage>()
        private set

    private val api = provideCalmiApi()

    fun toggleDarkTheme(enabled: Boolean) {
        darkThemeEnabled = enabled
    }

    fun saveAnswer(question: String, answer: String) {
        answers[question] = answer
    }

    fun addChatMessage(sender: String, message: String) {
        chatMessages.add(ChatMessage(sender, message))
    }

    fun sendDetectStress(onResult: (String) -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {

                val imagePart = capturedImageUri?.let { uri ->
                    val file = FileUtil.from(context, uri)
                    val req = file.asRequestBody("image/*".toMediaTypeOrNull())
                    MultipartBody.Part.createFormData("image", file.name, req)
                }

                val audioPart = audioFilePath?.let { path ->
                    val file = File(path)
                    val req = file.asRequestBody("audio/m4a".toMediaTypeOrNull())
                    MultipartBody.Part.createFormData("audio", file.name, req)
                }

                // --- FIX: SEND LIST, NOT MAP ---
                val answerList = listOf(
                    answers["activity"],
                    answers["company"],
                    answers["pressure"],
                    answers["tired"],
                    answers["energetic"]
                )

                val jsonAnswers = Gson().toJson(answerList)
                val answersBody = RequestBody.create(
                    "application/json".toMediaTypeOrNull(),
                    jsonAnswers
                )

                val responseBody = api.detectStress(imagePart, audioPart, answersBody)

                val respString = try {
                    responseBody.string()
                } catch (ex: Exception) {
                    responseBody.close()
                    "{\"error\":\"failed_to_read_response\"}"
                }

                onResult(respString)

            } catch (ex: Throwable) {
                onError(ex)
            }
        }
    }

    fun sendChatMessageToBackend(
        message: String,
        onResult: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val body = mapOf("message" to message)
                val responseBody = api.chatWithBot(body)
                val s = try {
                    responseBody.string()
                } catch (ex: Exception) {
                    responseBody.close()
                    "{\"error\":\"failed_to_read_response\"}"
                }
                onResult(s)
            } catch (ex: Throwable) {
                onError(ex)
            }
        }
    }
}

// ---------------- NAV + APP ----------------

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Home : Screen("home")
    object DetectStress : Screen("detect_stress")
    object Chatbot : Screen("chatbot")
    object Settings : Screen("settings")
}

@Composable
fun CalmiApp(viewModel: CalmiViewModel) {
    val nav = rememberNavController()
    val dark = viewModel.darkThemeEnabled

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Scaffold(
            bottomBar = {
                val current = nav.currentBackStackEntryAsState().value?.destination?.route
                if (current !in listOf(Screen.Splash.route, Screen.Settings.route)) {
                    BottomNavigationBar(nav, current)
                }
            }
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = Screen.Splash.route,
                modifier = Modifier.padding(padding)
            ) {
                composable(Screen.Splash.route) { SplashScreen(nav) }
                composable(Screen.Home.route) { HomeScreen(nav) }
                composable(Screen.DetectStress.route) { DetectStressScreen(nav, viewModel) }
                composable(Screen.Chatbot.route) { ChatbotScreen(nav, viewModel) }
                composable(Screen.Settings.route) { SettingsScreen(nav, viewModel) }
            }
        }
    }
}

// ---------------- UI: Splash / Home / Nav ----------------

@Composable
fun SplashScreen(nav: NavController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color(0xFFB3E5FC), Color(0xFFE1F5FE))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = HeartIcon,
                contentDescription = null,
                tint = Color(0xFF4FC3F7),
                modifier = Modifier.size(80.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("Calmi", fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text("Detect • Relax • Heal", fontSize = 18.sp, color = Color.DarkGray)
        }
    }
    LaunchedEffect(Unit) {
        delay(1400)
        nav.navigate(Screen.Home.route) {
            popUpTo(Screen.Splash.route) { inclusive = true }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController) {
    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text("Calmi", fontWeight = FontWeight.Bold) },
            actions = {
                IconButton(onClick = { nav.navigate(Screen.Settings.route) }) {
                    Icon(
                        SettingsIcon,
                        null
                    )
                }
            }
        )
    }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "\"You are stronger than your stress.\"",
                    Modifier.padding(24.dp),
                    fontSize = 18.sp
                )
            }
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { nav.navigate(Screen.DetectStress.route) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Detect Stress", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { nav.navigate(Screen.Chatbot.route) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Chatbot", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun BottomNavigationBar(nav: NavController, current: String?) {
    NavigationBar {
        NavigationBarItem(
            selected = current == Screen.Home.route,
            onClick = { nav.navigate(Screen.Home.route) },
            icon = { Icon(HomeIcon, contentDescription = "Home") },
            label = { Text("Home") }
        )
        NavigationBarItem(
            selected = current == Screen.DetectStress.route,
            onClick = { nav.navigate(Screen.DetectStress.route) },
            icon = { Icon(HeartIcon, contentDescription = "Detect") },
            label = { Text("Detect") }
        )
        NavigationBarItem(
            selected = current == Screen.Chatbot.route,
            onClick = { nav.navigate(Screen.Chatbot.route) },
            icon = { Icon(ChatIcon, contentDescription = "Chat") },
            label = { Text("Chat") }
        )
    }
}

// ---------------- DetectStressScreen ----------------

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DetectStressScreen(nav: NavController, vm: CalmiViewModel) {
    val ctx = LocalContext.current
    val focusManager = LocalFocusManager.current

    val permissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    )

    val imageUriState = remember { mutableStateOf<Uri?>(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) imageUriState.value?.let { vm.capturedImageUri = it }
        }

    var recorder: MediaRecorder? by remember { mutableStateOf(null) }
    var isRecording by remember { mutableStateOf(false) }
    val outputFile =
        remember { ctx.cacheDir.absolutePath + "/calmi_audio_${System.currentTimeMillis()}.m4a" }

    fun startRecording() {
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(outputFile)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            try {
                prepare()
                start()
                isRecording = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopRecording() {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        isRecording = false
        vm.audioFilePath = outputFile
    }

    var activityAnswer by rememberSaveable { mutableStateOf("") }
    var companyAnswer by rememberSaveable { mutableStateOf("") }
    var pressureAnswer by rememberSaveable { mutableStateOf("") }
    var tiredAnswer by rememberSaveable { mutableStateOf("") }
    var energeticAnswer by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(activityAnswer, companyAnswer, pressureAnswer, tiredAnswer, energeticAnswer) {
        vm.saveAnswer("activity", activityAnswer)
        vm.saveAnswer("company", companyAnswer)
        vm.saveAnswer("pressure", pressureAnswer)
        vm.saveAnswer("tired", tiredAnswer)
        vm.saveAnswer("energetic", energeticAnswer)
    }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text("Detect Stress") },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        null
                    )
                }
            }
        )
    }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {

            Text("Face Image", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFFFD6BD)),
                contentAlignment = Alignment.Center
            ) {
                if (vm.capturedImageUri != null) {
                    AsyncImage(
                        model = vm.capturedImageUri,
                        contentDescription = "Captured",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Filled.Person, null, Modifier.size(100.dp))
                }

                Button(
                    onClick = {
                        if (!permissions.allPermissionsGranted) {
                            permissions.launchMultiplePermissionRequest()
                            return@Button
                        }
                        val uri = createImageUri(ctx)
                        imageUriState.value = uri
                        launcher.launch(uri)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                ) {
                    Text("Capture Photo")
                }
            }

            Spacer(Modifier.height(20.dp))

            Text("Audio Sample", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))

            ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Mic, null)
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (!permissions.allPermissionsGranted) {
                                permissions.launchMultiplePermissionRequest()
                                return@Button
                            }
                            if (!isRecording) {
                                startRecording()
                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(10_000)
                                    stopRecording()
                                }
                            }
                        },
                        enabled = !isRecording
                    ) {
                        Text(if (isRecording) "Recording..." else "Record 10s")
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text("Questionnaire", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))

            QuestionRadio(
                "What activity are you currently engaged in?",
                listOf("Working", "Studying", "Relaxing", "Exercising", "Other"),
                activityAnswer
            ) { activityAnswer = it }
            Spacer(Modifier.height(8.dp))
            QuestionRadio(
                "Who are you currently with?",
                listOf("Alone", "Friends", "Family", "Colleagues", "Strangers"),
                companyAnswer
            ) { companyAnswer = it }
            Spacer(Modifier.height(8.dp))
            QuestionRadio(
                "Do you feel under pressure right now?",
                listOf("Yes", "No", "Maybe"),
                pressureAnswer
            ) { pressureAnswer = it }
            Spacer(Modifier.height(8.dp))
            QuestionRadio(
                "Do you feel tired?",
                listOf("Yes", "No"),
                tiredAnswer
            ) { tiredAnswer = it }
            Spacer(Modifier.height(8.dp))
            QuestionRadio(
                "Do you feel energetic right now?",
                listOf("Very Energetic", "Somewhat Energetic", "Neutral", "Tired"),
                energeticAnswer
            ) { energeticAnswer = it }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    if (!permissions.allPermissionsGranted) {
                        permissions.launchMultiplePermissionRequest()
                        return@Button
                    }
                    vm.sendDetectStress(
                        onResult = { respString ->
                            vm.addChatMessage("bot", respString)
                            nav.navigate(Screen.Chatbot.route) {
                                launchSingleTop = true
                                popUpTo(Screen.Home.route)
                            }
                        },
                        onError = { ex ->
                            vm.addChatMessage("bot", "Error: ${ex.message ?: "unknown"}")
                            nav.navigate(Screen.Chatbot.route) {
                                launchSingleTop = true
                                popUpTo(Screen.Home.route)
                            }
                        }
                    )
                    focusManager.clearFocus()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Start Detection")
            }
        }
    }
}

@Composable
fun QuestionRadio(
    question: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    ElevatedCard(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(question, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(option) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected == option, onClick = { onSelect(option) })
                    Spacer(Modifier.width(8.dp))
                    Text(option)
                }
            }
        }
    }
}

// ---------------- Chatbot Screen ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatbotScreen(nav: NavController, vm: CalmiViewModel) {
    var userInput by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current

    fun sendMsg() {
        val msg = userInput.trim()
        if (msg.isEmpty()) return

        vm.addChatMessage("user", msg)
        userInput = ""

        vm.sendChatMessageToBackend(msg, onResult = { backendResp ->
            vm.addChatMessage("bot", backendResp)
        }, onError = {
            vm.addChatMessage("bot", "Error: ${it.message ?: "unknown"}")
        })
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Chatbot", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, null)
                    }
                }
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = userInput,
                    onValueChange = { userInput = it },
                    placeholder = { Text("Type your message...") },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFD6E8F9),
                        unfocusedContainerColor = Color(0xFFD6E8F9),
                        disabledIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(50),
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        sendMsg()
                        focus.clearFocus()
                    })
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { if (userInput.isNotBlank()) sendMsg() }) {
                    Icon(Icons.Filled.Send, null, tint = Color(0xFF4FC3F7))
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        ) {
            for (chat in vm.chatMessages) {
                if (chat.sender == "bot") {
                    Row(Modifier.padding(vertical = 6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD6E8F9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🤖", fontSize = 20.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        ElevatedCard(
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(chat.message, Modifier.padding(12.dp))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        ElevatedCard(
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = Color(
                                    0xFF4FC3F7
                                )
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.widthIn(max = 260.dp)
                        ) {
                            Text(chat.message, Modifier.padding(12.dp), color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

data class ChatMessage(val sender: String, val message: String)

// ---------------- Settings ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController, vm: CalmiViewModel) {
    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = { Text("Settings") }, navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.Filled.ArrowBack, null)
            }
        })
    }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (vm.darkThemeEnabled) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                        null,
                        tint = Color(0xFF4FC3F7)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Theme", Modifier.weight(1f))
                    Switch(checked = vm.darkThemeEnabled, onCheckedChange = {
                        vm.toggleDarkTheme(it)
                    })
                }
            }
            Spacer(Modifier.height(16.dp))
            ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, null, tint = Color(0xFF4FC3F7))
                        Spacer(Modifier.width(12.dp))
                        Text("About Us")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Developed by 4 engineering students.", fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PhoneAndroid, null, tint = Color(0xFF4FC3F7))
                    Spacer(Modifier.width(12.dp))
                    Text("App Version")
                    Spacer(Modifier.weight(1f))
                    Text("v1.0", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---------------- Utils ----------------

fun createImageUri(context: Context): Uri? {
    val contentResolver = context.contentResolver
    val values = android.content.ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "calmi_photo_${System.currentTimeMillis()}.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
    }
    return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
}

object FileUtil {
    fun from(ctx: Context, uri: Uri): File {
        val input = ctx.contentResolver.openInputStream(uri)
        val file = File(ctx.cacheDir, (uri.lastPathSegment ?: "temp_${System.currentTimeMillis()}"))
        input.use { src -> file.outputStream().use { dst -> src?.copyTo(dst) } }
        return file
    }
}
