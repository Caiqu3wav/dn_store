'use client';

import { useState } from 'react';
import useSWR from 'swr';
import { Trash2, Shield, ShieldAlert, Loader2, Search } from 'lucide-react';
import { fetcher } from '@/services/productService';
import { adminService } from '@/services/adminService';
import { User } from '@/types';
import { toast } from 'react-hot-toast';

export default function AdminUsersPage() {
    const { data: users, mutate } = useSWR<User[]>('/users', fetcher);
    const [searchTerm, setSearchTerm] = useState('');

    const handleToggleRole = async (user: User) => {
        const newRole = user.role === 'ADMIN' ? 'USER' : 'ADMIN';
        if (!confirm(`Deseja alterar o cargo de ${user.name} para ${newRole}?`)) return;

        try {
            await adminService.updateUserRole(user.id, newRole);
            toast.success(`Cargo de ${user.name} atualizado para ${newRole}!`);
            mutate();
        } catch (error: any) {
            toast.error('Erro ao atualizar cargo do usuário.');
        }
    };

    const handleDelete = async (user: User) => {
        if (!confirm(`Tem certeza que deseja excluir a conta de ${user.name}? Esta ação é irreversível.`)) return;

        try {
            await adminService.deleteUser(user.id);
            toast.success(`Usuário ${user.name} removido.`);
            mutate();
        } catch (error: any) {
            toast.error('Erro ao excluir usuário.');
        }
    };

    const filteredUsers = users?.filter(user => 
        user.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
        user.email.toLowerCase().includes(searchTerm.toLowerCase())
    ) || [];

    return (
        <div className="space-y-6 text-white max-w-6xl mx-auto">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div>
                    <h1 className="text-3xl font-bold">Usuários</h1>
                    <p className="text-gray-400 mt-1">Gerencie os privilégios e contas cadastradas no sistema.</p>
                </div>

                <div className="relative w-full md:w-80">
                    <input 
                        type="text"
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        placeholder="Buscar por nome ou email..."
                        className="w-full bg-[#2A2A2A] border border-white/5 rounded-xl py-2.5 pl-10 pr-4 text-white placeholder-gray-500 focus:outline-none focus:border-brand-secondary"
                    />
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-500" />
                </div>
            </div>

            <div className="bg-[#2A2A2A] border border-white/5 rounded-xl overflow-hidden">
                <div className="overflow-x-auto">
                    <table className="w-full text-left border-collapse">
                        <thead>
                            <tr className="border-b border-white/5 text-gray-400 text-sm">
                                <th className="p-4 font-semibold">Nome</th>
                                <th className="p-4 font-semibold">Email</th>
                                <th className="p-4 font-semibold">Cargo</th>
                                <th className="p-4 font-semibold text-right">Ações</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-white/5 text-sm">
                            {!users ? (
                                <tr>
                                    <td colSpan={4} className="p-8 text-center text-gray-500">
                                        <Loader2 className="w-6 h-6 animate-spin mx-auto mb-2" />
                                        Carregando usuários...
                                    </td>
                                </tr>
                            ) : filteredUsers.length === 0 ? (
                                <tr>
                                    <td colSpan={4} className="p-8 text-center text-gray-500">
                                        Nenhum usuário encontrado.
                                    </td>
                                </tr>
                            ) : (
                                filteredUsers.map((user) => (
                                    <tr key={user.id} className="hover:bg-white/[0.01] transition-colors">
                                        <td className="p-4 font-semibold text-white">{user.name}</td>
                                        <td className="p-4 text-gray-300">{user.email}</td>
                                        <td className="p-4">
                                            <span className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium border ${
                                                user.role === 'ADMIN'
                                                    ? 'bg-brand-secondary/10 text-brand-secondary border-brand-secondary/20'
                                                    : 'bg-blue-500/10 text-blue-400 border-blue-500/20'
                                            }`}>
                                                {user.role === 'ADMIN' ? (
                                                    <>
                                                        <ShieldAlert className="w-3.5 h-3.5" />
                                                        Administrador
                                                    </>
                                                ) : (
                                                    <>
                                                        <Shield className="w-3.5 h-3.5" />
                                                        Usuário Comum
                                                    </>
                                                )}
                                            </span>
                                        </td>
                                        <td className="p-4 text-right">
                                            <div className="flex justify-end gap-2">
                                                <button
                                                    onClick={() => handleToggleRole(user)}
                                                    className="p-1.5 bg-[#3A3A3A] hover:bg-white/10 rounded text-gray-300 hover:text-white transition-colors"
                                                    title={user.role === 'ADMIN' ? 'Tirar Admin' : 'Tornar Admin'}
                                                >
                                                    <Shield className="w-4 h-4" />
                                                </button>
                                                <button
                                                    onClick={() => handleDelete(user)}
                                                    className="p-1.5 bg-[#3A3A3A] hover:bg-red-500/20 rounded text-gray-400 hover:text-red-500 transition-colors"
                                                    title="Deletar Usuário"
                                                >
                                                    <Trash2 className="w-4 h-4" />
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
}
