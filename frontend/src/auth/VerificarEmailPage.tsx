import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ApiError, reenviarVerificacion, verificarEmail } from '../api'

type Estado = 'comprobando' | 'ok' | 'error'

/**
 * Página a la que llega el enlace del correo: {FRONTEND_URL}/verificar-email?token=...
 * Es pública, porque lo normal es abrirla sin haber iniciado sesión.
 */
export function VerificarEmailPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''

  const [estado, setEstado] = useState<Estado>(token ? 'comprobando' : 'error')
  const [error, setError] = useState(token ? '' : 'El enlace no es válido: le falta el token.')
  // El token solo sirve una vez, así que no se puede gastar dos veces (StrictMode repite los efectos).
  const pedido = useRef(false)

  useEffect(() => {
    if (!token || pedido.current) return
    pedido.current = true
    verificarEmail(token)
      .then(() => setEstado('ok'))
      .catch((e: unknown) => {
        setError(e instanceof ApiError ? e.message : 'Ha ocurrido un error inesperado')
        setEstado('error')
      })
  }, [token])

  return (
    <main className="auth">
      <h1>LigaComunio</h1>
      <h2>Confirmar el email</h2>

      {estado === 'comprobando' && <p>Comprobando el enlace...</p>}

      {estado === 'ok' && (
        <>
          <p>Tu dirección de correo está confirmada: ya puedes iniciar sesión.</p>
          <Link to="/">Ir a iniciar sesión</Link>
        </>
      )}

      {estado === 'error' && (
        <>
          <div className="error" role="alert">
            <p>{error}</p>
          </div>
          <OtroEnlace />
          <Link to="/">Volver al inicio</Link>
        </>
      )}
    </main>
  )
}

/** Si el enlace ya no vale, se puede pedir otro dando el email. */
function OtroEnlace() {
  const [email, setEmail] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [aviso, setAviso] = useState('')
  const [error, setError] = useState('')

  async function enviar(evento: FormEvent) {
    evento.preventDefault()
    setAviso('')
    setError('')
    setEnviando(true)
    try {
      await reenviarVerificacion(email)
      // El backend responde igual exista la cuenta o no, así que el mensaje no afirma que se haya enviado.
      setAviso('Si esa dirección está registrada y sin confirmar, te hemos enviado un correo nuevo.')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Ha ocurrido un error inesperado')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <form onSubmit={enviar}>
      <label>
        Pedir otro enlace de confirmación
        <input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
      </label>

      {aviso && <p className="aviso">{aviso}</p>}
      {error && (
        <div className="error" role="alert">
          <p>{error}</p>
        </div>
      )}

      <button type="submit" disabled={enviando}>
        {enviando ? 'Enviando...' : 'Enviarme otro enlace'}
      </button>
    </form>
  )
}
