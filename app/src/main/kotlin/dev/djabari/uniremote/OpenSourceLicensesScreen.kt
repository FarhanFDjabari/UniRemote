package dev.djabari.uniremote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

private data class OpenSourceLibrary(
    val name: String,
    val copyright: String,
    val url: String,
)

private val openSourceLibraries = listOf(
    OpenSourceLibrary(
        name = "AndroidX / Jetpack (Compose, Material 3, Activity, Lifecycle, Navigation, DataStore, Window, Core)",
        copyright = "The Android Open Source Project",
        url = "https://developer.android.com/jetpack/androidx",
    ),
    OpenSourceLibrary(
        name = "Dagger & Hilt",
        copyright = "The Dagger Authors",
        url = "https://github.com/google/dagger",
    ),
    OpenSourceLibrary(
        name = "Kotlin Standard Library",
        copyright = "JetBrains s.r.o. and Kotlin Programming Language contributors",
        url = "https://github.com/JetBrains/kotlin",
    ),
    OpenSourceLibrary(
        name = "kotlinx.coroutines",
        copyright = "JetBrains s.r.o. and contributors",
        url = "https://github.com/Kotlin/kotlinx.coroutines",
    ),
    OpenSourceLibrary(
        name = "OkHttp",
        copyright = "Square, Inc.",
        url = "https://github.com/square/okhttp",
    ),
    OpenSourceLibrary(
        name = "Okio",
        copyright = "Square, Inc.",
        url = "https://github.com/square/okio",
    ),
    OpenSourceLibrary(
        name = "Guava (ListenableFuture)",
        copyright = "The Guava Authors",
        url = "https://github.com/google/guava",
    ),
    OpenSourceLibrary(
        name = "JSR-305 Annotations",
        copyright = "FindBugs project",
        url = "https://github.com/findbugsproject/findbugs",
    ),
    OpenSourceLibrary(
        name = "javax.inject (JSR-330)",
        copyright = "The JSR-330 Expert Group",
        url = "https://github.com/javax-inject/javax-inject",
    ),
    OpenSourceLibrary(
        name = "Jakarta Dependency Injection",
        copyright = "Eclipse Foundation",
        url = "https://github.com/jakartaee/inject",
    ),
    OpenSourceLibrary(
        name = "JetBrains Java Annotations",
        copyright = "JetBrains s.r.o.",
        url = "https://github.com/JetBrains/java-annotations",
    ),
    OpenSourceLibrary(
        name = "JSpecify",
        copyright = "The JSpecify Authors",
        url = "https://github.com/jspecify/jspecify",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicensesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val licenseText = remember {
        context.resources.openRawResource(R.raw.apache_license_2_0)
            .bufferedReader()
            .use { it.readText() }
    }
    var showLicenseText by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Open-source licenses") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = modifier,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "UniRemote is built with these open-source libraries. " +
                        "Thank you to their authors.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            items(openSourceLibraries) { library ->
                LibraryCard(
                    library = library,
                    onClick = { uriHandler.openUri(library.url) },
                )
            }

            item {
                TextButton(
                    onClick = { showLicenseText = !showLicenseText },
                ) {
                    Text(if (showLicenseText) "Hide license text" else "Show license text")
                }
            }

            if (showLicenseText) {
                item {
                    Text(
                        text = licenseText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryCard(
    library: OpenSourceLibrary,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(library.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = library.copyright,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Apache License 2.0",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = library.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
