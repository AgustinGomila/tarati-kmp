package com.agustin.tarati.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agustin.tarati.core.domain.ai.services.Difficulty
import com.agustin.tarati.core.domain.game.time.TimeControlMode
import com.agustin.tarati.services.billing.EntitlementsRepository
import com.agustin.tarati.services.billing.LockedPalettes
import com.agustin.tarati.services.billing.effectiveOwnedProducts
import com.agustin.tarati.services.billing.lockedPaletteNames
import com.agustin.tarati.services.localization.AppLanguage
import com.agustin.tarati.ui.components.game.draw.pieces.ConversionAnimationStyle
import com.agustin.tarati.ui.components.game.draw.pieces.PieceTypeManager
import com.agustin.tarati.ui.components.game.draw.pieces.PieceTypes
import com.agustin.tarati.ui.theme.AppTheme
import com.agustin.tarati.ui.theme.PaletteList
import com.agustin.tarati.ui.theme.PaletteManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.agustin.tarati.ui.theme.availablePalettes as allAvailablePalettes

/**
 * SettingsViewModel base para todas las plataformas.
 *
 * Contiene toda la lógica de configuración que NO depende de APIs Android-specific
 * (billing, achievements).
 *
 * Para Android, usar [AndroidSettingsViewModel] que extiende esta clase y agrega
 * funcionalidad de billing y achievements.
 */
open class SettingsViewModel(
    private val repository: SettingsRepository,
    entitlementsRepository: EntitlementsRepository,
) : ViewModel(), ISettingsViewModel {

    // ── Estado persistido ─────────────────────────────────────────────────────

    private val _hasTutorialBeenSeen = MutableStateFlow(false)
    override val hasTutorialBeenSeen: StateFlow<Boolean> = _hasTutorialBeenSeen

    private val _hasMpTutorialBeenSeen = MutableStateFlow(false)
    override val hasMpTutorialBeenSeen: StateFlow<Boolean> = _hasMpTutorialBeenSeen

    // Ownership cross-platform leído del servidor, expandido con la regla supporter
    // (supporter desbloquea todo lo premium). Override en AndroidSettingsViewModel para
    // mergear además con las compras locales de Google Play Billing.
    override val purchasedProductIds: StateFlow<Set<String>> = entitlementsRepository.entitlements
        .map { effectiveOwnedProducts(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // ── settingsState: combina todos los flows del repositorio ─────────────────
    //
    // Se divide en dos etapas para no superar el límite de aridad del combine
    // estándar (5 flujos). Cada etapa genera un StateFlow intermedio que luego
    // se combina con el resto.

    // Etapa 1 — visual board state
    private val boardVisualStateFlow = combine(
        repository.labelsVisibility,
        repository.verticesVisibility,
        repository.edgesVisibility,
        repository.regionsVisibility,
        repository.perimeterVisibility,
    ) { labels, vertices, edges, regions, perimeter ->
        BoardVisualState(
            labelsVisibles = labels,
            verticesVisibles = vertices,
            edgesVisibles = edges,
            regionsVisibles = regions,
            perimeterVisible = perimeter,
        )
    }.combine(
        combine(repository.animateEffects, repository.conversionAnimationStyle) { animate, style ->
            animate to style
        },
    ) { boardVisual, (animate, style) ->
        boardVisual.copy(animateEffects = animate, conversionAnimationStyle = style)
    }

    // Etapa 2 — sound state
    private val soundStateFlow = combine(
        repository.soundEnabled,
        repository.soundVolume,
    ) { enabled, volume ->
        SoundState(soundEnabled = enabled, soundVolume = volume)
    }

    // Etapa 2b — time control & pre-moves
    //
    // Se agrupa como Pair para no exceder la aridad del combine final.
    // Ambos flows son livianos y cambian con baja frecuencia (solo cuando el
    // usuario toca el selector o el toggle), por lo que el coste del combine
    // adicional es despreciable.
    private val timeSettingsFlow = combine(
        repository.timeControl,
        repository.preMovesEnabled,
    ) { tc, preMoves -> tc to preMoves }

    // Etapa 3 — estado completo
    override val settingsState: StateFlow<SettingsState> = combine(
        combine(repository.appTheme, repository.difficulty, repository.difficultyBlack) { theme, diff, diffBlack ->
            Triple(theme, diff, diffBlack)
        },
        combine(repository.userName, repository.language, repository.palette) { user, lang, palette ->
            Triple(user, lang, palette)
        },
        combine(boardVisualStateFlow, soundStateFlow) { board, sound -> board to sound },
        combine(repository.pieceTypeId, timeSettingsFlow) { pieceTypeId, timeSettings ->
            pieceTypeId to timeSettings
        },
    ) { (theme, diff, diffBlack), (user, lang, palette), (board, sound), (pieceTypeId, timeSettings) ->
        val (timeControl, preMovesEnabled) = timeSettings
        SettingsState(
            appTheme = theme,
            difficulty = diff,
            difficultyBlack = diffBlack,
            userName = user,
            language = lang,
            boardVisualState = board,
            palette = palette,
            soundState = sound,
            pieceTypeId = pieceTypeId,
            timeControl = timeControl,
            preMovesEnabled = preMovesEnabled,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsState(),
    )

    // ── Paletas disponibles (base - sin filtering por achievements) ──────────────

    /**
     * Paletas disponibles base (todas las paletas).
     * Override en AndroidSettingsViewModel para filtrar por achievements/billing.
     */
    override val availablePalettes: StateFlow<PaletteList> = MutableStateFlow(
        PaletteList(items = allAvailablePalettes)
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PaletteList(items = allAvailablePalettes),
    )

    /**
     * Todas las paletas para el selector (base - todas disponibles).
     * Override en AndroidSettingsViewModel para incluir paletas bloqueadas.
     */
    override val allPalettesForSelector: StateFlow<PaletteList> = availablePalettes

    /**
     * Paletas bloqueadas según el entitlement supporter (C4): Gilded queda bloqueada
     * salvo que el usuario sea supporter o la posea. Override en AndroidSettingsViewModel
     * para mergear con el billing local.
     */
    override val lockedPalettes: StateFlow<LockedPalettes> = entitlementsRepository.entitlements
        .map { LockedPalettes(lockedPaletteNames(it)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = LockedPalettes.None,
        )

    // ── Inicialización: restaurar singletons desde Repository ──────────────────

    init {
        // Paleta activa → PaletteManager (notifica a todos los composables).
        viewModelScope.launch {
            repository.palette.collect { paletteName ->
                val palette = allAvailablePalettes.find { it.name == paletteName }
                    ?: allAvailablePalettes.first()
                PaletteManager.setPalette(palette)
            }
        }

        // Tipo de pieza activo → PieceTypeManager (notifica a todos los composables).
        viewModelScope.launch {
            repository.pieceTypeId.collect { id ->
                PieceTypeManager.setPieceType(PieceTypes.findById(id))
            }
        }

        // Tutorial visto: StateFlow independiente (no forma parte de settingsState).
        viewModelScope.launch {
            repository.tutorialSeen.collect { seen ->
                _hasTutorialBeenSeen.value = seen
            }
        }
        viewModelScope.launch {
            repository.mpTutorialSeen.collect { seen ->
                _hasMpTutorialBeenSeen.value = seen
            }
        }
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    override fun toggleDarkTheme(enabled: Boolean) {
        viewModelScope.launch { repository.setDarkTheme(enabled) }
    }

    override fun setAppTheme(theme: AppTheme) {
        viewModelScope.launch { repository.setAppTheme(theme) }
    }

    override fun setUserName(name: String) {
        viewModelScope.launch { repository.setUserName(name) }
    }

    override fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { repository.setLanguage(language) }
    }

    override fun setPalette(paletteName: String) {
        viewModelScope.launch { repository.setPalette(paletteName) }
    }

    override fun setDifficulty(newDifficulty: Difficulty) {
        viewModelScope.launch { repository.setDifficulty(newDifficulty) }
    }

    override fun setDifficultyBlack(newDifficulty: Difficulty) {
        viewModelScope.launch { repository.setDifficultyBlack(newDifficulty) }
    }

    override fun setLabelsVisibility(visible: Boolean) {
        viewModelScope.launch { repository.setLabelsVisibility(visible) }
    }

    override fun setVerticesVisibility(visible: Boolean) {
        viewModelScope.launch { repository.setVerticesVisibility(visible) }
    }

    override fun setEdgesVisibility(visible: Boolean) {
        viewModelScope.launch { repository.setEdgesVisibility(visible) }
    }

    override fun setRegionsVisibility(visible: Boolean) {
        viewModelScope.launch { repository.setRegionsVisibility(visible) }
    }

    override fun setPerimeterVisibility(visible: Boolean) {
        viewModelScope.launch { repository.setPerimeterVisibility(visible) }
    }

    override fun setAnimateEffects(animate: Boolean) {
        viewModelScope.launch { repository.setAnimateEffects(animate) }
    }

    override fun setConversionAnimationStyle(style: ConversionAnimationStyle) {
        viewModelScope.launch { repository.setConversionAnimationStyle(style) }
    }

    override fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setSoundEnabled(enabled) }
    }

    override fun setSoundVolume(volume: Float) {
        viewModelScope.launch { repository.setSoundVolume(volume) }
    }

    override fun markTutorialSeen() {
        viewModelScope.launch { repository.setTutorialSeen(true) }
    }

    override fun markMpTutorialSeen() {
        viewModelScope.launch { repository.setMpTutorialSeen(true) }
    }

    /**
     * Persiste el id del tipo de pieza y actualiza [PieceTypeManager].
     *
     * [PieceTypeManager] se actualiza automáticamente a través del `collect`
     * registrado en `init { }` cuando el flow `repository.pieceTypeId` emite
     * el nuevo valor. No es necesario llamar a `PieceTypeManager.setPieceType`
     * aquí directamente.
     */
    override fun setPieceType(pieceTypeId: String) {
        viewModelScope.launch { repository.setPieceTypeId(pieceTypeId) }
    }

    /**
     * No-op por defecto. Override en AndroidSettingsViewModel.
     */
    override fun launchPurchaseFlow(productId: String): Unit = Unit

    override fun setTimeControl(mode: TimeControlMode) {
        viewModelScope.launch { repository.setTimeControl(mode) }
    }

    override fun setPreMovesEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setPreMovesEnabled(enabled) }
    }
}