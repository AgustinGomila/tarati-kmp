package com.agustin.tarati.services.url

/**
 * Indica si se muestran los enlaces a las tiendas de la app nativa
 * (Google Play / itch.io) en Settings → Acerca de. Solo Web: es el punto
 * de entrada sin instalación y el puente natural hacia las apps nativas.
 * En Android se evita el enlace a itch.io (distribución alternativa,
 * políticas de Google Play).
 */
expect fun storeLinksAvailable(): Boolean

/**
 * Indica si se muestra la fila "Calificar en Google Play" en
 * Settings → Acerca de. Solo Android: abre la ficha propia de Play.
 */
expect fun rateAppAvailable(): Boolean
