import { Link, Navigate, NavLink, Outlet, Route, Routes } from 'react-router-dom'
import { AuthPage } from './auth/AuthPage'
import { AvisoVerificacion } from './auth/AvisoVerificacion'
import { RestablecerPage } from './auth/RestablecerPage'
import { VerificarEmailPage } from './auth/VerificarEmailPage'
import { useAuth } from './auth/contexto'
import { Jugadores } from './Jugadores'
import { Clasificacion } from './ligas/Clasificacion'
import { LigaPage } from './ligas/LigaPage'
import { LigasPage } from './ligas/LigasPage'
import { Mercado } from './ligas/Mercado'
import { MiPlantilla } from './ligas/MiPlantilla'
import { Opciones } from './ligas/Opciones'
import { Tacticas } from './ligas/Tacticas'
import { Rivales } from './ligas/Rivales'
import './App.css'

/** Marco de todo lo que exige sesión: sin ella se muestra el login en su lugar. */
function Privado() {
  const { usuario, cargando, cerrarSesion } = useAuth()

  if (cargando) {
    return (
      <main>
        <p>Cargando...</p>
      </main>
    )
  }

  if (!usuario) {
    return <AuthPage />
  }

  return (
    <main>
      <header className="cabecera">
        <Link to="/" className="logo">
          <h1>LigaComunio</h1>
        </Link>
        <nav className="menu">
          <NavLink to="/" end>
            Mis ligas
          </NavLink>
          <NavLink to="/jugadores">Jugadores</NavLink>
        </nav>
        <div className="sesion">
          <span>{usuario.username}</span>
          <button type="button" onClick={() => void cerrarSesion()}>
            Cerrar sesión
          </button>
        </div>
      </header>

      <AvisoVerificacion />
      <Outlet />
    </main>
  )
}

function App() {
  return (
    <Routes>
      {/* Las dos rutas de los enlaces del correo: se abren con sesión y sin ella. */}
      <Route path="/verificar-email" element={<VerificarEmailPage />} />
      <Route path="/restablecer" element={<RestablecerPage />} />

      <Route element={<Privado />}>
        <Route path="/" element={<LigasPage />} />
        <Route path="/ligas/:id" element={<LigaPage />}>
          <Route index element={<Clasificacion />} />
          <Route path="plantilla" element={<MiPlantilla />} />
          <Route path="tactica" element={<Tacticas />} />
          <Route path="mercado" element={<Mercado />} />
          <Route path="rivales" element={<Rivales />} />
          <Route path="opciones" element={<Opciones />} />
        </Route>
        <Route path="/jugadores" element={<Jugadores />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}

export default App
