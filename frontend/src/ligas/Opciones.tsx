import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { avanzarJornada, cambiarIntervalo, cambiarOpciones, expulsarMiembro, getClasificacion } from '../api'
import type { Clasificacion, ModoAvance } from '../api'
import { duracion, euros } from '../format'
import { mensajeDeError, useLiga } from './ayudas'

const RITMOS = [
  { etiqueta: '30 s (pruebas)', segundos: 30 },
  { etiqueta: '3 días', segundos: 3 * 86400 },
  { etiqueta: '7 días', segundos: 7 * 86400 },
]

const MODOS: { valor: ModoAvance; etiqueta: string; explicacion: string }[] = [
  {
    valor: 'POR_TIEMPO',
    etiqueta: 'Por días',
    explicacion: 'La jornada se juega cuando vence el plazo, haya confirmado quien sea.',
  },
  {
    valor: 'POR_CONFIRMACION',
    etiqueta: 'Al confirmar todos',
    explicacion:
      'La jornada se juega en cuanto todos los miembros confirman su alineación. El plazo pasa a ser la fecha límite: a quien no confirme se le juega la alineación que tenga puesta.',
  },
]

const ordinal = (puesto: number) => `${puesto}.º`

/** Panel de opciones de la liga: todos ven las reglas; el dueño las edita, avanza la jornada y expulsa. */
export function Opciones() {
  const { liga, recargar } = useLiga()
  const [modo, setModo] = useState<ModoAvance>(liga.advanceMode)
  const [valorPunto, setValorPunto] = useState(String(liga.pointValue))
  const [premios, setPremios] = useState<string[]>(liga.positionBonuses.map(String))
  const [miembros, setMiembros] = useState<Clasificacion[]>([])
  const [mensaje, setMensaje] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)
  const terminada = liga.status === 'FINALIZADA'

  useEffect(() => {
    if (!liga.isOwner) return
    getClasificacion(liga.id)
      .then(setMiembros)
      .catch((e: unknown) => setError(mensajeDeError(e)))
  }, [liga.id, liga.isOwner, liga.members])

  async function ejecutar(accion: () => Promise<string>) {
    setMensaje(null)
    setError(null)
    setOcupado(true)
    try {
      setMensaje(await accion())
      await recargar()
    } catch (e) {
      setError(mensajeDeError(e))
    } finally {
      setOcupado(false)
    }
  }

  function guardar(evento: FormEvent) {
    evento.preventDefault()
    void ejecutar(async () => {
      await cambiarOpciones(liga.id, modo, Number(valorPunto), premios.map(Number))
      return 'Opciones guardadas. Rigen desde la próxima jornada.'
    })
  }

  function expulsar(miembro: Clasificacion) {
    if (!window.confirm(`¿Expulsar a ${miembro.username} de la liga? Perderá su plantilla y sus ofertas.`)) return
    void ejecutar(async () => {
      await expulsarMiembro(liga.id, miembro.memberId)
      return `${miembro.username} ha sido expulsado de la liga.`
    })
  }

  return (
    <section className="opciones">
      <h3>Reglas de la liga</h3>
      <ul>
        <li>
          Por cada punto de tu alineación en una jornada se te abonan <strong>{euros(liga.pointValue)}</strong>.
        </li>
        <li>
          Premio por puesto en la jornada:{' '}
          {liga.positionBonuses.length === 0 ? (
            <strong>sin premios</strong>
          ) : (
            liga.positionBonuses.map((p, i) => (
              <span key={i}>
                {i > 0 && ', '}
                {ordinal(i + 1)} <strong>{euros(p)}</strong>
              </span>
            ))
          )}
          . Los empates comparten puesto y quien no suma puntos no cobra.
        </li>
        <li>
          Cada puesto de la alineación que se quede sin jugador resta{' '}
          <strong>{liga.emptySlotPenalty} puntos</strong> en esa jornada.
        </li>
        <li>
          Avance de jornada: <strong>{MODOS.find((m) => m.valor === liga.advanceMode)?.etiqueta}</strong>.{' '}
          {MODOS.find((m) => m.valor === liga.advanceMode)?.explicacion}
        </li>
        <li>
          {liga.advanceMode === 'POR_CONFIRMACION' ? 'Fecha límite para confirmar: ' : 'Ritmo de las jornadas: cada '}
          {duracion(liga.intervalSeconds)}
          {liga.advanceMode === 'POR_CONFIRMACION' ? ' desde que empieza la jornada.' : '.'}
        </li>
      </ul>

      {mensaje && <p role="status">{mensaje}</p>}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      {liga.isOwner && !terminada && (
        <>
          <h3>Reglas y avance (solo el dueño)</h3>
          <form onSubmit={guardar} className="form-opciones">
            <fieldset>
              <legend>Cuándo se juega la jornada</legend>
              {MODOS.map((m) => (
                <label key={m.valor} className="fila-premio">
                  <input
                    type="radio"
                    name="modo-avance"
                    value={m.valor}
                    checked={modo === m.valor}
                    onChange={() => setModo(m.valor)}
                  />
                  {m.etiqueta} <small>{m.explicacion}</small>
                </label>
              ))}
            </fieldset>
            <label>
              Abono por punto (€)
              <input type="number" min={0} max={10000000} step={1000} value={valorPunto} onChange={(e) => setValorPunto(e.target.value)} required />
            </label>
            <fieldset>
              <legend>Premio por puesto en la jornada (€)</legend>
              {premios.map((premio, i) => (
                <label key={i} className="fila-premio">
                  {ordinal(i + 1)}
                  <input
                    type="number"
                    min={0}
                    max={100000000}
                    step={100000}
                    value={premio}
                    onChange={(e) => setPremios(premios.map((p, j) => (j === i ? e.target.value : p)))}
                    required
                  />
                  <button type="button" onClick={() => setPremios(premios.filter((_, j) => j !== i))} aria-label={`Quitar el premio del ${ordinal(i + 1)}`}>
                    Quitar
                  </button>
                </label>
              ))}
              {premios.length < 20 && (
                <button type="button" onClick={() => setPremios([...premios, '0'])}>
                  Añadir puesto
                </button>
              )}
            </fieldset>
            <button type="submit" disabled={ocupado}>
              Guardar opciones
            </button>
          </form>

          <h3>Jornadas (solo el dueño)</h3>
          <div className="dueno-contenido">
            <button
              type="button"
              disabled={ocupado}
              onClick={() =>
                void ejecutar(async () => {
                  await avanzarJornada(liga.id)
                  return 'Jornada avanzada.'
                })
              }
            >
              {ocupado ? 'Trabajando...' : 'Avanzar jornada ahora'}
            </button>
            <span>Ritmo:</span>
            {RITMOS.map((r) => (
              <button
                key={r.segundos}
                type="button"
                disabled={ocupado}
                className={r.segundos === liga.intervalSeconds ? 'activo' : ''}
                onClick={() =>
                  void ejecutar(async () => {
                    await cambiarIntervalo(liga.id, r.segundos)
                    return `Ritmo cambiado a ${r.etiqueta}.`
                  })
                }
              >
                {r.etiqueta}
              </button>
            ))}
          </div>
          {ocupado && (
            <p role="status">
              Si faltan las estadísticas de la jornada {liga.currentMatchday + 1}, se simulan ahora mismo. No cierres
              esta página.
            </p>
          )}
        </>
      )}

      {liga.isOwner && (
        <>
          <h3>Miembros (solo el dueño)</h3>
          <table>
            <tbody>
              {miembros.map((m) => (
                <tr key={m.memberId} className={m.me ? 'yo' : ''}>
                  <td>{m.username}</td>
                  <td>{m.me ? 'Dueño' : ''}</td>
                  <td>
                    {!m.me && (
                      <button type="button" disabled={ocupado} onClick={() => expulsar(m)}>
                        Expulsar
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </section>
  )
}
