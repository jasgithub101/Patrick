package com.patrick.faceid

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.patrick.faceid.app.EngineStatus
import com.patrick.faceid.app.RecognitionEngine
import com.patrick.faceid.ui.FaceIdTheme
import com.patrick.faceid.ui.HomeScreen
import com.patrick.faceid.ui.IdentifyScreen
import com.patrick.faceid.ui.IdentifyViewModel
import com.patrick.faceid.ui.PeopleScreen
import com.patrick.faceid.ui.PeopleViewModel
import com.patrick.faceid.ui.RegisterScreen
import com.patrick.faceid.ui.RegisterViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FaceIdTheme { Surface(Modifier.fillMaxSize()) { FaceIdApp() } } }
    }
}

private object Routes {
    const val HOME = "home"
    const val REGISTER = "register"
    const val IDENTIFY = "identify"
    const val PEOPLE = "people"
}

@Composable
private fun FaceIdApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    var status by remember { mutableStateOf<EngineStatus?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        status = runCatching { RecognitionEngine.get(context).status() }.getOrNull()
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                status = status,
                onRegister = { navController.navigate(Routes.REGISTER) },
                onIdentify = { navController.navigate(Routes.IDENTIFY) },
                onPeople = { navController.navigate(Routes.PEOPLE) },
            )
        }
        composable(Routes.REGISTER) {
            TitledScreen("Register", onBack = { navController.popBackStack() }) {
                WithCameraPermission {
                    val vm: RegisterViewModel = viewModel()
                    val state by vm.state.collectAsState()
                    RegisterScreen(state, vm) {
                        refreshKey++
                        navController.popBackStack()
                    }
                }
            }
        }
        composable(Routes.IDENTIFY) {
            TitledScreen("Identify", onBack = { navController.popBackStack() }) {
                WithCameraPermission {
                    val vm: IdentifyViewModel = viewModel()
                    val state by vm.state.collectAsState()
                    IdentifyScreen(state, vm)
                }
            }
        }
        composable(Routes.PEOPLE) {
            TitledScreen("Employees", onBack = { navController.popBackStack() }) {
                val vm: PeopleViewModel = viewModel()
                val state by vm.state.collectAsState()
                PeopleScreen(state)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TitledScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { content() }
    }
}

/** Asks once, then renders [content]. Recognition itself needs no permission; only the camera does. */
@Composable
private fun WithCameraPermission(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var asked by remember { mutableStateOf(false) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    LaunchedEffect(Unit) {
        if (!granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (granted) {
        content()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(60.dp))
            Text(
                "Camera access is needed to capture a face.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { launcher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Grant camera access") }
        }
    }
}
