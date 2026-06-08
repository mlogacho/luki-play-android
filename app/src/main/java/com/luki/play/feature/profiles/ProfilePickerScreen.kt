// feature/profiles/ProfilePickerScreen.kt
package com.luki.play.feature.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

/**
 * Selector de perfil — UI tipo Netflix con grid 2-columns.
 *
 * Si el perfil requiere PIN, se muestra la pantalla parental encima
 * vía [com.luki.play.feature.parental.ParentalPinScreen]; el caller
 * proporciona [onRequestParentalGate].
 */
@Composable
fun ProfilePickerScreen(
    onProfileChosen: (String) -> Unit,
    onRequestParentalGate: (onVerified: () -> Unit) -> Unit,
    viewModel: ProfilePickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.pendingPinForProfile?.id) {
        state.pendingPinForProfile?.let {
            onRequestParentalGate { viewModel.onPinVerified() }
        }
    }
    LaunchedEffect(state.pickedProfileId) {
        state.pickedProfileId?.let {
            onProfileChosen(it)
            viewModel.consumePicked()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text(
                "¿Quién está viendo?",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 24.dp),
            )

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                return@Column
            }

            state.errorMessage?.let { msg ->
                Text(msg, color = MaterialTheme.colorScheme.error)
                return@Column
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.profiles, key = { it.id }) { profile ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.onProfileSelected(profile) }
                            .padding(8.dp),
                    ) {
                        Box(
                            Modifier
                                .size(96.dp)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (profile.avatarUrl != null) {
                                AsyncImage(
                                    model = profile.avatarUrl,
                                    contentDescription = profile.name,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = profile.name.take(1).uppercase(),
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.headlineLarge,
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = profile.name,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        if (profile.isKid) {
                            Text(
                                "Infantil",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
