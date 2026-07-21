package com.agustin.tarati.ui.components


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Paginación automática por scroll: dispara [onLoadMore] cuando quedan menos de
 * [threshold] ítems entre el último visible y el final de la lista.
 *
 * Converge el bloque `derivedStateOf { lastVisible >= total - N }` + `LaunchedEffect`
 * que duplicaban las pantallas con listados paginados (historial, feed, perfil).
 */
@Composable
fun InfiniteScrollEffect(
    listState: LazyListState,
    threshold: Int = 3,
    onLoadMore: () -> Unit,
) {
    val shouldLoadMore by remember(listState, threshold) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - threshold
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }
}

/**
 * Ítem de pie de lista con un spinner de "cargando más". No-op si [isLoadingMore] es false.
 * Compañero de [InfiniteScrollEffect] en los listados paginados.
 */
fun LazyListScope.loadingMoreIndicator(isLoadingMore: Boolean) {
    if (!isLoadingMore) return
    item(key = "loading_more") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}
