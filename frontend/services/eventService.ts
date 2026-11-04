import api from '../lib/axios';

export interface EventData {
    id: string;
    name: string;
    description: string;
    date: string;
    location: string;
    mapUrl?: string;
    imageUrl?: string;
    registrationUrl?: string;
    active: boolean;
}

export interface EventRequest {
    name: string;
    description?: string;
    date: string;
    location?: string;
    mapUrl?: string;
    imageUrl?: string;
    registrationUrl?: string;
    active?: boolean;
}

export const eventService = {
    getActive: async (): Promise<EventData[]> => {
        const res = await api.get('/events');
        return res.data;
    },

    getAll: async (): Promise<EventData[]> => {
        const res = await api.get('/events/all');
        return res.data;
    },

    getById: async (id: string): Promise<EventData> => {
        const res = await api.get(`/events/${id}`);
        return res.data;
    },

    create: async (data: EventRequest): Promise<EventData> => {
        const res = await api.post('/events', data);
        return res.data;
    },

    update: async (id: string, data: EventRequest): Promise<EventData> => {
        const res = await api.put(`/events/${id}`, data);
        return res.data;
    },

    delete: async (id: string): Promise<void> => {
        await api.delete(`/events/${id}`);
    },
};
