# Plano de Conexão e Integração Frontend <-> Backend

Quando removermos os mocks do frontend (Next.js/React), precisaremos de uma estrutura sólida, previsível e segura para consumir os dados do backend Java (Spring Boot). Este é o roteiro de como faremos essa integração.

## 1. Variáveis de Ambiente e Configuração
Criar um arquivo `.env.local` na raiz do frontend com a base URL da API para que o ambiente de dev e prod fiquem separados sem hardcoding:
```env
NEXT_PUBLIC_API_URL=http://localhost:8080/api
```

## 2. Cliente HTTP Centralizado (Axios + Interceptors)
Não faremos requisições diretamente com `fetch` avulso para rotas protegidas. Iremos criar uma instância central do **Axios** (`frontend/lib/axios.ts` ou `api/client.ts`).
- **Interceptors de Request**: Injetarão automaticamente o Token JWT (armazenado em HttpOnly Cookies ou LocalStorage) no header `Authorization: Bearer <token>` de todas as requisições, evitando repetição de código.
- **Interceptors de Response**: Vão capturar globalmente erros como `401 Unauthorized`. Se o token expirar, ele desloga o usuário e o redireciona para a tela de login.
## 3. Gerenciamento de Estado e Cache (React Query / SWR)
Para o catálogo, carrinho e favoritos, não usaremos apenas `useState` e `useEffect`. Iremos implementar uma biblioteca de Data Fetching (ex: **React Query** ou **SWR**).
- **Vantagens**: Cache automático (não recarrega a foto do produto se ele já foi visitado segundos atrás), loading states robustos (`isLoading`, `isError`), e refetch em background quando a aba do navegador volta ao foco.
- Os *mocks* estáticos atuais em `lib/data.ts` serão substituídos por Hooks. Exemplo: `useProducts({ category, search, sort })` que chama a API.

## 4. Separação de Serviços (Service Layer)
O frontend terá uma pasta `/services` para isolar a comunicação com a API dos componentes visuais:
- `services/productService.ts` (Listar, Buscar por ID)
- `services/cartService.ts` (Adicionar, Atualizar qtd, Remover)
- `services/authService.ts` (Login, Register, Logout)
- `services/favoriteService.ts`

## 5. Autenticação e Contexto do Usuário
- Substituir o estado local por uma integração real onde a validação de login chama `POST /api/auth/login`.
- Ao obter sucesso, o JWT será salvo e a aplicação atualizará o Header global.
- Atualizaremos o `Navbar` para esconder "Login" e mostrar os dados do Usuário (ex: miniatura, carrinho real).

## 6. Sincronização de Carrinho (Cart) e Favoritos
- **Usuários não logados**: O carrinho pode funcionar localmente (LocalStorage).
- **Usuários logados**: No momento que o usuário loga, o carrinho local é sincronizado e enviado para o backend (`POST /api/cart/items`), e a fonte de verdade passa a ser o banco de dados via API.

## 7. Renderização no Servidor (RSC - Next.js App Router)
Para otimização de SEO e performance da vitrine:
- Páginas como `/produtos` e a **Página Inicial (Home)** devem fazer o `fetch` dos produtos mais populares *no lado do servidor* (Server Components), entregando o HTML pronto para o Google e garantindo carregamento instantâneo.
- O cliente hidratará apenas as partes dinâmicas (botão de favoritar e adicionar ao carrinho).

## 8. Upload de Imagens no Admin (Cloudinary)
Para a administração de produtos (criação e edição):
- **Fluxo de Upload**:
  1. O painel do administrador no frontend aceita arquivos de imagem.
  2. O frontend faz o upload direto das imagens para o **Cloudinary** utilizando sua API de upload (geralmente via unsigned upload presets para maior simplicidade e segurança no client-side).
  3. O Cloudinary responde com as URLs públicas correspondentes (`https://res.cloudinary.com/...`).
  4. O frontend anexa essas URLs na chave `images` do JSON enviado para a API do backend Java (`POST /api/products` ou `PUT /api/products/{id}`).
- **Vantagens**:
  - Desonera o backend Java do processamento de arquivos multipart pesados.
  - O processamento de imagem (redimensionamento automático, otimização de formato) e CDN das fotos são gerenciados pelo Cloudinary.
  - O banco de dados MySQL do backend apenas persiste as referências (URLs).

## 9. Hook de Integração Customizado (`useApi`)
Para padronizar e facilitar a comunicação com o backend em componentes interativos (formulários, cliques de botões, etc.), utilizaremos um hook customizado chamado `useApi`.

### Estrutura do Hook (`frontend/hooks/useApi.ts`):
```typescript
import { useState, useCallback } from 'react';
import api from '../lib/axios'; // instância do Axios com interceptors

interface UseApiState<T> {
  data: T | null;
  error: string | null;
  loading: boolean;
}

export function useApi<T = any>() {
  const [state, setState] = useState<UseApiState<T>>({
    data: null,
    error: null,
    loading: false,
  });

  const request = useCallback(async (
    method: 'get' | 'post' | 'put' | 'delete',
    url: string,
    body?: any,
    config?: any
  ) => {
    setState(prev => ({ ...prev, loading: true, error: null }));
    try {
      const response = await api({
        method,
        url,
        data: body,
        ...config
      });
      setState({ data: response.data, error: null, loading: false });
      return response.data;
    } catch (err: any) {
      const errorMsg = err.response?.data?.message || 'Erro ao processar requisição.';
      setState({ data: null, error: errorMsg, loading: false });
      throw err;
    }
  }, []);

  return { ...state, request };
}
```

### Exemplo de Uso nos Componentes (ex: Login):
```tsx
import { useApi } from '@/hooks/useApi';

export function LoginForm() {
  const { loading, error, request } = useApi();

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const response = await request('post', '/auth/login', { email, password });
      // Salvar token e redirecionar
    } catch (err) {
      // Erro já tratado no estado local 'error'
    }
  };

  return (
    <form onSubmit={handleLogin}>
      {error && <p className="text-red-500">{error}</p>}
      <button disabled={loading}>Entrar</button>
    </form>
  );
}
```
