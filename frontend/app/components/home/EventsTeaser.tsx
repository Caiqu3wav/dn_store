'use client';

import { motion } from 'framer-motion';
import Link from 'next/link';
import Image from 'next/image';
import { Calendar, MapPin, ExternalLink, Timer, ArrowRight } from 'lucide-react';
import { EVENTS, NEXT_EVENT } from '../../../lib/data';

export function EventsTeaser() {
    return (
        <section
         id="eventos"
        className="py-24 bg-brand-primary relative overflow-hidden">
            <div className="container mx-auto px-4 lg:px-8 relative z-10">
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 lg:gap-20 items-start">
                    
                    {/* Left: Banner, Text and Upcoming Events List */}
                    <motion.div
                        initial={{ opacity: 0, x: -30 }}
                        whileInView={{ opacity: 1, x: 0 }}
                        viewport={{ once: true }}
                        transition={{ duration: 0.8 }}
                    >
                        <span className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-brand-red-primary/20 text-brand-secondary font-bold text-sm mb-6">
                            <Timer className="w-4 h-4" />
                            Próximos Desafios
                        </span>
                        <h2 style={{ fontFamily: 'DN'  }}
                        className="text-4xl md:text-5xl lg:text-6xl  mb-6 leading-tight tracking-tight">
                            Ultrapasse seus <br />
                            <span style={{ fontFamily: 'DN'  }}
                            className="text-brand-red-primary text-4xl md:text-5xl lg:text-6xl  mb-6 leading-tight tracking-tight">
                                Próprios Limites</span>
                        </h2>
                        <p className="text-foreground text-lg mb-8 leading-relaxed max-w-xl">
                            A verdadeira adrenalina acontece nas trilhas. Participe do nosso circuito anual e conecte-se com ciclistas que compartilham da mesma paixão que você.
                        </p>

                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-8">
                            {EVENTS.map((event) => (
                                <div key={event.id} className="rounded-3xl border border-white/10 bg-white/10 p-5 backdrop-blur-sm">
                                    <span className="text-xs uppercase tracking-[0.3em] text-brand-red-primary font-bold">
                                        {event.date}
                                    </span>
                                    <h3 className="text-xl font-black mt-3 leading-tight">
                                        {event.name}
                                    </h3>
                                    <p className="text-sm text-gray-500 mt-2">{event.location}</p>
                                </div>
                            ))}
                        </div>
                        <Link href="/eventos" className="inline-flex items-center gap-2 text-brand-red-primary font-bold hover:text-brand-secondary transition-colors mt-2 text-lg">
                             Ver Todos os Eventos <ArrowRight className="w-5 h-5" />
                        </Link>
                    </motion.div>

                    {/* Right: Event Card */}
                    <motion.div
                        initial={{ opacity: 0, y: 30 }}
                        whileInView={{ opacity: 1, y: 0 }}
                        viewport={{ once: true }}
                        transition={{ duration: 0.8, delay: 0.2 }}
                        className="bg-white rounded-3xl p-6 sm:p-8 md:p-10 shadow-2xl text-brand border border-gray-100"
                    >
                        <h3 className="text-3xl text-brand font-black mb-2">{NEXT_EVENT.name}</h3>
                        <p className="text-brand mb-8 font-medium">{NEXT_EVENT.description}</p>
                        
                        <div className="space-y-6 mb-8">
                            <div className="flex items-center gap-4">
                                <div className="w-12 h-12 bg-gray-50 rounded-2xl flex items-center justify-center shrink-0">
                                    <Calendar className="w-6 h-6 text-brand-secondary" />
                                </div>
                                <div>
                                    <span className="block text-sm text-gray-400 font-bold uppercase tracking-wider">Data do Evento</span>
                                    <span className="block font-bold mt-0.5">{NEXT_EVENT.date}</span>
                                </div>
                            </div>

                            <div className="flex items-center gap-4">
                                <div className="w-12 h-12 bg-gray-50 rounded-2xl flex items-center justify-center shrink-0">
                                    <MapPin className="w-6 h-6 text-brand-highlight" />
                                </div>
                                <div>
                                    <span className="block text-sm text-gray-400 font-bold uppercase tracking-wider">Localização</span>
                                    <span className="block text-brand font-bold mt-0.5">{NEXT_EVENT.location}</span>
                                </div>
                            </div>
                        </div>

{/* Visual Map */}
<div className="relative w-full h-40 sm:h-48 rounded-2xl overflow-hidden mb-8 border border-gray-200 group">

    {/* Google Maps */}
    <iframe
        src="https://www.google.com/maps/embed?pb=!4v1780628254951!6m8!1m7!1snAuHmHMTINLz-uPJu-z__g!2m2!1d-22.75262943589267!2d-45.10532564538919!3f86.66!4f-14.620000000000005!5f0.7820865974627469"
        width="100%"
        height="100%"
        style={{ border: 0 }}
        allowFullScreen
        loading="lazy"
        referrerPolicy="no-referrer-when-downgrade"
        className="absolute inset-0"
    />

    {/* Capa */}
    <div className="absolute inset-0 z-10 bg-white group-hover:hidden">
        <Image
            src="/assets/Images/local_dn_lorena.png"
            alt="Local da etapa Lorena"
            fill
            className="object-cover"
        />

        <div className="absolute inset-0 bg-black/40 flex items-center justify-center">
            <div className="text-center text-white">
                <MapPin className="w-8 h-8 mx-auto mb-2" />
                <p className="font-bold">Passe o mouse para abrir o mapa</p>
            </div>
        </div>
    </div>

</div>

<Link
    href={NEXT_EVENT.mapsLink}
    target="_blank"
    rel="noopener noreferrer"
    className="flex items-center justify-center gap-2 w-full bg-brand-primary font-bold py-4 rounded-xl hover:outline hover:outline-2 hover:outline-black transition-all focus:ring-4"
>
    Abrir no Google Maps
    <ExternalLink className="w-5 h-5" />
</Link>

<Link
    href="https://desafionatureza.com.br"
    target="_blank"
    rel="noopener noreferrer"
    className="bg-brand-red-primary text-white font-black px-5 py-2 rounded-lg transition-colors hover:bg-foreground block mx-auto my-5 text-center"
>
    Inscreva-se
</Link>

</motion.div>
</div>
</div>
</section>
);
}