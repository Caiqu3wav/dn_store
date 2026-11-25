# Checklist operacional — Melhor Envio em produção

Este checklist prepara a homologação operacional do DN Store. Ele não autoriza chamadas de produção, compra de etiquetas ou geração de envios.

## Escopo atual da integração

A integração implementada cobre **autorização OAuth e cotação de frete** (escopo `shipping-calculate`). O valor cotado é gravado no pedido e usado no checkout.

**Não** estão implementados: compra de etiquetas, impressão de etiquetas e rastreamento (exigiriam outros escopos do Melhor Envio e desenvolvimento adicional). Enquanto isso, o envio é operado manualmente: depois que o pagamento do pedido é confirmado, compre e imprima a etiqueta pelo painel do próprio Melhor Envio usando os dados do pedido.

**Renovação da autorização:** o backend renova o token sozinho, de duas formas: sob demanda (quando uma cotação encontra o token perto de vencer) e por um agendador (`MelhorEnvioTokenRenewalScheduler`) que verifica a cada 6 h e renova quando faltam `MELHOR_ENVIO_PROACTIVE_REFRESH_WINDOW` (padrão 7 dias) para o access token vencer. O Melhor Envio informa a validade do access token na resposta (`expires_in`, 30 dias) e o FAQ fala em 45 dias para o refresh token, mas **nenhum prazo local bloqueia a renovação**: quem decide é o Melhor Envio (`MELHOR_ENVIO_REFRESH_TOKEN_TTL` é só informativo). A conexão só passa a `REAUTH_REQUIRED` quando ele recusa o refresh token com `invalid_grant`; falhas de rede, 5xx e 429 são temporárias e mantêm tokens e status. Os tokens nunca são apagados por uma falha de renovação.

**Rotação do refresh token:** cada renovação devolve um refresh token novo "para o próximo refresh", então o antigo deve ser tratado como invalidado. Por isso (a) a renovação roda sob o lock da linha do banco, e agendador e cotação nunca usam o mesmo refresh token ao mesmo tempo; (b) a gravação dos tokens usa transação própria (`REQUIRES_NEW`), que não é desfeita se a transação da cotação der rollback; (c) uma resposta de renovação incompleta é rejeitada sem tocar no que está gravado; (d) se o provedor emitiu tokens novos e a gravação falhou, eles ficam em memória, são usados nas requisições seguintes e regravados com novas tentativas (o refresh token antigo do banco não é reutilizado nesse intervalo); o painel mostra o aviso. O marcador de pendência é posto sob o lock, antes da gravação, e só sai depois do commit, para que uma thread que esperava a linha após um rollback não reutilize o refresh token antigo. Isso vale dentro de um processo (instância única); entre instâncias o lock do banco serializa as renovações, mas a pendência em memória não é visível às outras. **Limite:** se o processo reiniciar antes de conseguir gravar, os tokens novos se perdem e é preciso reconectar pelo painel.

**Painel:** em `/admin`, o card "Integrações" mostra o estado (Conectado, Renovação falhando, Reconexão necessária, Não conectado, Integração desativada), até quando o token atual vale e a última falha de renovação. "Reconexão necessária" exige que um ADMIN clique em **Conectar Melhor Envio**; sem isso o checkout responde 503.

**Pendente de confirmação em sandbox/produção:** o formato exato do erro que o Melhor Envio devolve para refresh token revogado não é documentado. O código só trata `invalid_grant` como revogação (padrão OAuth2). Se o provedor usar outro código, o painel mostrará "Renovação falhando" com o HTTP status em vez de "Reconexão necessária": revise essa mensagem na homologação.

**Sem frete disponível:** quando o Melhor Envio responde normalmente mas nenhum serviço atende o CEP/itens (lista vazia ou todos com `error`), o backend responde **422** com `errors.code = SHIPPING_NOT_AVAILABLE` e o checkout mostra "Não encontramos entrega disponível para este CEP", sem tratar como indisponibilidade. Respostas sem preço, prazo ou id e sem motivo continuam sendo falha técnica (503). O evento `melhor_envio.quote.no_services` é registrado em `WARN`: se aparecer para CEPs variados, suspeite de CEP de origem ou medidas dos produtos mal configurados, não de falta de cobertura. O campo `error` por serviço não está documentado pelo Melhor Envio: confirme o formato real na primeira cotação de homologação.

**Cotação:** erros do provedor são tratados assim: 422 por CEP de destino vira 400 ("Confira o CEP"); 429, 5xx e falhas de rede são repetidos até 3 vezes com backoff (respeita `Retry-After` de até 3 s); 403 e demais 4xx não são repetidos e geram `ERROR` nos logs (`melhor_envio.quote.failed category=forbidden|client_error|unprocessable`). Logs estruturados `melhor_envio.quote.*` e `melhor_envio.token.*` trazem só categoria, status HTTP e contagem de tentativas, nunca token, corpo ou CEP. Cotações do mesmo cliente, carrinho e CEP feitas há menos de `SHIPPING_QUOTE_REUSE_WINDOW` (5 min) são reaproveitadas sem nova chamada. `POST /api/orders/shipping-quotes` aceita no máximo `SHIPPING_QUOTE_RATE_LIMIT_REQUESTS` (10) por cliente a cada `SHIPPING_QUOTE_RATE_LIMIT_WINDOW` (1 min); acima disso responde 429 com `Retry-After`.

**Limite por cliente — premissa de instância única:** o contador do limite de cotações fica **em memória** (`ShippingQuoteRateLimiter`), como o de login. Vale para o endpoint do checkout e para o legado `GET /api/shipping/quote`. Consequências: (a) com mais de uma instância o limite efetivo é multiplicado pelo número de instâncias; (b) reiniciar o processo, inclusive no deploy, zera os contadores; (c) **não existe limite global**: vários clientes cotando ao mesmo tempo não são contidos por este componente (a cota do provedor é protegida pelo reaproveitamento de cotações recentes e pelo tratamento de 429). Redis ou limite global em tabela **não** foram implementados; isso só precisa ser feito antes de escalar para mais de uma instância.

**Checkout:** se a cotação vencer, o carrinho mudar ou o CEP divergir, o backend responde 409 com `errors.code = SHIPPING_QUOTE_STALE`. O frontend volta à etapa de entrega, cota de novo com o mesmo CEP e pede só a escolha da opção; o preço e o frete continuam sendo calculados e validados no servidor.

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
MELHOR_ENVIO_REFRESH_TOKEN_TTL=PT1080H   # apenas informativo (45 dias, FAQ do Melhor Envio); não bloqueia renovação
MELHOR_ENVIO_PROACTIVE_REFRESH_WINDOW=P7D
SHIPPING_QUOTE_REUSE_WINDOW=PT5M
SHIPPING_QUOTE_RATE_LIMIT_REQUESTS=10
SHIPPING_QUOTE_RATE_LIMIT_WINDOW=PT1M
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
- [ ] Banco de produção está com todas as migrations aplicadas (V1–V18 e V20–V25; não existe V19; ver `BACKUP_RECUPERACAO.md`) e com `ddl-auto=validate`.
- [ ] Chave de criptografia é persistente, exclusiva do ambiente e possui 32 bytes após Base64.
- [ ] CEP de origem e dados físicos dos produtos foram revisados.
- [ ] HTTPS, domínio, CORS e disponibilidade do banco foram validados.
- [ ] Homologação real foi aprovada com dados controlados.
