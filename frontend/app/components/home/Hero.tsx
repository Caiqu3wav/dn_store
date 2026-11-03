'use client';

import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import Link from 'next/link';
import Image from 'next/image';
import { ChevronDown, Pause, Play } from 'lucide-react';
import { usePrefersReducedMotion } from '@/hooks/usePrefersReducedMotion';

interface HeroProps {
    images?: string[];
}

export function Hero({ images = [] }: HeroProps) {
    const [currentIndex, setCurrentIndex] = useState(0);
    const [rotationPreference, setRotationPreference] = useState<'auto' | 'paused' | 'playing'>('auto');
    const prefersReducedMotion = usePrefersReducedMotion();

    const safeImages = images.length > 0 ? images : ['/assets/bg_hero.png'];
    const shouldRotate = rotationPreference === 'playing' ||
        (rotationPreference === 'auto' && prefersReducedMotion === false);

    useEffect(() => {
        if (safeImages.length > 1 && shouldRotate) {
            const timer = setInterval(() => {
                setCurrentIndex((prev) => (prev + 1) % safeImages.length);
            }, 6000);
            return () => clearInterval(timer);
        }
    }, [safeImages.length, shouldRotate]);

    const toggleRotation = () => {
        setRotationPreference(shouldRotate ? 'paused' : 'playing');
    };

    return (
        <section className="relative h-full py-16 w-full flex items-center justify-center overflow-hidden">
            {/* Background Image Carousel */}
            <div
                className="absolute inset-0 z-0 bg-black"
                role="region"
                aria-roledescription="carrossel"
                aria-label="Imagens da DN Store"
            >
                <AnimatePresence mode="wait">
                    <motion.div
                        key={currentIndex}
                        initial={prefersReducedMotion ? false : { opacity: 0, scale: 1.05 }}
                        animate={{ opacity: 1, scale: 1 }}
                        exit={{ opacity: 0 }}
                        transition={{ duration: prefersReducedMotion ? 0 : 1.2, ease: "easeInOut" }}
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
                <div className="absolute inset-0 bg-black/40 z-10" />
            </div>

            <div className="relative z-20 flex flex-col items-center container mx-auto px-4 text-center text-white mt-16">
                
                {/* Logo + STORE */}
<motion.div
    initial={{ opacity: 0, scale: 0.9 }}
    animate={{ opacity: 1, scale: 1 }}
    transition={{ duration: 0.8 }}
    className="mb-8 mt-12 flex flex-col items-center opacity-90"
>
    <Image
        src="/assets/dnstore_logo_white.png"
        alt="Desafio Natureza"
        width={300}
        height={150}
        className="object-contain drop-shadow-lg"
    />

    <div
        className="
            -mt-19
             ml-42
            inline-block
            text-brand-secondary
            text-5xl
            text-outline
            tracking-[-0.06em]
            drop-shadow-lg
            rotate-[-18deg]
        "
        style={{ fontFamily: 'DN' }}
    >
        STORE
    </div>
</motion.div>

                <motion.h1
                    initial={prefersReducedMotion ? false : { opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: prefersReducedMotion ? 0 : 0.8, delay: prefersReducedMotion ? 0 : 0.4 }}
                    className="text-lg md:text-2xl font-light mt-12 max-w-2xl mx-auto text-gray-200"
                >
                    Vista o desafio. Viva a aventura.
                </motion.h1>
                
                {safeImages.length > 1 && (
                    <motion.div 
                        initial={prefersReducedMotion ? false : { opacity: 0 }}
                        animate={{ opacity: 1 }}
                        transition={{ delay: prefersReducedMotion ? 0 : 3.2 }}
                        className="mt-12 flex max-w-full flex-wrap items-center justify-center gap-1"
                        role="group"
                        aria-label="Controles do carrossel"
                    >
                        {safeImages.map((_, idx) => (
                            <button
                                key={idx}
                                onClick={() => setCurrentIndex(idx)}
                                type="button"
                                aria-label={`Ir para a imagem ${idx + 1}`}
                                aria-pressed={idx === currentIndex}
                                className={`min-h-11 rounded-full transition-all focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white ${idx === currentIndex ? 'w-11 bg-brand-secondary' : 'w-11 bg-white/20 hover:bg-white/40'}`}
                            >
                                <span aria-hidden="true" className={`mx-auto block h-1.5 rounded-full ${idx === currentIndex ? 'w-8 bg-white' : 'w-2 bg-white/80'}`} />
                            </button>
                        ))}
                        <button
                            type="button"
                            onClick={toggleRotation}
                            aria-label={shouldRotate ? 'Pausar rotação automática' : 'Retomar rotação automática'}
                            className="inline-flex min-h-11 min-w-11 items-center justify-center rounded-full bg-white/20 text-white hover:bg-white/40 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
                        >
                            {shouldRotate
                                ? <Pause aria-hidden="true" className="h-5 w-5" />
                                : <Play aria-hidden="true" className="h-5 w-5" />}
                        </button>
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
                        animate={prefersReducedMotion ? undefined : { y: [0, 10, 0] }}
                        transition={prefersReducedMotion ? undefined : { repeat: Infinity, duration: 1.5, ease: "easeInOut" }}
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
