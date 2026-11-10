# Checklist operacional — Melhor Envio em produção

Este checklist prepara a homologação operacional do DN Store. Ele não autoriza chamadas de produção, compra de etiquetas ou geração de envios.

## Informações necessárias do cliente

- Client ID do aplicativo Melhor Envio.
- Client Secret, enviado por canal seguro e nunca pelo chat ou por commit.
- Confirmação de que o aplicativo e a conta estão no ambiente de produção.
- Redirect URI cadastrada exatamente como será publicada no backend:
  `https://<backend-host>/api/integrations/melhor-envio/callback`.
- Permissão `shipping-calculate` concedida ao aplicativo.
- CEP de origem da operação.
- Confirmação de que a conta está habilitada para os serviços necessários à cotação.
- Contato técnico para compor o User-Agent da aplicação.

## Configuração do ambiente

No servidor do backend, configure as variáveis sem colocá-las no repositório:

```text
SHIPPING_GATEWAY=melhorEnvio
ORIGIN_ZIP_CODE=<cep-de-origem>
MELHOR_ENVIO_ENV=production
MELHOR_ENVIO_CLIENT_ID=<client-id>
MELHOR_ENVIO_CLIENT_SECRET=<client-secret>
MELHOR_ENVIO_REDIRECT_URI=https://<backend-host>/api/integrations/melhor-envio/callback
MELHOR_ENVIO_API_BASE_URL=https://melhorenvio.com.br
MELHOR_ENVIO_AUTHORIZATION_URL=https://melhorenvio.com.br/oauth/authorize
MELHOR_ENVIO_TOKEN_URL=https://melhorenvio.com.br/oauth/token
MELHOR_ENVIO_SCOPES=shipping-calculate
MELHOR_ENVIO_USER_AGENT=DN-Store/1.0 (suporte@example.com)
MELHOR_ENVIO_TOKEN_ENCRYPTION_KEY=<base64-de-32-bytes>
MELHOR_ENVIO_STATE_TTL=PT10M
SHIPPING_QUOTE_TTL=PT15M
MELHOR_ENVIO_REFRESH_SKEW=PT5M
MELHOR_ENVIO_REFRESH_TOKEN_TTL=PT720H
SHIPPING_HTTP_CONNECT_TIMEOUT=PT3S
SHIPPING_HTTP_READ_TIMEOUT=PT10S
```

Use perfil Spring `prod` ou `production`. A aplicação rejeita gateway simulado em produção, ambiente sandbox em perfil de produção e URLs que não correspondam ao host oficial do ambiente configurado.

Para homologação, use `MELHOR_ENVIO_ENV=sandbox`, perfil `homolog` ou `staging` e os hosts oficiais de sandbox. Não reutilize Client Secret, chave AES-GCM ou tokens entre sandbox e produção.

## Procedimento de autorização

1. Configurar as variáveis no ambiente correto e iniciar o backend.
2. Confirmar nos logs de inicialização que não há erro de configuração; os logs não devem conter credenciais ou tokens.
3. Entrar no painel administrativo como usuário `ADMIN`.
4. Clicar em **Conectar Melhor Envio**.
5. Confirmar que o navegador foi redirecionado para a URL oficial do ambiente escolhido.
6. Autorizar a aplicação na conta do cliente.
7. Confirmar o retorno ao callback cadastrado.
8. Validar state, expiração e uso único no backend.
9. Confirmar a troca do authorization code e a persistência criptografada dos tokens.
10. Confirmar a conexão no painel sem exibir access token ou refresh token.

## Testes operacionais autorizados

- Autorizar a aplicação no ambiente de homologação.
- Confirmar persistência dos tokens e renovação antecipada do access token.
- Cotar um CEP válido usando produtos com dimensões e pesos diferentes.
- Comparar pelo menos dois serviços retornados.
- Confirmar que o valor usado no checkout é o valor da cotação persistida.
- Confirmar que cotação de outro usuário, cotação expirada ou carrinho alterado são rejeitados.
- Simular timeout, erro temporário e `invalid_grant` sem expor dados sensíveis.
- Criar um pedido de teste sem comprar etiqueta ou gerar envio.

Não comprar etiquetas, não gerar envios e não executar operações que possam gerar cobrança.

## Critérios antes de produção

- [ ] Credenciais recebidas por canal seguro e armazenadas somente no secret manager/ambiente.
- [ ] Redirect URI HTTPS cadastrada exatamente no aplicativo Melhor Envio.
- [ ] Perfil, ambiente e URLs são coerentes.
- [ ] `shipping-calculate` está concedido.
- [ ] Banco de produção está migrado até a versão V18 e com `ddl-auto=validate`.
- [ ] Chave de criptografia é persistente, exclusiva do ambiente e possui 32 bytes após Base64.
- [ ] CEP de origem e dados físicos dos produtos foram revisados.
- [ ] HTTPS, domínio, CORS e disponibilidade do banco foram validados.
- [ ] Homologação real foi aprovada com dados controlados.
