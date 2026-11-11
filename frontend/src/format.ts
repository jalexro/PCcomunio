const formatoEuros = new Intl.NumberFormat('es-ES', {
  style: 'currency',
  currency: 'EUR',
  maximumFractionDigits: 0,
})

export const euros = (cantidad: number) => formatoEuros.format(cantidad)

export const puntos = (valor: number | null | undefined) =>
  valor === null || valor === undefined ? '-' : valor.toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/** 93784000 ms -> "1 d 02 h 03 m 04 s". */
export function cuentaAtras(milisegundos: number) {
  if (milisegundos <= 0) return 'ahora'
  const total = Math.floor(milisegundos / 1000)
  const dias = Math.floor(total / 86400)
  const horas = Math.floor((total % 86400) / 3600)
  const minutos = Math.floor((total % 3600) / 60)
  const segundos = total % 60
  const dos = (n: number) => String(n).padStart(2, '0')
  return `${dias > 0 ? `${dias} d ` : ''}${dos(horas)} h ${dos(minutos)} m ${dos(segundos)} s`
}

export function duracion(segundos: number) {
  if (segundos % 86400 === 0) return `${segundos / 86400} d`
  if (segundos % 3600 === 0) return `${segundos / 3600} h`
  if (segundos % 60 === 0) return `${segundos / 60} min`
  return `${segundos} s`
}

export const ABREVIATURA_POSICION = {
  PORTERO: 'POR',
  DEFENSA: 'DEF',
  CENTROCAMPISTA: 'MED',
  DELANTERO: 'DEL',
} as const
