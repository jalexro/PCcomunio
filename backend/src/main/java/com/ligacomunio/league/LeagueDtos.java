package com.ligacomunio.league;

import com.ligacomunio.player.PlayerStatus;
import com.ligacomunio.player.Position;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Respuestas y peticiones de la API de ligas, agrupadas para no tener un archivo por cada record. */
public final class LeagueDtos {

	private LeagueDtos() {
	}

	// --- Respuestas ---

	public record LigaDto(
			Long id,
			String name,
			String joinCode,
			int season,
			int currentMatchday,
			int lastMatchday,
			LeagueStatus status,
			AdvanceMode advanceMode,
			long intervalSeconds,
			Instant nextAdvanceAt,
			int members,
			String owner,
			boolean isOwner,
			Long myMemberId,
			BigDecimal myBudget,
			Formation myFormation,
			BigDecimal pointValue,
			List<BigDecimal> positionBonuses,
			BigDecimal emptySlotPenalty,
			/** Reparto que recibe quien entra en la liga. */
			InitialSquadMode initialSquadMode,
			/** Presupuesto con el que entra cada miembro. */
			BigDecimal initialBudget,
			/** Tope de la suma de valores de los 11 iniciales; null salvo en ALEATORIO_CON_TOPE. */
			BigDecimal initialSquadCap,
			/** Si yo ya he confirmado mi alineación para la jornada que viene. */
			boolean iConfirmed,
			/** A quién se está esperando en el modo por confirmación (vacío si no falta nadie). */
			List<String> pendingConfirmations) {
	}

	/** Cuánto ha cobrado cada miembro en toda la liga y cómo quedó en la última jornada puntuada. */
	public record AbonoDto(
			int position,
			Long memberId,
			String username,
			BigDecimal totalPayout,
			BigDecimal totalFromPoints,
			BigDecimal totalFromPosition,
			Integer lastMatchdayPosition,
			BigDecimal lastMatchdayPayout,
			boolean me) {
	}

	public record ClasificacionDto(
			int position,
			Long memberId,
			String username,
			BigDecimal totalPoints,
			BigDecimal lastMatchdayPoints,
			/** Puestos de la alineación que se quedaron vacíos en la última jornada puntuada. */
			Integer lastMatchdayEmptySlots,
			/** Si ha confirmado la alineación de la jornada que viene. */
			boolean lineupConfirmed,
			boolean me) {
	}

	public record JugadorPlantillaDto(
			Long playerId,
			String name,
			Position position,
			String teamName,
			String teamShortName,
			BigDecimal value,
			boolean starter,
			BigDecimal pricePaid,
			BigDecimal lastMatchdayPoints,
			PlayerStatus status) {
	}

	public record PlantillaDto(
			Long memberId,
			String username,
			Formation formation,
			BigDecimal budget,
			int maxSize,
			List<JugadorPlantillaDto> players) {
	}

	public record JugadorMercadoDto(
			Long playerId,
			String name,
			Position position,
			String teamName,
			String teamShortName,
			BigDecimal value,
			BigDecimal minBid,
			BigDecimal maxBid,
			BigDecimal myBid,
			PlayerStatus status) {
	}

	public record OfertaDto(
			Long id,
			Long playerId,
			String playerName,
			Position position,
			String teamShortName,
			BigDecimal value,
			String bidder,
			String seller,
			BigDecimal amount,
			OfferStatus status,
			Instant createdAt,
			boolean sent,
			boolean received) {
	}

	// --- Peticiones ---

	/**
	 * Al crear la liga se elige el reparto inicial. El tope solo se admite en ALEATORIO_CON_TOPE, donde además
	 * es obligatorio: enviarlo en las otras dos modalidades se <strong>rechaza</strong> con un 400 (no se
	 * ignora en silencio, para que nadie crea que su liga tiene un tope que no se va a aplicar).
	 */
	public record CrearLigaRequest(
			@NotBlank(message = "es obligatorio") @Size(min = 3, max = 40, message = "debe tener de 3 a 40 caracteres") String name,
			@NotNull(message = "es obligatoria") InitialSquadMode initialSquadMode,
			@NotNull(message = "es obligatorio") @DecimalMin(value = "0", message = "no puede ser negativo")
			@DecimalMax(value = "500000000", message = "máximo 500.000.000") BigDecimal initialBudget,
			@DecimalMin(value = "1000000", message = "mínimo 1.000.000") @DecimalMax(value = "500000000",
					message = "máximo 500.000.000") BigDecimal squadCap) {
	}

	public record UnirseRequest(@NotBlank(message = "es obligatorio") @Size(max = 12) String code) {
	}

	public record IntervaloRequest(
			@Min(value = 10, message = "mínimo 10 segundos") @Max(value = 2_592_000, message = "máximo 30 días") long seconds) {
	}

	public record OpcionesRequest(
			@NotNull(message = "es obligatorio") AdvanceMode advanceMode,
			@NotNull(message = "es obligatorio") @DecimalMin(value = "0", message = "no puede ser negativo")
			@DecimalMax(value = "10000000", message = "máximo 10.000.000 por punto") BigDecimal pointValue,
			@NotNull(message = "es obligatorio") @Size(max = 20, message = "máximo 20 puestos") List<
					@NotNull(message = "es obligatorio") @DecimalMin(value = "0", message = "no puede ser negativo")
					@DecimalMax(value = "100000000", message = "máximo 100.000.000 por puesto") BigDecimal> positionBonuses) {
	}

	public record AlineacionRequest(
			@NotNull(message = "es obligatoria") Formation formation,
			@NotNull(message = "es obligatorio") @Size(max = 30) List<Long> starterIds) {
	}

	public record PujaRequest(@NotNull(message = "es obligatorio") @Positive(message = "debe ser positivo") BigDecimal amount) {
	}

	public record OfertaDirectaRequest(
			@NotNull(message = "es obligatorio") Long playerId,
			@NotNull(message = "es obligatorio") @Positive(message = "debe ser positivo") BigDecimal amount) {
	}
}
