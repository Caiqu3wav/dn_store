"use client";

import React, { useEffect } from "react";
import Image from "next/image";
import { Instagram } from "lucide-react";

const INSTAGRAM_MTB_URL =
  "https://www.instagram.com/desafionaturezamtb/";

const INSTAGRAM_STORE_URL =
  "https://www.instagram.com/lojadesafionatureza/";

export default function InstagramSection() {
  // Carrega os scripts do Elfsight e do Behold no navegador de forma assíncrona
  useEffect(() => {
    // 1. Script do Elfsight
    const elfsightScript = document.createElement("script");
    elfsightScript.src = "https://elfsightcdn.com/platform.js";
    elfsightScript.async = true;
    document.body.appendChild(elfsightScript);

    // 2. Script do Behold
    if (!window.__bhldScript) {
      window.__bhldScript = true;
      const beholdScript = document.createElement("script");
      beholdScript.type = "module";
      beholdScript.src = "https://w.behold.so/widget.js";
      document.head.appendChild(beholdScript);
    }

    return () => {
      if (document.body.contains(elfsightScript)) {
        document.body.removeChild(elfsightScript);
      }
    };
  }, []);

  return (
    <section className="w-full bg-white py-16 md:py-20">
      <div className="mx-auto w-full max-w-7xl px-5 md:px-8">
        
        {/* Título Principal */}
        <h2
          className="mb-4 text-center text-2xl text-black sm:text-4xl"
          style={{ fontFamily: "DN" }}
        >
          FALA BIKER! SIGA NOSSAS REDES SOCIAIS
        </h2>

        {/* ========================================= */}
        {/* INSTAGRAM - DESAFIO NATUREZA MTB (Elfsight) */}
        {/* ========================================= */}

        <div>
          {/* Cabeçalho MTB */}
          <div className="mb-7 flex flex-wrap items-center justify-between gap-3">
            <a
              href={INSTAGRAM_MTB_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="group flex items-center gap-3"
            >
              <div className="flex h-11 w-11 items-center justify-center rounded-full border border-neutral-300 text-brand transition-colors group-hover:text-[#c70808]">
                <Instagram
                  size={24}
                  strokeWidth={1.8}
                  className="transition-transform duration-300 group-hover:scale-110"
                />
              </div>

              <div>
                <p className="text-sm text-neutral-500">Instagram</p>
                <p className="text-lg font-semibold text-neutral-900 transition-colors group-hover:text-[#c70808]">
                  @desafionaturezamtb
                </p>
              </div>
            </a>

            <a
              href={INSTAGRAM_MTB_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="rounded-md bg-[#c70808] px-5 py-2.5 text-sm font-semibold text-white transition-all duration-300 hover:bg-[#a80606] hover:shadow-md"
            >
              Seguir
            </a>
          </div>

          {/* Mural MTB com o widget do Elfsight */}
          <div className="w-full">
            <div 
              className="elfsight-app-4d0b64ad-46aa-4076-8c2d-d08b0fcc2df5 w-full" 
              data-elfsight-app-lazy
            />
          </div>
        </div>

        {/* ========================================= */}
        {/* ESPAÇAMENTO ENTRE OS DOIS                  */}
        {/* ========================================= */}

        <div className="my-5 border-t border-neutral-200" />

        {/* ========================================= */}
        {/* INSTAGRAM - DN STORE (Behold)             */}
        {/* ========================================= */}

        <div>
          {/* Cabeçalho LOJA */}
          <div className="mb-7 flex flex-wrap items-center justify-between gap-3">
            <a
              href={INSTAGRAM_STORE_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="group flex items-center gap-3"
            >
              <div className="flex h-11 w-11 items-center justify-center rounded-full border border-neutral-300 text-brand transition-colors group-hover:text-[#c70808]">
                <Instagram
                  size={24}
                  strokeWidth={1.8}
                  className="transition-transform duration-300 group-hover:scale-110"
                />
              </div>

              <div>
                <p className="text-sm text-neutral-500">Instagram</p>
                <p className="text-lg font-semibold text-neutral-900 transition-colors group-hover:text-[#c70808]">
                  @lojadesafionatureza
                </p>
              </div>
            </a>

            <a
              href={INSTAGRAM_STORE_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="rounded-md bg-[#c70808] px-5 py-2.5 text-sm font-semibold text-white transition-all duration-300 hover:bg-[#a80606] hover:shadow-md"
            >
              Seguir
            </a>
          </div>

          {/* Mural da Loja com Behold */}
          <div className="w-full">
            {/* @ts-ignore */}
            <behold-widget feed-id="6OwnBAUKXZiiO0rEiWyn"></behold-widget>
          </div>
        </div>

        {/* ========================================= */}
        {/* PATROCÍNIO E APOIO                         */}
        {/* ========================================= */}

        <div className="mt-20 flex justify-center">
          <Image
            src="/assets/patrocinio.png"
            alt="Patrocínio e Apoio - Desafio Natureza"
            width={1536}
            height={448}
            className="h-auto w-[70%] object-contain"
          />
        </div>

      </div>
    </section>
  );
}