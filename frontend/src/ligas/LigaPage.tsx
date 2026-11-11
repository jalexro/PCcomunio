import { useCallback, useEffect, useState } from 'react'
import { NavLink, Outlet, useParams } from 'react-router-dom'
import { getLiga } from '../api'
import type { Liga } from '../api'
import { cuentaAtras, duracion, euros } from '../format'
import { ContextoLigaCtx, mensajeDeError, useAhora } from './ayudas'

export function LigaPage() {
  const ligaId = Number(useParams().id)
  const [liga, setLiga] = useState<Liga | null>(null)
  const [error, setError] = useState<string | null>(null)
  const ahora = useAhora()

  const recargar = useCallback(async () => {
    try {
      setLiga(await getLiga(ligaId))
      setError(null)
    } catch (e) {
      setError(mensajeDeError(e))
    }
  }, [ligaId])

  useEffect(() => {
    getLiga(ligaId)
      .then(setLiga)
      .catch((e: unknown) => setError(mensajeDeError(e)))
  }, [ligaId])

  // Cuando vence la cuenta atrás el reloj del servidor avanza la jornada en menos de un minuto: se consulta cada 5 s.
  // Jugando por confirmación la jornada puede caer en cualquier momento (en cuanto confirme el último), así que
  // ahí se consulta cada 10 s aunque no haya vencido el plazo.
  useEffect(() => {
    if (!liga || liga.status !== 'EN_CURSO') return
    const porConfirmacion = liga.advanceMode === 'POR_CONFIRMACION'
    const temporizador = setInterval(
      () => {
        if (porConfirmacion || Date.now() >= Date.parse(liga.nextAdvanceAt)) void recargar()
      },
      porConfirmacion ? 10000 : 5000,
    )
    return () => clearInterval(temporizador)
  }, [liga, recargar])

  if (error) {
    return (
      <p className="error" role="alert">
        {error}
      </p>
    )
  }
  if (!liga) return <p>Cargando...</p>

  const terminada = liga.status === 'FINALIZADA'

  return (
    <section>
      <header className="liga-cabecera">
        <div>
          <h2>{liga.name}</h2>
          <p>
            Jornada <strong>{liga.currentMatchday}</strong> de {liga.lastMatchday} · {liga.members} jugadores · código{' '}
            <code>{liga.joinCode}</code>
          </p>
          <p>
            Tu presupuesto: <strong>{euros(liga.myBudget)}</strong>
          </p>
        </div>
        <div className="reloj">
          {terminada ? (
            <strong>Liga terminada</strong>
          ) : liga.advanceMode === 'POR_CONFIRMACION' ? (
            <>
              <span>Se juega al confirmar todos</span>
              <strong>{cuentaAtras(Date.parse(liga.nextAdvanceAt) - ahora)}</strong>
              <small>
                {liga.pendingConfirmations.length === 0
                  ? 'Todos han confirmado'
                  : `Faltan: ${liga.pendingConfirmations.join(', ')}`}
              </small>
            </>
          ) : (
            <>
              <span>Próxima jornada en</span>
              <strong>{cuentaAtras(Date.parse(liga.nextAdvanceAt) - ahora)}</strong>
              <small>Cada {duracion(liga.intervalSeconds)}</small>
            </>
          )}
        </div>
      </header>

      {!terminada && liga.advanceMode === 'POR_CONFIRMACION' && !liga.iConfirmed && (
        <p className="aviso">
          Todavía no has confirmado tu alineación para la jornada {liga.currentMatchday + 1}. Si no confirmas antes de
          la fecha límite se jugará con la que tengas puesta.
        </p>
      )}

      <nav className="pestanas">
        <NavLink to={`/ligas/${liga.id}`} end>
          Clasificación
        </NavLink>
        <NavLink to={`/ligas/${liga.id}/plantilla`}>Jugadores</NavLink>
        <NavLink to={`/ligas/${liga.id}/tactica`}>Táctica</NavLink>
        <NavLink to={`/ligas/${liga.id}/mercado`}>Mercado</NavLink>
        <NavLink to={`/ligas/${liga.id}/rivales`}>Rivales</NavLink>
        <NavLink to={`/ligas/${liga.id}/opciones`}>Opciones</NavLink>
      </nav>

      <ContextoLigaCtx.Provider value={{ liga, recargar }}>
        {/* La clave fuerza a las pestañas a recargar sus datos cuando cambia la jornada */}
        <Outlet key={liga.currentMatchday} />
      </ContextoLigaCtx.Provider>
    </section>
  )
}
