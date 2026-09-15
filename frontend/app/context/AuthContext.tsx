'use client';

import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import Cookies from 'js-cookie';
import { authService } from '@/services/authService';
import { User } from '@/types';

interface AuthContextType {
  user: User | null;
  loading: boolean;
  login: (token: string, user: User) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const checkAuth = async () => {
      const token = Cookies.get('auth_token');
      if (token) {
        try {
          // Temporariamente, se não tiver endpoint `/auth/me`, poderíamos apenas decodificar o token.
          // Assumindo que o backend não tem `/auth/me` implementado, salvaremos o user inteiro no localStorage 
          // apenas para facilidade no frontend, além do JWT no cookie. 
          const savedUser = localStorage.getItem('user_data');
          if (savedUser) {
             setUser(JSON.parse(savedUser));
          }
        } catch (error) {
          console.error("Erro ao recuperar sessão", error);
          logout();
        }
      }
      setLoading(false);
    };

    checkAuth();
  }, []);

  const login = (token: string, userData: User) => {
    Cookies.set('auth_token', token, { expires: 1 }); // Expira em 1 dia
    localStorage.setItem('user_data', JSON.stringify(userData));
    setUser(userData);
  };

  const logout = () => {
    Cookies.remove('auth_token');
    localStorage.removeItem('user_data');
    setUser(null);
    window.location.href = '/auth'; // Redireciona para tela de login
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth deve ser usado dentro de um AuthProvider');
  }
  return context;
}
