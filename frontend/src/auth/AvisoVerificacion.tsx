import { useState } from 'react'
import { ApiError, reenviarVerificacion } from '../api'
import { useAuth } from './contexto'

/**
 * Recordatorio para quien acaba de registrarse (o aún no ha confirmado el email). Aparece dentro de
 * la aplicación porque el registro ya deja la sesión abierta; desaparece cuando el email se confirma
 * y se vuelve a iniciar sesión (o se recarga: /api/auth/me trae el dato al día).
 */
export function AvisoVerificacion() {
  const { usuario } = useAuth()
  const [estado, setEstado] = useState<'inicial' | 'enviando' | 'enviado' | 'error'>('inicial')
  const [error, setError] = useState('')

  if (!usuario || usuario.emailVerified) return null

  async function reenviar() {
    if (!usuario) return
    setEstado('enviando')
    try {
      await reenviarVerificacion(usuario.email)
      setEstado('enviado')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Ha ocurrido un error inesperado')
      setEstado('error')
    }
  }

  return (
    <div className="aviso" role="status">
      <p>
        Te hemos enviado un correo a <strong>{usuario.email}</strong>: confirma tu dirección abriendo el enlace.
      </p>
      {estado === 'enviado' ? (
        <p>Correo reenviado. Revisa también la carpeta de spam.</p>
      ) : (
        <button type="button" disabled={estado === 'enviando'} onClick={() => void reenviar()}>
          {estado === 'enviando' ? 'Enviando...' : 'Reenviar el correo'}
        </button>
      )}
      {estado === 'error' && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}
