'use client';

import { Search, Mail, Phone, Calendar, MoreVertical, Edit, Trash2 } from 'lucide-react';
import { useState } from 'react';

const mockUsuarios = [
    { id: '1', name: 'João Silva', email: 'joao.silva@email.com', phone: '(11) 98765-4321', joined: '15 Mai 2026', role: 'Cliente', status: 'Ativo' },
    { id: '2', name: 'Maria Oliveira', email: 'maria.oliveira@email.com', phone: '(21) 99876-5432', joined: '10 Mai 2026', role: 'Cliente', status: 'Ativo' },
    { id: '3', name: 'Carlos Santos', email: 'carlos.santos@email.com', phone: '(31) 91234-5678', joined: '02 Mai 2026', role: 'Cliente', status: 'Inativo' },
    { id: '4', name: 'Ana Paula', email: 'ana.paula@email.com', phone: '(41) 97777-8888', joined: '28 Abr 2026', role: 'Admin', status: 'Ativo' },
    { id: '5', name: 'Lucas Mendes', email: 'lucas.mendes@email.com', phone: '(51) 95555-4444', joined: '15 Abr 2026', role: 'Cliente', status: 'Ativo' },
];

export default function AdminUsuariosPage() {
    const [searchTerm, setSearchTerm] = useState('');

    const filteredUsuarios = mockUsuarios.filter(u => 
        u.name.toLowerCase().includes(searchTerm.toLowerCase()) || 
        u.email.toLowerCase().includes(searchTerm.toLowerCase())
    );

    return (
        <div className="space-y-6 text-white">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div>
                    <h1 className="text-3xl font-bold">Usuários</h1>
                    <p className="text-gray-400 mt-1">Gerencie os clientes e administradores do sistema.</p>
                </div>
            </div>

            {/* Filters & Search */}
            <div className="flex flex-col md:flex-row gap-4">
                <div className="relative flex-1 max-w-md">
                    <input 
                        type="text" 
                        placeholder="Buscar por nome ou email..." 
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        className="w-full bg-[#2A2A2A] border border-white/10 rounded-lg py-2.5 pl-10 pr-4 text-sm text-white placeholder-gray-500 focus:outline-none focus:border-brand-secondary focus:ring-1 focus:ring-brand-secondary"
                    />
                    <Search className="w-4 h-4 text-gray-500 absolute left-3.5 top-1/2 -translate-y-1/2" />
                </div>
            </div>

            {/* Users Table */}
            <div className="bg-[#2A2A2A] border border-white/5 rounded-xl shadow-sm overflow-hidden">
                <div className="overflow-x-auto">
                    <table className="w-full text-left border-collapse">
                        <thead>
                            <tr className="bg-[#1A1B1D] text-gray-400 text-sm">
                                <th className="p-4 font-medium">Nome</th>
                                <th className="p-4 font-medium">Contato</th>
                                <th className="p-4 font-medium">Data de Cadastro</th>
                                <th className="p-4 font-medium">Role</th>
                                <th className="p-4 font-medium">Status</th>
                                <th className="p-4 font-medium text-right">Ações</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-white/5">
                            {filteredUsuarios.map((user) => (
                                <tr key={user.id} className="hover:bg-white/[0.02] transition-colors group">
                                    <td className="p-4 font-medium text-white">{user.name}</td>
                                    <td className="p-4 text-sm text-gray-400">
                                        <div className="flex items-center gap-2">
                                            <Mail className="w-3 h-3" /> {user.email}
                                        </div>
                                        <div className="flex items-center gap-2 mt-1">
                                            <Phone className="w-3 h-3" /> {user.phone}
                                        </div>
                                    </td>
                                    <td className="p-4 text-sm text-gray-400">
                                        <div className="flex items-center gap-2">
                                            <Calendar className="w-3 h-3" /> {user.joined}
                                        </div>
                                    </td>
                                    <td className="p-4">
                                        <span className={`px-2 py-1 text-xs font-medium rounded-full border ${
                                            user.role === 'Admin' 
                                                ? 'bg-purple-500/10 text-purple-400 border-purple-500/20' 
                                                : 'bg-gray-500/10 text-gray-300 border-gray-500/20'
                                        }`}>
                                            {user.role}
                                        </span>
                                    </td>
                                    <td className="p-4">
                                        <span className={`px-2 py-1 text-xs font-medium rounded-full border ${
                                            user.status === 'Ativo' 
                                                ? 'bg-green-500/10 text-green-500 border-green-500/20' 
                                                : 'bg-red-500/10 text-red-500 border-red-500/20'
                                        }`}>
                                            {user.status}
                                        </span>
                                    </td>
                                    <td className="p-4 text-right">
                                        <div className="flex items-center justify-end gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                                            <button className="p-2 text-gray-400 hover:text-white hover:bg-white/10 rounded-lg transition-colors">
                                                <Edit className="w-4 h-4" />
                                            </button>
                                            <button className="p-2 text-gray-400 hover:text-red-500 hover:bg-red-500/10 rounded-lg transition-colors">
                                                <Trash2 className="w-4 h-4" />
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                            {filteredUsuarios.length === 0 && (
                                <tr>
                                    <td colSpan={6} className="p-8 text-center text-gray-500">
                                        Nenhum usuário encontrado.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
}
