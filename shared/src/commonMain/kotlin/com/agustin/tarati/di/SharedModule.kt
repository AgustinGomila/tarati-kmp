package com.agustin.tarati.di

import com.agustin.tarati.core.domain.ai.api.IAIEngine
import com.agustin.tarati.core.domain.ai.engine.TaratiAI
import com.agustin.tarati.features.detail.GameDetailsViewModel
import com.agustin.tarati.features.game6.MpTutorialViewModel
import com.agustin.tarati.features.library.GamesLibraryViewModel
import com.agustin.tarati.features.online.onlineModule
import com.agustin.tarati.services.ai.AIViewModel
import com.agustin.tarati.services.clock.ClockViewModel
import com.agustin.tarati.services.clock.IClockService
import com.agustin.tarati.services.notifications.UIMessageBus
import com.agustin.tarati.ui.components.game.animation.AnimationCoordinator
import com.agustin.tarati.ui.components.game.animation.BoardAnimationViewModel
import com.agustin.tarati.ui.components.game.animation.BoardGeometryViewModel
import com.agustin.tarati.ui.components.game.animation.IBoardGeometryViewModel
import com.agustin.tarati.ui.components.game.behaviors.BoardSelectionViewModel
import com.agustin.tarati.ui.components.tutorial.ITutorialService
import com.agustin.tarati.ui.components.tutorial.TutorialService
import com.agustin.tarati.ui.components.tutorial.TutorialViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Módulo Koin compartido entre todas las plataformas.
 *
 * Contiene únicamente dependencias que no requieren APIs de Android:
 * - Motor de IA
 * - ViewModels de animación/geometría/selección (lógica pura)
 * - Bus de mensajes UI (notificaciones + alertas)
 * - Servicios de tutorial (lógica pura)
 *
 * ## Lo que NO está aquí (es platform-specific):
 * - Room/DataStore → androidModule
 * - SoundService (MediaPlayer) → androidModule / desktopModule
 * - ClipboardService → androidModule / desktopModule
 * - Achievements/Billing → androidModule
 * - SavedStateHandle → eliminado de ViewModels KMP
 */
val sharedAiModule: Module = module {
    single<IAIEngine> { TaratiAI.instance }
}

val sharedAnimationModule: Module = module {
    single<AnimationCoordinator> { AnimationCoordinator(get()) }
}

val sharedNotificationModule: Module = module {
    single { UIMessageBus() }
}

val sharedTutorialModule: Module = module {
    single<ITutorialService> { TutorialService(get()) }
}

val sharedViewModelModule: Module = module {
    viewModel { GamesLibraryViewModel(get()) }
    viewModel { GameDetailsViewModel(get()) }

    viewModel { BoardGeometryViewModel() } bind IBoardGeometryViewModel::class
    viewModel { BoardAnimationViewModel(get()) }
    viewModel { BoardSelectionViewModel() }
    viewModel { ClockViewModel() } bind IClockService::class
    viewModel { AIViewModel(get()) }
    viewModel { (animatorCoordinator: AnimationCoordinator) ->
        TutorialViewModel(animatorCoordinator, get())
    }

    // Tutorial del juego multijugador (game6): recorrido propio sobre el tablero 25, reusa el
    // ISoundService de la plataforma (mismo `get()` que el TutorialViewModel de single).
    viewModel { MpTutorialViewModel(get()) }
}

/**
 * Lista de módulos compartidos para usar en todas las plataformas.
 */
val sharedModules: List<Module> = listOf(
    sharedAiModule,
    sharedAnimationModule,
    sharedNotificationModule,
    sharedTutorialModule,
    sharedViewModelModule,
    onlineModule
)
