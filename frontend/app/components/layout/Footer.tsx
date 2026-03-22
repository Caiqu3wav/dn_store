import Link from 'next/link';
import Image from 'next/image';
import { Instagram, Phone, MapPin, Mail, CreditCard } from 'lucide-react';

export function Footer() {
    return (
        <footer className="bg-brand-primary text-gray-300 pt-16 pb-8 border-t border-gray-800">
            <div className="container mx-auto px-4 lg:px-8">
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-12 mb-16">
                    {/* Brand Info */}
                    <div className="space-y-6">
                        <div className="relative w-40 h-16">
                            <Image 
                                src="/assets/dn_store2.png" 
                                alt="DN Store Logo" 
                                fill 
                                className="object-contain object-left"
                            />
                        </div>
                        <p className="text-gray-400 text-sm leading-relaxed">
                            Aventura sobre duas rodas. Roupas e acessórios de alta performance para quem vive a adrenalina do mountain bike e do ciclismo.
                        </p>
                        <div className="flex items-center gap-4">
                            <a href="#" className="w-10 h-10 rounded-full bg-gray-800 flex items-center justify-center hover:bg-brand-secondary transition-colors text-white">
                                <Instagram className="w-5 h-5" />
                            </a>
                            <a href="#" className="w-10 h-10 rounded-full bg-gray-800 flex items-center justify-center hover:bg-brand-secondary transition-colors text-white">
                                <Mail className="w-5 h-5" />
                            </a>
                        </div>
                    </div>

                    {/* Quick Links */}
                    <div>
                        <h4 className="text-white font-bold mb-6 text-lg tracking-wide uppercase text-sm">Navegação</h4>
                        <ul className="space-y-3 text-sm">
                            <li><Link href="/" className="hover:text-brand-secondary transition-colors">Home</Link></li>
                            <li><Link href="/produtos" className="hover:text-brand-secondary transition-colors">Produtos</Link></li>
                            <li><Link href="/eventos" className="hover:text-brand-secondary transition-colors">Eventos MTB</Link></li>
                            <li><Link href="/sobre" className="hover:text-brand-secondary transition-colors">Sobre Nós</Link></li>
                        </ul>
                    </div>

                    {/* Support & Politices */}
                    <div>
                        <h4 className="text-white font-bold mb-6 text-lg tracking-wide uppercase text-sm">Suporte</h4>
                        <ul className="space-y-3 text-sm">
                            <li><Link href="/faq" className="hover:text-brand-secondary transition-colors">Dúvidas Frequentes FAQ</Link></li>
                            <li><Link href="/trocas" className="hover:text-brand-secondary transition-colors">Trocas e Devoluções</Link></li>
                            <li><Link href="/rastreio" className="hover:text-brand-secondary transition-colors">Rastreie seu Pedido</Link></li>
                            <li><Link href="/termos" className="hover:text-brand-secondary transition-colors">Termos de Uso</Link></li>
                        </ul>
                    </div>

                    {/* Contact */}
                    <div>
                        <h4 className="text-white font-bold mb-6 text-lg tracking-wide uppercase text-sm">Contato</h4>
                        <ul className="space-y-4 text-sm">
                            <li className="flex items-start gap-3">
                                <MapPin className="w-5 h-5 text-brand-secondary shrink-0" />
                                <span>Avenida das Trilhas, 456<br />São Paulo - SP</span>
                            </li>
                            <li className="flex items-center gap-3">
                                <Phone className="w-5 h-5 text-brand-secondary shrink-0" />
                                <span>(11) 99999-9999</span>
                            </li>
                            <li className="flex items-center gap-3">
                                <Mail className="w-5 h-5 text-brand-secondary shrink-0" />
                                <span>contato@dnstore.com.br</span>
                            </li>
                        </ul>
                    </div>
                </div>

                <div className="border-t border-gray-800 pt-8 flex flex-col md:flex-row items-center justify-between gap-4 text-gray-500 text-xs text-center md:text-left">
                    <p>&copy; {new Date().getFullYear()} Desafio Natureza (DN Store). Todos os direitos reservados.</p>
                </div>
            </div>
        </footer>
    );
}
