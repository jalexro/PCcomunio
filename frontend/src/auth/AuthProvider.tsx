import { useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import * as api from '../api'
import type { Usuario } from '../api'
import { AuthContext } from './contexto'
import type { AuthContextValue } from './contexto'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<Usuario | null>(null)
  const [cargando, setCargando] = useState(true)

  useEffect(() => {
    let vivo = true
    // En la app nativa la sesión se guarda con Capacitor Preferences, que es asíncrono: hay que
    // esperar a tenerla cargada antes de dar al usuario por no logueado. Si había sesión guardada,
    // además se comprueba contra el servidor.
    api
      .inicializarSesion()
      .then(() => (api.haySesion() ? api.obtenerUsuario() : null))
      .then((encontrado) => {
        if (vivo) setUsuario(encontrado)
      })
      .catch(() => {
        if (vivo) setUsuario(null)
      })
      .finally(() => {
        if (vivo) setCargando(false)
      })

    const dejarDeEscuchar = api.alCaducarLaSesion(() => setUsuario(null))
    return () => {
      vivo = false
      dejarDeEscuchar()
    }
  }, [])

  const valor = useMemo<AuthContextValue>(
    () => ({
      usuario,
      cargando,
      iniciarSesion: async (email, password) => setUsuario(await api.iniciarSesion(email, password)),
      registrar: async (email, username, password) => setUsuario(await api.registrar(email, username, password)),
      cerrarSesion: async () => {
        await api.cerrarSesion()
        setUsuario(null)
      },
    }),
    [usuario, cargando],
  )

  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>
}
