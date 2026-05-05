'use client';

import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import Image from 'next/image';
import Link from 'next/link';
import { ArrowRight, ChevronLeft, ChevronRight } from 'lucide-react';

interface SobreProps {
    images: string[];
}

export function Sobre({ images }: SobreProps) {
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
                                    src={"/assets/images/pelotaodn.jpeg"}
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
                        className='flex flex-col items-center'
                    >
                        <span className="text-brand-secondary self-center font-bold tracking-widest uppercase text-4xl mb-4 block">Sobre a Marca</span>
                        <div className="relative w-90 h-90 overflow-hidden flex items-center justify-center">
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

                        <Link href="/sobre" className="inline-flex w-70 flex items-center gap-3 bg-brand-secondary text-white text-base font-bold px-8 py-4 rounded-full hover:bg-foreground transition-colors outline-none focus:ring-4 focus:ring-brand-secondary/30">
                            Saiba Mais Sobre Nós
                            <ArrowRight className="w-5 h-5" />
                        </Link>
                    </motion.div>
                </div>
            </div>
        </section>
    );
}
