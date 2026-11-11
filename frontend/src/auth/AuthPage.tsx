import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, pedirEnlaceDeContrasena, reenviarVerificacion } from '../api'
import { useAuth } from './contexto'

type Modo = 'entrar' | 'registro' | 'olvidada'

const ETIQUETAS: Record<string, string> = { email: 'Email', username: 'Usuario', password: 'Contraseña' }

export function AuthPage() {
  const { iniciarSesion, registrar } = useAuth()
  const [modo, setModo] = useState<Modo>('entrar')
  const [email, setEmail] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [errores, setErrores] = useState<string[]>([])
  const [aviso, setAviso] = useState('')
  // El backend responde 403 cuando la contraseña es correcta pero el email está sin confirmar.
  const [sinVerificar, setSinVerificar] = useState(false)
  const [enviando, setEnviando] = useState(false)

  const esRegistro = modo === 'registro'
  const esOlvidada = modo === 'olvidada'

  function cambiarModo(nuevo: Modo) {
    setModo(nuevo)
    setErrores([])
    setAviso('')
    setSinVerificar(false)
  }

  function mostrarError(e: unknown) {
    if (e instanceof ApiError) {
      const porCampo = Object.entries(e.errores).map(([campo, mensaje]) => `${ETIQUETAS[campo] ?? campo}: ${mensaje}`)
      setErrores(porCampo.length > 0 ? porCampo : [e.message])
      if (e.status === 403) setSinVerificar(true)
    } else {
      setErrores(['Ha ocurrido un error inesperado'])
    }
  }

  async function enviar(evento: FormEvent) {
    evento.preventDefault()
    setErrores([])
    setAviso('')
    setSinVerificar(false)
    setEnviando(true)
    try {
      if (esOlvidada) {
        await pedirEnlaceDeContrasena(email)
        // Mismo mensaje exista la cuenta o no: el backend responde 204 en los dos casos para no
        // revelar quién está registrado.
        setAviso('Si esa dirección está registrada, te hemos enviado un correo con un enlace para cambiar la contraseña.')
      } else if (esRegistro) {
        await registrar(email, username, password)
      } else {
        await iniciarSesion(email, password)
      }
    } catch (e) {
      mostrarError(e)
    } finally {
      setEnviando(false)
    }
  }

  async function reenviar() {
    setErrores([])
    setAviso('')
    setEnviando(true)
    try {
      await reenviarVerificacion(email)
      setAviso('Si esa dirección está registrada y sin confirmar, te hemos enviado un correo nuevo.')
      setSinVerificar(false)
    } catch (e) {
      mostrarError(e)
    } finally {
      setEnviando(false)
    }
  }

  const titulo = esRegistro ? 'Crear cuenta' : esOlvidada ? 'Recuperar la contraseña' : 'Iniciar sesión'
  const textoDelBoton = esRegistro ? 'Registrarme' : esOlvidada ? 'Enviarme el enlace' : 'Entrar'

  return (
    <main className="auth">
      <h1>LigaComunio</h1>
      <h2>{titulo}</h2>

      {esOlvidada && <p>Te enviamos un enlace para elegir una contraseña nueva.</p>}

      <form onSubmit={enviar}>
        <label>
          Email
          <input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>

        {esRegistro && (
          <label>
            Usuario
            <input
              type="text"
              autoComplete="username"
              required
              minLength={3}
              maxLength={20}
              pattern="[A-Za-z0-9_]+"
              title="Letras, números o guion bajo (3 a 20 caracteres)"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
          </label>
        )}

        {!esOlvidada && (
          <label>
            Contraseña
            <input
              type="password"
              autoComplete={esRegistro ? 'new-password' : 'current-password'}
              required
              minLength={esRegistro ? 8 : undefined}
              maxLength={72}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </label>
        )}
        {esRegistro && <small>Entre 8 y 72 caracteres. Después tendrás que confirmar tu email.</small>}

        {errores.length > 0 && (
          <div className="error" role="alert">
            {errores.map((mensaje) => (
              <p key={mensaje}>{mensaje}</p>
            ))}
          </div>
        )}

        {aviso && <p className="aviso">{aviso}</p>}

        <button type="submit" disabled={enviando}>
          {enviando ? 'Enviando...' : textoDelBoton}
        </button>
      </form>

      {sinVerificar && (
        <button type="button" disabled={enviando} onClick={() => void reenviar()}>
          Reenviarme el correo de confirmación
        </button>
      )}

      {esOlvidada ? (
        <button type="button" className="enlace" onClick={() => cambiarModo('entrar')}>
          Volver al inicio de sesión
        </button>
      ) : (
        <>
          <button type="button" className="enlace" onClick={() => cambiarModo('olvidada')}>
            ¿Has olvidado la contraseña?
          </button>
          <button type="button" className="enlace" onClick={() => cambiarModo(esRegistro ? 'entrar' : 'registro')}>
            {esRegistro ? '¿Ya tienes cuenta? Inicia sesión' : '¿No tienes cuenta? Regístrate'}
          </button>
        </>
      )}
    </main>
  )
}
