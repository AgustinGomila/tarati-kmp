package com.agustin.tarati.network.protocol

import com.agustin.tarati.core.domain.game6.pieces.PlayerColor
import com.agustin.tarati.core.domain.game6.play.MpGameState
import com.agustin.tarati.core.domain.game6.play.MpResult
import com.agustin.tarati.network.models.MpPlayerDto
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

    /** Crear una mesa pública para [playerCount] jugadores (2–6). El emisor queda de host en el asiento 0. */
    @Serializable
    data class CreateTable(val playerCount: Int) : MpClientMessage()

    /** Unirse a una mesa abierta ocupando el primer asiento libre. */
    @Serializable
    data class JoinTable(val tableId: String) : MpClientMessage()

    /** Abandonar una mesa (si es el host, la mesa se cierra para todos). */
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
        /** Jugadas ya realizadas (serializadas `A:D1-C1`) — para poblar la lista al unirse/reconectar. */
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

    /** La partida terminó. [result] trae ganador(es), motivo y conteos finales. */
    @Serializable
    data class GameEnded(val gameId: String, val result: MpResult) : MpServerMessage()
}
