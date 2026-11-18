import type { NextConfig } from "next";

// NEXT_PUBLIC_API_URL é embutida no bundle em tempo de build. Sem ela, a loja publicada
// não consegue falar com a API; por isso o deploy de produção na Vercel falha cedo.
if (process.env.VERCEL_ENV === "production" && !process.env.NEXT_PUBLIC_API_URL) {
  throw new Error(
    "NEXT_PUBLIC_API_URL é obrigatória no ambiente Production da Vercel (ex.: https://<backend>/api).",
  );
}

const nextConfig: NextConfig = {
  images: {
    remotePatterns: [
      {
        protocol: 'https',
        hostname: 'images.unsplash.com',
      },
      {
        protocol: 'https',
        hostname: 'source.unsplash.com',
      },
      {
        protocol: "https",
        hostname: "res.cloudinary.com",
      },
    ],
  },
};

export default nextConfig;
