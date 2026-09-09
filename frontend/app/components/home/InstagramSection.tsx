import Image from "next/image";
import { Instagram } from "lucide-react";

const INSTAGRAM_URL =
  "https://www.instagram.com/desafionaturezamtb/";

const posts: {
  id: number;
  image: string;
}[] = [];

export default function InstagramSection() {
  return (
    <section className="w-full bg-white py-16 md:py-20">

      {/* ========================================= */}
      {/* INSTAGRAM */}
      {/* ========================================= */}

      <div className="mx-auto w-full max-w-7xl px-5 md:px-8">

        {/* Cabeçalho do Instagram */}
        <div className="mb-7 flex items-center justify-between">

          {/* Perfil */}
          <a
            href={INSTAGRAM_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="group flex items-center gap-3"
          >
            <div className="flex text-brand transition-colors group-hover:text-[#c70808] h-11 w-11 items-center justify-center rounded-full border border-neutral-300">
              <Instagram
                size={24}
                strokeWidth={1.8}
                className="transition-transform duration-300 group-hover:scale-110"
              />
            </div>

            <div>
              <p className="text-sm text-neutral-500">
                Instagram
              </p>

              <p className="text-lg font-semibold text-neutral-900 transition-colors group-hover:text-[#c70808]">
                @desafionaturezamtb
              </p>
            </div>
          </a>

          {/* Botão Seguir */}
          <a
            href={INSTAGRAM_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="rounded-md bg-[#c70808] px-5 py-2.5 text-sm font-semibold text-white transition-all duration-300 hover:bg-[#a80606] hover:shadow-md"
          >
            Seguir
          </a>

        </div>


        {/* ========================================= */}
        {/* MURAL DE PUBLICAÇÕES */}
        {/* ========================================= */}

        {posts.length > 0 && (
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 md:gap-3">

            {posts.map((post) => (
              <a
                key={post.id}
                href={INSTAGRAM_URL}
                target="_blank"
                rel="noopener noreferrer"
                className="group relative aspect-square overflow-hidden bg-neutral-100"
              >
                <Image
                  src={post.image}
                  alt="Publicação do Desafio Natureza"
                  fill
                  className="object-cover transition-transform duration-500 group-hover:scale-105"
                />
              </a>
            ))}

          </div>
        )}


        {/* ========================================= */}
        {/* PATROCÍNIO E APOIO */}
        {/* ========================================= */}

        <div className="mt-16 flex justify-center">
          <Image
            src="/assets/patrocinio.png"
            alt="Patrocínio e Apoio - Desafio Natureza"
            width={1536}
            height={448}
            className="h-auto w-full object-contain"
          />
        </div>

      </div>

    </section>
  );
}