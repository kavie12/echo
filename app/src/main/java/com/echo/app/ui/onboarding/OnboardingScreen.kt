@file:OptIn(ExperimentalMaterial3Api::class)

package com.echo.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.echo.app.domain.model.Role

@Composable
fun OnboardingScreen(onSelect: (Role) -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Echo", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        Text(
            "Keep messages and clipboard in sync across your phones, over your own network.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(32.dp))
        Text("What is this phone?", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        RoleCard("Host", "The main phone. It shares its messages and clipboard with others.") { onSelect(Role.HOST) }
        Spacer(Modifier.height(12.dp))
        RoleCard("Client", "A secondary phone. It receives data from the host.") { onSelect(Role.CLIENT) }
    }
}

@Composable
private fun RoleCard(title: String, description: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
