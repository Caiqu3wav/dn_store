import Link from 'next/link';
import Image from 'next/image';
import { Instagram, Phone, MapPin, Mail, CreditCard } from 'lucide-react';

export function Footer() {
    return (
        <footer className="bg-background flex flex-col items-center text-gray-300 border-t border-gray-800">
            <div className="  mx-auto">
                <div className="grid ml-20 mt-10 grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-12 mb-16">
                    {/* Brand Info */}
                    <div className="space-y-6">
                        <div className="relative w-40 h-16">
                            <Image 
                                src="/assets/Images/novo-logotipo.png" 
                                alt="DN Store Logo" 
                                fill 
                                className="object-contain object-left"
                            />
                        </div>
                        <p className="text-gray-200 text-sm leading-relaxed">
                           Inspirando aventura e conexão com a natureza. A Desafio Natureza acompanha você em cada desafio.
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
                                <span>Vale do Paraíba<br />São Paulo - SP</span>
                            </li>
                            <li className="flex items-center gap-3">
                                <Phone className="w-5 h-5 text-brand-secondary shrink-0" />
                                <span>(12) 98159-2841</span>
                            </li>
                            <li className="flex items-center gap-3">
                                <Mail className="w-5 h-5 text-brand-secondary shrink-0" />
                                <span>contato@desafionatureza.com.br</span>
                            </li>
                        </ul>
                    </div>
                </div>
                


            <div className="flex flex-col items-center gap-3 mb-6">
    <p className="text-xl text-gray-400">Formas de pagamento</p>

    <div className="flex items-center gap-6">
        <Image src="/assets/pagamentos/pix.svg" alt="Pix" width={70} height={42} className="object-contain opacity-80 hover:opacity-100 transition"/>
        <Image src="/assets/pagamentos/visa.svg" alt="Visa" width={70} height={42} className="object-contain opacity-80 hover:opacity-100 transition"/>
        <Image src="/assets/pagamentos/mastercard.svg" alt="Mastercard" width={80} height={48} className="object-contain opacity-80 hover:opacity-100 transition"/>
        <Image src="/assets/pagamentos/mercadopago.svg" alt="Mercado Pago" width={70} height={42} className="object-contain opacity-80 hover:opacity-100 transition"/>
    </div>
</div>
                <div className="border-t w-full  self-center border-gray-800 pt-8 flex flex-col md:flex-row items-center justify-center gap-4 text-white text-xs text-center md:text-left bg-black py-8">
                    <p>Copyright 2018 Desafio Natureza® - Todos os direitos reservados.</p>
                </div>
            </div>
        </footer>
    );
}
