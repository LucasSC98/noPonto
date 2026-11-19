import { Navigate, Route, Routes } from 'react-router-dom';
import { PaginaAgendamentoPublico } from './pages/PaginaAgendamentoPublico';
import { PaginaDashboard } from './pages/PaginaDashboard';
import { PaginaLogin } from './pages/PaginaLogin';
import { PaginaProfissionais } from './pages/PaginaProfissionais';
import { PrivateRoute } from './routes/PrivateRoute';

export function App() {
  return (
    <Routes>
      <Route path="/agendar/:profissionalId" element={<PaginaAgendamentoPublico />} />
      <Route path="/login" element={<PaginaLogin />} />
      <Route
        path="/dashboard"
        element={
          <PrivateRoute>
            <PaginaDashboard />
          </PrivateRoute>
        }
      />
      <Route
        path="/profissionais"
        element={
          <PrivateRoute>
            <PaginaProfissionais />
          </PrivateRoute>
        }
      />
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
}

export default App;
