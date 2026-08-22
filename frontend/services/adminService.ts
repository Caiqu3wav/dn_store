import api from '../lib/axios';
import { User } from '@/types';

export interface AdminDashboardData {
    totalOrders: number;
    totalUsers: number;
    totalProducts: number;
    totalRevenue: number;
    averageTicket: number;
}

export const adminService = {
    getDashboardData: async (): Promise<AdminDashboardData> => {
        const response = await api.get('/admin/dashboard');
        return response.data;
    },

    // User CRUD
    getUsers: async (): Promise<User[]> => {
        const response = await api.get('/users');
        return response.data;
    },

    updateUserRole: async (id: string, role: string): Promise<User> => {
        const response = await api.put(`/users/${id}/role`, { role });
        return response.data;
    },

    deleteUser: async (id: string): Promise<void> => {
        await api.delete(`/users/${id}`);
    },

    // Order Management
    getAllOrders: async (): Promise<any[]> => {
        const response = await api.get('/orders/all');
        return response.data;
    },

    updateOrderStatus: async (id: string, status: string): Promise<any> => {
        const response = await api.put(`/orders/${id}/status`, { status });
        return response.data;
    }
};
