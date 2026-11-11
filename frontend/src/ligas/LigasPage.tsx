import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { crearLiga, misLigas, unirseLiga } from '../api'
import type { Liga, ModalidadReparto } from '../api'
import { euros } from '../format'
import { mensajeDeError } from './ayudas'

const MODALIDADES: { valor: ModalidadReparto; etiqueta: string; explicacion: string }[] = [
  {
    valor: 'ALEATORIO',
    etiqueta: '11 jugadores al azar',
    explicacion:
      'Quien entra recibe gratis 1 portero, 4 defensas, 4 centrocampistas y 2 delanteros al azar, sin mirar el precio.',
  },
  {
    valor: 'SIN_JUGADORES',
    etiqueta: 'Sin jugadores',
    explicacion: 'Se empieza con la plantilla vacía: todo el once hay que ficharlo en el mercado.',
  },
  {
    valor: 'ALEATORIO_CON_TOPE',
    etiqueta: '11 al azar con tope de valor',
    explicacion:
      'Igual que el reparto al azar, pero la suma de los valores de los 11 no pasa del tope que elijas (y el presupuesto suele ser más bajo).',
  },
]

/** Presupuesto que se propone según la modalidad: con tope se reparten 11 jugadores, así que se da menos dinero. */
const PRESUPUESTOS: Record<ModalidadReparto, number> = {
  ALEATORIO: 40_000_000,
  SIN_JUGADORES: 40_000_000,
  ALEATORIO_CON_TOPE: 20_000_000,
}
const TOPE_POR_DEFECTO = 30_000_000

export function LigasPage() {
  const navegar = useNavigate()
  const [ligas, setLigas] = useState<Liga[] | null>(null)
  const [nombre, setNombre] = useState('')
  const [modalidad, setModalidad] = useState<ModalidadReparto>('ALEATORIO')
  const [presupuesto, setPresupuesto] = useState(String(PRESUPUESTOS.ALEATORIO))
  const [tope, setTope] = useState(String(TOPE_POR_DEFECTO))
  const [codigo, setCodigo] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  function elegirModalidad(nueva: ModalidadReparto) {
    setModalidad(nueva)
    setPresupuesto(String(PRESUPUESTOS[nueva]))
  }

  useEffect(() => {
    misLigas()
      .then(setLigas)
      .catch((e: unknown) => setError(mensajeDeError(e)))
  }, [])

  async function enviar(evento: FormEvent, accion: () => Promise<Liga>) {
    evento.preventDefault()
    setError(null)
    setEnviando(true)
    try {
      const liga = await accion()
      navegar(`/ligas/${liga.id}`)
    } catch (e) {
      setError(mensajeDeError(e))
    } finally {
      setEnviando(false)
    }
  }

  return (
    <section>
      <h2>Mis ligas</h2>

      {ligas === null && !error && <p>Cargando...</p>}
      {ligas?.length === 0 && <p>Todavía no estás en ninguna liga. Crea una o únete con el código de un amigo.</p>}

      <ul className="tarjetas">
        {ligas?.map((l) => (
          <li key={l.id}>
            <Link to={`/ligas/${l.id}`}>
              <strong>{l.name}</strong>
              <span>
                Jornada {l.currentMatchday}/{l.lastMatchday} · {l.members} jugadores
                {l.status === 'FINALIZADA' ? ' · terminada' : ''}
              </span>
            </Link>
          </li>
        ))}
      </ul>

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      <div className="dos-columnas">
        <form
          className="form-opciones"
          onSubmit={(e) =>
            void enviar(e, () =>
              crearLiga(
                nombre,
                modalidad,
                Number(presupuesto),
                modalidad === 'ALEATORIO_CON_TOPE' ? Number(tope) : null,
              ),
            )
          }
        >
          <h3>Crear una liga</h3>
          <label>
            Nombre
            <input value={nombre} onChange={(e) => setNombre(e.target.value)} required minLength={3} maxLength={40} />
          </label>

          <fieldset>
            <legend>Con qué se empieza</legend>
            {MODALIDADES.map((m) => (
              <label key={m.valor} className="fila-premio">
                <input
                  type="radio"
                  name="modalidad-reparto"
                  value={m.valor}
                  checked={modalidad === m.valor}
                  onChange={() => elegirModalidad(m.valor)}
                />
                {m.etiqueta} <small>{m.explicacion}</small>
              </label>
            ))}
          </fieldset>

          {modalidad === 'SIN_JUGADORES' && (
            <div className="aviso">
              <p>
                Cada puesto de la alineación que llegue a una jornada sin jugador resta 4 puntos: quien no fiche a
                nadie empieza con −44 en la primera jornada. Es a propósito, para que haya que moverse en el mercado.
              </p>
            </div>
          )}

          <label>
            Presupuesto inicial (€)
            <input
              type="number"
              value={presupuesto}
              onChange={(e) => setPresupuesto(e.target.value)}
              required
              min={0}
              max={500000000}
              step={1000000}
            />
            <small>{euros(Number(presupuesto))} para cada miembro, también para quien se una después.</small>
          </label>

          {modalidad === 'ALEATORIO_CON_TOPE' && (
            <label>
              Tope de valor de los 11 (€)
              <input
                type="number"
                value={tope}
                onChange={(e) => setTope(e.target.value)}
                required
                min={1000000}
                max={500000000}
                step={1000000}
              />
              <small>La suma de los valores de los 11 jugadores sorteados no pasará de {euros(Number(tope))}.</small>
            </label>
          )}

          <button type="submit" disabled={enviando}>
            Crear
          </button>
        </form>

        <form onSubmit={(e) => void enviar(e, () => unirseLiga(codigo))}>
          <h3>Unirme con un código</h3>
          <label>
            Código
            <input value={codigo} onChange={(e) => setCodigo(e.target.value)} required maxLength={12} />
          </label>
          <button type="submit" disabled={enviando}>
            Unirme
          </button>
        </form>
      </div>
    </section>
  )
}
