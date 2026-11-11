import type { EstadoJugador as Estado } from '../api'

const ETIQUETAS: Record<Exclude<Estado, 'DISPONIBLE'>, string> = {
  LESIONADO: 'Lesionado',
  SANCIONADO: 'Sancionado',
  DUDA: 'Duda',
}

/**
 * Avisos junto al nombre del jugador: lesionado, sancionado o duda (estado de la fuente de valores) y, si la
 * liga ya ha puntuado alguna jornada, si no jugó en la última (no convocado o sin minutos).
 */
export function EstadoJugador({ estado, noJugo = false }: { estado: Estado; noJugo?: boolean }) {
  return (
    <>
      {estado !== 'DISPONIBLE' && <span className={`insignia insignia-${estado.toLowerCase()}`}>{ETIQUETAS[estado]}</span>}
      {noJugo && (
        <span className="insignia insignia-nojugo" title="No tuvo minutos en la última jornada (no convocado o suplente sin jugar)">
          No jugó
        </span>
      )}
    </>
  )
}
