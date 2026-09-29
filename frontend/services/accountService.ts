import api from '@/lib/axios';

export interface Address {
  id?: string;
  street: string;
  number: string;
  complement?: string;
  neighborhood?: string;
  city: string;
  state: string;
  zipCode: string;
}

export interface AccountProfile {
  id: string;
  name: string;
  email: string;
  phone?: string;
  role: 'USER' | 'ADMIN';
  emailMfaEnabled: boolean;
}

export const accountService = {
  getProfile: async () => (await api.get<AccountProfile>('/account/me')).data,
  updateProfile: async (data: { name: string; phone: string }) => (await api.put('/account/me', data)).data,
  setEmailMfaEnabled: async (enabled: boolean) => (await api.put<AccountProfile>('/account/me/email-mfa', { enabled })).data,
  getAddresses: async () => (await api.get<Address[]>('/account/addresses')).data,
  addAddress: async (data: Address) => (await api.post<Address>('/account/addresses', data)).data,
  deleteAddress: async (id: string) => api.delete(`/account/addresses/${id}`),
  getOrders: async () => (await api.get('/account/orders')).data,
};