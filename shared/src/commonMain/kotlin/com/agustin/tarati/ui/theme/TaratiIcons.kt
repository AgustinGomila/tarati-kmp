package com.agustin.tarati.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Íconos de Material Design definidos como [ImageVector] en commonMain.
 *
 * ## Motivación
 * El artefacto `material-icons-extended` de Compose Multiplatform no incluye
 * soporte para targets iOS native (ios_x64, iosArm64, iosSimulatorArm64) en
 * ninguna versión hasta CMP 1.10.x. Al tener `shared` con targets iOS, agregar
 * esa dependencia en commonMain rompe la compilación.
 *
 * Este objeto define los íconos usados en la UI de Tarati directamente como
 * paths SVG (licencia Apache 2.0 — Material Design Icons, Google), construidos
 * con la API pública de [ImageVector.Builder] + [PathParser] disponible en
 * commonMain de Compose UI.
 *
 * ## Viewport 24×24
 * Todos los paths usan el viewport de 24×24 de Material Icons (Filled).
 * NO usar paths de Material Symbols (viewport 960px — valores en cientos y negativos).
 *
 * ## Cómo añadir un ícono nuevo
 * 1. Ir a https://fonts.google.com/icons → Style: Filled → descargar SVG.
 * 2. Extraer el atributo `d` del elemento `<path>`.
 * 3. Verificar que los valores estén en rango 0–24 (viewport 24px).
 * 4. Agregar un `val` con `by lazy { icon(...) }` en orden alfabético.
 *
 * ## Uso en código
 * ```kotlin
 * Icon(imageVector = TaratiIcons.Menu, contentDescription = null)
 * Icon(imageVector = TaratiIcons.ArrowBack, contentDescription = null)
 * ```
 *
 * Paths bajo licencia Apache 2.0 — Material Design Icons © Google.
 */
object TaratiIcons {

    // ── A ────────────────────────────────────────────────────────────────────

    /** ＋ agregar. */
    val Add: ImageVector by lazy {
        icon("Add", "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
    }

    /**
     * 🎞 animación / efectos visuales.
     * Corregido: path 24px (el original usaba viewport 960px).
     */
    val Animation: ImageVector by lazy {
        icon(
            name = "Animation",
            pathData = "M15 2c-2.71 0-5.05 1.54-6.22 3.78-1.28.67-2.34 1.72-3 3C3.54 9.95 2 12.29 2 15c0 3.87 3.13 7 7 7 2.71 0 5.05-1.54 6.22-3.78 1.28-.67 2.34-1.72 3-3C20.46 14.05 22 11.71 22 9c0-3.87-3.13-7-7-7zM9 20c-2.76 0-5-2.24-5-5 0-1.12.37-2.16 1-3 0 3.87 3.13 7 7 7-.84.63-1.88 1-3 1zm3-3c-2.76 0-5-2.24-5-5 0-1.12.37-2.16 1-3 0 3.86 3.13 6.99 7 7-.84.63-1.88 1-3 1zm4.7-3.3c-.53.19-1.1.3-1.7.3-2.76 0-5-2.24-5-5 0-.6.11-1.17.3-1.7.53-.19 1.1-.3 1.7-.3 2.76 0 5 2.24 5 5 0 .6-.11 1.17-.3 1.7zM19 12c0-3.86-3.13-6.99-7-7 .84-.63 1.87-1 3-1 2.76 0 5 2.24 5 5 0 1.12-.37 2.16-1 3z",
        )
    }

    /** ← flecha de retroceso. AutoMirrored: se invierte en RTL. */
    val ArrowBack: ImageVector by lazy {
        icon(
            name = "ArrowBack",
            autoMirror = true,
            pathData = "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z",
        )
    }

    val AccountCircle: ImageVector by lazy {
        icon(
            name = "AccountCircle",
            autoMirror = true,
            pathData = "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 4c1.93 0 3.5 1.57 3.5 3.5S13.93 13 12 13s-3.5-1.57-3.5-3.5S10.07 6 12 6zm0 14c-2.03 0-4.43-.82-6.14-2.88C7.55 15.8 9.68 15 12 15s4.45.8 6.14 2.12C16.43 19.18 14.03 20 12 20z"
        )
    }

    /** 🗂 árbol de bracket (torneo por Eliminación). */
    val AccountTree: ImageVector by lazy {
        icon(
            name = "AccountTree",
            pathData = "M22 11V3h-7v3H9V3H2v8h7V8h2v10h4v3h7v-8h-7v3h-2V8h2v3z",
        )
    }

    /** ˅ flecha chevron hacia abajo en menú desplegable. AutoMirrored. */
    val ArrowDropDown: ImageVector by lazy {
        icon(
            name = "ArrowDropDown",
            autoMirror = true,
            pathData = "M7 10l5 5 5-5z",
        )
    }

    /** → flecha de avance. AutoMirrored: se invierte en RTL. */
    val ArrowForward: ImageVector by lazy {
        icon(
            name = "ArrowForward",
            autoMirror = true,
            pathData = "M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z",
        )
    }

    // ── B ────────────────────────────────────────────────────────────────────

    /** ⚡ rayo (modo de juego Blitz). */
    val Bolt: ImageVector by lazy {
        icon("Bolt", "M7 2v11h3v9l7-12h-4l4-8z")
    }

    /** 🔫 bala / proyectil (modo de juego Bullet). Path custom 24×24. */
    val Bullet: ImageVector by lazy {
        icon(
            name = "Bullet",
            pathData = "M14.5 8.5C14.5 5.5 13.5 3 12 1.5 10.5 3 9.5 5.5 9.5 8.5V16h5V8.5z" +
                    "M9.5 17.5h5v2H13v1.5h-2V19.5H9.5z",
        )
    }

    // ── C ────────────────────────────────────────────────────────────────────

    /** ✕ cancelar (círculo con X). */
    val Cancel: ImageVector by lazy {
        icon(
            name = "Cancel",
            pathData = "M12 2C6.47 2 2 6.47 2 12s4.47 10 10 10 10-4.47 10-10S17.53 2 12 2zm5 13.59L15.59 17 12 13.41 8.41 17 7 15.59 10.59 12 7 8.41 8.41 7 12 10.59 15.59 7 17 8.41 13.41 12 17 15.59z",
        )
    }

    /** 🎁 regalo (logros especiales). AutoMirrored. */
    val CardGiftcard: ImageVector by lazy {
        icon(
            name = "CardGiftcard",
            autoMirror = true,
            pathData = "M20 6h-2.18c.11-.31.18-.65.18-1 0-1.66-1.34-3-3-3-1.05 0-1.96.54-2.5 1.35l-.5.67-.5-.68C10.96 2.54 10.05 2 9 2 7.34 2 6 3.34 6 5c0 .35.07.69.18 1H4c-1.11 0-1.99.89-1.99 2L2 19c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V8c0-1.11-.89-2-2-2zm-5-2c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zM9 4c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zm11 15H4v-2h16v2zm0-5H4V8h5.08L7 10.83 8.62 12 12 7.4l3.38 4.6L17 10.83 14.92 8H20v6z",
        )
    }

    /** ✓ hecho / check simple. */
    val Check: ImageVector by lazy {
        icon(
            name = "Check",
            pathData = "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z",
        )
    }

    /** ✕ cerrar (X simple, sin círculo). */
    val Close: ImageVector by lazy {
        icon(
            name = "Close",
            pathData = "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z",
        )
    }

    /** ⧉ copiar contenido. */
    val ContentCopy: ImageVector by lazy {
        icon(
            name = "ContentCopy",
            pathData = "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z",
        )
    }

    val CloudOff: ImageVector by lazy {
        icon(
            name = "CloudOff",
            autoMirror = true,
            pathData = "M24 15c0-2.64-2.05-4.78-4.65-4.96C18.67 6.59 15.64 4 12 4c-1.33 0-2.57.36-3.65.97l1.49 1.49C10.51 6.17 11.23 6 12 6c3.04 0 5.5 2.46 5.5 5.5v.5H19c1.66 0 3 1.34 3 3 0 .99-.48 1.85-1.21 2.4l1.41 1.41c1.09-.92 1.8-2.27 1.8-3.81zM4.41 3.86L3 5.27l2.77 2.77h-.42C2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h11.73l2 2 1.41-1.41L4.41 3.86zM6 18c-2.21 0-4-1.79-4-4s1.79-4 4-4h1.73l8 8H6z"
        )
    }
    // ── D ────────────────────────────────────────────────────────────────────

    /**
     * 🌙 modo oscuro.
     * Corregido: path 24px (el original usaba viewport 960px).
     */
    val DarkMode: ImageVector by lazy {
        icon(
            name = "DarkMode",
            pathData = "M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9 9-4.03 9-9c0-.46-.04-.92-.1-1.36-.98 1.37-2.58 2.26-4.4 2.26-2.98 0-5.4-2.42-5.4-5.4 0-1.81.89-3.42 2.26-4.4-.44-.06-.9-.1-1.36-.1z",
        )
    }

    /** 🗑 eliminar / borrar. */
    val Delete: ImageVector by lazy {
        icon(
            name = "Delete",
            pathData = "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z",
        )
    }

    /** ✓ hecho / confirmado. */
    val Done: ImageVector by lazy {
        icon("Done", "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z")
    }

    /** ⬇ descargar. */
    val Download: ImageVector by lazy {
        icon(
            name = "Download",
            pathData = "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z",
        )
    }

    // ── E ────────────────────────────────────────────────────────────────────

    /** ✏ editar. */
    val Edit: ImageVector by lazy {
        icon(
            name = "Edit",
            pathData = "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z",
        )
    }

    /** 🏆 trofeo (logros). */
    val EmojiEvents: ImageVector by lazy {
        icon(
            name = "EmojiEvents",
            pathData = "M19 5h-2V3H7v2H5c-1.1 0-2 .9-2 2v1c0 2.55 1.92 4.63 4.39 4.94.63 1.5 1.98 2.63 3.61 2.96V19H7v2h10v-2h-4v-3.1c1.63-.33 2.98-1.46 3.61-2.96C19.08 12.63 21 10.55 21 8V7c0-1.1-.9-2-2-2zM5 8V7h2v3.82C5.84 10.4 5 9.3 5 8zm14 0c0 1.3-.84 2.4-2 2.82V7h2v1z",
        )
    }

    val Error: ImageVector by lazy {
        icon(
            name = "Error",
            autoMirror = true,
            pathData = "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"
        )
    }

    /** ˅ chevron hacia abajo / expandir. */
    val ExpandLess: ImageVector by lazy {
        icon("ExpandLess", "M12 8l-6 6 1.41 1.41L12 10.83l4.59 4.58L18 14z")
    }

    /** ˄ chevron hacia arriba / colapsar. */
    val ExpandMore: ImageVector by lazy {
        icon("ExpandMore", "M16.59 8.59L12 13.17 7.41 8.59 6 10l6 6 6-6z")
    }

    // ── F ────────────────────────────────────────────────────────────────────

    /**
     * ❤ corazón (donación / supporter). Dibujado como vector: el glifo ♥ (U+2665) no está en el
     * font bundle de Skiko WASM, así que en la web no se renderiza como texto (ver [HeartText]).
     */
    val Favorite: ImageVector by lazy {
        icon(
            name = "Favorite",
            pathData = "M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z",
        )
    }

    // ── G ────────────────────────────────────────────────────────────────────

    /** 👥 grupo de personas / feed de seguidos. */
    val Group: ImageVector by lazy {
        icon(
            name = "Group",
            pathData = "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z",
        )
    }

    // ── H ────────────────────────────────────────────────────────────────────

    val Home: ImageVector by lazy {
        icon(
            name = "Home",
            autoMirror = true,
            pathData = "M12 5.69l5 4.5V18h-2v-6H9v6H7v-7.81l5-4.5M12 3L2 12h3v8h6v-6h2v6h6v-8h3L12 3z"
        )
    }

    // ── I ────────────────────────────────────────────────────────────────────

    /** ℹ información. */
    val Info: ImageVector by lazy {
        icon(
            name = "Info",
            pathData = "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z",
        )
    }

    // ── K ────────────────────────────────────────────────────────────────────

    /** ˅ flecha chevron hacia abajo. */
    val KeyboardArrowDown: ImageVector by lazy {
        icon("KeyboardArrowDown", "M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6 1.41-1.41z")
    }

    /** ‹ flecha hacia la izquierda. AutoMirrored. */
    val KeyboardArrowLeft: ImageVector by lazy {
        icon(
            name = "KeyboardArrowLeft",
            autoMirror = true,
            pathData = "M15.41 16.59L10.83 12l4.58-4.59L14 6l-6 6 6 6z",
        )
    }

    /** › flecha hacia la derecha. AutoMirrored. */
    val KeyboardArrowRight: ImageVector by lazy {
        icon(
            name = "KeyboardArrowRight",
            autoMirror = true,
            pathData = "M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6z",
        )
    }

    // ── L ────────────────────────────────────────────────────────────────────

    /** 🏅 tabla de clasificación. */
    val Leaderboard: ImageVector by lazy {
        icon(
            name = "Leaderboard",
            pathData = "M7.5 21H2V9h5.5v12zm7.25-18h-5.5v18h5.5V3zM22 11h-5.5v10H22V11z",
        )
    }

    /**
     * 🌐 idioma / lenguaje.
     * Corregido: path 24px (el original usaba viewport 960px).
     */
    val Language: ImageVector by lazy {
        icon(
            name = "Language",
            pathData = "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zm6.93 6h-2.95c-.32-1.25-.78-2.45-1.38-3.56 1.84.63 3.37 1.91 4.33 3.56zM12 4.04c.83 1.2 1.48 2.53 1.91 3.96h-3.82c.43-1.43 1.08-2.76 1.91-3.96zM4.26 14C4.1 13.36 4 12.69 4 12s.1-1.36.26-2h3.38c-.08.66-.14 1.32-.14 2s.06 1.34.14 2H4.26zm.82 2h2.95c.32 1.25.78 2.45 1.38 3.56-1.84-.63-3.37-1.9-4.33-3.56zm2.95-8H5.08c.96-1.66 2.49-2.93 4.33-3.56C8.81 5.55 8.35 6.75 8.03 8zM12 19.96c-.83-1.2-1.48-2.53-1.91-3.96h3.82c-.43 1.43-1.08 2.76-1.91 3.96zM14.34 14H9.66c-.09-.66-.16-1.32-.16-2s.07-1.35.16-2h4.68c.09.65.16 1.32.16 2s-.07 1.34-.16 2zm.25 5.56c.6-1.11 1.06-2.31 1.38-3.56h2.95c-.96 1.65-2.49 2.93-4.33 3.56zM16.36 14c.08-.66.14-1.32.14-2s-.06-1.34-.14-2h3.38c.16.64.26 1.31.26 2s-.1 1.36-.26 2h-3.38z",
        )
    }

    /** 🔒 candado cerrado. */
    val Lock: ImageVector by lazy {
        icon(
            name = "Lock",
            pathData = "M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z",
        )
    }

    /** ← salir / cerrar sesión. */
    val Logout: ImageVector by lazy {
        icon(
            name = "Logout",
            pathData = "M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h8v-2H4V5z",
        )
    }

    // ── M ────────────────────────────────────────────────────────────────────

    /** 📖 libro abierto (historial de movimientos). AutoMirrored. */
    val MenuBook: ImageVector by lazy {
        icon(
            name = "MenuBook",
            autoMirror = true,
            pathData = "M21 5c-1.11-.35-2.33-.5-3.5-.5-1.95 0-4.05.4-5.5 1.5-1.45-1.1-3.55-1.5-5.5-1.5S2.45 4.9 1 6v14.65c0 .25.25.5.5.5.1 0 .15-.05.25-.05C3.1 20.45 5.05 20 6.5 20c1.95 0 4.05.4 5.5 1.5 1.35-.85 3.8-1.5 5.5-1.5 1.65 0 3.35.3 4.75 1.05.1.05.15.05.25.05.25 0 .5-.25.5-.5V6c-.6-.45-1.25-.75-2-1zM21 18.5c-1.1-.35-2.3-.5-3.5-.5-1.7 0-4.15.65-5.5 1.5V8c1.35-.85 3.8-1.5 5.5-1.5 1.2 0 2.4.15 3.5.5v11.5z",
        )
    }

    /** ≡ menú hamburguesa. */
    val Menu: ImageVector by lazy {
        icon("Menu", "M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z")
    }

    // ── P ────────────────────────────────────────────────────────────────────

    /**
     * 🎨 paleta de colores.
     * Corregido: path 24px (el original usaba viewport 960px).
     */
    val Palette: ImageVector by lazy {
        icon(
            name = "Palette",
            pathData = "M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9c.83 0 1.5-.67 1.5-1.5 0-.39-.15-.74-.39-1.01-.23-.26-.38-.61-.38-.99 0-.83.67-1.5 1.5-1.5H16c2.76 0 5-2.24 5-5 0-4.42-4.03-8-9-8zm-5.5 9c-.83 0-1.5-.67-1.5-1.5S5.67 9 6.5 9 8 9.67 8 10.5 7.33 12 6.5 12zm3-4C8.67 8 8 7.33 8 6.5S8.67 5 9.5 5s1.5.67 1.5 1.5S10.33 8 9.5 8zm5 0c-.83 0-1.5-.67-1.5-1.5S13.67 5 14.5 5s1.5.67 1.5 1.5S15.33 8 14.5 8zm3 4c-.83 0-1.5-.67-1.5-1.5S16.67 9 17.5 9s1.5.67 1.5 1.5-.67 1.5-1.5 1.5z",
        )
    }

    /** 👤 persona / jugador humano. */
    val Person: ImageVector by lazy {
        icon(
            name = "Person",
            pathData = "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z",
        )
    }

    /** ▶ reproducir / iniciar. */
    val PlayArrow: ImageVector by lazy {
        icon("PlayArrow", "M8 5v14l11-7z")
    }

    val Public: ImageVector by lazy {
        icon(
            name = "Public",
            autoMirror = true,
            pathData = "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zM4 12c0-.61.08-1.21.21-1.78L8.99 15v1c0 1.1.9 2 2 2v1.93C7.06 19.43 4 16.07 4 12zm13.89 5.4c-.26-.81-1-1.4-1.9-1.4h-1v-3c0-.55-.45-1-1-1h-6v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41C17.92 5.77 20 8.65 20 12c0 2.08-.81 3.98-2.11 5.4z"
        )
    }

    // ── R ────────────────────────────────────────────────────────────────────

    /** ↺ repetir / reiniciar. */
    val Replay: ImageVector by lazy {
        icon(
            name = "Replay",
            pathData = "M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z",
        )
    }

    /** ↻ rotar a la derecha. AutoMirrored. */
    val RotateRight: ImageVector by lazy {
        icon(
            name = "RotateRight",
            autoMirror = true,
            pathData = "M15.55 5.55L11 1v3.07C7.06 4.56 4 7.92 4 12s3.05 7.44 7 7.93v-2.02c-2.84-.48-5-2.94-5-5.91s2.16-5.43 5-5.91V10l4.55-4.45zM19.93 11c-.17-1.39-.72-2.73-1.62-3.89l-1.42 1.42c.54.75.88 1.6 1.02 2.47h2.02zM13 17.9v2.02c1.39-.17 2.74-.71 3.9-1.61l-1.44-1.44c-.75.54-1.59.89-2.46 1.03zm3.89-2.42l1.42 1.41c.9-1.16 1.45-2.5 1.62-3.89h-2.02c-.14.87-.48 1.72-1.02 2.48z",
        )
    }

    // ── S ────────────────────────────────────────────────────────────────────

    /** 💾 guardar. */
    val Save: ImageVector by lazy {
        icon(
            name = "Save",
            pathData = "M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-10H5V5h10v4z",
        )
    }

    /** 🕐 reloj analógico (modo de juego Classical). */
    val Schedule: ImageVector by lazy {
        icon(
            name = "Schedule",
            pathData = "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2z" +
                    "M12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z",
        )
    }

    /** 🔍 buscar. AutoMirrored. */
    val Search: ImageVector by lazy {
        icon(
            name = "Search",
            autoMirror = true,
            pathData = "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z",
        )
    }

    /** ⚙ ajustes / configuración. */
    val Settings: ImageVector by lazy {
        icon(
            name = "Settings",
            pathData = "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z",
        )
    }

    /** ⏭ saltar al siguiente. AutoMirrored. */
    val SkipNext: ImageVector by lazy {
        icon(
            name = "SkipNext",
            autoMirror = true,
            pathData = "M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z",
        )
    }

    /** ⏮ saltar al anterior. AutoMirrored. */
    val SkipPrevious: ImageVector by lazy {
        icon(
            name = "SkipPrevious",
            autoMirror = true,
            pathData = "M6 6h2v12H6zm3.5 6l8.5 6V6z",
        )
    }

    /** 🤖 robot / jugador IA. */
    val SmartToy: ImageVector by lazy {
        icon(
            name = "SmartToy",
            pathData = "M20 9V7c0-1.1-.9-2-2-2h-3c0-1.66-1.34-3-3-3S9 3.34 9 5H6c-1.1 0-2 .9-2 2v2c-1.66 0-3 1.34-3 3s1.34 3 3 3v4c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2v-4c1.66 0 3-1.34 3-3s-1.34-3-3-3zm-2 11H6V7h12v13zm-9-6c-.83 0-1.5-.67-1.5-1.5S8.17 11 9 11s1.5.67 1.5 1.5S9.83 14 9 14zm6 0c-.83 0-1.5-.67-1.5-1.5S14.17 11 15 11s1.5.67 1.5 1.5S15.83 14 15 14zm-5 3h4v-2h-4v2z",
        )
    }

    /**
     * ⚡ velocidad / dificultad.
     * Corregido: path 24px (el original usaba viewport 960px).
     */
    val Speed: ImageVector by lazy {
        icon(
            name = "Speed",
            pathData = "M20.38 8.57l-1.23 1.85a8 8 0 0 1-.22 7.58H5.07A8 8 0 0 1 15.58 6.85l1.85-1.23A10 10 0 0 0 3.35 19a2 2 0 0 0 1.72 1h13.85a2 2 0 0 0 1.74-1 10 10 0 0 0-.27-10.44zm-9.79 6.84a2 2 0 0 0 2.83 0l5.66-8.49-8.49 5.66a2 2 0 0 0 0 2.83z",
        )
    }

    /** 📐 escuadra / editor de tablero. */
    val SquareFoot: ImageVector by lazy {
        icon(
            name = "SquareFoot",
            pathData = "M17.66,17.66l-1.06,1.06l-0.71-0.71l1.06-1.06l-1.94-1.94l-1.06,1.06l-0.71-0.71l1.06-1.06l-1.94-1.94l-1.06,1.06 l-0.71-0.71l1.06-1.06L9.7,9.7l-1.06,1.06l-0.71-0.71l1.06-1.06L7.05,7.05L5.99,8.11L5.28,7.4l1.06-1.06L4,4v14c0,1.1,0.9,2,2,2 h14L17.66,17.66z M7,17v-5.76L12.76,17H7z",
        )
    }

    val Sort: ImageVector by lazy {
        icon(
            name = "Sort",
            autoMirror = true,
            pathData = "M3 18h6v-2H3v2zM3 6v2h18V6H3zm0 7h12v-2H3v2z"
        )
    }

    /** Donación voluntaria. AutoMirrored. */
    val Supporter: ImageVector by lazy {
        icon(
            name = "Supporter",
            autoMirror = true,
            pathData = "M16,13c3.09-2.81,6-5.44,6-7.7C22,3.45,20.55,2,18.7,2c-1.04,0-2.05,0.49-2.7,1.25C15.34,2.49,14.34,2,13.3,2 C11.45,2,10,3.45,10,5.3C10,7.56,12.91,10.19,16,13z M13.3,4c0.44,0,0.89,0.21,1.18,0.55L16,6.34l1.52-1.79 C17.81,4.21,18.26,4,18.7,4C19.44,4,20,4.56,20,5.3c0,1.12-2.04,3.17-4,4.99c-1.96-1.82-4-3.88-4-4.99C12,4.56,12.56,4,13.3,4zM19,16h-2c0-1.2-0.75-2.28-1.87-2.7L8.97,11H1v11h6v-1.44l7,1.94l8-2.5v-1C22,17.34,20.66,16,19,16z M3,20v-7h2v7H3z M13.97,20.41L7,18.48V13h1.61l5.82,2.17C14.77,15.3,15,15.63,15,16c0,0-1.99-0.05-2.3-0.15l-2.38-0.79l-0.63,1.9l2.38,0.79 c0.51,0.17,1.04,0.26,1.58,0.26H19c0.39,0,0.74,0.23,0.9,0.56L13.97,20.41z"
        )
    }

    // ── T ────────────────────────────────────────────────────────────────────

    /**
     * ⏱ temporizador.
     * Corregido: path 24px (el original usaba viewport 960px).
     */
    val Timer: ImageVector by lazy {
        icon(
            name = "Timer",
            pathData = "M15 1H9v2h6V1zm-4 13h2V8h-2v6zm8.03-6.61l1.42-1.42c-.43-.51-.9-.99-1.41-1.41l-1.42 1.42C16.07 4.74 14.12 4 12 4c-4.97 0-9 4.03-9 9s4.02 9 9 9 9-4.03 9-9c0-2.12-.74-4.07-1.97-5.61zM12 20c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z",
        )
    }

    // ── V ────────────────────────────────────────────────────────────────────

    /**
     * 👁 visibilidad / mostrar.
     * Corregido: path 24px (el original usaba viewport 960px).
     */
    val Visibility: ImageVector by lazy {
        icon(
            name = "Visibility",
            pathData = "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z",
        )
    }

    /** 👁‍🗨 visibilidad desactivada / ocultar contraseña. */
    val VisibilityOff: ImageVector by lazy {
        icon(
            name = "VisibilityOff",
            pathData = "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75C21.27 7.61 17 4.5 12 4.5c-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z",
        )
    }

    /**
     * 🔇 silenciado (sin volumen).
     * Corregido: path 24px (el original usaba viewport 960px).
     */
    val VolumeDown: ImageVector by lazy {
        icon(
            name = "VolumeDown",
            pathData = "M18.5 12c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM5 9v6h4l5 5V4L9 9H5z",
        )
    }

    /**
     * 🔇 silenciado total.
     * Corregido: path 24px (el original usaba viewport 960px).
     */
    val VolumeMute: ImageVector by lazy {
        icon(
            name = "VolumeMute",
            pathData = "M7 9v6h4l5 5V4l-5 5H7z",
        )
    }

    /**
     * 🔕 sin volumen / desactivado.
     * Corregido: path 24px (el original usaba viewport 960px).
     */
    val VolumeOff: ImageVector by lazy {
        icon(
            name = "VolumeOff",
            pathData = "M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z",
        )
    }

    /**
     * 🔊 volumen alto.
     * Corregido: path 24px (el original usaba viewport 960px).
     */
    val VolumeUp: ImageVector by lazy {
        icon(
            name = "VolumeUp",
            pathData = "M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z",
        )
    }

    // ── W ────────────────────────────────────────────────────────────────────

    /** ⚠ advertencia / alerta. */
    val Warning: ImageVector by lazy {
        icon(
            name = "Warning",
            pathData = "M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z",
        )
    }

    // ── Constructor interno ───────────────────────────────────────────────────

    /**
     * Construye un [ImageVector] de 24×24dp a partir de un string de path SVG.
     *
     * Usa [PathParser] — API pública de `androidx.compose.ui.graphics.vector`
     * disponible en commonMain desde Compose UI 1.0.
     *
     * @param name       Nombre de debug del ícono (aparece en herramientas).
     * @param pathData   String de path SVG (`d` attribute), Apache 2.0.
     * @param autoMirror Si `true`, el ícono se invierte horizontalmente en locales RTL.
     */
    private fun icon(
        name: String,
        pathData: String,
        autoMirror: Boolean = false,
    ): ImageVector = ImageVector.Builder(
        name = "TaratiIcons.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = SolidColor(Color.Black),
        )
    }.build()
}