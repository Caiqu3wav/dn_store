'use client';

import { useState } from 'react';
import useSWR from 'swr';
import { Trash2, Shield, ShieldAlert, Search, Users } from 'lucide-react';
import { fetcher } from '@/services/productService';
import { adminService } from '@/services/adminService';
import { User } from '@/types';
import { toast } from 'react-hot-toast';
import { Badge, Card, EmptyState, ErrorState, IconButton, PageHeader, TableSkeletonRows } from '../_components/ui';
import { Input } from '../_components/form';
import { Table, TableWrap, Tbody, Td, Th, Thead, rowClass } from '../_components/table';
import { useConfirm } from '../_components/confirm';

export default function AdminUsersPage() {
    const confirm = useConfirm();
    const { data: users, error: usersError, mutate } = useSWR<User[]>('/users', fetcher);
    const [searchTerm, setSearchTerm] = useState('');

    const handleToggleRole = async (user: User) => {
        const newRole = user.role === 'ADMIN' ? 'USER' : 'ADMIN';
        const confirmed = await confirm({
            title: 'Alterar cargo',
            message: `Deseja alterar o cargo de ${user.name} para ${newRole}?`,
            confirmLabel: 'Alterar cargo',
        });
        if (!confirmed) return;

        try {
            await adminService.updateUserRole(user.id, newRole);
            toast.success(`Cargo de ${user.name} atualizado para ${newRole}!`);
            mutate();
        } catch {
            toast.error('Erro ao atualizar cargo do usuário.');
        }
    };

    const handleDelete = async (user: User) => {
        const confirmed = await confirm({
            title: 'Excluir usuário',
            message: `Tem certeza que deseja excluir a conta de ${user.name}? Esta ação é irreversível.`,
            confirmLabel: 'Excluir conta',
        });
        if (!confirmed) return;

        try {
            await adminService.deleteUser(user.id);
            toast.success(`Usuário ${user.name} removido.`);
            mutate();
        } catch {
            toast.error('Erro ao excluir usuário.');
        }
    };

    const filteredUsers = users?.filter(user =>
        user.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
        user.email.toLowerCase().includes(searchTerm.toLowerCase())
    ) || [];

    return (
        <div className="mx-auto max-w-6xl space-y-6">
            <PageHeader
                title="Usuários"
                description="Gerencie os privilégios e contas cadastradas no sistema."
                actions={
                    <div className="relative w-full md:w-80">
                        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-adm-ink-subtle" aria-hidden="true" />
                        <Input
                            type="text"
                            aria-label="Buscar usuários por nome ou email"
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            placeholder="Buscar por nome ou email..."
                            className="min-h-11 pl-10"
                        />
                    </div>
                }
            />

            <Card className="overflow-hidden">
                <TableWrap>
                    <Table>
                        <Thead>
                            <tr>
                                <Th>Nome</Th>
                                <Th>Email</Th>
                                <Th>Cargo</Th>
                                <Th className="text-right">Ações</Th>
                            </tr>
                        </Thead>
                        <Tbody>
                            {usersError && !users ? (
                                <tr>
                                    <td colSpan={4}><ErrorState title="Não foi possível carregar os usuários." onRetry={() => { void mutate(); }} /></td>
                                </tr>
                            ) : !users ? (
                                <TableSkeletonRows rows={6} columns={4} />
                            ) : filteredUsers.length === 0 ? (
                                <tr>
                                    <td colSpan={4}><EmptyState icon={Users} title="Nenhum usuário encontrado." /></td>
                                </tr>
                            ) : (
                                filteredUsers.map((user) => (
                                    <tr key={user.id} className={rowClass()}>
                                        <Td className="font-medium">{user.name}</Td>
                                        <Td className="text-adm-ink-muted">{user.email}</Td>
                                        <Td>
                                            {user.role === 'ADMIN' ? (
                                                <Badge tone="accent">
                                                    <ShieldAlert className="h-3.5 w-3.5" aria-hidden="true" />
                                                    Administrador
                                                </Badge>
                                            ) : (
                                                <Badge tone="info">
                                                    <Shield className="h-3.5 w-3.5" aria-hidden="true" />
                                                    Usuário Comum
                                                </Badge>
                                            )}
                                        </Td>
                                        <Td className="text-right">
                                            <div className="flex justify-end gap-1">
                                                <IconButton
                                                    onClick={() => handleToggleRole(user)}
                                                    label={`${user.role === 'ADMIN' ? 'Tirar Admin' : 'Tornar Admin'}: ${user.name}`}
                                                >
                                                    <Shield className="h-4 w-4" aria-hidden="true" />
                                                </IconButton>
                                                <IconButton
                                                    tone="danger"
                                                    onClick={() => handleDelete(user)}
                                                    label={`Deletar Usuário: ${user.name}`}
                                                >
                                                    <Trash2 className="h-4 w-4" aria-hidden="true" />
                                                </IconButton>
                                            </div>
                                        </Td>
                                    </tr>
                                ))
                            )}
                        </Tbody>
                    </Table>
                </TableWrap>
            </Card>
        </div>
    );
}
