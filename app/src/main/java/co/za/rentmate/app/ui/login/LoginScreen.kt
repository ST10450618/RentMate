package co.za.rentmate.app.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import co.za.rentmate.app.auth.AuthUiState
import co.za.rentmate.app.auth.AuthViewModel

@Composable
fun LoginScreen(
    onSignedIn: (jwt: String) -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        if (state is AuthUiState.Success) onSignedIn((state as AuthUiState.Success).jwt)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("RentMate", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        when (val s = state) {
            is AuthUiState.Loading -> CircularProgressIndicator()
            is AuthUiState.Error -> {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.signIn() }) { Text("Try again") }
            }
            else -> Button(onClick = { viewModel.signIn() }) { Text("Sign in with Google") }
        }
    }
}
