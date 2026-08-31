package com.letters2my.app.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * First-launch onboarding. At completion: initialize local archive state
 * (seeded branches are created by the Application), then transition
 * immediately into the main UI — never blocked on analytics or network.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var step by remember { mutableStateOf(0) }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Email,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(24.dp))
            Text("Letters to My", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            when (step) {
                0 -> {
                    Text(
                        "Write letters for the people you love — to open on " +
                            "birthdays, graduations, weddings, or a difficult day.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                1 -> {
                    Text(
                        "Add your children in Family, write letters, and seal them " +
                            "with an unlock rule: a specific date, a birthday age, " +
                            "or a life event.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Text(
                        "Back up with an encrypted .letterstomy archive — the same " +
                            "format iOS uses — and restore on any device.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { i ->
                    val isCurrent = i == step
                    // MaterialTheme.colorScheme is a @Composable read — must be
                    // hoisted OUT of the Canvas draw lambda (DrawScope is not a
                    // composable context).
                    val dotColor = if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                    val dotSize = if (isCurrent) 10.dp else 8.dp
                    Box(
                        modifier = Modifier.size(dotSize),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(Modifier.size(dotSize)) {
                            drawCircle(color = dotColor)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { if (step < 2) step++ else onComplete() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (step < 2) "Next" else "Get Started")
            }
        }
    }
}