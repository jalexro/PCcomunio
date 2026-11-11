import { useEffect, useState } from 'react'
import { getAbonos, getClasificacion } from '../api'
import type { Abono, Clasificacion as Fila } from '../api'
import { euros, puntos } from '../format'
import { mensajeDeError, useLiga } from './ayudas'

export function Clasificacion() {
  const { liga } = useLiga()
  const [tabla, setTabla] = useState<Fila[] | null>(null)
  const [abonos, setAbonos] = useState<Abono[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([getClasificacion(liga.id), getAbonos(liga.id)])
      .then(([puntuacion, cobrado]) => {
        setTabla(puntuacion)
        setAbonos(cobrado)
      })
      .catch((e: unknown) => setError(mensajeDeError(e)))
  }, [liga.id])

  if (error) return <p className="error">{error}</p>
  if (!tabla) return <p>Cargando...</p>

  const porConfirmacion = liga.advanceMode === 'POR_CONFIRMACION' && liga.status === 'EN_CURSO'

  return (
    <>
    <h3>Clasificación por puntos</h3>
    <table>
      <thead>
        <tr>
          <th>#</th>
          <th>Jugador</th>
          <th>Última jornada</th>
          <th>Huecos</th>
          <th>Total</th>
          {porConfirmacion && <th>Alineación</th>}
        </tr>
      </thead>
      <tbody>
        {tabla.map((f) => (
          <tr key={f.memberId} className={f.me ? 'yo' : ''}>
            <td>{f.position}</td>
            <td>{f.username}</td>
            <td>{puntos(f.lastMatchdayPoints)}</td>
            <td title="Puestos de la alineación sin jugador en la última jornada">
              {f.lastMatchdayEmptySlots ? `${f.lastMatchdayEmptySlots} (-${f.lastMatchdayEmptySlots * liga.emptySlotPenalty})` : '-'}
            </td>
            <td>
              <strong>{puntos(f.totalPoints)}</strong>
            </td>
            {porConfirmacion && <td>{f.lineupConfirmed ? 'Confirmada' : 'Pendiente'}</td>}
          </tr>
        ))}
      </tbody>
    </table>

    <h3>Clasificación por abonos</h3>
    <table>
      <thead>
        <tr>
          <th>#</th>
          <th>Jugador</th>
          <th>Puesto última jornada</th>
          <th>Cobrado última jornada</th>
          <th>Por puntos</th>
          <th>Por puesto</th>
          <th>Total cobrado</th>
        </tr>
      </thead>
      <tbody>
        {abonos.map((a) => (
          <tr key={a.memberId} className={a.me ? 'yo' : ''}>
            <td>{a.position}</td>
            <td>{a.username}</td>
            <td>{a.lastMatchdayPosition ? `${a.lastMatchdayPosition}.º` : '-'}</td>
            <td>{a.lastMatchdayPayout === null ? '-' : euros(a.lastMatchdayPayout)}</td>
            <td>{euros(a.totalFromPoints)}</td>
            <td>{euros(a.totalFromPosition)}</td>
            <td>
              <strong>{euros(a.totalPayout)}</strong>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
    </>
  )
}
