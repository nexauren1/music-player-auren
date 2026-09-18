# Changelog

## 3.3.0 — Launch preparation

### Corrigido
- Corrigido o fluxo de primeira autorização da biblioteca de música: depois de o utilizador conceder acesso, a biblioteca é carregada imediatamente.
- Adicionado estado de permissão negada com acesso direto às definições do aplicativo.
- Melhoradas as mensagens da biblioteca vazia para separar falta de músicas de falta de permissão.

### Desafio musical
- Transformado o antigo desafio de tentativa/erro num jogo explicável por pistas.
- Adicionados pontos, rodadas, sequência atual e melhor sequência.
- O utilizador passa a ver o resultado antes de avançar para a próxima pergunta.
- Adicionada opção para recomeçar a pontuação.
- O desafio continua totalmente local.

### Produto
- Substituída a afirmação de “IA local” por linguagem mais precisa sobre padrões e comportamento local.
- Esclarecida a função do Bluetooth para não prometer uma ligação de áudio que é finalizada pelo Android.

### Release
- O workflow deixou de criar uma GitHub Release em cada push para `main`.
- Pull requests e pushes para `main` fazem apenas build/validação.
- Releases públicas usam tags `vX.Y.Z`.
- Builds de release exigem keystore de produção; não existe mais fallback silencioso para APK debug em uma release.
- A tag precisa corresponder ao `versionName`.
- Removido o commit automático de `build-status.json` do workflow; o resultado passa a ficar no GitHub Actions Step Summary.
