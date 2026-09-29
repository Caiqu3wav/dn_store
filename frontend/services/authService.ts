import api from '../lib/axios';
import type { addressFormsDataTypes } from '@/app/components/ui/AddressForm';

export interface AuthUser {
  id: string;
  name: string;
  email: string;
  phone?: string;
  role: 'USER' | 'ADMIN';
  emailMfaEnabled: boolean;
}

export interface AuthResponse {
  status: 'AUTHENTICATED';
  token: string;
  user: AuthUser;
}

export interface AuthChallengeResponse {
  status: 'EMAIL_VERIFICATION_REQUIRED' | 'MFA_REQUIRED';
  email: string;
  challengeToken: string | null;
}

export type AuthResult = AuthResponse | AuthChallengeResponse;

export interface RegisterData {
  name: string;
  phone?: string;
  email: string;
  password: string;
  cpf: string;
  address: addressFormsDataTypes;
}

export const authService = {
  register: async (data: RegisterData) => (await api.post<AuthChallengeResponse>('/auth/register', data)).data,
  login: async (data: { email: string; password: string }) => (await api.post<AuthResult>('/auth/login', data)).data,
  verifyEmail: async (data: { email: string; code: string }) => (await api.post<AuthResponse>('/auth/verify-email', data)).data,
  resendVerification: async (data: { email: string }) => api.post('/auth/resend-verification', data),
  verifyMfa: async (data: { challengeToken: string; code: string }) => (await api.post<AuthResponse>('/auth/verify-mfa', data)).data,
  resendMfa: async (data: { challengeToken: string }) => api.post('/auth/resend-mfa', data),
  forgotPassword: async (data: { email: string }) => api.post('/auth/forgot-password', data),
  resetPassword: async (data: { token: string; newPassword: string }) => api.post('/auth/reset-password', data),
  changePassword: async (data: { currentPassword: string; newPassword: string }) => api.post('/auth/change-password', data),
  me: async () => {
    const response = await api.get('/auth/me');
    return response.data;
  }
};
