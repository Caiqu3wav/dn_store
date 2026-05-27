'use client';

import { Tag } from 'lucide-react';

const categories = [
    {
        name: 'Camisa Poliamida',
        products: 42,
        sold: 128,
        revenue: 'R$ 8.950,00',
        status: 'Mais Vendida'
    },
    {
        name: 'Camisas de Ciclismo',
        products: 31,
        sold: 87,
        revenue: 'R$ 6.420,00',
        status: 'Alta'
    },
    {
        name: 'Bonés & Meias',
        products: 18,
        sold: 53,
        revenue: 'R$ 2.180,00',
        status: 'Normal'
    },
];

function StatusBadge({ status }: { status: string }) {
    const colors: Record<string, string> = {
        'Mais Vendida': 'bg-green-500/10 text-green-500 border-green-500/20',
        'Alta': 'bg-yellow-500/10 text-yellow-500 border-yellow-500/20',
        'Normal': 'bg-blue-500/10 text-blue-500 border-blue-500/20',
    };

    return (
        <span className={`px-2 py-1 text-xs font-medium rounded-full border ${colors[status]}`}>
            {status}
        </span>
    );
}

export default function CategoriasPage() {
    return (
        <div className="space-y-8 text-white">

            {/* Header */}
            <div>
                <h1 className="text-3xl font-bold">Categorias</h1>

                <p className="text-gray-400 mt-1">
                    Gerencie o desempenho das categorias da loja.
                </p>
            </div>

            {/* Card/Table */}
            <div className="bg-[#2A2A2A] border border-white/5 rounded-xl shadow-sm overflow-hidden">

                {/* Top */}
                <div className="p-6 border-b border-white/5 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-[#1A1B1D] rounded-lg">
                            <Tag className="w-5 h-5 text-brand-secondary" />
                        </div>

                        <h2 className="text-lg font-bold text-white">
                            Categorias da Loja
                        </h2>
                    </div>

                    <span className="text-sm text-gray-400">
                        {categories.length} categorias
                    </span>
                </div>

                {/* Table */}
                <div className="overflow-x-auto">
                    <table className="w-full text-left border-collapse">

                        <thead>
                            <tr className="bg-[#1A1B1D] text-gray-400 text-sm">
                                <th className="p-4 font-medium">Categoria</th>
                                <th className="p-4 font-medium">Produtos</th>
                                <th className="p-4 font-medium">Vendidos no Mês</th>
                                <th className="p-4 font-medium">Faturamento</th>
                                <th className="p-4 font-medium">Status</th>
                            </tr>
                        </thead>

                        <tbody className="divide-y divide-white/5">
                            {categories.map((category, i) => (
                                <tr
                                    key={i}
                                    className="hover:bg-white/[0.02] transition-colors"
                                >
                                    <td className="p-4 font-medium text-white">
                                        {category.name}
                                    </td>

                                    <td className="p-4 text-gray-300">
                                        {category.products}
                                    </td>

                                    <td className="p-4 text-gray-300">
                                        {category.sold}
                                    </td>

                                    <td className="p-4 font-medium text-white">
                                        {category.revenue}
                                    </td>

                                    <td className="p-4">
                                        <StatusBadge status={category.status} />
                                    </td>
                                </tr>
                            ))}
                        </tbody>

                    </table>
                </div>
            </div>
        </div>
    );
}