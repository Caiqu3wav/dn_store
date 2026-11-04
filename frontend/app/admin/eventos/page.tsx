'use client';

import { useState } from 'react';
import useSWR from 'swr';
import { Plus, Trash2, Edit, Calendar, MapPin, Loader2, X, Save } from 'lucide-react';
import { eventService, EventData, EventRequest } from '@/services/eventService';
import { toast } from 'react-hot-toast';
import api from '@/lib/axios';

const fetcher = (url: string) => api.get(url.replace('/api', '')).then(r => r.data);

const EMPTY: EventRequest = {
    name: '', description: '', date: '', location: '',
    mapUrl: '', imageUrl: '', registrationUrl: '', active: true,
};

export default function AdminEventosPage() {
    const { data: events, mutate, isLoading } = useSWR<EventData[]>('/api/events/all', fetcher);

    const [modal, setModal] = useState(false);
    const [editing, setEditing] = useState<EventData | null>(null);
    const [form, setForm] = useState<EventRequest>(EMPTY);
    const [saving, setSaving] = useState(false);

    const openNew = () => { setEditing(null); setForm(EMPTY); setModal(true); };
    const openEdit = (e: EventData) => {
        setEditing(e);
        setForm({
            name: e.name, description: e.description, date: e.date,
            location: e.location, mapUrl: e.mapUrl, imageUrl: e.imageUrl,
            registrationUrl: e.registrationUrl, active: e.active,
        });
        setModal(true);
    };

    const handleSave = async () => {
        if (!form.name || !form.date) { toast.error('Nome e data são obrigatórios.'); return; }
        setSaving(true);
        try {
            if (editing) {
                await eventService.update(editing.id, form);
                toast.success('Evento atualizado!');
            } else {
                await eventService.create(form);
                toast.success('Evento criado!');
            }
            mutate();
            setModal(false);
        } catch {
            toast.error('Erro ao salvar evento.');
        } finally {
            setSaving(false);
        }
    };

    const handleDelete = async (id: string, name: string) => {
        if (!confirm(`Remover "${name}"?`)) return;
        try {
            await eventService.delete(id);
            toast.success('Evento removido.');
            mutate();
        } catch {
            toast.error('Erro ao remover evento.');
        }
    };

    const set = (field: keyof EventRequest, value: string | boolean) =>
        setForm(f => ({ ...f, [field]: value }));

    return (
        <div className="space-y-6 text-white">
            <header className="flex items-center justify-between border-b border-white/10 pb-5">
                <div>
                    <p className="text-xs font-semibold uppercase tracking-wider text-brand-secondary mb-1">Calendário</p>
                    <h1 className="text-2xl font-bold">Eventos</h1>
                    <p className="text-sm text-gray-400 mt-1">Gerencie as etapas do circuito.</p>
                </div>
                <button onClick={openNew}
                    className="inline-flex items-center gap-2 bg-brand-secondary px-4 py-2.5 rounded-lg text-sm font-semibold hover:bg-red-700 transition-colors">
                    <Plus className="w-4 h-4" /> Novo Evento
                </button>
            </header>

            {isLoading && (
                <div className="flex justify-center py-12">
                    <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
                </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
                {events?.map(event => (
                    <div key={event.id} className="bg-[#222325] border border-white/10 rounded-xl p-5 flex flex-col gap-3">
                        {event.imageUrl && (
                            <img src={event.imageUrl} alt={event.name}
                                className="w-full h-36 object-cover rounded-lg" />
                        )}
                        <div className="flex items-start justify-between gap-2">
                            <h2 className="font-bold text-base leading-tight">{event.name}</h2>
                            <span className={`shrink-0 text-xs px-2 py-0.5 rounded-full font-medium ${
                                event.active ? 'bg-emerald-500/20 text-emerald-400' : 'bg-gray-500/20 text-gray-400'
                            }`}>
                                {event.active ? 'Ativo' : 'Inativo'}
                            </span>
                        </div>
                        <div className="flex items-center gap-2 text-xs text-gray-400">
                            <Calendar className="w-3.5 h-3.5" />
                            {new Date(event.date + 'T00:00:00').toLocaleDateString('pt-BR')}
                        </div>
                        {event.location && (
                            <div className="flex items-center gap-2 text-xs text-gray-400">
                                <MapPin className="w-3.5 h-3.5" /> {event.location}
                            </div>
                        )}
                        <div className="flex gap-2 mt-auto pt-2 border-t border-white/5">
                            <button onClick={() => openEdit(event)}
                                className="flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg text-xs font-medium text-gray-300 hover:bg-white/10 transition-colors">
                                <Edit className="w-3.5 h-3.5" /> Editar
                            </button>
                            <button onClick={() => handleDelete(event.id, event.name)}
                                className="flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg text-xs font-medium text-red-400 hover:bg-red-500/10 transition-colors">
                                <Trash2 className="w-3.5 h-3.5" /> Remover
                            </button>
                        </div>
                    </div>
                ))}
            </div>

            {!isLoading && (!events || events.length === 0) && (
                <div className="flex flex-col items-center justify-center py-16 text-center">
                    <Calendar className="w-8 h-8 text-gray-600 mb-3" />
                    <p className="text-sm font-medium text-gray-300">Nenhum evento cadastrado</p>
                    <p className="text-xs text-gray-500 mt-1">Clique em "Novo Evento" para começar.</p>
                </div>
            )}

            {modal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
                    <div className="bg-[#1A1B1D] border border-white/10 rounded-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto">
                        <div className="flex items-center justify-between p-5 border-b border-white/10">
                            <h2 className="font-bold text-lg">{editing ? 'Editar Evento' : 'Novo Evento'}</h2>
                            <button onClick={() => setModal(false)} className="p-1.5 hover:bg-white/10 rounded-lg transition-colors">
                                <X className="w-4 h-4" />
                            </button>
                        </div>
                        <div className="p-5 space-y-4">
                            {([
                                { label: 'Nome *', field: 'name', type: 'text', placeholder: 'Ex: 3ª Etapa Desafio Natureza' },
                                { label: 'Data *', field: 'date', type: 'date', placeholder: '' },
                                { label: 'Local', field: 'location', type: 'text', placeholder: 'Ex: Passa Quatro, MG' },
                                { label: 'URL da Imagem', field: 'imageUrl', type: 'url', placeholder: 'https://...' },
                                { label: 'Link do Mapa', field: 'mapUrl', type: 'url', placeholder: 'https://maps.google.com/...' },
                                { label: 'Link de Inscrição', field: 'registrationUrl', type: 'url', placeholder: 'https://...' },
                            ] as const).map(({ label, field, type, placeholder }) => (
                                <div key={field} className="space-y-1.5">
                                    <label className="text-xs font-medium text-gray-400">{label}</label>
                                    <input type={type} value={(form[field] as string) || ''} placeholder={placeholder}
                                        onChange={e => set(field, e.target.value)}
                                        className="w-full bg-[#2A2A2A] border border-white/10 rounded-lg px-3 py-2.5 text-sm text-white focus:outline-none focus:border-brand-secondary" />
                                </div>
                            ))}
                            <div className="space-y-1.5">
                                <label className="text-xs font-medium text-gray-400">Descrição</label>
                                <textarea rows={3} value={form.description || ''}
                                    onChange={e => set('description', e.target.value)}
                                    className="w-full bg-[#2A2A2A] border border-white/10 rounded-lg px-3 py-2.5 text-sm text-white focus:outline-none focus:border-brand-secondary resize-none" />
                            </div>
                            <label className="flex items-center gap-2 cursor-pointer">
                                <input type="checkbox" checked={form.active ?? true}
                                    onChange={e => set('active', e.target.checked)}
                                    className="w-4 h-4 rounded border-gray-600 text-brand-secondary" />
                                <span className="text-sm text-gray-300">Evento ativo</span>
                            </label>
                        </div>
                        <div className="flex gap-3 p-5 border-t border-white/10">
                            <button onClick={() => setModal(false)}
                                className="flex-1 py-2.5 rounded-lg border border-white/10 text-sm font-medium hover:bg-white/5 transition-colors">
                                Cancelar
                            </button>
                            <button onClick={handleSave} disabled={saving}
                                className="flex-1 py-2.5 rounded-lg bg-brand-secondary text-sm font-semibold flex items-center justify-center gap-2 hover:bg-red-700 transition-colors disabled:opacity-50">
                                {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
                                {saving ? 'Salvando...' : 'Salvar'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
