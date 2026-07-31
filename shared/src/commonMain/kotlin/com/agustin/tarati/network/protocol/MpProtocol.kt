package com.agustin.tarati.network.protocol

import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpResult
import com.agustin.tarati.network.models.MpPlayerDto
import com.agustin.tarati.network.models.MpStartPolicy
import com.agustin.tarati.network.models.MpTableDto
import kotlinx.serialization.Serializable

/**
 * Protocolo del **juego multijugador** (mesas 2–6, tablero `25`) — M6 del plan.
 *
 * Va **anidado** dentro del protocolo existente ([ClientMessage.Mp] / [ServerMessage.Mp]) en vez de
 * agregar variantes sueltas a los sealed de 2 jugadores. Así el cliente 2-jugadores
 * (`OnlineGameClient`) no queda acoplado: maneja un único caso `is ServerMessage.Mp` y todo el
 * protocolo MP evoluciona aquí sin tocarlo (principio de aislamiento del juego nuevo).
 */

/** Mensajes MP que el cliente envía al servidor (van dentro de [ClientMessage.Mp]). */
@Serializable
sealed class MpClientMessage {

    /**
     * Crear una mesa pública para [playerCount] jugadores (2–6). El emisor queda de host en el
     * asiento 0. [startPolicy] fija cómo arranca la partida (host manual o votación de listos).
     */
    @Serializable
    data class CreateTable(
        val playerCount: Int,
        val startPolicy: MpStartPolicy = MpStartPolicy.HOST_MANUAL,
    ) : MpClientMessage()

    /** Unirse a una mesa abierta ocupando el primer asiento libre. */
    @Serializable
    data class JoinTable(val tableId: String) : MpClientMessage()

    /** Abandonar una mesa (si es el host, se promueve al siguiente ocupante; si no queda nadie, se cierra). */
    @Serializable
    data class LeaveTable(val tableId: String) : MpClientMessage()

    /** (Host) Colocar un bot greedy en un asiento vacío. */
    @Serializable
    data class AddBot(val tableId: String, val seatIndex: Int) : MpClientMessage()

    /** (Host) Quitar el bot de un asiento. */
    @Serializable
    data class RemoveBot(val tableId: String, val seatIndex: Int) : MpClientMessage()

    /** (Host) Iniciar la partida con los asientos ocupados (≥2; los vacíos se descartan). */
    @Serializable
    data class StartTable(val tableId: String) : MpClientMessage()

    /** (Mesa VOTE) Marcar/desmarcar "listo" el asiento del emisor. Al estar todos listos, arranca. */
    @Serializable
    data class SetReady(val tableId: String, val ready: Boolean) : MpClientMessage()

    /** (Host) Invitar a un usuario online a un asiento libre de la mesa (paridad con los desafíos single). */
    @Serializable
    data class InviteToTable(val tableId: String, val targetUserId: String) : MpClientMessage()

    /** Responder a una invitación de mesa recibida. Al aceptar, se ocupa el primer asiento libre. */
    @Serializable
    data class RespondToTableInvite(val inviteId: String, val accept: Boolean) : MpClientMessage()

    /** (Host) Cancelar una invitación de mesa que había enviado. */
    @Serializable
    data class CancelTableInvite(val inviteId: String) : MpClientMessage()

    /** Realizar una jugada en una partida en curso. Vértices por nombre (p.ej. "D1", "C1"). */
    @Serializable
    data class MakeMove(val gameId: String, val from: String, val to: String) : MpClientMessage()

    /** Empezar a **observar** (espectador) una partida en curso: el servidor manda el estado y difunde. */
    @Serializable
    data class SpectateGame(val gameId: String) : MpClientMessage()

    /** Dejar de observar una partida (idempotente). */
    @Serializable
    data class LeaveSpectating(val gameId: String) : MpClientMessage()
}

/** Mensajes MP que el servidor envía al cliente (van dentro de [ServerMessage.Mp]). */
@Serializable
sealed class MpServerMessage {

    /** Estado actualizado de una mesa (asientos, ocupantes, bots). Enviado a todos los sentados. */
    @Serializable
    data class TableUpdate(val table: MpTableDto) : MpServerMessage()

    /** La mesa se cerró (host la abandonó / se inició). [reason] p.ej. "host_left", "started". */
    @Serializable
    data class TableClosed(val tableId: String, val reason: String) : MpServerMessage()

    /** Error en una acción de mesa. [code] p.ej. "table_full", "not_host", "already_in_table". */
    @Serializable
    data class Error(val code: String) : MpServerMessage()

    /**
     * Invitación a unirse a una mesa (dirigida). El receptor la acepta/rechaza con
     * [MpClientMessage.RespondToTableInvite]. Expira a los 30 s (como los desafíos single).
     *
     * @property inviteId identificador para responder.
     * @property tableId mesa a la que se lo invita.
     * @property inviterName nombre visible del host que invita.
     * @property playerCount asientos de la mesa (para el texto de la notificación).
     */
    @Serializable
    data class TableInviteReceived(
        val inviteId: String,
        val tableId: String,
        val inviterName: String,
        val playerCount: Int,
    ) : MpServerMessage()

    /** El invitado rechazó la invitación (enviado al host). */
    @Serializable
    data class TableInviteDeclined(val inviteId: String) : MpServerMessage()

    /** La invitación caducó/canceló/quedó sin efecto. [reason] p.ej. "timeout", "cancelled", "table_full". */
    @Serializable
    data class TableInviteExpired(val inviteId: String, val reason: String) : MpServerMessage()

    /**
     * La partida comenzó (o se reenvía al reconectar). [state] es el estado; [players] mapea cada color
     * (`A..F`) a su ocupante (humano o bot). [turnTimeoutMs] es el tiempo por turno para el countdown
     * del cliente (`0` = sin timer). Enviado a los jugadores humanos.
     */
    @Serializable
    data class GameStarted(
        val gameId: String,
        val state: MpGameState,
        val players: List<MpPlayerDto>,
        val turnTimeoutMs: Long = 0,
        /** Jugadas ya realizadas (serializadas `a:D1-C1`) — para poblar la lista al unirse/reconectar. */
        val moves: List<String> = emptyList(),
        /** `true` si el receptor es **espectador** (partida read-only, sin jugar). */
        val spectating: Boolean = false,
    ) : MpServerMessage()

    /**
     * Estado tras una jugada (o un retiro). [lastMoveFrom]/[lastMoveTo] son los vértices del último
     * movimiento (null si el cambio no fue una jugada, p.ej. un retiro → snap sin animación);
     * [converted] mapea cada vértice volteado a su **dueño previo** (para animar el flip de la captura).
     */
    @Serializable
    data class StateUpdate(
        val gameId: String,
        val state: MpGameState,
        val lastMoveFrom: String? = null,
        val lastMoveTo: String? = null,
        val converted: Map<String, PlayerColor> = emptyMap(),
    ) : MpServerMessage()

    /**
     * La partida terminó. [result] trae ganador(es), motivo y conteos finales. [ratingDeltas] mapea el
     * `userId` de cada humano registrado a su cambio de rating MP (vacío si la partida fue casual —
     * bot/invitado presente— o si el cálculo falló); el cliente lo muestra en el popup de resultado.
     */
    @Serializable
    data class GameEnded(
        val gameId: String,
        val result: MpResult,
        val ratingDeltas: Map<String, Int> = emptyMap(),
    ) : MpServerMessage()
}
