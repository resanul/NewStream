package com.lagradost.cloudstream4

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.app_name
import com.lagradost.cloudstream4.generated.resources.default_icon
import com.lagradost.cloudstream4.theme.CloudStreamTheme
import com.lagradost.cloudstream4.theme.CloudStreamThemeMode
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.net.URI

private enum class DesktopPage(val title: String) {
    Home("Home"),
    Search("Search"),
    Library("Library"),
    Settings("Settings")
}

private data class CatalogItem(
    val title: String,
    val description: String,
    val tags: String
)

private val catalog = listOf(
    CatalogItem("NewStream", "Your independent streaming library for desktop.", "Featured"),
    CatalogItem("Discover providers", "Browse and manage community content sources.", "Providers"),
    CatalogItem("Watch anywhere", "Keep your library and playback preferences together.", "Library")
)

fun main() = application {
    val windowState = rememberWindowState(width = 1100.dp, height = 700.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = stringResource(Res.string.app_name),
        icon = painterResource(Res.drawable.default_icon),
        state = windowState
    ) {
        NewStreamDesktop(windowState)
    }
}

@Composable
private fun NewStreamDesktop(windowState: WindowState) {
    var page by remember { mutableStateOf(DesktopPage.Home) }
    var darkMode by remember { mutableStateOf(true) }
    val library = remember { mutableStateListOf<String>() }

    CloudStreamTheme(mode = if (darkMode) CloudStreamThemeMode.Dark else CloudStreamThemeMode.Light) {
        Scaffold { padding ->
            Row(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalAlignment = Alignment.Top
            ) {
                NavigationPanel(page, darkMode) { page = it }
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    when (page) {
                        DesktopPage.Home -> HomePage(library) { openNewStreamSite() }
                        DesktopPage.Search -> SearchPage(library)
                        DesktopPage.Library -> LibraryPage(library)
                        DesktopPage.Settings -> SettingsPage(darkMode) { darkMode = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationPanel(
    selected: DesktopPage,
    darkMode: Boolean,
    onSelect: (DesktopPage) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxHeight().width(230.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.fillMaxHeight().padding(20.dp)) {
            Text("NEWSTREAM", fontWeight = FontWeight.Bold, color = Color(0xFF31E6D1))
            Text("Desktop", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(28.dp))
            DesktopPage.entries.forEach { item ->
                val selectedColor = if (item == selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                }
                TextButton(
                    onClick = { onSelect(item) },
                    modifier = Modifier.fillMaxWidth().background(selectedColor, RoundedCornerShape(12.dp))
                ) {
                    Text(item.title, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(if (darkMode) "Dark theme" else "Light theme", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun HomePage(library: List<String>, onOpen: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Welcome to NewStream", style = MaterialTheme.typography.headlineLarge)
        Text(
            "An independent desktop experience for your streaming library.",
            style = MaterialTheme.typography.bodyLarge
        )
        Button(onClick = onOpen) { Text("Open NewStream online") }
        Divider()
        Text("Quick start", style = MaterialTheme.typography.titleLarge)
        Text("Use Search to find content, then save items to Library.")
        Text("${library.size} item(s) in your library", style = MaterialTheme.typography.labelLarge)
        catalog.forEach { item -> CatalogCard(item, library.contains(item.title), {}) }
    }
}

@Composable
private fun SearchPage(library: MutableList<String>) {
    var query by remember { mutableStateOf("") }
    val results = catalog.filter { item ->
        query.isBlank() || item.title.contains(query, true) || item.tags.contains(query, true)
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineLarge)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search NewStream") },
            singleLine = true
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(results) { item ->
                CatalogCard(item, library.contains(item.title)) {
                    if (library.contains(item.title)) library.remove(item.title) else library.add(item.title)
                }
            }
        }
    }
}

@Composable
private fun LibraryPage(library: MutableList<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Library", style = MaterialTheme.typography.headlineLarge)
        if (library.isEmpty()) {
            Text("Your library is empty. Search for something and save it here.")
        } else {
            library.toList().forEach { title ->
                val item = catalog.first { it.title == title }
                CatalogCard(item, true) { library.remove(title) }
            }
        }
    }
}

@Composable
private fun CatalogCard(item: CatalogItem, saved: Boolean, onToggleSaved: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(item.description, style = MaterialTheme.typography.bodyMedium)
                Text(item.tags, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = onToggleSaved) { Text(if (saved) "Remove" else "Save") }
        }
    }
}

@Composable
private fun SettingsPage(darkMode: Boolean, onDarkModeChanged: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge)
        Text("Personalize your NewStream desktop experience.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dark theme", modifier = Modifier.weight(1f))
            TextButton(onClick = { onDarkModeChanged(!darkMode) }) {
                Text(if (darkMode) "Enabled" else "Disabled")
            }
        }
        Text("Version 1.0.2", style = MaterialTheme.typography.labelMedium)
    }
}

private fun openNewStreamSite() {
    if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().browse(URI("https://github.com/resanul/NewStream"))
    }
}
