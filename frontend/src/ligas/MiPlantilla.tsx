import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getMiPlantilla, venderJugador } from '../api'
import type { Plantilla } from '../api'
import { ABREVIATURA_POSICION, euros, puntos } from '../format'
import { EstadoJugador } from './EstadoJugador'
import { mensajeDeError, useLiga } from './ayudas'

const TITULARES = 11

/**
 * Mis jugadores: quién tengo, cuánto vale, cuánto puntuó y venderlos. Quién juega y en qué puesto se
 * decide en la pestaña Táctica, que es la que tiene el campo.
 */
export function MiPlantilla() {
  const { liga, recargar } = useLiga()
  const [plantilla, setPlantilla] = useState<Plantilla | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)

  const aplicar = useCallback((p: Plantilla) => setPlantilla(p), [])

  useEffect(() => {
    getMiPlantilla(liga.id)
      .then(aplicar)
      .catch((e: unknown) => setError(mensajeDeError(e)))
  }, [liga.id, aplicar])

  if (error && !plantilla) return <p className="error">{error}</p>
  if (!plantilla) return <p>Cargando...</p>

  const terminada = liga.status === 'FINALIZADA'
  const titulares = plantilla.players.filter((j) => j.starter).length
  const huecos = Math.max(0, TITULARES - titulares)

  async function vender(playerId: number, nombre: string) {
    if (!window.confirm(`¿Vender a ${nombre} al mercado por su valor?`)) return
    setError(null)
    setAviso(null)
    try {
      aplicar(await venderJugador(liga.id, playerId))
      await recargar()
      setAviso(`${nombre} vendido`)
    } catch (e) {
      setError(mensajeDeError(e))
    }
  }

  return (
    <div>
      <p>
        Plantilla: {plantilla.players.length}/{plantilla.maxSize} jugadores · Titulares: {titulares}/{TITULARES} ·
        Presupuesto: <strong>{euros(plantilla.budget)}</strong>
      </p>

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {aviso && <p className="bien">{aviso}</p>}

      {huecos > 0 && !terminada && (
        <p className="aviso">
          Te faltan {huecos} {huecos === 1 ? 'titular' : 'titulares'} para completar los {TITULARES}: cada puesto vacío
          resta <strong>{liga.emptySlotPenalty} puntos</strong> en la jornada ({huecos * liga.emptySlotPenalty} en
          total). Colócalos en <Link to={`/ligas/${liga.id}/tactica`}>Táctica</Link> o fíchalos en el mercado.
        </p>
      )}

      {plantilla.players.length === 0 ? (
        <p>No tienes ningún jugador todavía. Ficha en el mercado para empezar.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Jugador</th>
              <th>Pos.</th>
              <th>Equipo</th>
              <th>Valor</th>
              <th>Pagado</th>
              <th>Pts. jornada</th>
              <th>Alineación</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {plantilla.players.map((j) => (
              <tr key={j.playerId} className={j.starter ? 'titular' : ''}>
                <td>
                  {j.name}{' '}
                  <EstadoJugador
                    estado={j.status}
                    noJugo={liga.currentMatchday > 0 && j.lastMatchdayPoints === null}
                  />
                </td>
                <td>{ABREVIATURA_POSICION[j.position]}</td>
                <td>{j.teamShortName}</td>
                <td>{euros(j.value)}</td>
                <td>{euros(j.pricePaid)}</td>
                <td>{puntos(j.lastMatchdayPoints)}</td>
                <td>{j.starter ? 'Titular' : 'Banquillo'}</td>
                <td>
                  <button type="button" onClick={() => void vender(j.playerId, j.name)} disabled={terminada}>
                    Vender
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
