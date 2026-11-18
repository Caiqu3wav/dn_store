import api from '../lib/axios';
import { User } from '@/types';

export interface AdminDashboardData {
    totalOrders: number;
    totalUsers: number;
    totalProducts: number;
    totalRevenue: number;
    averageTicket: number;
    /** Pedidos com pagamento confirmado: base da receita e do ticket médio. */
    paidOrders?: number;
}

export interface MelhorEnvioAuthorizationResponse {
    authorizationUrl: string;
}

export interface AdminOrder {
    id: string;
    status: string;
    total?: number;
    createdAt?: string;
    discountAmount?: number;
    /** Pagamento confirmado manualmente por um admin (não pelo Asaas). */
    manualPaymentConfirmed?: boolean;
    user?: {
        name?: string;
        email?: string;
        phone?: string;
    };
    address?: {
        street?: string;
        number?: string;
        neighborhood?: string;
        city?: string;
        state?: string;
        zipCode?: string;
    };
    items?: AdminOrderItem[];
}

export interface OrderStatusAuditEntry {
    id: string;
    changeType: string;
    previousStatus: string;
    newStatus: string;
    reason: string;
    createdAt: string;
    actorId: string;
    actorName: string;
}

interface AdminOrderItem {
    id: string;
    quantity: number;
    price: number;
    totalPrice: number;
    productName: string | null;
}

export const adminService = {
    getMelhorEnvioAuthorizationUrl: async (): Promise<string> => {
        const response = await api.get<MelhorEnvioAuthorizationResponse>(
            '/integrations/melhor-envio/authorize'
        );
        return response.data.authorizationUrl;
    },

    getDashboardData: async (): Promise<AdminDashboardData> => {
        const response = await api.get('/admin/dashboard');
        return response.data;
    },

    // User CRUD
    getUsers: async (): Promise<User[]> => {
        const response = await api.get('/users');
        return response.data;
    },

    updateUserRole: async (id: string, role: User['role']): Promise<User> => {
        const response = await api.put(`/users/${id}/role`, { role });
        return response.data;
    },

    deleteUser: async (id: string): Promise<void> => {
        await api.delete(`/users/${id}`);
    },

    // Order Management
    getAllOrders: async (): Promise<AdminOrder[]> => {
        const response = await api.get('/orders/all');
        return response.data;
    },

    updateOrderStatus: async (id: string, status: string): Promise<AdminOrder> => {
        const response = await api.put(`/orders/${id}/status`, { status });
        return response.data;
    },

    /** Confirmação manual de pagamento: exige justificativa e fica registrada com o admin responsável. */
    confirmPaymentManually: async (id: string, reason: string): Promise<AdminOrder> => {
        const response = await api.post(`/orders/${id}/confirm-payment`, { reason });
        return response.data;
    }
};
