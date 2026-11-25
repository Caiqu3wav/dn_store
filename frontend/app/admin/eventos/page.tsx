'use client';

import { useState } from 'react';
import useSWR from 'swr';
import { Plus, Trash2, Edit, Calendar, MapPin, Save } from 'lucide-react';
import { eventService, EventData, EventRequest } from '@/services/eventService';
import { fetcher } from '@/services/productService';
import { toast } from 'react-hot-toast';
import { Badge, Button, Card, EmptyState, ErrorState, PageHeader, Skeleton } from '../_components/ui';
import { Checkbox, Field, Input, Textarea } from '../_components/form';
import { Modal } from '../_components/Modal';
import { useConfirm } from '../_components/confirm';

const EMPTY: EventRequest = {
    name: '', description: '', date: '', location: '',
    mapUrl: '', imageUrl: '', registrationUrl: '', active: true,
};

export default function AdminEventosPage() {
    const confirm = useConfirm();
    const { data: events, error: eventsError, mutate, isLoading } = useSWR<EventData[]>('/events/all', fetcher);

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
        const confirmed = await confirm({
            title: 'Remover evento',
            message: `Remover "${name}"?`,
            confirmLabel: 'Remover',
        });
        if (!confirmed) return;

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
        <div className="mx-auto max-w-7xl space-y-6">
            <PageHeader
                eyebrow="Calendário"
                title="Eventos"
                description="Gerencie as etapas do circuito."
                actions={
                    <Button variant="primary" size="lg" onClick={openNew}>
                        <Plus className="h-4 w-4" aria-hidden="true" /> Novo Evento
                    </Button>
                }
            />

            {isLoading && (
                <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3" role="status" aria-label="Carregando eventos">
                    {Array.from({ length: 3 }, (_, index) => (
                        <Card key={index} className="space-y-3 p-5">
                            <Skeleton className="h-36 w-full" />
                            <Skeleton className="h-5 w-2/3" />
                            <Skeleton className="h-4 w-1/3" />
                        </Card>
                    ))}
                </div>
            )}

            {eventsError && !events && <ErrorState title="Não foi possível carregar os eventos." onRetry={() => { void mutate(); }} />}

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
                {events?.map(event => (
                    <Card as="article" key={event.id} className="flex flex-col gap-3 p-5">
                        {event.imageUrl && (
                            // <img> de propósito: a URL é digitada pelo admin e pode ser de qualquer host,
                            // e next/image só aceita os hosts liberados em next.config.ts.
                            // eslint-disable-next-line @next/next/no-img-element
                            <img src={event.imageUrl} alt={event.name}
                                className="h-36 w-full rounded-adm-control object-cover" />
                        )}
                        <div className="flex items-start justify-between gap-2">
                            <h2 className="text-base font-semibold leading-tight">{event.name}</h2>
                            <Badge tone={event.active ? 'success' : 'neutral'} dot className="shrink-0">
                                {event.active ? 'Ativo' : 'Inativo'}
                            </Badge>
                        </div>
                        <div className="flex items-center gap-2 text-xs text-adm-ink-muted">
                            <Calendar className="h-3.5 w-3.5" aria-hidden="true" />
                            {new Date(event.date + 'T00:00:00').toLocaleDateString('pt-BR')}
                        </div>
                        {event.location && (
                            <div className="flex items-center gap-2 text-xs text-adm-ink-muted">
                                <MapPin className="h-3.5 w-3.5" aria-hidden="true" /> {event.location}
                            </div>
                        )}
                        <div className="mt-auto flex gap-2 border-t border-adm-line pt-3">
                            <Button variant="ghost" size="sm" className="flex-1" onClick={() => openEdit(event)} aria-label={`Editar ${event.name}`}>
                                <Edit className="h-3.5 w-3.5" aria-hidden="true" /> Editar
                            </Button>
                            <Button variant="danger" size="sm" className="flex-1 border-transparent" onClick={() => handleDelete(event.id, event.name)} aria-label={`Remover ${event.name}`}>
                                <Trash2 className="h-3.5 w-3.5" aria-hidden="true" /> Remover
                            </Button>
                        </div>
                    </Card>
                ))}
            </div>

            {!isLoading && !eventsError && (!events || events.length === 0) && (
                <EmptyState
                    icon={Calendar}
                    className="py-16"
                    title="Nenhum evento cadastrado"
                    description='Clique em "Novo Evento" para começar.'
                />
            )}

            <Modal
                open={modal}
                onClose={() => setModal(false)}
                title={editing ? 'Editar Evento' : 'Novo Evento'}
                footer={
                    <>
                        <Button className="sm:flex-1" onClick={() => setModal(false)}>Cancelar</Button>
                        <Button variant="primary" className="sm:flex-1" onClick={handleSave} loading={saving}>
                            {!saving && <Save className="h-4 w-4" aria-hidden="true" />}
                            {saving ? 'Salvando...' : 'Salvar'}
                        </Button>
                    </>
                }
            >
                <div className="space-y-4">
                    {([
                        { label: 'Nome *', field: 'name', type: 'text', placeholder: 'Ex: 3ª Etapa Desafio Natureza' },
                        { label: 'Data *', field: 'date', type: 'date', placeholder: '' },
                        { label: 'Local', field: 'location', type: 'text', placeholder: 'Ex: Passa Quatro, MG' },
                        { label: 'URL da Imagem', field: 'imageUrl', type: 'url', placeholder: 'https://...' },
                        { label: 'Link do Mapa', field: 'mapUrl', type: 'url', placeholder: 'https://maps.google.com/...' },
                        { label: 'Link de Inscrição', field: 'registrationUrl', type: 'url', placeholder: 'https://...' },
                    ] as const).map(({ label, field, type, placeholder }) => (
                        <Field key={field} label={label}>
                            <Input type={type} value={(form[field] as string) || ''} placeholder={placeholder}
                                onChange={e => set(field, e.target.value)} />
                        </Field>
                    ))}
                    <Field label="Descrição">
                        <Textarea rows={3} value={form.description || ''}
                            onChange={e => set('description', e.target.value)} className="resize-none" />
                    </Field>
                    <Checkbox checked={form.active ?? true} onChange={e => set('active', e.target.checked)}>
                        Evento ativo
                    </Checkbox>
                </div>
            </Modal>
        </div>
    );
}
