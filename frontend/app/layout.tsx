import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import { Navbar } from "./components/Navbar";
import { Footer } from "./components/Footer";
import { CartProvider } from "./context/CartContext";
import StyledComponentsRegistry from "./StyledComponentsRegistry";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Desafio Natureza | Aventura sobre duas rodas",
  description: "Loja oficial da Desafio Natureza. Roupas e acessórios para mountain bike e eventos esportivos.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {

  return (
    <html lang="en">
      <body
        className={`${geistSans.variable} ${geistMono.variable} antialiased bg-black text-white`}
      >
        <CartProvider>
          <StyledComponentsRegistry>
            <Navbar />
            <main className="min-h-screen">{children}</main>
            <Footer />
          </StyledComponentsRegistry>
        </CartProvider>
      </body>
    </html>
  );
}

