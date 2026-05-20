'use client';

import { usePathname } from 'next/navigation';
import { Navbar } from './Navbar';
import { Footer } from './Footer';

export function ClientLayoutWrapper({ children }: { children: React.ReactNode }) {
    const pathname = usePathname();
    const isAdminRoute = pathname?.startsWith('/admin');

    return (
        <>
            {!isAdminRoute && <Navbar />}
            <main className={!isAdminRoute ? 'min-h-screen' : ''}>{children}</main>
            {!isAdminRoute && <Footer />}
        </>
    );
}
