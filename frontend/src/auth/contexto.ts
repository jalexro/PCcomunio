import { createContext, useContext } from 'react'
import type { Usuario } from '../api'

export interface AuthContextValue {
  usuario: Usuario | null
  /** true mientras se comprueba si la sesión guardada sigue siendo válida. */
  cargando: boolean
  iniciarSesion: (email: string, password: string) => Promise<void>
  registrar: (email: string, username: string, password: string) => Promise<void>
  cerrarSesion: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const contexto = useContext(AuthContext)
  if (!contexto) throw new Error('useAuth debe usarse dentro de <AuthProvider>')
  return contexto
}
