import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ApiError, restablecerContrasena } from '../api'

/**
 * Página a la que llega el enlace del correo: {FRONTEND_URL}/restablecer?token=...
 * Es pública: quien ha olvidado la contraseña no puede iniciar sesión.
 */
export function RestablecerPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''

  const [password, setPassword] = useState('')
  const [repetida, setRepetida] = useState('')
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [listo, setListo] = useState(false)

  async function enviar(evento: FormEvent) {
    evento.preventDefault()
    setError('')
    if (password !== repetida) {
      // Se comprueba aquí para no gastar el token con una petición que va a fallar seguro.
      setError('Las dos contraseñas no coinciden')
      return
    }
    setEnviando(true)
    try {
      await restablecerContrasena(token, password)
      setListo(true)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Ha ocurrido un error inesperado')
    } finally {
      setEnviando(false)
    }
  }

  if (!token) {
    return (
      <main className="auth">
        <h1>LigaComunio</h1>
        <h2>Nueva contraseña</h2>
        <div className="error" role="alert">
          <p>El enlace no es válido: le falta el token. Pide otro desde la pantalla de inicio de sesión.</p>
        </div>
        <Link to="/">Volver al inicio</Link>
      </main>
    )
  }

  if (listo) {
    return (
      <main className="auth">
        <h1>LigaComunio</h1>
        <h2>Nueva contraseña</h2>
        <p>Contraseña cambiada. Se han cerrado las sesiones que hubiera abiertas; entra con la contraseña nueva.</p>
        <Link to="/">Ir a iniciar sesión</Link>
      </main>
    )
  }

  return (
    <main className="auth">
      <h1>LigaComunio</h1>
      <h2>Nueva contraseña</h2>

      <form onSubmit={enviar}>
        <label>
          Contraseña nueva
          <input
            type="password"
            autoComplete="new-password"
            required
            minLength={8}
            maxLength={72}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        <small>Entre 8 y 72 caracteres.</small>

        <label>
          Repite la contraseña
          <input
            type="password"
            autoComplete="new-password"
            required
            minLength={8}
            maxLength={72}
            value={repetida}
            onChange={(e) => setRepetida(e.target.value)}
          />
        </label>

        {error && (
          <div className="error" role="alert">
            <p>{error}</p>
          </div>
        )}

        <button type="submit" disabled={enviando}>
          {enviando ? 'Guardando...' : 'Guardar la contraseña'}
        </button>
      </form>

      <Link to="/">Volver al inicio</Link>
    </main>
  )
}
