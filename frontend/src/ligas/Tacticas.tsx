import { useCallback, useEffect, useMemo, useState } from 'react'
import { alinear, confirmarAlineacion, FORMACIONES, getMiPlantilla } from '../api'
import type { Formation, JugadorPlantilla, Plantilla, Position } from '../api'
import { ABREVIATURA_POSICION, euros, puntos } from '../format'
import { EstadoJugador } from './EstadoJugador'
import { mensajeDeError, useLiga } from './ayudas'

/** Cuántos jugadores de cada línea exige cada táctica (el portero es siempre 1). */
const ESQUEMA: Record<Formation, Record<Position, number>> = {
  '4-3-3': { PORTERO: 1, DEFENSA: 4, CENTROCAMPISTA: 3, DELANTERO: 3 },
  '5-3-2': { PORTERO: 1, DEFENSA: 5, CENTROCAMPISTA: 3, DELANTERO: 2 },
  '4-4-2': { PORTERO: 1, DEFENSA: 4, CENTROCAMPISTA: 4, DELANTERO: 2 },
  '3-5-2': { PORTERO: 1, DEFENSA: 3, CENTROCAMPISTA: 5, DELANTERO: 2 },
}

/** De arriba abajo en el campo: los delanteros al fondo del ataque y el portero en su área. */
const LINEAS: Position[] = ['DELANTERO', 'CENTROCAMPISTA', 'DEFENSA', 'PORTERO']

const TITULARES = 11

/** Identificador de un puesto del campo: la línea más el número de hueco dentro de ella. */
type Hueco = string
const hueco = (posicion: Position, indice: number): Hueco => `${posicion}-${indice}`
const posicionDelHueco = (h: Hueco) => h.split('-')[0] as Position

type Alineacion = Record<Hueco, number | null>

/** Huecos vacíos de una táctica, en orden de campo. */
function huecosDe(formacion: Formation): Hueco[] {
  const esquema = ESQUEMA[formacion]
  return LINEAS.flatMap((linea) => Array.from({ length: esquema[linea] }, (_, i) => hueco(linea, i)))
}

/**
 * Coloca a los jugadores dados en los huecos de la táctica, cada uno en una plaza de su posición y por
 * orden. Los que no caben se quedan fuera (al banquillo). Sirve tanto para cargar la alineación guardada
 * como para rehacerla al cambiar de táctica sin perder a quien sí encaja.
 */
function colocar(formacion: Formation, jugadores: JugadorPlantilla[]): Alineacion {
  const alineacion: Alineacion = {}
  huecosDe(formacion).forEach((h) => (alineacion[h] = null))
  const porPosicion = new Map<Position, number>()
  for (const jugador of jugadores) {
    const siguiente = porPosicion.get(jugador.position) ?? 0
    const destino = hueco(jugador.position, siguiente)
    if (destino in alineacion) {
      alineacion[destino] = jugador.playerId
      porPosicion.set(jugador.position, siguiente + 1)
    }
  }
  return alineacion
}

/**
 * Táctica: el campo con los once puestos y, al lado, el banquillo. Se puede arrastrar un jugador a un
 * puesto o, si el arrastre no va bien (pantallas táctiles), tocar al jugador y después el puesto. Un
 * puesto solo acepta jugadores de su posición.
 */
export function Tacticas() {
  const { liga, recargar } = useLiga()
  const [plantilla, setPlantilla] = useState<Plantilla | null>(null)
  const [formacion, setFormacion] = useState<Formation>('4-4-2')
  const [alineacion, setAlineacion] = useState<Alineacion>({})
  const [seleccionado, setSeleccionado] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  const aplicar = useCallback((p: Plantilla) => {
    setPlantilla(p)
    setFormacion(p.formation)
    setAlineacion(colocar(p.formation, p.players.filter((j) => j.starter)))
    setSeleccionado(null)
  }, [])

  useEffect(() => {
    getMiPlantilla(liga.id)
      .then(aplicar)
      .catch((e: unknown) => setError(mensajeDeError(e)))
  }, [liga.id, aplicar])

  const porId = useMemo(() => {
    const mapa = new Map<number, JugadorPlantilla>()
    plantilla?.players.forEach((j) => mapa.set(j.playerId, j))
    return mapa
  }, [plantilla])

  if (error && !plantilla) return <p className="error">{error}</p>
  if (!plantilla) return <p>Cargando...</p>

  const terminada = liga.status === 'FINALIZADA'
  const enCampo = new Set(Object.values(alineacion).filter((id): id is number => id !== null))
  const banquillo = plantilla.players.filter((j) => !enCampo.has(j.playerId))
  const colocados = enCampo.size
  const completa = colocados === TITULARES
  const titularesGuardados = plantilla.players.filter((j) => j.starter).length
  const huecosGuardados = Math.max(0, TITULARES - titularesGuardados)
  const porConfirmacion = liga.advanceMode === 'POR_CONFIRMACION'

  /** Cambiar de táctica conserva a quien encaje en la nueva y manda al banquillo al resto. */
  function cambiarFormacion(nueva: Formation) {
    const enCampoAhora = Object.values(alineacion)
      .filter((id): id is number => id !== null)
      .map((id) => porId.get(id))
      .filter((j): j is JugadorPlantilla => j !== undefined)
    setFormacion(nueva)
    setAlineacion(colocar(nueva, enCampoAhora))
    setSeleccionado(null)
    setAviso(null)
  }

  /** Mete a un jugador en un puesto. Si el puesto estaba ocupado, los dos se intercambian. */
  function colocarEn(destino: Hueco, playerId: number) {
    const jugador = porId.get(playerId)
    if (!jugador) return
    if (jugador.position !== posicionDelHueco(destino)) {
      setAviso(null)
      setError(
        `${jugador.name} es ${ABREVIATURA_POSICION[jugador.position]} y ese puesto es de ` +
          `${ABREVIATURA_POSICION[posicionDelHueco(destino)]}.`,
      )
      return
    }
    setError(null)
    setAviso(null)
    setAlineacion((actual) => {
      const nueva = { ...actual }
      const origen = Object.keys(nueva).find((h) => nueva[h] === playerId)
      const desplazado = nueva[destino]
      nueva[destino] = playerId
      // Si venía de otro puesto, el que estaba allí ocupa el que deja libre (intercambio).
      if (origen) nueva[origen] = desplazado
      return nueva
    })
    setSeleccionado(null)
  }

  /** Saca a un jugador del campo. */
  function alBanquillo(playerId: number) {
    setError(null)
    setAlineacion((actual) => {
      const nueva = { ...actual }
      const origen = Object.keys(nueva).find((h) => nueva[h] === playerId)
      if (origen) nueva[origen] = null
      return nueva
    })
    setSeleccionado(null)
  }

  /** Un toque: elige al jugador; el siguiente toque en un puesto lo coloca ahí. */
  function tocar(playerId: number) {
    if (terminada) return
    if (seleccionado === playerId) {
      setSeleccionado(null)
      return
    }
    setSeleccionado(playerId)
    setError(null)
  }

  function tocarHueco(h: Hueco) {
    if (terminada) return
    if (seleccionado !== null) {
      colocarEn(h, seleccionado)
      return
    }
    const ocupante = alineacion[h]
    if (ocupante !== null && ocupante !== undefined) setSeleccionado(ocupante)
  }

  async function guardar() {
    setError(null)
    setAviso(null)
    setGuardando(true)
    try {
      aplicar(await alinear(liga.id, formacion, [...enCampo]))
      await recargar()
      setAviso('Alineación guardada')
    } catch (e) {
      setError(mensajeDeError(e))
    } finally {
      setGuardando(false)
    }
  }

  async function confirmar() {
    setError(null)
    setAviso(null)
    setGuardando(true)
    try {
      aplicar(await confirmarAlineacion(liga.id))
      await recargar()
      setAviso('Alineación confirmada')
    } catch (e) {
      setError(mensajeDeError(e))
    } finally {
      setGuardando(false)
    }
  }

  if (plantilla.players.length === 0) {
    return (
      <div>
        <p className="aviso">
          Todavía no tienes jugadores. Ficha en el <strong>Mercado</strong> para poder alinear a once: cada puesto
          vacío resta <strong>{liga.emptySlotPenalty} puntos</strong> en la jornada.
        </p>
      </div>
    )
  }

  return (
    <div>
      <div className="filtros">
        <label>
          Táctica{' '}
          <select
            value={formacion}
            onChange={(e) => cambiarFormacion(e.target.value as Formation)}
            disabled={terminada}
          >
            {FORMACIONES.map((f) => (
              <option key={f} value={f}>
                {f}
              </option>
            ))}
          </select>
        </label>
        <span className={completa ? 'bien' : 'mal'}>
          {colocados}/{TITULARES} puestos cubiertos
        </span>
        <button type="button" onClick={() => void guardar()} disabled={!completa || terminada || guardando}>
          Guardar alineación
        </button>
        {porConfirmacion && (
          <button type="button" onClick={() => void confirmar()} disabled={terminada || guardando || liga.iConfirmed}>
            {liga.iConfirmed ? 'Alineación confirmada' : 'Confirmar alineación'}
          </button>
        )}
      </div>

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {aviso && <p className="bien">{aviso}</p>}
      {huecosGuardados > 0 && !terminada && (
        <p className="aviso">
          La alineación guardada tiene {huecosGuardados} {huecosGuardados === 1 ? 'puesto' : 'puestos'} sin cubrir:
          eso resta <strong>{huecosGuardados * liga.emptySlotPenalty} puntos</strong> en la próxima jornada.
        </p>
      )}

      <p className="ayuda-tactica">
        Arrastra un jugador del banquillo a un puesto del campo, o tócalo y después toca el puesto. Cada puesto solo
        acepta jugadores de su posición. Arrastra fuera del campo (o al banquillo) para quitar a alguien.
      </p>

      <div className="tactica">
        <div
          className="campo"
          onDragOver={(e) => e.preventDefault()}
          onDrop={(e) => {
            // Soltar en el césped, fuera de un puesto, no hace nada: evita perder al jugador sin querer.
            e.preventDefault()
          }}
        >
          {LINEAS.map((linea) => (
            <div className="linea" key={linea}>
              {Array.from({ length: ESQUEMA[formacion][linea] }, (_, i) => {
                const h = hueco(linea, i)
                const ocupante = alineacion[h] ?? null
                const jugador = ocupante === null ? undefined : porId.get(ocupante)
                return (
                  <button
                    type="button"
                    key={h}
                    className={`puesto${jugador ? ' ocupado' : ''}${
                      seleccionado !== null && seleccionado === ocupante ? ' elegido' : ''
                    }`}
                    draggable={!terminada && jugador !== undefined}
                    onDragStart={(e) => {
                      if (ocupante !== null) e.dataTransfer.setData('text/plain', String(ocupante))
                    }}
                    onDragOver={(e) => e.preventDefault()}
                    onDrop={(e) => {
                      e.preventDefault()
                      const id = Number(e.dataTransfer.getData('text/plain'))
                      if (!Number.isNaN(id) && id > 0) colocarEn(h, id)
                    }}
                    onClick={() => tocarHueco(h)}
                    disabled={terminada}
                    aria-label={
                      jugador
                        ? `${ABREVIATURA_POSICION[linea]}: ${jugador.name}. Quitar o cambiar de puesto`
                        : `Puesto libre de ${ABREVIATURA_POSICION[linea]}`
                    }
                  >
                    {jugador ? (
                      <>
                        <span className="puesto-nombre">{jugador.name}</span>
                        <span className="puesto-datos">
                          {jugador.teamShortName} · {puntos(jugador.lastMatchdayPoints)}
                        </span>
                      </>
                    ) : (
                      <span className="puesto-vacio">{ABREVIATURA_POSICION[linea]}</span>
                    )}
                  </button>
                )
              })}
            </div>
          ))}
        </div>

        <div
          className="banquillo"
          onDragOver={(e) => e.preventDefault()}
          onDrop={(e) => {
            e.preventDefault()
            const id = Number(e.dataTransfer.getData('text/plain'))
            if (!Number.isNaN(id) && id > 0) alBanquillo(id)
          }}
        >
          <h3>Banquillo ({banquillo.length})</h3>
          {banquillo.length === 0 ? (
            <p>Todos tus jugadores están en el campo.</p>
          ) : (
            <ul>
              {banquillo.map((j) => (
                <li key={j.playerId}>
                  <button
                    type="button"
                    className={`suplente${seleccionado === j.playerId ? ' elegido' : ''}`}
                    draggable={!terminada}
                    onDragStart={(e) => e.dataTransfer.setData('text/plain', String(j.playerId))}
                    onClick={() => tocar(j.playerId)}
                    disabled={terminada}
                    aria-label={`${ABREVIATURA_POSICION[j.position]} ${j.name}. Elegir para alinear`}
                  >
                    <span className="suplente-pos">{ABREVIATURA_POSICION[j.position]}</span>
                    <span className="suplente-nombre">
                      {j.name}{' '}
                      <EstadoJugador
                        estado={j.status}
                        noJugo={liga.currentMatchday > 0 && j.lastMatchdayPoints === null}
                      />
                    </span>
                    <span className="suplente-datos">
                      {j.teamShortName} · {euros(j.value)} · {puntos(j.lastMatchdayPoints)}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  )
}
