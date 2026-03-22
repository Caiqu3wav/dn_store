'use client';

import { motion } from 'framer-motion';
import Link from 'next/link';
import Image from 'next/image';
import { ChevronDown } from 'lucide-react';

export function Hero() {
    return (
        <section className="relative h-screen w-full flex items-center justify-center overflow-hidden">
            {/* Background Image */}
            <div className="absolute inset-0 z-0">
                <Image
                    src="/assets/bg_hero.png"
                    alt="Mountain biking background"
                    fill
                    priority
                    className="object-cover object-center"
                />
                <div className="absolute inset-0 bg-black/60" />
            </div>

            <div className="relative z-10 flex flex-col items-center container mx-auto px-4 text-center text-white mt-16">
                
                {/* Logo Overlay */}
                <motion.div
                    initial={{ opacity: 0, scale: 0.9 }}
                    animate={{ opacity: 1, scale: 1 }}
                    transition={{ duration: 0.8 }}
                    className="mb-8 opacity-90"
                >
                    <Image 
                        src="/assets/dn_store2.png" 
                        alt="DN Store" 
                        width={230} 
                        height={100} 
                        className="object-contain drop-shadow-lg"
                    />
                </motion.div>

                <motion.h1
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.8, delay: 0.2 }}
                    className="text-5xl sm:text-6xl md:text-7xl font-black tracking-tight mb-6 max-w-4xl"
                >
                    Vista a emoção da aventura
                </motion.h1>

                <motion.p
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.8, delay: 0.4 }}
                    className="text-lg md:text-2xl font-light max-w-2xl mx-auto text-gray-200"
                >
                    Performance, tecnologia e estilo puro para quem domina as montanhas de bicicleta.
                </motion.p>
            </div>

            {/* Scroll Down Arrow Indicator */}
            <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: 0.8, duration: 1 }}
                className="absolute bottom-12 left-1/2 -translate-x-1/2 flex flex-col items-center"
            >
                <Link 
                    href="#destaques"
                    className="flex flex-col items-center text-white/80 hover:text-brand-secondary transition-colors"
                    aria-label="Ver produtos em destaque"
                >
                    <motion.div
                        animate={{ y: [0, 10, 0] }}
                        transition={{ repeat: Infinity, duration: 1.5, ease: "easeInOut" }}
                        className="p-3 rounded-full bg-white/10 backdrop-blur-sm border border-white/20 hover:bg-brand-secondary/20 hover:border-brand-secondary/50 transition-all"
                    >
                        <ChevronDown className="w-6 h-6" />
                    </motion.div>
                </Link>
            </motion.div>
        </section>
    );
}
