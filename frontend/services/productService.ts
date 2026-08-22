/* eslint-disable @typescript-eslint/no-explicit-any */
import api from '../lib/axios';

// Utilizando fetcher genérico para o SWR no frontend
export const fetcher = (url: string) => api.get(url).then(res => res.data);

export const productService = {
  // Chamadas de mutação ou métodos não cacheados
  createProduct: async (productData: any) => {
    const response = await api.post('/products', productData);
    return response.data;
  },

  updateProduct: async (id: string, productData: any) => {
    const response = await api.put(`/products/${id}`, productData);
    return response.data;
  },

  deleteProduct: async (id: string) => {
    const response = await api.delete(`/products/${id}`);
    return response.data;
  },

  getProductById: async (id: string) => {
    const response = await api.get(`/products/${id}`);
    return response.data;
  }
};
