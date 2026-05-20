import Image from 'next/image';
import { Leaf, Mountain, ShieldCheck, Compass } from 'lucide-react';
import Link from 'next/link';

export default function SobrePage() {
    return (
        <div className="min-h-screen bg-background pt-24 pb-20 text-foreground overflow-hidden">
            {/* Hero Section */}
            <section className="relative w-full h-[60vh] flex items-center justify-center">
                <div className="absolute inset-0 bg-black/60 z-10" /> {/* Overlay escuro */}
                <div 
                    className="absolute inset-0 bg-cover bg-center bg-no-repeat"
                    style={{ backgroundImage: 'url("https://images.unsplash.com/photo-1551632811-561732d1e306?q=80&w=2070&auto=format&fit=crop")' }}
                />
                <div className="relative z-20 text-center px-4 max-w-4xl mx-auto">
                    <h1 className="text-5xl md:text-7xl font-bold text-white mb-6 tracking-tight">
                        Desafio <span className="text-brand-secondary">Natureza</span>
                    </h1>
                    <p className="text-xl text-gray-200 mb-8 max-w-2xl mx-auto">
                        Mais do que vestuário, um estilo de vida. Nascemos da paixão por superar limites, explorar o desconhecido e vestir o espírito da aventura com excelência e qualidade premium.
                    </p>
                    <Link 
                        href="/produtos" 
                        className="inline-flex items-center px-8 py-3 rounded-full bg-brand-secondary text-white font-medium hover:bg-red-700 transition-colors"
                    >
                        Conheça as Coleções
                    </Link>
                </div>
            </section>

            {/* Nossa Essência */}
            <section className="py-20 px-4">
                <div className="container mx-auto max-w-6xl">
                    <div className="flex flex-col md:flex-row items-center gap-16">
                        <div className="w-full md:w-1/2">
                            <h2 className="text-3xl font-bold mb-6 text-white border-b-2 border-brand-secondary inline-block pb-2">A Nossa Essência</h2>
                            <p className="text-gray-300 leading-relaxed mb-4">
                                A DN Store (Desafio Natureza) foi criada para aqueles que não se contentam com o básico. Nossas peças são projetadas para resistir aos desafios diários, sejam eles nas ruas da cidade ou nas trilhas mais inóspitas.
                            </p>
                            <p className="text-gray-300 leading-relaxed mb-6">
                                Unimos design contemporâneo, estética premium e materiais de altíssima durabilidade. Cada costura reflete o nosso compromisso com a excelência, garantindo conforto sem abrir mão da atitude.
                            </p>
                            <div className="grid grid-cols-2 gap-6 mt-10">
                                <div className="flex items-start gap-4">
                                    <div className="p-3 bg-[#2A2A2A] border border-white/5 rounded-lg text-brand-secondary">
                                        <Mountain className="w-6 h-6" />
                                    </div>
                                    <div>
                                        <h4 className="font-bold text-white">Aventura</h4>
                                        <p className="text-sm text-gray-400">Inspirados na vida ao ar livre.</p>
                                    </div>
                                </div>
                                <div className="flex items-start gap-4">
                                    <div className="p-3 bg-[#2A2A2A] border border-white/5 rounded-lg text-brand-secondary">
                                        <ShieldCheck className="w-6 h-6" />
                                    </div>
                                    <div>
                                        <h4 className="font-bold text-white">Durabilidade</h4>
                                        <p className="text-sm text-gray-400">Feito para durar anos.</p>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div className="w-full md:w-1/2 relative">
                            <div className="absolute inset-0 bg-brand-secondary/20 rounded-2xl transform translate-x-4 translate-y-4" />
                            <div className="relative aspect-square md:aspect-auto md:h-[600px] w-full rounded-2xl overflow-hidden border border-white/10">
                                <Image 
                                    src="https://images.unsplash.com/photo-1522163182402-834f871fd851?q=80&w=2003&auto=format&fit=crop"
                                    alt="Estilo de vida ao ar livre"
                                    fill
                                    className="object-cover"
                                />
                            </div>
                        </div>
                    </div>
                </div>
            </section>

            {/* Qualidade e Materiais */}
            <section className="py-20 bg-[#2A2A2A] border-y border-white/5 px-4">
                <div className="container mx-auto max-w-6xl text-center">
                    <h2 className="text-3xl font-bold mb-4 text-white">O Padrão DN</h2>
                    <p className="text-gray-400 max-w-2xl mx-auto mb-16">
                        Trabalhamos apenas com tecidos selecionados rigorosamente para entregar a melhor experiência de uso possível, do primeiro contato na pele até depois de dezenas de lavagens.
                    </p>

                    <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
                        <div className="bg-background border border-white/5 p-8 rounded-xl hover:border-brand-secondary/50 transition-colors">
                            <div className="w-14 h-14 bg-[#2A2A2A] rounded-full flex items-center justify-center mx-auto mb-6 text-white">
                                <Leaf className="w-7 h-7" />
                            </div>
                            <h3 className="text-xl font-bold text-white mb-3">Materiais Premium</h3>
                            <p className="text-gray-400 text-sm">
                                Fibras de alta densidade e algodão sustentável que proporcionam um toque macio incomparável e respirabilidade essencial.
                            </p>
                        </div>
                        <div className="bg-background border border-white/5 p-8 rounded-xl hover:border-brand-secondary/50 transition-colors">
                            <div className="w-14 h-14 bg-[#2A2A2A] rounded-full flex items-center justify-center mx-auto mb-6 text-white">
                                <Compass className="w-7 h-7" />
                            </div>
                            <h3 className="text-xl font-bold text-white mb-3">Design Funcional</h3>
                            <p className="text-gray-400 text-sm">
                                Modelagens testadas e ajustadas milimetricamente para garantir total liberdade de movimento e caimento perfeito no corpo.
                            </p>
                        </div>
                        <div className="bg-background border border-white/5 p-8 rounded-xl hover:border-brand-secondary/50 transition-colors">
                            <div className="w-14 h-14 bg-[#2A2A2A] rounded-full flex items-center justify-center mx-auto mb-6 text-white">
                                <ShieldCheck className="w-7 h-7" />
                            </div>
                            <h3 className="text-xl font-bold text-white mb-3">Garantia de Qualidade</h3>
                            <p className="text-gray-400 text-sm">
                                Cada peça passa por um controle de qualidade exigente. Se não estiver 100% perfeito, não chega até você.
                            </p>
                        </div>
                    </div>
                </div>
            </section>
        </div>
    );
}
