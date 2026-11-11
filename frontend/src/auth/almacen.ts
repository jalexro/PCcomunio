import { Capacitor } from '@capacitor/core'
import { Preferences } from '@capacitor/preferences'

/**
 * Dónde se guardan los tokens de la sesión.
 *
 * - En web: `localStorage`, como siempre.
 * - En la app nativa (Android): `Preferences`, que en Android es `SharedPreferences` dentro del
 *   almacenamiento privado de la app. Qué mejora: ya no vive en el `localStorage` del WebView, así
 *   que otras apps no pueden leerlo y no se borra al limpiar los datos del navegador. Qué NO mejora:
 *   **no está cifrado**. En un móvil con root o con una copia de seguridad del directorio de la app
 *   los tokens se leen en claro. El cifrado de verdad (EncryptedSharedPreferences en Android,
 *   Keychain en iOS) necesitaría un plugin de terceros, que está sin decidir.
 *
 * El API de `Preferences` es asíncrono y el resto del código espera lecturas sincrónicas, así que se
 * mantiene una copia en memoria: {@link cargar} la rellena una vez al arrancar y {@link escribir} la
 * actualiza al momento y persiste en segundo plano.
 */
const CLAVE = 'ligacomunio.sesion'

const nativo = Capacitor.isNativePlatform()

let enMemoria: string | null = null
let cargado = false

function leerDeLocalStorage(): string | null {
  try {
    return localStorage.getItem(CLAVE)
  } catch {
    // Sin almacenamiento disponible (modo privado, etc.).
    return null
  }
}

/** Carga la sesión guardada. Hay que esperarla antes de decidir si el usuario tiene sesión. */
async function cargar(): Promise<void> {
  if (cargado) return
  if (nativo) {
    try {
      const { value } = await Preferences.get({ key: CLAVE })
      enMemoria = value ?? null
    } catch {
      enMemoria = null
    }
  } else {
    enMemoria = leerDeLocalStorage()
  }
  cargado = true
}

/** La sesión guardada, ya en memoria. En web funciona incluso antes de {@link cargar}. */
function leer(): string | null {
  if (!cargado && !nativo) {
    enMemoria = leerDeLocalStorage()
    cargado = true
  }
  return enMemoria
}

/** Guarda (o borra con `null`) la sesión. La copia en memoria queda al día sin esperar al disco. */
function escribir(texto: string | null): void {
  enMemoria = texto
  cargado = true
  if (nativo) {
    const operacion = texto === null ? Preferences.remove({ key: CLAVE }) : Preferences.set({ key: CLAVE, value: texto })
    // Si el disco falla, la sesión dura al menos lo que dure la app abierta.
    void operacion.catch(() => undefined)
    return
  }
  try {
    if (texto === null) localStorage.removeItem(CLAVE)
    else localStorage.setItem(CLAVE, texto)
  } catch {
    // Sin almacenamiento: la sesión solo dura mientras la pestaña esté abierta.
  }
}

export const almacenDeSesion = { cargar, leer, escribir }
