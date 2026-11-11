import { useCallback, useEffect, useState } from 'react'
import { aceptarOferta, getMercado, getOfertas, pujar, rechazarOferta, retirarOferta } from '../api'
import type { JugadorMercado, Oferta } from '../api'
import { ABREVIATURA_POSICION, euros } from '../format'
import { EstadoJugador } from './EstadoJugador'
import { mensajeDeError, useLiga } from './ayudas'

export function Mercado() {
  const { liga, recargar } = useLiga()
  const [mercado, setMercado] = useState<JugadorMercado[] | null>(null)
  const [ofertas, setOfertas] = useState<Oferta[]>([])
  const [importes, setImportes] = useState<Record<number, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)

  const cargar = useCallback(async () => {
    try {
      setMercado(await getMercado(liga.id))
      setOfertas(await getOfertas(liga.id))
    } catch (e) {
      setError(mensajeDeError(e))
    }
  }, [liga.id])

  useEffect(() => {
    Promise.all([getMercado(liga.id), getOfertas(liga.id)])
      .then(([listado, mias]) => {
        setMercado(listado)
        setOfertas(mias)
      })
      .catch((e: unknown) => setError(mensajeDeError(e)))
  }, [liga.id])

  async function ejecutar(accion: () => Promise<unknown>, exito: string) {
    setError(null)
    setAviso(null)
    try {
      await accion()
      setAviso(exito)
    } catch (e) {
      setError(mensajeDeError(e))
    }
    await cargar()
    await recargar()
  }

  if (!mercado) return <p>{error ?? 'Cargando...'}</p>

  const terminada = liga.status === 'FINALIZADA'
  const recibidas = ofertas.filter((o) => o.received && o.status === 'PENDIENTE')
  const pendientesEnviadas = ofertas.filter((o) => o.sent && o.status === 'PENDIENTE')
  const historial = ofertas.filter((o) => o.status !== 'PENDIENTE')

  return (
    <div>
      <p>
        Las pujas se resuelven al cambiar de jornada: gana la más alta. Puedes ofertar entre el valor del jugador y un 20 % más.
      </p>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {aviso && <p className="bien">{aviso}</p>}

      <h3>En el mercado</h3>
      <table>
        <thead>
          <tr>
            <th>Jugador</th>
            <th>Pos.</th>
            <th>Equipo</th>
            <th>Valor</th>
            <th>Rango</th>
            <th>Mi puja</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {mercado.map((j) => (
            <tr key={j.playerId}>
              <td>
                {j.name} <EstadoJugador estado={j.status} />
              </td>
              <td>{ABREVIATURA_POSICION[j.position]}</td>
              <td>{j.teamShortName}</td>
              <td>{euros(j.value)}</td>
              <td>
                {euros(j.minBid)} - {euros(j.maxBid)}
              </td>
              <td>
                <input
                  type="number"
                  min={j.minBid}
                  max={j.maxBid}
                  step={1}
                  className="importe"
                  value={importes[j.playerId] ?? j.myBid ?? j.minBid}
                  onChange={(e) => setImportes({ ...importes, [j.playerId]: e.target.value })}
                  aria-label={`Importe de la puja por ${j.name}`}
                  disabled={terminada}
                />
              </td>
              <td>
                <button
                  type="button"
                  disabled={terminada}
                  onClick={() =>
                    void ejecutar(
                      () => pujar(liga.id, j.playerId, Number(importes[j.playerId] ?? j.myBid ?? j.minBid)),
                      `Puja por ${j.name} guardada`,
                    )
                  }
                >
                  {j.myBid === null ? 'Pujar' : 'Actualizar'}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {mercado.length === 0 && <p>No hay jugadores en el mercado ahora mismo.</p>}

      <h3>Ofertas que me han hecho</h3>
      {recibidas.length === 0 && <p>Ninguna pendiente.</p>}
      {recibidas.map((o) => (
        <div key={o.id} className="oferta">
          <span>
            <strong>{o.bidder}</strong> ofrece {euros(o.amount)} por {o.playerName} (valor {euros(o.value)})
          </span>
          <button type="button" onClick={() => void ejecutar(() => aceptarOferta(liga.id, o.id), `Has vendido a ${o.playerName}`)}>
            Aceptar
          </button>
          <button type="button" onClick={() => void ejecutar(() => rechazarOferta(liga.id, o.id), 'Oferta rechazada')}>
            Rechazar
          </button>
        </div>
      ))}

      <h3>Mis ofertas pendientes</h3>
      {pendientesEnviadas.length === 0 && <p>Ninguna pendiente.</p>}
      {pendientesEnviadas.map((o) => (
        <div key={o.id} className="oferta">
          <span>
            {euros(o.amount)} por {o.playerName} {o.seller ? `(a ${o.seller})` : '(al mercado)'}
          </span>
          <button type="button" onClick={() => void ejecutar(() => retirarOferta(liga.id, o.id), 'Oferta retirada')}>
            Retirar
          </button>
        </div>
      ))}

      {historial.length > 0 && (
        <>
          <h3>Historial</h3>
          <ul>
            {historial.map((o) => (
              <li key={o.id}>
                {o.playerName}: {euros(o.amount)} - {o.status.toLowerCase()} ({o.sent ? (o.seller ? `a ${o.seller}` : 'al mercado') : `de ${o.bidder}`})
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}
