'use client';

import { Button } from '../../components/ui/Button';
import Link from 'next/link';
import { CheckCircle } from 'lucide-react';
import { motion } from 'framer-motion';

export default function SuccessPage() {
    return (
        <div className="min-h-screen bg-white flex items-center justify-center pt-24 pb-20">
            <div className="container mx-auto px-4 text-center">
                <motion.div
                    initial={{ scale: 0 }}
                    animate={{ scale: 1 }}
                    transition={{ type: "spring", stiffness: 260, damping: 20 }}
                    className="w-24 h-24 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-8"
                >
                    <CheckCircle className="w-12 h-12 text-green-600" />
                </motion.div>

                <motion.h1
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: 0.2 }}
                    className="text-4xl font-bold text-black mb-4"
                >
                    Pedido Confirmado!
                </motion.h1>

                <motion.p
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: 0.4 }}
                    className="text-gray-600 text-lg mb-8 max-w-lg mx-auto"
                >
                    Obrigado por comprar na Desafio Natureza. Você receberá um e-mail com os detalhes do seu pedido e o código de rastreio em breve.
                </motion.p>

                <motion.div
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: 0.6 }}
                >
                    <Link href="/">
                        <Button size="lg" className="px-8">
                            Voltar para a Home
                        </Button>
                    </Link>
                </motion.div>
            </div>
        </div>
    );
}
