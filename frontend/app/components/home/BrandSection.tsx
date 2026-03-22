'use client';

import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import Image from 'next/image';
import Link from 'next/link';
import { ArrowRight, ChevronLeft, ChevronRight } from 'lucide-react';

interface BrandSectionProps {
    images: string[];
}

export function BrandSection({ images }: BrandSectionProps) {
    const [currentIndex, setCurrentIndex] = useState(0);

    // Auto-play for carousel
    useEffect(() => {
        if (images.length > 1) {
            const timer = setInterval(() => {
                setCurrentIndex((prev) => (prev + 1) % images.length);
            }, 5000);
            return () => clearInterval(timer);
        }
    }, [images.length]);

    const nextSlide = () => setCurrentIndex((prev) => (prev + 1) % images.length);
    const prevSlide = () => setCurrentIndex((prev) => (prev === 0 ? images.length - 1 : prev - 1));

    return (
        <section className="py-24 bg-background">
            <div className="container mx-auto px-4 lg:px-8">
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 lg:gap-20 items-center">
                    
                    {/* Image Area with Carousel */}
                    <div className="relative h-[400px] lg:h-[600px] w-full rounded-[2rem] overflow-hidden shadow-2xl group">
                        <AnimatePresence mode="wait">
                            <motion.div
                                key={currentIndex}
                                initial={{ opacity: 0, scale: 1.05 }}
                                animate={{ opacity: 1, scale: 1 }}
                                exit={{ opacity: 0 }}
                                transition={{ duration: 0.8 }}
                                className="absolute inset-0"
                            >
                                <Image
                                    src={images[currentIndex]}
                                    alt={`Sobre a DN Store imagem ${currentIndex + 1}`}
                                    fill
                                    className="object-cover"
                                />
                            </motion.div>
                        </AnimatePresence>

                        {/* Carousel Controls (only if > 1 image) */}
                        {images.length > 1 && (
                            <>
                                <button 
                                    onClick={prevSlide}
                                    className="absolute left-4 top-1/2 -translate-y-1/2 w-10 h-10 rounded-full bg-black/40 text-white flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity hover:bg-black/60 backdrop-blur-sm"
                                >
                                    <ChevronLeft className="w-6 h-6" />
                                </button>
                                <button 
                                    onClick={nextSlide}
                                    className="absolute right-4 top-1/2 -translate-y-1/2 w-10 h-10 rounded-full bg-black/40 text-white flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity hover:bg-black/60 backdrop-blur-sm"
                                >
                                    <ChevronRight className="w-6 h-6" />
                                </button>
                                
                                {/* Indicators */}
                                <div className="absolute bottom-6 left-1/2 -translate-x-1/2 flex items-center gap-2">
                                    {images.map((_, idx) => (
                                        <button
                                            key={idx}
                                            onClick={() => setCurrentIndex(idx)}
                                            className={`h-2 rounded-full transition-all ${idx === currentIndex ? 'w-8 bg-brand-secondary' : 'w-2 bg-white/60'}`}
                                            aria-label={`Ir para a imagem ${idx + 1}`}
                                        />
                                    ))}
                                </div>
                            </>
                        )}
                    </div>

                    {/* Content Area */}
                    <motion.div
                        initial={{ opacity: 0, x: 40 }}
                        whileInView={{ opacity: 1, x: 0 }}
                        viewport={{ once: true }}
                        transition={{ duration: 0.8 }}
                    >
                        <span className="text-brand-highlight font-bold tracking-widest uppercase text-sm mb-4 block">Sobre a Marca</span>
                        <h2 className="text-4xl md:text-5xl lg:text-6xl font-black text-brand-primary mb-6 tracking-tight leading-tight">
                            Desafio Natureza
                        </h2>
                        
                        <div className="space-y-6 text-gray-600 text-base md:text-lg mb-10 leading-relaxed">
                            <p>
                                Nascemos na trilha. Nossa jornada começou com a paixão pelo mountain bike e a necessidade de equipamentos que realmente acompanhassem o ritmo intenso das montanhas.
                            </p>
                            <p>
                                Hoje, a DN Store veste ciclistas que não têm medo de subir mais alto. Cada peça é desenhada pensando em resistência, respirabilidade e, claro, um estilo inconfundível.
                            </p>
                        </div>

                        <Link href="/sobre" className="inline-flex items-center gap-3 bg-brand-primary text-white text-base font-bold px-8 py-4 rounded-full hover:bg-brand-secondary transition-colors outline-none focus:ring-4 focus:ring-brand-secondary/30">
                            Saiba Mais Sobre Nós
                            <ArrowRight className="w-5 h-5" />
                        </Link>
                    </motion.div>
                </div>
            </div>
        </section>
    );
}
