import api from '../lib/axios';
import { Product } from '@/types';

export interface FavoriteResponse {
  id: string;
  product: Product;
}

export const favoriteService = {
  getFavorites: async (): Promise<FavoriteResponse[]> => {
    const response = await api.get('/favorites');
    return response.data;
  },

  addFavorite: async (productId: string): Promise<FavoriteResponse> => {
    const response = await api.post(`/favorites/${productId}`);
    return response.data;
  },

  removeFavorite: async (productId: string): Promise<void> => {
    await api.delete(`/favorites/${productId}`);
  }
};
