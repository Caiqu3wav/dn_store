'use client';

import { motion } from 'framer-motion';
import Link from 'next/link';
import Image from 'next/image';
import { Calendar, MapPin, ExternalLink, Timer, ArrowRight, Mountain, Users, Trophy } from 'lucide-react';
import { EVENTS, NEXT_EVENT } from '../../lib/data';

export default function EventosPage() {
  return (
    <div className="min-h-screen bg-background">
      {/* Hero Section */}
      <section className="relative pt-32 pb-20 lg:pt-48 lg:pb-32 overflow-hidden bg-foreground">
        <div className="absolute inset-0 z-0 opacity-40">
           <Image 
            src="/assets/bg_hero.png" 
            alt="Eventos Hero" 
            fill 
            className="object-cover"
            priority
          />
          <div className="absolute inset-0 bg-gradient-to-t from-foreground via-foreground/80 to-transparent" />
        </div>
        
        <div className="container mx-auto px-4 relative z-10 text-center">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8 }}
          >
            <span className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-brand-red-primary/20 text-brand-red-secondary font-bold text-sm mb-6 border border-brand-red-primary/30 shadow-[0_0_15px_rgba(199,8,8,0.3)] backdrop-blur-md">
              <Mountain className="w-4 h-4" />
              Circuito Anual
            </span>
            <h1 
              style={{ fontFamily: 'DN' }} 
              className="text-5xl md:text-7xl lg:text-8xl text-white mb-6 tracking-wider uppercase text-outline drop-shadow-2xl"
            >
              Nossos <span className="text-brand-red-primary">Eventos</span>
            </h1>
            <p className="text-gray-300 text-lg md:text-xl max-w-2xl mx-auto leading-relaxed">
              Junte-se à maior comunidade de ciclistas. Supere seus limites, explore novas trilhas e viva a verdadeira adrenalina em nossas etapas anuais.
            </p>
          </motion.div>
        </div>
      </section>

      {/* Featured Event Section */}
      <section className="py-24 bg-brand-primary relative">
        <div className="container mx-auto px-4 lg:px-8">
          <div className="mb-16 text-center">
            <h2 style={{ fontFamily: 'DN' }} className="text-4xl md:text-5xl text-foreground tracking-tight">
              Próximo <span className="text-brand-red-primary">Desafio</span>
            </h2>
            <div className="w-24 h-1 bg-brand-red-primary mx-auto mt-6 rounded-full" />
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 lg:gap-20 items-center">
            {/* Event Info */}
            <motion.div
              initial={{ opacity: 0, x: -30 }}
              whileInView={{ opacity: 1, x: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.8 }}
            >
              <div className="bg-white rounded-3xl p-8 md:p-12 shadow-2xl border border-gray-100 relative overflow-hidden">
                <div className="absolute top-0 right-0 w-32 h-32 bg-brand-red-primary/5 rounded-bl-full -z-10" />
                <div className="absolute bottom-0 left-0 w-24 h-24 bg-brand-highlight/5 rounded-tr-full -z-10" />

                <span className="inline-flex items-center gap-2 text-brand-red-primary font-bold text-sm tracking-[0.2em] uppercase mb-4">
                  <Timer className="w-4 h-4" />
                  Inscrições Abertas
                </span>
                
                <h3 className="text-4xl md:text-5xl font-black text-foreground mb-6 leading-tight">
                  {NEXT_EVENT.name}
                </h3>
                
                <p className="text-gray-600 text-lg mb-8 leading-relaxed">
                  {NEXT_EVENT.description}
                </p>

                <div className="space-y-6 mb-10">
                  <div className="flex items-center gap-4">
                    <div className="w-14 h-14 bg-gray-50 rounded-2xl flex items-center justify-center shrink-0 border border-gray-100 shadow-sm">
                      <Calendar className="w-7 h-7 text-brand-red-primary" />
                    </div>
                    <div>
                      <span className="block text-sm text-gray-400 font-bold uppercase tracking-wider mb-1">Data</span>
                      <span className="block text-lg font-bold text-foreground">{NEXT_EVENT.date}</span>
                    </div>
                  </div>

                  <div className="flex items-center gap-4">
                    <div className="w-14 h-14 bg-gray-50 rounded-2xl flex items-center justify-center shrink-0 border border-gray-100 shadow-sm">
                      <MapPin className="w-7 h-7 text-brand-highlight" />
                    </div>
                    <div>
                      <span className="block text-sm text-gray-400 font-bold uppercase tracking-wider mb-1">Localização</span>
                      <span className="block text-lg font-bold text-foreground">{NEXT_EVENT.location}</span>
                    </div>
                  </div>
                  
                  <div className="flex items-center gap-4">
                    <div className="w-14 h-14 bg-gray-50 rounded-2xl flex items-center justify-center shrink-0 border border-gray-100 shadow-sm">
                      <Users className="w-7 h-7 text-brand" />
                    </div>
                    <div>
                      <span className="block text-sm text-gray-400 font-bold uppercase tracking-wider mb-1">Vagas</span>
                      <span className="block text-lg font-bold text-foreground">Limitadas - Garanta a sua!</span>
                    </div>
                  </div>
                </div>

                <div className="flex flex-col sm:flex-row gap-4">
                  <Link
                    href="https://desafionatureza.com.br"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex-1 bg-brand-red-primary text-white font-black px-6 py-4 rounded-xl transition-all hover:bg-brand-red-secondary hover:shadow-lg hover:-translate-y-1 text-center text-lg flex items-center justify-center gap-2"
                  >
                    Garantir Inscrição
                    <ArrowRight className="w-5 h-5" />
                  </Link>
                </div>
              </div>
            </motion.div>

            {/* Event Media / Map */}
            <motion.div
              initial={{ opacity: 0, y: 30 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.8, delay: 0.2 }}
              className="relative h-full min-h-[400px] lg:min-h-[600px] rounded-3xl overflow-hidden shadow-2xl group border-4 border-white"
            >
              {/* Google Maps Embed */}
              <iframe
                  src="https://www.google.com/maps/embed?pb=!4v1780628254951!6m8!1m7!1snAuHmHMTINLz-uPJu-z__g!2m2!1d-22.75262943589267!2d-45.10532564538919!3f86.66!4f-14.620000000000005!5f0.7820865974627469"
                  width="100%"
                  height="100%"
                  style={{ border: 0 }}
                  allowFullScreen
                  loading="lazy"
                  referrerPolicy="no-referrer-when-downgrade"
                  className="absolute inset-0 z-0"
              />

              {/* Cover Image that hides on hover */}
              <div className="absolute inset-0 z-10 bg-white transition-opacity duration-500 group-hover:opacity-0 group-hover:pointer-events-none">
                  {NEXT_EVENT.image ? (
                    <Image
                        src={NEXT_EVENT.image}
                        alt={`Local da ${NEXT_EVENT.name}`}
                        fill
                        className="object-cover"
                    />
                  ) : (
                    <Image
                        src="/assets/Images/local_dn_lorena.png"
                        alt="Local da etapa"
                        fill
                        className="object-cover"
                    />
                  )}
                  
                  <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/30 to-transparent flex flex-col justify-end p-8">
                    <div className="transform translate-y-4 group-hover:translate-y-0 transition-transform duration-300">
                      <div className="flex items-center gap-2 text-white/80 mb-2">
                        <MapPin className="w-5 h-5" />
                        <span className="font-medium text-sm tracking-wide uppercase">Passe o mouse para explorar</span>
                      </div>
                      <h4 className="text-3xl font-black text-white">{NEXT_EVENT.location}</h4>
                    </div>
                  </div>
              </div>
              
              {/* Map overlay controls */}
              <div className="absolute top-4 right-4 z-0 opacity-0 group-hover:opacity-100 transition-opacity duration-300">
                 <Link
                    href={NEXT_EVENT.mapsLink || "#"}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-2 bg-white text-foreground font-bold px-4 py-2 rounded-lg shadow-lg hover:bg-gray-50 transition-colors text-sm"
                >
                    Abrir no Maps
                    <ExternalLink className="w-4 h-4" />
                </Link>
              </div>
            </motion.div>
          </div>
        </div>
      </section>

      {/* Upcoming Events Schedule */}
      <section className="py-24 bg-foreground relative">
        <div className="container mx-auto px-4 lg:px-8">
          <div className="text-center mb-16">
             <h2 style={{ fontFamily: 'DN' }} className="text-4xl md:text-5xl text-white tracking-tight">
              Calendário <span className="text-brand-highlight">Anual</span>
            </h2>
            <p className="text-gray-400 mt-4 max-w-2xl mx-auto text-lg">
              Programe-se para as próximas etapas do nosso circuito. Prepare sua bike e venha fazer história com a gente.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            {EVENTS.map((event, index) => {
              const isPast = index < EVENTS.findIndex(e => e.id === NEXT_EVENT.id);
              const isNext = event.id === NEXT_EVENT.id;
              
              return (
                <motion.div 
                  key={event.id}
                  initial={{ opacity: 0, y: 20 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true }}
                  transition={{ duration: 0.5, delay: index * 0.1 }}
                  className={`relative rounded-3xl p-6 backdrop-blur-sm border flex flex-col h-full transition-all duration-300 ${
                    isNext 
                      ? 'bg-brand-red-primary/10 border-brand-red-primary shadow-[0_0_30px_rgba(199,8,8,0.15)] hover:bg-brand-red-primary/20 hover:-translate-y-2' 
                      : isPast
                        ? 'bg-white/5 border-white/5 opacity-60 grayscale hover:grayscale-0 hover:opacity-100'
                        : 'bg-white/5 border-white/10 hover:bg-white/10 hover:border-white/20 hover:-translate-y-2'
                  }`}
                >
                  {isNext && (
                    <div className="absolute -top-3 left-1/2 -translate-x-1/2 bg-brand-red-primary text-white text-xs font-black px-4 py-1 rounded-full uppercase tracking-wider shadow-lg whitespace-nowrap">
                      Próxima Etapa
                    </div>
                  )}
                  {isPast && (
                    <div className="absolute -top-3 left-1/2 -translate-x-1/2 bg-gray-600 text-white text-xs font-bold px-3 py-1 rounded-full uppercase tracking-wider">
                      Realizado
                    </div>
                  )}
                  
                  <div className="mb-6 flex-1">
                    <span className={`text-xs uppercase tracking-[0.2em] font-bold block mb-2 ${isNext ? 'text-brand-red-primary' : 'text-gray-400'}`}>
                      {event.date}
                    </span>
                    <h3 className="text-2xl font-black text-white mb-2 leading-tight">
                      {event.name}
                    </h3>
                    <p className="text-gray-400 text-sm line-clamp-3">
                      {event.description}
                    </p>
                  </div>

                  <div className="mt-auto pt-6 border-t border-white/10">
                    <div className="flex items-center gap-2 text-gray-300 mb-4">
                      <MapPin className={`w-4 h-4 ${isNext ? 'text-brand-red-primary' : ''}`} />
                      <span className="text-sm font-medium">{event.location}</span>
                    </div>
                    
                    {isNext ? (
                      <button className="w-full bg-brand-red-primary text-white font-bold py-3 rounded-lg hover:bg-brand-red-secondary transition-colors text-sm">
                        Inscreva-se
                      </button>
                    ) : isPast ? (
                      <button className="w-full bg-white/10 text-white font-bold py-3 rounded-lg hover:bg-white/20 transition-colors text-sm" disabled>
                        Ver Cobertura
                      </button>
                    ) : (
                      <button className="w-full bg-white text-foreground font-bold py-3 rounded-lg hover:bg-gray-100 transition-colors text-sm">
                        Saiba Mais
                      </button>
                    )}
                  </div>
                </motion.div>
              )
            })}
          </div>
        </div>
      </section>

      {/* Stats/Benefits Section */}
      <section className="py-20 bg-brand-primary border-t border-gray-100">
        <div className="container mx-auto px-4">
           <div className="grid grid-cols-1 md:grid-cols-3 gap-10">
              <motion.div 
                initial={{ opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                className="text-center"
              >
                <div className="w-20 h-20 mx-auto bg-brand-red-primary/10 rounded-full flex items-center justify-center mb-6">
                  <Trophy className="w-10 h-10 text-brand-red-primary" />
                </div>
                <h4 className="text-2xl font-black text-foreground mb-3">Competição Saudável</h4>
                <p className="text-gray-600">Desafie a si mesmo e a outros ciclistas em percursos cuidadosamente planejados para todos os níveis.</p>
              </motion.div>
              
              <motion.div 
                initial={{ opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ delay: 0.2 }}
                className="text-center"
              >
                <div className="w-20 h-20 mx-auto bg-brand-highlight/10 rounded-full flex items-center justify-center mb-6">
                  <Mountain className="w-10 h-10 text-brand-highlight" />
                </div>
                <h4 className="text-2xl font-black text-foreground mb-3">Contato com a Natureza</h4>
                <p className="text-gray-600">Explore paisagens incríveis, respire ar puro e conecte-se com o meio ambiente de forma sustentável.</p>
              </motion.div>

              <motion.div 
                initial={{ opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ delay: 0.4 }}
                className="text-center"
              >
                <div className="w-20 h-20 mx-auto bg-gray-100 rounded-full flex items-center justify-center mb-6">
                  <Users className="w-10 h-10 text-brand" />
                </div>
                <h4 className="text-2xl font-black text-foreground mb-3">Comunidade Unida</h4>
                <p className="text-gray-600">Faça novas amizades, troque experiências e faça parte da família Desafio Natureza em cada etapa.</p>
              </motion.div>
           </div>
        </div>
      </section>

    </div>
  );
}
