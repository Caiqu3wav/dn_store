import axios from 'axios';
import Cookies from 'js-cookie';

const authenticatedRoutes = ['/checkout', '/conta', '/admin'];

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
      if (typeof window !== 'undefined') {
        const pathname = window.location.pathname;
        const requiresAuthentication = authenticatedRoutes.some(
          (route) => pathname === route || pathname.startsWith(`${route}/`)
        );
        const hasToken = Boolean(Cookies.get('auth_token'));

        if (hasToken) {
          Cookies.remove('auth_token');
        }

        if (requiresAuthentication) {
          window.location.href = `/auth?next=${encodeURIComponent(pathname)}`;
        }
      }
    }
    return Promise.reject(error);
  }
);

export default api;
