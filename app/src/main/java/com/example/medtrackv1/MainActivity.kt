package com.example.medtrackv1

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.medtrackv1.ui.screens.MainScreen
import com.example.medtrackv1.ui.theme.Medtrackv1Theme
import com.example.medtrackv1.ui.viewmodel.MedicationViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MedicationViewModel by viewModels {
        val app = application as MedTrackApplication
        MedicationViewModel.Factory(app.medicationRepository, app.doseLogRepository, app.counterRepository)
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        enableEdgeToEdge()
        setContent {
            Medtrackv1Theme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}
