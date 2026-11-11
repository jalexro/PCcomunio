import { almacenDeSesion } from './auth/almacen'

// En web, Vite proxifica /api al backend (base vacía). En la app Capacitor no hay proxy:
// definir VITE_API_URL (p. ej. http://192.168.1.10:8080) al construir para móvil.
const API_URL = import.meta.env.VITE_API_URL ?? ''

export type Position = 'PORTERO' | 'DEFENSA' | 'CENTROCAMPISTA' | 'DELANTERO'

export interface Team {
  id: number
  name: string
  shortName: string
  crestUrl: string | null
}

export type EstadoJugador = 'DISPONIBLE' | 'LESIONADO' | 'SANCIONADO' | 'DUDA'

export interface Player {
  id: number
  name: string
  position: Position
  status: EstadoJugador
  marketValue: number
  photoUrl: string | null
  teamId: number
  teamName: string
  teamShortName: string
}

export interface Usuario {
  id: number
  email: string
  username: string
  role: 'USER' | 'ADMIN'
  emailVerified: boolean
}

interface AuthResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: Usuario
}

/** Error de la API con el mensaje en castellano que envía el backend (y los errores por campo, si los hay). */
export class ApiError extends Error {
  readonly status: number
  readonly errores: Record<string, string>

  constructor(status: number, message: string, errores: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.errores = errores
  }
}

// --- Sesión -----------------------------------------------------------------------------------
// Los tokens los guarda auth/almacen.ts: localStorage en web y Capacitor Preferences en la app
// nativa (almacenamiento privado de la app, pero sin cifrar; está explicado allí).

interface Sesion {
  accessToken: string
  refreshToken: string
}

const oyentesDeCaducidad = new Set<() => void>()

function leerSesion(): Sesion | null {
  const texto = almacenDeSesion.leer()
  try {
    return texto ? (JSON.parse(texto) as Sesion) : null
  } catch {
    return null
  }
}

function guardarSesion(sesion: Sesion | null) {
  almacenDeSesion.escribir(sesion ? JSON.stringify(sesion) : null)
}

/**
 * Lee la sesión guardada al arrancar. Hay que esperarla antes de dar al usuario por no logueado,
 * porque en la app nativa el almacenamiento es asíncrono.
 */
export const inicializarSesion = () => almacenDeSesion.cargar()

export const haySesion = () => leerSesion() !== null

/** Avisa cuando la sesión caduca y no se puede renovar. Devuelve la función para dejar de escuchar. */
export function alCaducarLaSesion(oyente: () => void) {
  oyentesDeCaducidad.add(oyente)
  return () => {
    oyentesDeCaducidad.delete(oyente)
  }
}

// --- Peticiones -------------------------------------------------------------------------------

async function leerError(respuesta: Response): Promise<ApiError> {
  let cuerpo: { message?: string; errors?: Record<string, string> } = {}
  try {
    cuerpo = await respuesta.json()
  } catch {
    // Respuesta sin JSON (p. ej. un 401 de seguridad): se usa el mensaje genérico.
  }
  return new ApiError(respuesta.status, cuerpo.message ?? `Error ${respuesta.status}`, cuerpo.errors)
}

async function enviar(path: string, init: RequestInit, token: string | null): Promise<Response> {
  const cabeceras = new Headers(init.headers)
  if (init.body) cabeceras.set('Content-Type', 'application/json')
  if (token) cabeceras.set('Authorization', `Bearer ${token}`)
  try {
    return await fetch(`${API_URL}${path}`, { ...init, headers: cabeceras })
  } catch {
    throw new ApiError(0, 'No se pudo conectar con el servidor')
  }
}

// Una sola renovación a la vez: si varias peticiones reciben 401 a la vez, comparten el mismo refresh.
let renovando: Promise<boolean> | null = null

async function pedirRenovacion(): Promise<boolean> {
  const sesion = leerSesion()
  if (!sesion) return false
  try {
    const respuesta = await enviar(
      '/api/auth/refresh',
      { method: 'POST', body: JSON.stringify({ refreshToken: sesion.refreshToken }) },
      null,
    )
    if (!respuesta.ok) return false
    const datos = (await respuesta.json()) as AuthResponse
    guardarSesion({ accessToken: datos.accessToken, refreshToken: datos.refreshToken })
    return true
  } catch {
    return false
  }
}

function renovarSesion(): Promise<boolean> {
  // El .finally va encadenado a la promesa ya asignada: si estuviera dentro de la función async y esta
  // terminara de forma síncrona, limpiaría la variable antes de asignarla y quedaría memorizada para siempre.
  renovando ??= pedirRenovacion().finally(() => {
    renovando = null
  })
  return renovando
}

/** Petición autenticada: ante un 401 intenta renovar la sesión una vez y repite; si no puede, cierra la sesión. */
async function peticion<T>(path: string, init: RequestInit = {}): Promise<T> {
  let respuesta = await enviar(path, init, leerSesion()?.accessToken ?? null)

  if (respuesta.status === 401 && leerSesion()) {
    if (await renovarSesion()) {
      respuesta = await enviar(path, init, leerSesion()?.accessToken ?? null)
    } else {
      guardarSesion(null)
      oyentesDeCaducidad.forEach((oyente) => oyente())
    }
  }

  if (!respuesta.ok) throw await leerError(respuesta)
  return respuesta.status === 204 ? (undefined as T) : ((await respuesta.json()) as T)
}

// --- Autenticación ----------------------------------------------------------------------------

async function autenticar(path: string, datos: object): Promise<Usuario> {
  // Estas rutas son públicas: se envían sin token y no intentan renovar la sesión.
  const respuesta = await enviar(path, { method: 'POST', body: JSON.stringify(datos) }, null)
  if (!respuesta.ok) throw await leerError(respuesta)
  const auth = (await respuesta.json()) as AuthResponse
  guardarSesion({ accessToken: auth.accessToken, refreshToken: auth.refreshToken })
  return auth.user
}

export const registrar = (email: string, username: string, password: string) =>
  autenticar('/api/auth/register', { email, username, password })

export const iniciarSesion = (email: string, password: string) => autenticar('/api/auth/login', { email, password })

export async function cerrarSesion() {
  const sesion = leerSesion()
  guardarSesion(null)
  if (!sesion) return
  try {
    await enviar('/api/auth/logout', { method: 'POST', body: JSON.stringify({ refreshToken: sesion.refreshToken }) }, null)
  } catch {
    // Aunque el servidor no responda, la sesión local ya está cerrada.
  }
}

export const obtenerUsuario = () => peticion<Usuario>('/api/auth/me')

// --- Verificación de email y contraseña olvidada -----------------------------------------------

/**
 * Llamada pública: sin token, sin renovar la sesión y sin cuerpo que leer (el backend responde 204).
 * Los errores sí llevan JSON con `message`, así que llegan a la interfaz como ApiError: el 429 del
 * control de intentos, el 400 de un enlace caducado o el 403 del email sin confirmar.
 */
async function publica(path: string, datos: object): Promise<void> {
  const respuesta = await enviar(path, { method: 'POST', body: JSON.stringify(datos) }, null)
  if (!respuesta.ok) throw await leerError(respuesta)
}

export const verificarEmail = (token: string) => publica('/api/auth/verify', { token })

export const reenviarVerificacion = (email: string) => publica('/api/auth/verify/resend', { email })

export const pedirEnlaceDeContrasena = (email: string) => publica('/api/auth/password/forgot', { email })

export const restablecerContrasena = (token: string, password: string) =>
  publica('/api/auth/password/reset', { token, password })

// --- Ligas ------------------------------------------------------------------------------------

export type Formation = '4-3-3' | '5-3-2' | '4-4-2' | '3-5-2'
export const FORMACIONES: Formation[] = ['4-3-3', '5-3-2', '4-4-2', '3-5-2']

export type EstadoOferta = 'PENDIENTE' | 'ACEPTADA' | 'RECHAZADA' | 'CADUCADA' | 'PERDIDA' | 'ANULADA' | 'RETIRADA'

/** POR_TIEMPO: la jornada se juega al vencer el plazo. POR_CONFIRMACION: cuando confirman todos (o al vencer). */
export type ModoAvance = 'POR_TIEMPO' | 'POR_CONFIRMACION'

/**
 * Reparto que recibe quien entra en la liga. ALEATORIO: 11 jugadores al azar. SIN_JUGADORES: plantilla vacia,
 * hay que fichar en el mercado. ALEATORIO_CON_TOPE: 11 al azar cuya suma de valores no pasa del tope.
 */
export type ModalidadReparto = 'ALEATORIO' | 'SIN_JUGADORES' | 'ALEATORIO_CON_TOPE'

export interface Liga {
  id: number
  name: string
  joinCode: string
  season: number
  currentMatchday: number
  lastMatchday: number
  status: 'EN_CURSO' | 'FINALIZADA'
  advanceMode: ModoAvance
  intervalSeconds: number
  nextAdvanceAt: string
  members: number
  owner: string
  isOwner: boolean
  myMemberId: number
  myBudget: number
  myFormation: Formation
  pointValue: number
  positionBonuses: number[]
  /** Puntos que resta cada puesto de la alineación que se quede sin jugador. */
  emptySlotPenalty: number
  /** Reparto que recibe quien entra en la liga (el mismo para el dueño y para quien se une después). */
  initialSquadMode: ModalidadReparto
  initialBudget: number
  /** Tope de la suma de valores de los 11 iniciales; null salvo en ALEATORIO_CON_TOPE. */
  initialSquadCap: number | null
  iConfirmed: boolean
  /** A quién se espera en el modo por confirmación (vacío en el modo por tiempo). */
  pendingConfirmations: string[]
}

export interface Abono {
  position: number
  memberId: number
  username: string
  totalPayout: number
  totalFromPoints: number
  totalFromPosition: number
  lastMatchdayPosition: number | null
  lastMatchdayPayout: number | null
  me: boolean
}

export interface Clasificacion {
  position: number
  memberId: number
  username: string
  totalPoints: number
  lastMatchdayPoints: number | null
  lastMatchdayEmptySlots: number | null
  lineupConfirmed: boolean
  me: boolean
}

export interface JugadorPlantilla {
  playerId: number
  name: string
  position: Position
  teamName: string
  teamShortName: string
  value: number
  starter: boolean
  pricePaid: number
  lastMatchdayPoints: number | null
  status: EstadoJugador
}

export interface Plantilla {
  memberId: number
  username: string
  formation: Formation
  budget: number
  maxSize: number
  players: JugadorPlantilla[]
}

export interface JugadorMercado {
  playerId: number
  name: string
  position: Position
  teamName: string
  teamShortName: string
  value: number
  minBid: number
  maxBid: number
  myBid: number | null
  status: EstadoJugador
}

export interface Oferta {
  id: number
  playerId: number
  playerName: string
  position: Position
  teamShortName: string
  value: number
  bidder: string
  seller: string | null
  amount: number
  status: EstadoOferta
  createdAt: string
  sent: boolean
  received: boolean
}

const json = (metodo: string, cuerpo?: object): RequestInit => ({
  method: metodo,
  ...(cuerpo ? { body: JSON.stringify(cuerpo) } : {}),
})

export const misLigas = () => peticion<Liga[]>('/api/leagues')
/** squadCap solo se admite (y es obligatorio) en la modalidad ALEATORIO_CON_TOPE. */
export const crearLiga = (
  name: string,
  initialSquadMode: ModalidadReparto,
  initialBudget: number,
  squadCap: number | null,
) => peticion<Liga>('/api/leagues', json('POST', { name, initialSquadMode, initialBudget, squadCap }))
export const unirseLiga = (code: string) => peticion<Liga>('/api/leagues/join', json('POST', { code }))
export const getLiga = (id: number) => peticion<Liga>(`/api/leagues/${id}`)
export const getClasificacion = (id: number) => peticion<Clasificacion[]>(`/api/leagues/${id}/standings`)
export const avanzarJornada = (id: number) => peticion<Liga>(`/api/leagues/${id}/advance`, json('POST'))
export const cambiarIntervalo = (id: number, seconds: number) =>
  peticion<Liga>(`/api/leagues/${id}`, json('PATCH', { seconds }))

export const cambiarOpciones = (
  id: number,
  advanceMode: ModoAvance,
  pointValue: number,
  positionBonuses: number[],
) => peticion<Liga>(`/api/leagues/${id}/options`, json('PUT', { advanceMode, pointValue, positionBonuses }))
export const expulsarMiembro = (id: number, memberId: number) =>
  peticion<void>(`/api/leagues/${id}/members/${memberId}`, json('DELETE'))
export const getAbonos = (id: number) => peticion<Abono[]>(`/api/leagues/${id}/payouts`)

export const getMiPlantilla = (id: number) => peticion<Plantilla>(`/api/leagues/${id}/squad`)
export const getPlantillaRival = (id: number, memberId: number) =>
  peticion<Plantilla>(`/api/leagues/${id}/members/${memberId}/squad`)
export const alinear = (id: number, formation: Formation, starterIds: number[]) =>
  peticion<Plantilla>(`/api/leagues/${id}/lineup`, json('PUT', { formation, starterIds }))
/** Da por buena la alineación actual para la jornada que viene. */
export const confirmarAlineacion = (id: number) =>
  peticion<Plantilla>(`/api/leagues/${id}/lineup/confirm`, json('POST'))

export const venderJugador = (id: number, playerId: number) =>
  peticion<Plantilla>(`/api/leagues/${id}/squad/${playerId}/sell`, json('POST'))

export const getMercado = (id: number) => peticion<JugadorMercado[]>(`/api/leagues/${id}/market`)
export const pujar = (id: number, playerId: number, amount: number) =>
  peticion<Oferta>(`/api/leagues/${id}/market/${playerId}/bid`, json('PUT', { amount }))
export const getOfertas = (id: number) => peticion<Oferta[]>(`/api/leagues/${id}/offers`)
export const ofertarDirecta = (id: number, playerId: number, amount: number) =>
  peticion<Oferta>(`/api/leagues/${id}/offers`, json('POST', { playerId, amount }))
export const aceptarOferta = (id: number, offerId: number) =>
  peticion<Oferta>(`/api/leagues/${id}/offers/${offerId}/accept`, json('POST'))
export const rechazarOferta = (id: number, offerId: number) =>
  peticion<Oferta>(`/api/leagues/${id}/offers/${offerId}/reject`, json('POST'))
export const retirarOferta = (id: number, offerId: number) =>
  peticion<Oferta>(`/api/leagues/${id}/offers/${offerId}/cancel`, json('POST'))

// --- Datos ------------------------------------------------------------------------------------

export const getTeams = () => peticion<Team[]>('/api/teams')

export function getPlayers(filtros: { teamId?: number; position?: Position } = {}) {
  const params = new URLSearchParams()
  if (filtros.teamId !== undefined) params.set('teamId', String(filtros.teamId))
  if (filtros.position !== undefined) params.set('position', filtros.position)
  const query = params.toString()
  return peticion<Player[]>(`/api/players${query ? `?${query}` : ''}`)
}
