'use client';

import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import Link from 'next/link';
import Image from 'next/image';
import { ChevronDown } from 'lucide-react';

interface HeroProps {
    images?: string[];
}

export function Hero({ images = [] }: HeroProps) {
    const [currentIndex, setCurrentIndex] = useState(0);

    const safeImages = images.length > 0 ? images : ['/assets/bg_hero.png'];

    // Auto-play for carousel
    useEffect(() => {
        if (safeImages.length > 1) {
            const timer = setInterval(() => {
                setCurrentIndex((prev) => (prev + 1) % safeImages.length);
            }, 6000); // 6 seconds per slide
            return () => clearInterval(timer);
        }
    }, [safeImages.length]);

    return (
        <section className="relative h-screen w-full flex items-center justify-center overflow-hidden">
            {/* Background Image Carousel */}
            <div className="absolute inset-0 z-0 bg-black">
                <AnimatePresence mode="wait">
                    <motion.div
                        key={currentIndex}
                        initial={{ opacity: 0, scale: 1.05 }}
                        animate={{ opacity: 1, scale: 1 }}
                        exit={{ opacity: 0 }}
                        transition={{ duration: 1.2, ease: "easeInOut" }}
                        className="absolute inset-0"
                    >
                        <Image
                            src={safeImages[currentIndex]}
                            alt={`DN Store Background ${currentIndex + 1}`}
                            fill
                            priority={currentIndex === 0}
                            className="object-cover object-center"
                        />
                    </motion.div>
                </AnimatePresence>
                {/* Dark Overlay to make text and logo pop */}
                <div className="absolute inset-0 bg-black/60 z-10" />
            </div>

            <div className="relative z-20 flex flex-col items-center container mx-auto px-4 text-center text-white mt-16">
                
                {/* Logo Overlay */}
                <motion.div
                    initial={{ opacity: 0, scale: 0.9 }}
                    animate={{ opacity: 1, scale: 1 }}
                    transition={{ duration: 0.8 }}
                    className="mb-8 opacity-90"
                >
                    <Image 
                        src="/assets/dnstore_logo_white.png" 
                        alt="DN Store" 
                        width={230} 
                        height={100} 
                        className="object-contain mt-6 drop-shadow-lg"
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
                
                {/* Carousel Indicators (Optional visual cue) */}
                {safeImages.length > 1 && (
                    <motion.div 
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        transition={{ delay: 3.2 }}
                        className="flex items-center gap-2 mt-12"
                    >
                        {safeImages.map((_, idx) => (
                            <button
                                key={idx}
                                onClick={() => setCurrentIndex(idx)}
                                className={`h-1.5 rounded-full transition-all ${idx === currentIndex ? 'w-8 bg-brand-secondary' : 'w-2 bg-white/40 hover:bg-white/60'}`}
                                aria-label={`Ir para a imagem ${idx + 1}`}
                            />
                        ))}
                    </motion.div>
                )}
                {/* Scroll Down Arrow Indicator */}
            <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: 0.8, duration: 1 }}
                className="mt-8 flex flex-col items-center z-20"
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
            </div>

            
        </section>
    );
}
