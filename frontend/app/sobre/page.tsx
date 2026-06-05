import Image from 'next/image';
import { Leaf, Mountain, ShieldCheck, Compass } from 'lucide-react';
import Link from 'next/link';

export default function SobrePage() {
    return (
        <div className="min-h-screen bg-background pt-2 pb-20 text-foreground overflow-hidden">
            {/* Hero Section */}
            <section className="relative w-full h-[70vh] flex items-center justify-center">
                <div className="absolute inset-0 bg-black/60 z-10" /> {/* Overlay escuro */}
                <div
    className="absolute inset-0 bg-cover bg-center bg-no-repeat"
    style={{ backgroundImage: 'url("/assets/Images/foto_sobre_dn.jpeg")' }}
/>
                <div className="relative z-20 text-center px-4 max-w-4xl mx-auto">
                    <h1 style={{ fontFamily: 'DN'  }}
                     className="text-5xl md:text-7xl text-white mb-6 tracking-tight">
                        Desafio
                        <span style={{ fontFamily: 'DN'  }}
                         className="text-white"> Natureza®</span> <p>
                            <span style={{ fontFamily: 'DN'  }}
                         className="text-white"> Store</span>
                         </p>
                    </h1>
                    <Link 
                        href="/produtos" 
                        className="inline-flex items-center px-8 py-3 rounded-full bg-brand-secondary text-white font-medium hover:bg-red-700 transition-colors"
                    >
                        Conheça os Produtos
                    </Link>
                </div>
            </section>

            {/* Nossa Essência */}
            <section className="py-20 px-4">
                <div className="container mx-auto max-w-6xl">
                    <div className="flex flex-col md:flex-row items-center gap-16">
                        <div className="w-full md:w-1/2">
                            <h2 style={{ fontFamily: 'DN'  }}
                            className="text-4xl mb-6 text-white border-b-2 border-brand-secondary inline-block pb-2">Quem Somos</h2>
                            <p className="text-white leading-relaxed mb-4">
                               A DN Store nasceu da mesma paixão que move o Desafio Natureza desde 2011: superar limites, viver novas experiências e encarar cada desafio com determinação. Inspirados pelas trilhas, montanhas e pela energia dos atletas que fazem parte da nossa história, criamos produtos que carregam o espírito aventureiro do mountain bike e da vida ao ar livre.

Mais do que uma marca, somos uma extensão da comunidade Desafio Natureza. Cada peça é desenvolvida para oferecer conforto, qualidade e estilo, acompanhando você nos treinos, competições e também no dia a dia.
                            </p>
                            <p className="text-white leading-relaxed mb-6">
                                Unimos design contemporâneo, estética premium e materiais de altíssima durabilidade. Cada costura reflete o nosso compromisso com a excelência, garantindo conforto sem abrir mão da atitude.
                            </p>
                            <div className="grid grid-cols-2 gap-6 mt-10">
                                <div className="flex items-start gap-4">
                                    <div className="p-3 bg-[#2A2A2A] border border-white/5 rounded-lg text-brand-secondary">
                                        <Mountain className="w-6 h-6" />
                                    </div>
                                    <div>
                                        <h4 className="font-bold text-white">Natureza</h4>
                                        <p className="text-sm text-gray-400">Conectados às trilhas, paisagens e ao espírito de aventura.</p>
                                    </div>
                                </div>
                                <div className="flex items-start gap-4">
                                    <div className="p-3 bg-[#2A2A2A] border border-white/5 rounded-lg text-brand-secondary">
                                        <ShieldCheck className="w-6 h-6" />
                                    </div>
                                    <div>
                                        <h4 className="font-bold text-white">Superação</h4>
                                        <p className="text-sm text-gray-400">Inspirados por atletas que transformam desafios em conquistas.</p>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div className="w-full md:w-1/2 relative">
                            <div className="absolute inset-0 bg-brand-secondary/20 rounded-2xl transform translate-x-4 translate-y-4" />
                            <div className="relative aspect-square md:aspect-auto md:h-[600px] w-full rounded-2xl overflow-hidden border border-white/10">
                                <Image 
                                    src="/assets/Images/tenda_dn.jpeg"
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
                    <h2 style={{ fontFamily: 'DN'  }}
                    className="text-5xl mb-4 text-white">O Padrão DN</h2>
                    <p className="text-white max-w-2xl mx-auto mb-16">
                       Acreditamos que cada conquista começa com um desafio. Fazemos parte de uma comunidade que valoriza esforço, evolução e a satisfação de cruzar a linha de chegada.
                    </p>

                    <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
                        <div className="bg-background border border-white/5 p-8 rounded-xl hover:border-brand-secondary/50 transition-colors">
                            <div className="w-14 h-14 bg-[#2A2A2A] rounded-full flex items-center justify-center mx-auto mb-6 text-brand-secondary">
                                <Leaf className="w-7 h-7" />
                            </div>
                            <h3 className="text-xl font-bold text-white mb-3">Materiais Premium</h3>
                            <p className="text-white text-sm">
                                Fibras de alta densidade e algodão sustentável que proporcionam um toque macio incomparável e respirabilidade essencial.
                            </p>
                        </div>
                        <div className="bg-background border border-white/5 p-8 rounded-xl hover:border-brand-secondary/50 transition-colors">
                            <div className="w-14 h-14 bg-[#2A2A2A] rounded-full flex items-center justify-center mx-auto mb-6 text-brand-secondary">
                                <Compass className="w-7 h-7" />
                            </div>
                            <h3 className="text-xl font-bold text-white mb-3">Design Funcional</h3>
                            <p className="text-white text-sm">
                                Modelagens testadas e ajustadas milimetricamente para garantir total liberdade de movimento e caimento perfeito no corpo.
                            </p>
                        </div>
                        <div className="bg-background border border-white/5 p-8 rounded-xl hover:border-brand-secondary/50 transition-colors">
                            <div className="w-14 h-14 bg-[#2A2A2A] rounded-full flex items-center justify-center mx-auto mb-6 text-brand-secondary">
                                <ShieldCheck className="w-7 h-7" />
                            </div>
                            <h3 className="text-xl font-bold text-white mb-3">Garantia de Qualidade</h3>
                            <p className="text-white text-sm">
                                Cada peça passa por um controle de qualidade exigente. Se não estiver 100% perfeito, não chega até você.
                            </p>
                        </div>
                    </div>
                </div>
            </section>
        </div>
    );
}
