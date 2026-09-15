import axios from 'axios';
import Cookies from 'js-cookie';

const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Interceptor de requisição: anexa o token JWT se existir
api.interceptors.request.use(
  (config) => {
    const token = Cookies.get('auth_token');
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Interceptor de resposta: trata erros globais (ex: 401)
api.interceptors.response.use(
  (response) => {
    return response;
  },
  (error) => {
    if (error.response?.status === 401) {
      // Token expirou ou é inválido: remover cookie e redirecionar para login se estiver no client
      Cookies.remove('auth_token');
      if (typeof window !== 'undefined') {
        // Redireciona para login apenas se não estivermos na página de login já
        if (window.location.pathname !== '/login') {
           window.location.href = '/login';
        }
      }
    }
    return Promise.reject(error);
  }
);

export default api;
