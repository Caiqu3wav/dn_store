"use client";

import { useEffect, useState } from "react";
import { Instagram } from "lucide-react";
import Image from "next/image";
import api from "@/lib/axios";
import type { InstagramMediaItem, InstagramPost } from "@/types";

const FEEDS = [
  {
    key: "mtb",
    username: "@desafionaturezamtb",
    url: "https://www.instagram.com/desafionaturezamtb/",
    endpoint: "/instagram/mtb",
  },
  {
    key: "store",
    username: "@lojadesafionatureza",
    url: "https://www.instagram.com/lojadesafionatureza/",
    endpoint: "/instagram/store",
  },
] as const;

type FeedKey = (typeof FEEDS)[number]["key"];
type FeedState = {
  posts: InstagramPost[];
  status: "loading" | "loaded" | "error";
};

const LOADING_FEED: FeedState = { posts: [], status: "loading" };

function formatDate(timestamp: string | null) {
  if (!timestamp) return "Data indisponível";
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) return "Data indisponível";
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "medium" }).format(date);
}

function MediaTile({ media }: { media: InstagramMediaItem }) {
  const src = media.mediaUrl || media.thumbnailUrl;

  if (media.mediaType === "VIDEO" && media.mediaUrl) {
    return (
      <video
        src={media.mediaUrl}
        poster={media.thumbnailUrl ?? undefined}
        controls
        playsInline
        preload="metadata"
        className="h-full w-full object-cover"
        aria-label="Vídeo do Instagram"
      />
    );
  }

  if (!src) {
    return (
      <div className="flex h-full items-center justify-center bg-neutral-100 text-sm text-neutral-500">
        Mídia indisponível
      </div>
    );
  }

  return (
    // Instagram media URLs use dynamic CDN hosts, so use a native image element.
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={src}
      alt="Publicação do Instagram"
      loading="lazy"
      className="h-full w-full object-cover"
    />
  );
}

function PostCard({ post }: { post: InstagramPost }) {
  const mediaItems =
    post.mediaType === "CAROUSEL_ALBUM" && post.children.length > 0
      ? post.children
      : [post];
  const visibleItems = mediaItems.slice(0, 4);

  return (
    <article className="overflow-hidden rounded-lg border border-neutral-200 bg-white">
      <div
        className={`relative aspect-square overflow-hidden bg-neutral-100 ${
          visibleItems.length > 1 ? "grid grid-cols-2 gap-1" : ""
        }`}
      >
        {visibleItems.map((media, index) => (
          <div
            key={media.id}
            className={`relative min-h-0 overflow-hidden ${
              visibleItems.length === 3 && index === 0 ? "row-span-2" : ""
            }`}
          >
            <MediaTile media={media} />
            {index === visibleItems.length - 1 && mediaItems.length > 4 && (
              <span className="absolute inset-0 flex items-center justify-center bg-black/50 text-2xl font-semibold text-white">
                +{mediaItems.length - 4}
              </span>
            )}
          </div>
        ))}
      </div>

      <div className="space-y-2 p-4">
        <p className="text-xs text-neutral-500">{formatDate(post.timestamp)}</p>
        <p className="line-clamp-3 min-h-12 text-sm text-neutral-800">
          {post.caption || "Publicação do Instagram"}
        </p>
        {post.permalink && (
          <a
            href={post.permalink}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-2 text-sm font-semibold text-[#c70808] hover:underline"
          >
            Ver publicação
          </a>
        )}
      </div>
    </article>
  );
}

export default function InstagramSection() {
  const [feeds, setFeeds] = useState<Record<FeedKey, FeedState>>({
    mtb: LOADING_FEED,
    store: LOADING_FEED,
  });
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();

    for (const feed of FEEDS) {
      api
        .get<InstagramPost[]>(feed.endpoint, { signal: controller.signal })
        .then(({ data }) => {
          setFeeds((current) => ({
            ...current,
            [feed.key]: { posts: data, status: "loaded" },
          }));
        })
        .catch(() => {
          if (controller.signal.aborted) return;
          setFeeds((current) => ({
            ...current,
            [feed.key]: { posts: [], status: "error" },
          }));
        });
    }

    return () => controller.abort();
  }, [retryKey]);

  function retryFeeds() {
    setFeeds({ mtb: LOADING_FEED, store: LOADING_FEED });
    setRetryKey((current) => current + 1);
  }

  return (
    <section className="w-full bg-white py-16 md:py-20">
      <div className="mx-auto w-full max-w-7xl px-5 md:px-8">
        <h2
          className="mb-10 text-center text-2xl text-black sm:text-4xl"
          style={{ fontFamily: "DN" }}
        >
          FALA BIKER! SIGA NOSSAS REDES SOCIAIS
        </h2>

        {FEEDS.map((feed, index) => {
          const state = feeds[feed.key];

          return (
            <div key={feed.key}>
              {index > 0 && <div className="my-10 border-t border-neutral-200" />}

              <div className="mb-7 flex flex-wrap items-center justify-between gap-3">
                <a
                  href={feed.url}
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
                      {feed.username}
                    </p>
                  </div>
                </a>

                <a
                  href={feed.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="rounded-md bg-[#c70808] px-5 py-2.5 text-sm font-semibold text-white transition-all duration-300 hover:bg-[#a80606] hover:shadow-md"
                >
                  Seguir
                </a>
              </div>

              {state.status === "loading" && (
                <p className="py-8 text-center text-sm text-neutral-500" role="status">
                  Carregando publicações...
                </p>
              )}

              {state.status === "error" && (
                <div className="py-8 text-center" role="alert">
                  <p className="mb-3 text-sm text-neutral-600">
                    Não foi possível carregar as publicações agora.
                  </p>
                  <button
                    type="button"
                    onClick={retryFeeds}
                    className="text-sm font-semibold text-[#c70808] hover:underline"
                  >
                    Tentar novamente
                  </button>
                </div>
              )}

              {state.status === "loaded" && state.posts.length === 0 && (
                <p className="py-8 text-center text-sm text-neutral-500">
                  Nenhuma publicação disponível.
                </p>
              )}

              {state.status === "loaded" && state.posts.length > 0 && (
                <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
                  {state.posts.map((post) => (
                    <PostCard key={post.id} post={post} />
                  ))}
                </div>
              )}
            </div>
          );
        })}

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
