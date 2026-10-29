package dev.pantheon.launcher.ui.setup

import android.Manifest
import android.app.AppOpsManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.pantheon.launcher.accessibility.PantheonAccessibilityService
import dev.pantheon.launcher.container
import dev.pantheon.launcher.ui.components.TText
import dev.pantheon.launcher.ui.components.terminalBox
import dev.pantheon.launcher.ui.theme.Pantheon

@Composable
fun BootScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val colors = Pantheon.colors
    val scroll = rememberScrollState()
    var currentStep by remember { mutableIntStateOf(0) }
    var refreshTick by remember { mutableIntStateOf(0) }

    BackHandler {
        if (currentStep > 0) currentStep-- else onComplete()
    }

    // Permission launchers
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshTick++
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshTick++
    }
    val media = context.container.media
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshTick++
    }
    val calLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshTick++
    }
    val accessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshTick++
    }
    val usageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshTick++
    }

    // Permission checks keyed on refreshTick
    val isHome = remember(refreshTick) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true
    }
    val notifGranted = remember(refreshTick) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    val photoGranted = remember(refreshTick) {
        media.hasPhotoAccess()
    }
    val calGranted = remember(refreshTick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }
    val accessGranted = remember(refreshTick) {
        PantheonAccessibilityService.isConnected()
    }
    val usageGranted = remember(refreshTick) {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps?.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            appOps?.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    }

    val totalSteps = 7

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .padding(16.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TText("$", color = colors.textDim, size = 15.sp)
                    TText(" setup --init", color = colors.accent, size = 15.sp, glow = true)
                }
                TText(
                    "[skip all]",
                    color = colors.textDim,
                    size = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onComplete)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }

            // Terminal Boot Log
            Column(
                Modifier
                    .fillMaxWidth()
                    .terminalBox(colors, radius = 8.dp)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                TText("PANTHEON v0.1.0 (x86_64)", color = colors.accent, size = 11.sp, glow = true)
                TText("Linux 6.6-android #1 PREEMPT SMP", color = colors.textDim, size = 11.sp)
                TText("Mounting root filesystem ... [OK]", color = colors.textDim, size = 11.sp)
                TText("Initializing configuration wizard ... [OK]", color = colors.textDim, size = 11.sp)
            }

            // Step Content
            when (currentStep) {
                0 -> StepCard(
                    stepNumber = "1/6",
                    title = "DEFAULT HOME LAUNCHER",
                    description = "Set Pantheon as your default home app so pressing the home button or navigation gestures return to your terminal workspace.",
                    isGranted = isHome,
                    onGrant = {
                        val roleManager = context.getSystemService(RoleManager::class.java)
                        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                            roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                        } else {
                            roleLauncher.launch(Intent(Settings.ACTION_HOME_SETTINGS))
                        }
                    },
                    onNext = { currentStep = 1 },
                )
                1 -> StepCard(
                    stepNumber = "2/6",
                    title = "NOTIFICATION ACCESS",
                    description = "Required to count unread notifications and display badges like [msg:3] on your dock, home grid, and app drawer.",
                    isGranted = notifGranted,
                    onGrant = {
                        notifLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onNext = { currentStep = 2 },
                )
                2 -> StepCard(
                    stepNumber = "3/6",
                    title = "PHOTOS ACCESS",
                    description = "Required for the photo slideshow widget and importing custom image frames on your home screen.",
                    isGranted = photoGranted,
                    onGrant = {
                        photoLauncher.launch(media.photoPermissions())
                    },
                    onNext = { currentStep = 3 },
                )
                3 -> StepCard(
                    stepNumber = "4/6",
                    title = "CALENDAR ACCESS",
                    description = "Required for the terminal agenda widget to query and display today and tomorrow's upcoming events.",
                    isGranted = calGranted,
                    onGrant = {
                        calLauncher.launch(Manifest.permission.READ_CALENDAR)
                    },
                    onNext = { currentStep = 4 },
                )
                4 -> StepCard(
                    stepNumber = "5/6",
                    title = "ACCESSIBILITY SERVICE",
                    description = "Required for double-tapping empty space to lock the screen, and expanding notifications via swipe gestures.",
                    isGranted = accessGranted,
                    onGrant = {
                        accessLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onNext = { currentStep = 5 },
                )
                5 -> StepCard(
                    stepNumber = "6/6",
                    title = "USAGE STATS (OPTIONAL)",
                    description = "Allows Pantheon to inspect recent app usage for frequency-based sorting and terminal analytics.",
                    isGranted = usageGranted,
                    onGrant = {
                        usageLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                    onNext = { currentStep = 6 },
                )
                6 -> FinishedCard(onLaunch = onComplete)
            }

            // Bottom Navigation
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (currentStep > 0) {
                    TText(
                        "‹ prev",
                        color = colors.textDim,
                        size = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { currentStep-- }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                } else {
                    Spacer(Modifier.height(1.dp))
                }

                if (currentStep < totalSteps - 1) {
                    TText(
                        "next ›",
                        color = colors.accent,
                        size = 13.sp,
                        glow = true,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { currentStep++ }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StepCard(
    stepNumber: String,
    title: String,
    description: String,
    isGranted: Boolean,
    onGrant: () -> Unit,
    onNext: () -> Unit,
) {
    val colors = Pantheon.colors
    Column(
        Modifier
            .fillMaxWidth()
            .terminalBox(colors, radius = 8.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TText("── STEP $stepNumber: $title", color = colors.accent, size = 12.sp, glow = true)
            TText(
                if (isGranted) "[GRANTED]" else "[NOT SET]",
                color = if (isGranted) colors.accent else colors.textDim,
                size = 11.sp,
                glow = isGranted,
            )
        }

        TText(description, color = colors.text, size = 12.sp)

        Spacer(Modifier.height(4.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TText(
                "[skip]",
                color = colors.textDim,
                size = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onNext)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(1.dp))
            TText(
                if (isGranted) "[configured]" else "[grant]",
                color = if (isGranted) colors.textDim else colors.accent,
                size = 13.sp,
                glow = !isGranted,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onGrant)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun FinishedCard(onLaunch: () -> Unit) {
    val colors = Pantheon.colors
    Column(
        Modifier
            .fillMaxWidth()
            .terminalBox(colors, radius = 8.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TText("── SETUP COMPLETE", color = colors.accent, size = 12.sp, glow = true)
        TText(
            "All initial configuration steps have been processed.\nYou can fine-tune preferences at any time by editing ~/.pantheonrc from the home menu.",
            color = colors.text,
            size = 12.sp,
        )

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TText(
                "[ launch pantheon ]",
                color = colors.accent,
                size = 14.sp,
                glow = true,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onLaunch)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}
