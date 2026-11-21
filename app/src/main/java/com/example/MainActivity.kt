package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.*
import com.example.firebase.*
import com.example.ui.MainViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.components.B2bCoachPortalDialog
import com.example.ui.components.OnboardingDialog
import com.example.ui.i18n.stringsFor
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize App Check BEFORE enableEdgeToEdge / setContent / ViewModels
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val credentialManager = remember(context) { CredentialManager.create(context) }
            val currentUser by Firebase.auth.authStateFlow().collectAsStateWithLifecycle(
                initialValue = Firebase.auth.currentUser
            )

            var authDarkTheme by remember { mutableStateOf(true) }
            var authLang by remember { mutableStateOf("ar") }

            LaunchedEffect(Unit) {
                attemptAutoSignIn(
                    context = context,
                    credentialManager = credentialManager,
                    onAuthSuccess = {},
                    onUnauthenticated = {},
                    scope = scope
                )
            }

            val user = currentUser
            if (user == null) {
                val strings = stringsFor(authLang)
                val dir = if (authLang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
                AmainoLabTheme(darkTheme = authDarkTheme, isArabic = authLang == "ar") {
                    CompositionLocalProvider(LocalLayoutDirection provides dir) {
                        AuthScreen(
                            strings = strings,
                            darkTheme = authDarkTheme,
                            onToggleTheme = { authDarkTheme = !authDarkTheme },
                            onToggleLang = { authLang = if (authLang == "ar") "en" else "ar" },
                            onAuthSuccess = {}
                        )
                    }
                }
            } else {
                key(user.uid) {
                    val app = context.applicationContext as Application
                    val vm: MainViewModel = viewModel(
                        key = user.uid,
                        factory = viewModelFactory {
                            initializer {
                                MainViewModel(
                                    repo = FirestoreRepository(app),
                                    aiRepo = AiRepository(),
                                    photoStore = ProgressPhotoStore(app),
                                    userId = user.uid,
                                    defaultDisplayName = user.displayName.orEmpty()
                                )
                            }
                        }
                    )
                    MainAppContent(
                        viewModel = vm,
                        onSignOut = { signOut(credentialManager, scope) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAppContent(
    viewModel: MainViewModel,
    onSignOut: () -> Unit
) {
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val workoutsState by viewModel.workoutsState.collectAsStateWithLifecycle()
    val mealsState by viewModel.mealsState.collectAsStateWithLifecycle()
    val bodyLogsState by viewModel.bodyLogsState.collectAsStateWithLifecycle()
    val bestsState by viewModel.exerciseBestsState.collectAsStateWithLifecycle()
    val coachClientsState by viewModel.coachClientsState.collectAsStateWithLifecycle()
    val customExercisesState by viewModel.customExercisesState.collectAsStateWithLifecycle()

    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()
    val swappedIds by viewModel.swappedIds.collectAsStateWithLifecycle()
    val setStates by viewModel.setStates.collectAsStateWithLifecycle()
    val sessionElapsedSec by viewModel.sessionElapsedSec.collectAsStateWithLifecycle()
    val restRemainingSec by viewModel.restRemainingSec.collectAsStateWithLifecycle()
    val restDurationSec by viewModel.restDurationSec.collectAsStateWithLifecycle()
    val tempoActive by viewModel.tempoActive.collectAsStateWithLifecycle()
    val tempoPhaseMs by viewModel.tempoPhaseMs.collectAsStateWithLifecycle()
    val tempoRepCount by viewModel.tempoRepCount.collectAsStateWithLifecycle()
    val progressPhotos by viewModel.progressPhotos.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isCoachStreaming by viewModel.isCoachStreaming.collectAsStateWithLifecycle()
    val coachError by viewModel.coachError.collectAsStateWithLifecycle()
    val mealScanState by viewModel.mealScanState.collectAsStateWithLifecycle()

    val remoteProfile = (profileState as? UiState.Success)?.data
    val profile = remoteProfile ?: viewModel.currentProfile
    val strings = stringsFor(profile.lang)
    val layoutDir = if (profile.lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr

    val workouts = (workoutsState as? UiState.Success)?.data.orEmpty()
    val meals = (mealsState as? UiState.Success)?.data.orEmpty()
    val bodyLogs = (bodyLogsState as? UiState.Success)?.data.orEmpty()
    val bests = (bestsState as? UiState.Success)?.data.orEmpty()
    val coachClients = (coachClientsState as? UiState.Success)?.data.orEmpty()
    val customExercises = (customExercisesState as? UiState.Success)?.data.orEmpty()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showB2bDialog by remember { mutableStateOf(false) }
    var scannerMode by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    AmainoLabTheme(darkTheme = profile.darkTheme, isArabic = profile.lang == "ar") {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
            if (profileState is UiState.Success && (remoteProfile == null || !remoteProfile.onboarded || showProfileDialog)) {
                val canDismiss = remoteProfile?.onboarded == true
                OnboardingDialog(
                    strings = strings,
                    initialProfile = profile,
                    dismissible = canDismiss,
                    onDismiss = { showProfileDialog = false },
                    onSave = { name, sex, age, weight, height, goal, act ->
                        viewModel.saveOnboarding(name, sex, age, weight, height, goal, act)
                        showProfileDialog = false
                    }
                )
            }

            if (showB2bDialog) {
                B2bCoachPortalDialog(
                    strings = strings,
                    clients = coachClients,
                    onAddClient = viewModel::addCoachClient,
                    onDeleteClient = viewModel::deleteCoachClient,
                    onApplyClient = viewModel::applyCoachClientPlan,
                    onDismiss = { showB2bDialog = false }
                )
            }

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Brush.linearGradient(listOf(IndigoPrimary, VioletAccent, CyanAccent))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                Text("Amaino Lab", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { scannerMode = "meal" },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("topbar_scanner_button")
                            ) {
                                Icon(Icons.Filled.QrCodeScanner, contentDescription = strings.scannerTitle)
                            }
                            IconButton(
                                onClick = { showB2bDialog = true },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("topbar_b2b_button")
                            ) {
                                Icon(Icons.Filled.School, contentDescription = strings.coachB2bTitle)
                            }
                            TextButton(
                                onClick = viewModel::toggleLang,
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("topbar_lang_button")
                            ) {
                                Text(strings.langToggle, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = viewModel::toggleTheme,
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("topbar_theme_button")
                            ) {
                                Icon(
                                    if (profile.darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                    contentDescription = "Theme"
                                )
                            }
                            IconButton(
                                onClick = { showProfileDialog = true },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("topbar_profile_button")
                            ) {
                                Icon(Icons.Filled.Person, contentDescription = strings.editProfile)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                },
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        val navItems = listOf(
                            Triple(0, strings.nvHome, Icons.Filled.Home),
                            Triple(1, strings.nvPlan, Icons.Filled.FitnessCenter),
                            Triple(2, strings.nvFood, Icons.Filled.Restaurant),
                            Triple(3, strings.nvAi, Icons.Filled.AutoAwesome),
                            Triple(4, strings.nvProg, Icons.Filled.Insights)
                        )
                        navItems.forEach { (idx, label, icon) ->
                            NavigationBarItem(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_tab_$idx")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(modifier = Modifier.fillMaxSize().widthIn(max = 840.dp)) {
                        when (profileState) {
                            is UiState.Loading -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                            is UiState.Error -> {
                                val err = (profileState as UiState.Error).message
                                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                    Card {
                                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text(err, color = MaterialTheme.colorScheme.error)
                                            OutlinedButton(onClick = onSignOut) { Text(strings.signOut) }
                                        }
                                    }
                                }
                            }
                            is UiState.Success -> {
                                when (selectedTab) {
                                    0 -> HomeScreen(
                                        strings = strings,
                                        profile = profile,
                                        workouts = workouts,
                                        meals = meals,
                                        bodyLogs = bodyLogs,
                                        bests = bests,
                                        selectedDayIdx = selectedDay,
                                        setStates = setStates,
                                        customExercises = customExercises,
                                        onStartWorkout = { selectedTab = 1 },
                                        onEditProfile = { showProfileDialog = true },
                                        onActivateDeload = viewModel::activateDeloadToday,
                                        onSyncTdee = viewModel::syncAdaptiveTdee,
                                        onAutoBuildMeals = viewModel::autoBuildMeals,
                                        onQuickAddProtein = { name, cal, p, c, f -> viewModel.addMeal(name, cal, p, c, f, "auto") },
                                        onAskCoach = { selectedTab = 3 },
                                        onSetWater = viewModel::setWaterCups
                                    )
                                    1 -> PlanScreen(
                                        strings = strings,
                                        profile = profile,
                                        selectedDayIdx = selectedDay,
                                        daySlots = viewModel.getCurrentDaySlots(),
                                        swappedIds = swappedIds,
                                        setStates = setStates,
                                        bests = bests,
                                        sessionElapsedSec = sessionElapsedSec,
                                        restDurationSec = restDurationSec,
                                        tempoActive = tempoActive,
                                        tempoPhaseMs = tempoPhaseMs,
                                        tempoRepCount = tempoRepCount,
                                        effectiveSetsProvider = viewModel::effectiveSetsCount,
                                        onSelectSplit = viewModel::selectSplit,
                                        onSelectEquip = viewModel::selectEquip,
                                        onSelectInjury = viewModel::selectInjury,
                                        onToggleTimeCrunch = viewModel::toggleTimeCrunch,
                                        onSelectDay = viewModel::selectDay,
                                        onSwapExercise = viewModel::swapExerciseInSlot,
                                        onUpdateSetWeight = viewModel::updateSetWeight,
                                        onUpdateSetReps = viewModel::updateSetReps,
                                        onToggleSetDone = viewModel::toggleSetDone,
                                        onAutoFill = viewModel::autoFillTargetWeights,
                                        onToggleTempo = viewModel::toggleTempoPacer,
                                        onSetRestDuration = viewModel::setRestDuration,
                                        onAddCustomExercise = viewModel::addCustomExercise,
                                        onDeleteCustomExercise = viewModel::deleteCustomExercise,
                                        onFinishWorkout = viewModel::finishWorkout
                                    )
                                    2 -> FoodScreen(
                                        strings = strings,
                                        profile = profile,
                                        meals = meals,
                                        bodyLogs = bodyLogs,
                                        selectedDayIdx = selectedDay,
                                        mealScanState = mealScanState,
                                        onOpenScanner = { mode -> scannerMode = mode },
                                        onScanMealBitmap = { bmp -> viewModel.scanMealBitmap(bmp, "meal") },
                                        onClearMealScan = viewModel::clearMealScanState,
                                        onToggleSyncNutr = viewModel::toggleSyncNutr,
                                        onSyncAdaptiveTdee = viewModel::syncAdaptiveTdee,
                                        onAutoBuildMeals = viewModel::autoBuildMeals,
                                        onApplyBudgetPlan = viewModel::applyBudgetPlan,
                                        onAddMeal = viewModel::addMeal,
                                        onDeleteMeal = viewModel::deleteMeal,
                                        onAdjustTarget = { showProfileDialog = true }
                                    )
                                    3 -> CoachScreen(
                                        strings = strings,
                                        messages = chatMessages,
                                        isStreaming = isCoachStreaming,
                                        coachError = coachError,
                                        onSendMessage = viewModel::sendCoachMessage,
                                        onClearChat = viewModel::clearCoachChat,
                                        onShowMessage = viewModel::emitMessage
                                    )
                                    4 -> ProgressScreen(
                                        strings = strings,
                                        profile = profile,
                                        workouts = workouts,
                                        bodyLogs = bodyLogs,
                                        photos = progressPhotos,
                                        onLogWeight = viewModel::logWeight,
                                        onSaveMeasurements = viewModel::saveMeasurements,
                                        onAddPhoto = viewModel::addProgressPhoto,
                                        onDeletePhoto = viewModel::deleteProgressPhoto,
                                        onOpenB2bPortal = { showB2bDialog = true },
                                        onSignOut = onSignOut
                                    )
                                }
                            }
                        }

                        // Floating Rest Timer Pill
                        val rem = restRemainingSec
                        AnimatedVisibility(
                            visible = rem != null,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
                        ) {
                            if (rem != null) {
                                Card(
                                    shape = RoundedCornerShape(999.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.border(1.5.dp, EmeraldOk, RoundedCornerShape(999.dp))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Filled.Timer, contentDescription = null, tint = EmeraldOk)
                                        Text(
                                            "%02d:%02d".format(rem / 60, rem % 60),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        TextButton(onClick = { viewModel.addRestSeconds(15) }) {
                                            Text("+15s")
                                        }
                                        IconButton(onClick = viewModel::stopRestTimer) {
                                            Icon(Icons.Filled.Close, contentDescription = "Stop Rest Timer")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val activeScannerMode = scannerMode
            if (activeScannerMode != null) {
                NutritionScannerScreen(
                    strings = strings,
                    initialMode = activeScannerMode,
                    mealScanState = mealScanState,
                    onAnalyzeBitmap = viewModel::scanMealBitmap,
                    onClearScan = viewModel::clearMealScanState,
                    onLogMeal = { name, cal, p, c, f, src ->
                        viewModel.addMeal(name, cal, p, c, f, src)
                        viewModel.emitMessage(
                            if (profile.lang == "ar") "تمت إضافة الوجبة بنجاح!" else "Scanned food logged to today!"
                        )
                    },
                    onClose = { scannerMode = null }
                )
            }
        }
    }
}
