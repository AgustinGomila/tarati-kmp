package com.agustin.tarati.features.online.supporter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agustin.tarati.services.billing.SupporterInterval
import com.agustin.tarati.services.localization.LocalAppLanguage
import com.agustin.tarati.services.localization.localizedString
import com.agustin.tarati.shared.generated.resources.Res
import com.agustin.tarati.shared.generated.resources.supporter_body
import com.agustin.tarati.shared.generated.resources.supporter_checkout_failed
import com.agustin.tarati.shared.generated.resources.supporter_completed_payment
import com.agustin.tarati.shared.generated.resources.supporter_continue
import com.agustin.tarati.shared.generated.resources.supporter_monthly
import com.agustin.tarati.shared.generated.resources.supporter_once
import com.agustin.tarati.shared.generated.resources.supporter_play_cta
import com.agustin.tarati.shared.generated.resources.supporter_thanks
import com.agustin.tarati.shared.generated.resources.supporter_title
import com.agustin.tarati.shared.generated.resources.supporter_unavailable_platform
import com.agustin.tarati.ui.components.HeartText
import com.agustin.tarati.ui.components.topbar.TaratiTopBar
import com.agustin.tarati.ui.components.topbar.TopBarNavigationType
import com.agustin.tarati.ui.theme.TaratiIcons
import org.koin.compose.koinInject

/**
 * Pantalla de pago Supporter (fase C3) — donación única o mensual vía Stripe.
 *
 * Inicia el Checkout Session en el servidor y abre la URL de Stripe en el browser
 * ([androidx.compose.ui.platform.LocalUriHandler]). El grant del entitlement llega por
 * el webhook server-side; al volver, [ISupporterViewModel.refresh] trae el estado nuevo.
 * En plataformas sin Stripe (Android/iOS) muestra una nota — el gate de compra sigue off.
 */
@Composable
fun SupporterScreen(
    viewModel: ISupporterViewModel = koinInject(),
    onNavigateBack: () -> Unit = {},
) {
    val currentLanguage = LocalAppLanguage.current
    key(currentLanguage) {
        SupporterContent(viewModel = viewModel, onNavigateBack = onNavigateBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupporterContent(
    viewModel: ISupporterViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    // Acción de compra nativa (Google Play en Android); null en Desktop/Web/iOS.
    val nativePurchase = rememberSupporterPurchaseAction()

    // Refrescar al entrar (trae el estado tras volver del pago) y abrir el checkout.
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(Unit) {
        viewModel.checkoutUrlEvent.collect { url -> uriHandler.openUri(url) }
    }

    Scaffold(
        topBar = {
            TaratiTopBar(
                title = localizedString(Res.string.supporter_title),
                navigationType = TopBarNavigationType.Back,
                onNavigationClick = onNavigateBack,
            )
        },
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = TaratiIcons.Supporter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(12.dp))
                HeartText(
                    text = localizedString(Res.string.supporter_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (state.isSupporter) {
                    Spacer(Modifier.height(16.dp))
                    HeartText(
                        text = localizedString(Res.string.supporter_thanks),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.height(24.dp))

                if (!viewModel.stripeAvailable) {
                    // Android: compra del tier supporter por Google Play (precio fijo de consola).
                    if (nativePurchase != null) {
                        Button(
                            onClick = nativePurchase,
                            enabled = !state.isSupporter,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(localizedString(Res.string.supporter_play_cta))
                        }
                    } else {
                        // iOS u otras plataformas sin compra nativa.
                        Text(
                            text = localizedString(Res.string.supporter_unavailable_platform),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    return@Column
                }

                // ── Intervalo: único / mensual ────────────────────────────────
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.interval == SupporterInterval.ONCE,
                        onClick = { viewModel.selectInterval(SupporterInterval.ONCE) },
                        label = { Text(localizedString(Res.string.supporter_once)) },
                    )
                    FilterChip(
                        selected = state.interval == SupporterInterval.MONTHLY,
                        onClick = { viewModel.selectInterval(SupporterInterval.MONTHLY) },
                        label = { Text(localizedString(Res.string.supporter_monthly)) },
                    )
                }

                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = localizedString(Res.string.supporter_checkout_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = viewModel::checkout,
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(localizedString(Res.string.supporter_continue))
                    }
                }

                Spacer(Modifier.height(8.dp))

                TextButton(onClick = viewModel::refresh) {
                    Text(localizedString(Res.string.supporter_completed_payment))
                }
            }
        }
    }
}
