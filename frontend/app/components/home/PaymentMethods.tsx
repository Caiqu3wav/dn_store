'use client';

import { motion } from 'framer-motion';

export function PaymentMethods() {
    return (
        <section className="py-12 bg-white border-t border-gray-100">
            <div className="container mx-auto px-4 lg:px-8">
                <div className="flex flex-col md:flex-row items-center justify-between gap-6">
                    <div className="text-center md:text-left">
                        <h3 className="text-brand-primary font-bold text-lg mb-1">Compra Segura e Rápida</h3>
                        <p className="text-gray-500 text-sm">Aceitamos as melhores formas de pagamento</p>
                    </div>

                    <div className="flex items-center gap-6 md:gap-10 opacity-70">
                        {/* Pix Logo (Custom Text Representation for simplicity) */}
                        <motion.div 
                            whileHover={{ scale: 1.1, opacity: 1 }}
                            className="flex items-center gap-1.5 font-bold text-[#32BCAD] text-xl"
                        >
                            <svg className="w-6 h-6" viewBox="0 0 512 512" fill="currentColor">
                                <path d="M473.2 216.7L327 70.4c-38.3-38.3-100.9-38.3-139.3 0L41.5 216.7c-38.3 38.3-38.3 100.9 0 139.3l146.3 146.3c38.3 38.3 100.9 38.3 139.3 0l146.3-146.3c38.1-38.3 38.1-101-1.2-139.3zM158.8 353l-64.8-64.8c-6.3-6.3-6.3-16.5 0-22.7l64.8-64.8c6.3-6.3 16.5-6.3 22.7 0l64.8 64.8c6.3 6.3 6.3 16.5 0 22.7l-64.8 64.8c-6.4 6.3-16.6 6.3-22.7 0zm197.1 0l-64.8-64.8c-6.3-6.3-6.3-16.5 0-22.7l64.8-64.8c6.3-6.3 16.5-6.3 22.7 0l64.8 64.8c6.3 6.3 6.3 16.5 0 22.7l-64.8 64.8c-6.2 6.3-16.4 6.3-22.7 0z"/>
                            </svg>
                            <span>Pix</span>
                        </motion.div>

                        {/* Credit Card text rep */}
                        <motion.div 
                            whileHover={{ scale: 1.1, opacity: 1 }}
                            className="flex items-center gap-2 font-bold text-gray-800 text-lg"
                        >
                            <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" />
                            </svg>
                            <span>Cartões</span>
                        </motion.div>

                        {/* Mercado Pago */}
                        <motion.div 
                            whileHover={{ scale: 1.1, opacity: 1 }}
                            className="flex items-center gap-1.5 font-bold text-[#009EE3] text-lg"
                        >
                            <div className="w-8 h-8 bg-[#009EE3]/10 rounded-full flex items-center justify-center">
                                <svg className="w-5 h-5 text-[#009EE3]" fill="currentColor" viewBox="0 0 24 24">
                                    <path d="M11 20H6a2 2 0 01-2-2V6a2 2 0 012-2h12a2 2 0 012 2v5h-2V6H6v12h5v2zm9-3v3h-2v-3h-3v-2h3v-3h2v3h3v2h-3z" />
                                </svg>
                            </div>
                            <span>MercadoPago</span>
                        </motion.div>
                    </div>
                </div>
            </div>
        </section>
    );
}
