import { useEffect, useState } from 'react'
import { getPlayers, getTeams } from './api'
import type { Player, Position, Team } from './api'
import { EstadoJugador } from './ligas/EstadoJugador'

const POSICIONES: Position[] = ['PORTERO', 'DEFENSA', 'CENTROCAMPISTA', 'DELANTERO']

const formatoEuros = new Intl.NumberFormat('es-ES', {
  style: 'currency',
  currency: 'EUR',
  maximumFractionDigits: 0,
})

export function Jugadores() {
  const [equipos, setEquipos] = useState<Team[]>([])
  const [jugadores, setJugadores] = useState<Player[]>([])
  const [equipoId, setEquipoId] = useState<number | undefined>()
  const [posicion, setPosicion] = useState<Position | undefined>()
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getTeams()
      .then(setEquipos)
      .catch((e: Error) => setError(e.message))
  }, [])

  useEffect(() => {
    getPlayers({ teamId: equipoId, position: posicion })
      .then((datos) => {
        setJugadores(datos)
        setError(null)
      })
      .catch((e: Error) => setError(e.message))
  }, [equipoId, posicion])

  return (
    <>
      <div className="filtros">
        <select value={equipoId ?? ''} onChange={(e) => setEquipoId(e.target.value ? Number(e.target.value) : undefined)}>
          <option value="">Todos los equipos</option>
          {equipos.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>

        <select value={posicion ?? ''} onChange={(e) => setPosicion((e.target.value || undefined) as Position | undefined)}>
          <option value="">Todas las posiciones</option>
          {POSICIONES.map((p) => (
            <option key={p} value={p}>
              {p}
            </option>
          ))}
        </select>
      </div>

      {error && <p className="error">{error}</p>}

      <table>
        <thead>
          <tr>
            <th>Jugador</th>
            <th>Equipo</th>
            <th>Posición</th>
            <th>Valor</th>
          </tr>
        </thead>
        <tbody>
          {jugadores.map((j) => (
            <tr key={j.id}>
              <td>
                {j.name} <EstadoJugador estado={j.status} />
              </td>
              <td>{j.teamShortName}</td>
              <td>{j.position}</td>
              <td>{formatoEuros.format(j.marketValue)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {!error && jugadores.length === 0 && <p>No hay jugadores con esos filtros.</p>}
    </>
  )
}
