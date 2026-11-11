import { createContext, useContext, useEffect, useState } from 'react'
import { ApiError } from '../api'
import type { Liga } from '../api'

/** Lo que la pantalla de una liga comparte con sus pestañas. */
export interface ContextoLiga {
  liga: Liga
  recargar: () => Promise<void>
}

export const ContextoLigaCtx = createContext<ContextoLiga | null>(null)

export function useLiga(): ContextoLiga {
  const contexto = useContext(ContextoLigaCtx)
  if (!contexto) throw new Error('useLiga debe usarse dentro de la pantalla de una liga')
  return contexto
}

export function mensajeDeError(e: unknown): string {
  if (e instanceof ApiError) {
    const campos = Object.entries(e.errores).map(([campo, mensaje]) => `${campo}: ${mensaje}`)
    return campos.length > 0 ? campos.join('. ') : e.message
  }
  return 'Ha ocurrido un error inesperado'
}

/** Hora actual que se refresca cada segundo (para cuentas atrás). */
export function useAhora(cadaMs = 1000) {
  const [ahora, setAhora] = useState(() => Date.now())
  useEffect(() => {
    const temporizador = setInterval(() => setAhora(Date.now()), cadaMs)
    return () => clearInterval(temporizador)
  }, [cadaMs])
  return ahora
}
