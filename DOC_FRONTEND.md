# 📘 DOC_FRONTEND — Guia do Frontend (Next.js)

Documentação técnica para membros do time que vão trabalhar no `./frontend`.

---

## 🗂️ Estrutura de Pastas

```
frontend/
├── app/                  ← Tudo fica aqui (App Router do Next.js)
│   ├── layout.tsx        ← Layout raiz (Navbar, Footer, Providers)
│   ├── page.tsx          ← Página inicial (rota "/")
│   ├── globals.css       ← CSS global + variáveis de tema
│   ├── styles/           ← CSS modular por componente (opcional)
│   ├── components/       ← Componentes reutilizáveis
│   │   ├── home/         ← Componentes exclusivos da home
│   │   ├── layout/       ← Navbar, Footer
│   │   ├── shop/         ← Componentes da loja
│   │   └── ui/           ← Componentes genéricos (Button, etc.)
│   ├── context/          ← Contextos React (ex: CartContext)
│   ├── loja/page.tsx     ← Rota "/loja"
│   ├── carrinho/page.tsx ← Rota "/carrinho"
│   └── produto/[id]/     ← Rota dinâmica "/produto/123"
├── lib/                  ← Funções utilitárias e dados
├── public/               ← Arquivos estáticos (imagens, svgs)
└── package.json
```

---

## 📄 Como Criar Novas Páginas

No Next.js com App Router, **cada pasta dentro de `app/` com um arquivo `page.tsx` vira uma rota automaticamente**. Não existe arquivo de rotas separado.

| Arquivo                          | Rota resultante     |
|----------------------------------|---------------------|
| `app/page.tsx`                   | `/`                 |
| `app/loja/page.tsx`              | `/loja`             |
| `app/carrinho/page.tsx`          | `/carrinho`         |
| `app/produto/[id]/page.tsx`      | `/produto/123`      |
| `app/eventos/page.tsx`           | `/eventos`          |

### Criando uma nova página

1. Crie a pasta com o nome da rota dentro de `app/`
2. Crie o arquivo `page.tsx` dentro dela
3. Exporte um componente como `default`

```tsx
// app/eventos/page.tsx  →  rota: /eventos

export default function EventosPage() {
  return (
    <div className="pt-24 container mx-auto px-4">
      <h1 className="text-3xl font-bold">Eventos</h1>
    </div>
  );
}
```

### Rota dinâmica (ex: produto por ID)

```tsx
// app/produto/[id]/page.tsx  →  rota: /produto/qualquer-coisa

export default function ProdutoPage({ params }: { params: { id: string } }) {
  return <div>Produto: {params.id}</div>;
}
```

---

## 🎨 Estilização: TailwindCSS + CSS Modular

### Tailwind (padrão do projeto)

Usamos Tailwind v4. As classes são aplicadas diretamente no JSX via `className`.

```tsx
<div className="flex items-center gap-4 bg-white rounded-lg p-6 shadow-sm">
  <h2 className="text-xl font-bold text-brand-primary">Título</h2>
</div>
```

### Cores customizadas do projeto

Definidas em `globals.css` e disponíveis como classes Tailwind:

| Classe Tailwind          | Valor           | Uso                        |
|--------------------------|-----------------|----------------------------|
| `bg-background`          | `#F5F5F5`       | Fundo padrão               |
| `text-foreground`        | `#0F0F0F`       | Texto padrão               |
| `text-brand-primary`     | `#0F0F0F`       | Preto da marca             |
| `text-brand-secondary`   | `#D97706`       | Laranja/marrom da marca    |
| `text-brand-highlight`   | `#16A34A`       | Verde natureza             |

### CSS modular (quando Tailwind não é suficiente)

Para estilos mais complexos ou animações específicas, crie um arquivo `.css` na pasta `app/styles/` e importe no componente:

```css
/* app/styles/hero.css */
.hero-overlay {
  background: linear-gradient(to bottom, transparent 40%, rgba(0,0,0,0.8));
}
```

```tsx
// app/components/home/Hero.tsx
import '../styles/hero.css';  // caminho relativo ao arquivo

export function Hero() {
  return <div className="hero-overlay">...</div>;
}
```

> Use CSS modular apenas quando necessário. Prefira Tailwind para manter consistência.

---

## ⚡ `'use client'` — Quando e Por Quê

### O problema

No Next.js App Router, **todos os componentes são Server Components por padrão**. Isso significa que eles rodam no servidor e não têm acesso a:

- `useState`, `useEffect`, `useContext`
- Eventos do browser (`onClick`, `onChange`, etc.)
- APIs do browser (`localStorage`, `window`, etc.)

### A solução

Adicione `'use client'` no topo do arquivo quando o componente precisar de qualquer coisa acima:

```tsx
'use client';  // ← deve ser a primeira linha do arquivo

import { useState } from 'react';

export function Contador() {
  const [count, setCount] = useState(0);
  return <button onClick={() => setCount(count + 1)}>{count}</button>;
}
```

### Exemplos no projeto

| Arquivo                          | Por que tem `'use client'`                          |
|----------------------------------|-----------------------------------------------------|
| `Navbar.tsx`                     | Usa `useState` (menu mobile) e `useEffect` (scroll) |
| `CartContext.tsx`                 | Usa `useState`, `useEffect` e `localStorage`        |
| `carrinho/page.tsx`              | Usa `useCart()` (hook de contexto)                  |

### Regra prática

```
Tem useState / useEffect / onClick / useContext / localStorage?
  → Coloca 'use client' no topo.

É só exibir dados (sem interação)?
  → Deixa como Server Component (sem 'use client').
```

> Mantenha `'use client'` apenas nos componentes que realmente precisam. Server Components são mais performáticos.

---

## 🔄 Next.js para quem vem do React puro

Se você está acostumado com Create React App ou Vite + React, aqui estão as principais diferenças:

### Navegação: `Link` no lugar de `<a>`

```tsx
// ❌ Não use <a> para links internos
<a href="/loja">Loja</a>

// ✅ Use o componente Link do Next.js
import Link from 'next/link';
<Link href="/loja">Loja</Link>
```

O `Link` faz navegação client-side sem recarregar a página.

### Imagens: `Image` no lugar de `<img>`

```tsx
// ❌ Evite <img> para imagens do projeto
<img src="/assets/logo.jpg" alt="Logo" />

// ✅ Use o componente Image do Next.js
import Image from 'next/image';
<Image src="/assets/logo.jpg" alt="Logo" width={100} height={100} />
```

O `Image` otimiza automaticamente: lazy loading, formato WebP, tamanho responsivo.

### Não existe `index.html` nem `ReactDOM.render()`

O ponto de entrada é o `app/layout.tsx`. Ele envolve todas as páginas automaticamente — é onde ficam Navbar, Footer e Providers globais.

### Não existe `react-router-dom`

O roteamento é feito pela estrutura de pastas dentro de `app/`. Não instale nem use `react-router-dom`.

### Dados: fetch no servidor (sem `useEffect` para carregar dados)

Em Server Components, você pode buscar dados diretamente, sem `useEffect`:

```tsx
// app/loja/page.tsx — Server Component (sem 'use client')
export default async function LojaPage() {
  const produtos = await fetch('http://localhost:8080/api/produtos').then(r => r.json());

  return (
    <div>
      {produtos.map(p => <div key={p.id}>{p.nome}</div>)}
    </div>
  );
}
```

> Isso é mais eficiente que buscar dados no cliente com `useEffect`.

### Resumo das diferenças

| React puro                  | Next.js (App Router)                     |
|-----------------------------|------------------------------------------|
| `react-router-dom`          | Pastas em `app/` viram rotas             |
| `<a href="...">`            | `<Link href="...">`                      |
| `<img src="...">`           | `<Image src="..." width height>`         |
| `useEffect` para fetch      | `async/await` direto no componente       |
| Tudo é client-side          | Server Component por padrão              |
| `index.html` + `main.tsx`   | `app/layout.tsx` é o ponto de entrada    |

---

## 🚀 Rodando o projeto

```bash
cd frontend
npm install
npm run dev
```

Acesse: [http://localhost:3000](http://localhost:3000)
