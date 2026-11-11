import { useEffect, useState } from 'react'
import { getClasificacion, getPlantillaRival, ofertarDirecta } from '../api'
import type { Clasificacion, Plantilla } from '../api'
import { ABREVIATURA_POSICION, euros } from '../format'
import { EstadoJugador } from './EstadoJugador'
import { mensajeDeError, useLiga } from './ayudas'

export function Rivales() {
  const { liga } = useLiga()
  const [rivales, setRivales] = useState<Clasificacion[]>([])
  const [rivalId, setRivalId] = useState<number | null>(null)
  const [plantilla, setPlantilla] = useState<Plantilla | null>(null)
  const [importes, setImportes] = useState<Record<number, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)

  useEffect(() => {
    getClasificacion(liga.id)
      .then((tabla) => {
        const otros = tabla.filter((f) => !f.me)
        setRivales(otros)
        setRivalId(otros[0]?.memberId ?? null)
      })
      .catch((e: unknown) => setError(mensajeDeError(e)))
  }, [liga.id])

  useEffect(() => {
    if (rivalId === null) return
    getPlantillaRival(liga.id, rivalId)
      .then(setPlantilla)
      .catch((e: unknown) => setError(mensajeDeError(e)))
  }, [liga.id, rivalId])

  async function ofertar(playerId: number, nombre: string, valor: number) {
    setError(null)
    setAviso(null)
    try {
      await ofertarDirecta(liga.id, playerId, Number(importes[playerId] ?? valor))
      setAviso(`Oferta por ${nombre} enviada: se resuelve cuando su dueño la acepte o al cambiar de jornada`)
    } catch (e) {
      setError(mensajeDeError(e))
    }
  }

  if (rivales.length === 0) return <p>{error ?? 'Todavía no hay otros jugadores en la liga. Comparte el código para invitarlos.'}</p>

  const terminada = liga.status === 'FINALIZADA'

  return (
    <div>
      <label>
        Ver la plantilla de{' '}
        <select value={rivalId ?? ''} onChange={(e) => setRivalId(Number(e.target.value))}>
          {rivales.map((r) => (
            <option key={r.memberId} value={r.memberId}>
              {r.username}
            </option>
          ))}
        </select>
      </label>

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {aviso && <p className="bien">{aviso}</p>}

      {plantilla && (
        <table>
          <thead>
            <tr>
              <th>Jugador</th>
              <th>Pos.</th>
              <th>Equipo</th>
              <th>Valor</th>
              <th>Mi oferta</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {plantilla.players.map((j) => (
              <tr key={j.playerId}>
                <td>
                  {j.name} <EstadoJugador estado={j.status} noJugo={liga.currentMatchday > 0 && j.lastMatchdayPoints === null} />
                </td>
                <td>{ABREVIATURA_POSICION[j.position]}</td>
                <td>{j.teamShortName}</td>
                <td>{euros(j.value)}</td>
                <td>
                  <input
                    type="number"
                    min={Math.ceil(j.value)}
                    max={Math.floor(j.value * 1.2)}
                    step={1}
                    className="importe"
                    value={importes[j.playerId] ?? Math.ceil(j.value)}
                    onChange={(e) => setImportes({ ...importes, [j.playerId]: e.target.value })}
                    aria-label={`Importe de la oferta por ${j.name}`}
                    disabled={terminada}
                  />
                </td>
                <td>
                  <button type="button" disabled={terminada} onClick={() => void ofertar(j.playerId, j.name, Math.ceil(j.value))}>
                    Ofertar
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
