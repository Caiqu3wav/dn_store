import api from '../lib/axios';

export const favoriteService = {
  getFavorites: async () => {
    const response = await api.get('/favorites');
    return response.data;
  },

  addFavorite: async (productId: number) => {
    const response = await api.post('/favorites', { productId });
    return response.data;
  },

  removeFavorite: async (favoriteId: number) => {
    const response = await api.delete(`/favorites/${favoriteId}`);
    return response.data;
  }
};
