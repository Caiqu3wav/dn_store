'use client';

import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import Image from 'next/image';
import Link from 'next/link';
import { ArrowRight, ChevronLeft, ChevronRight, Pause, Play } from 'lucide-react';
import { usePrefersReducedMotion } from '@/hooks/usePrefersReducedMotion';

interface SobreProps {
    images: string[];
}

export function Sobre({ images }: SobreProps) {
    const [currentIndex, setCurrentIndex] = useState(0);
    const [rotationPreference, setRotationPreference] = useState<'auto' | 'paused' | 'playing'>('auto');
    const prefersReducedMotion = usePrefersReducedMotion();
    const safeImages = images.length > 0 ? images : ['/assets/Images/novo-logotipo.png'];
    const shouldRotate = rotationPreference === 'playing' ||
        (rotationPreference === 'auto' && prefersReducedMotion === false);

    useEffect(() => {
        if (safeImages.length > 1 && shouldRotate) {
            const timer = setInterval(() => {
                setCurrentIndex((prev) => (prev + 1) % safeImages.length);
            }, 5000);
            return () => clearInterval(timer);
        }
    }, [safeImages.length, shouldRotate]);

    const nextSlide = () => setCurrentIndex((prev) => (prev + 1) % safeImages.length);
    const prevSlide = () => setCurrentIndex((prev) => (prev === 0 ? safeImages.length - 1 : prev - 1));
    const toggleRotation = () => {
        setRotationPreference(shouldRotate ? 'paused' : 'playing');
    };

    return (
        <section className="py-24 bg-background">
            <div className="container mx-auto px-4 lg:px-8">
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 lg:gap-20 items-center">
                    
                    {/* Image Area with Carousel */}
                    <div
                        className="relative h-[400px] lg:h-[600px] w-full rounded-[2rem] overflow-hidden shadow-2xl group"
                        role="region"
                        aria-roledescription="carrossel"
                        aria-label="Imagens da marca Desafio Natureza"
                    >
                        <AnimatePresence mode="wait">
                            <motion.div
                                key={currentIndex}
                                initial={prefersReducedMotion ? false : { opacity: 0, scale: 1.05 }}
                                animate={{ opacity: 1, scale: 1 }}
                                exit={{ opacity: 0 }}
                                transition={{ duration: prefersReducedMotion ? 0 : 0.8 }}
                                className="absolute inset-0"
                            >
                                <Image
                                    src={safeImages[currentIndex]}
                                    alt={`Sobre a DN Store imagem ${currentIndex + 1}`}
                                    fill
                                    className="object-cover"
                                />
                            </motion.div>
                        </AnimatePresence>

                        {/* Carousel Controls (only if > 1 image) */}
                        {safeImages.length > 1 && (
                            <>
                                <button 
                                    type="button"
                                    onClick={prevSlide}
                                    aria-label="Imagem anterior"
                                    className="absolute left-4 top-1/2 -translate-y-1/2 w-11 h-11 rounded-full bg-black/60 text-white flex items-center justify-center opacity-100 lg:opacity-0 lg:group-hover:opacity-100 focus-visible:opacity-100 transition-opacity hover:bg-black/80 backdrop-blur-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-white"
                                >
                                    <ChevronLeft className="w-6 h-6" />
                                </button>
                                <button 
                                    type="button"
                                    onClick={nextSlide}
                                    aria-label="Próxima imagem"
                                    className="absolute right-4 top-1/2 -translate-y-1/2 w-11 h-11 rounded-full bg-black/60 text-white flex items-center justify-center opacity-100 lg:opacity-0 lg:group-hover:opacity-100 focus-visible:opacity-100 transition-opacity hover:bg-black/80 backdrop-blur-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-white"
                                >
                                    <ChevronRight className="w-6 h-6" />
                                </button>
                                
                                {/* Indicators */}
                                <div className="absolute bottom-4 left-1/2 -translate-x-1/2 flex max-w-[calc(100%-5rem)] flex-wrap items-center justify-center gap-1">
                                    {safeImages.map((_, idx) => (
                                        <button
                                            key={idx}
                                            type="button"
                                            onClick={() => setCurrentIndex(idx)}
                                            aria-label={`Ir para a imagem ${idx + 1}`}
                                            aria-pressed={idx === currentIndex}
                                            className={`flex min-h-11 w-11 items-center justify-center rounded-full focus-visible:outline focus-visible:outline-2 focus-visible:outline-white`}
                                        >
                                            <span aria-hidden="true" className={`h-2 rounded-full transition-all ${idx === currentIndex ? 'w-8 bg-brand-secondary' : 'w-2 bg-white/80'}`} />
                                        </button>
                                    ))}
                                    <button
                                        type="button"
                                        onClick={toggleRotation}
                                        aria-label={shouldRotate ? 'Pausar rotação automática' : 'Retomar rotação automática'}
                                        className="flex min-h-11 min-w-11 items-center justify-center rounded-full bg-black/60 text-white hover:bg-black/80 focus-visible:outline focus-visible:outline-2 focus-visible:outline-white"
                                    >
                                        {shouldRotate
                                            ? <Pause aria-hidden="true" className="h-5 w-5" />
                                            : <Play aria-hidden="true" className="h-5 w-5" />}
                                    </button>
                                </div>
                            </>
                        )}
                    </div>

                    {/* Content Area */}
                    <motion.div
                        initial={prefersReducedMotion ? false : { opacity: 0, y: 20 }}
                        whileInView={{ opacity: 1, x: 0 }}
                        viewport={{ once: true }}
                        transition={{ duration: prefersReducedMotion ? 0 : 0.8 }}
                        className='flex flex-col items-center'
                    >
                        <h2
                        style={{ fontFamily: 'DN'  }}
                        className="text-brand-secondary text-outline self-center tracking-widest uppercase text-5xl mb-4 block"
                        >Sobre a Marca
                        </h2>
                        <div className="relative aspect-square w-full max-w-[360px] overflow-hidden flex items-center justify-center">
                        <Image 
                            src="/assets/Images/novo-logotipo.png" 
                            alt="DN Store Logo" 
                            fill 
                            className="object-contain "
                        />
                    </div>
                        
                        <div className="space-y-6 text-brand-primary text-base md:text-lg mb-10 leading-relaxed">
                            <p>
                               O Desafio Natureza nasceu em Lorena (SP) com a proposta de reunir apaixonados por mountain bike e aventura ao ar livre. Mais do que um evento, tornou-se uma comunidade que valoriza superação, esporte e conexão com a natureza.
                            </p>
                            <p>
                                A loja Desafio Natureza surge para levar esse espírito além das pistas. Aqui você encontra produtos desenvolvidos para quem vive o ciclismo e valoriza conforto, desempenho e qualidade em cada pedal. Mais do que uma marca, o Desafio Natureza representa uma comunidade movida pela paixão pelo esporte, pela natureza e pelos desafios que nos fazem ir sempre mais longe.
                            </p>
                        </div>

                        <Link href="/sobre" className="inline-flex w-70 flex items-center gap-3 bg-brand-red-primary text-white text-base font-bold px-8 py-4 rounded-full hover:bg-foreground transition-colors outline-none focus:ring-4 focus:ring-brand-secondary/30">
                            Saiba Mais Sobre Nós
                            <ArrowRight className="w-5 h-5" />
                        </Link>
                    </motion.div>
                </div>
            </div>
        </section>
    );
}
