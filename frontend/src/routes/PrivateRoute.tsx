import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { estaAutenticado } from '../services/authService';

interface PrivateRouteProps {
  children: ReactNode;
}

export function PrivateRoute({ children }: PrivateRouteProps) {
  const localizacao = useLocation();

  if (!estaAutenticado()) {
    return <Navigate to="/login" state={{ origem: localizacao.pathname }} replace />;
  }

  return <>{children}</>;
}
