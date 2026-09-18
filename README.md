# Nexauren Music Player

O **Nexauren Music Player** é um player Android focado em música local: biblioteca do dispositivo, reprodução em segundo plano, fila, playlists, favoritos, equalizador, temporizador, estatísticas e recursos de descoberta baseados no histórico local.

## Estado do projeto

**Versão atual: 3.3.2**

A versão 3.3.2 aplica o ícone oficial e inclui atualização automática com aviso no aplicativo e notificação do Android. Commits normais em `main` fazem validação/build; versões públicas são publicadas diretamente como GitHub Releases.

## Recursos

- Biblioteca de músicas do dispositivo
- Pesquisa por música, artista, álbum e género
- Reprodução com Media3/ExoPlayer
- Reprodução em segundo plano com MediaSession
- Fila e reprodução aleatória
- Retomar posição da faixa
- Favoritos e playlists locais
- Equalizador com presets
- Temporizador de sono
- Modo conduzir
- Estatísticas locais e Nexauren Journey
- Nexauren Mix, Replay, Memories, Discovery e Mood
- Desafio musical por pistas
- Verificação de atualizações pelo GitHub Releases
- Interface clara com tema e cor de destaque configuráveis

## Como funciona o Desafio musical

O desafio usa somente as músicas disponíveis no aparelho.

1. O Nexauren escolhe uma faixa da biblioteca.
2. Mostra pistas como artista, álbum, género e duração.
3. Apresenta 3 ou 4 títulos como respostas.
4. Um acerto dá 1 ponto e aumenta a sequência.
5. Um erro mantém a pontuação, mas reinicia a sequência.
6. A pontuação, número de rodadas e melhor sequência ficam guardados localmente.

Não existe servidor de jogo, ranking online ou envio das músicas para um serviço externo.

## Privacidade

A biblioteca e as estatísticas de audição são tratadas localmente pelo aplicativo. O projeto não precisa enviar o catálogo musical para gerar o funcionamento normal do player.

A verificação de atualização acessa os GitHub Releases do projeto para descobrir uma versão mais recente e, quando o utilizador confirma, baixa o APK correspondente.

## Arquitetura

O projeto usa:

- Android Gradle Plugin
- Java 17
- AndroidX
- Material 3
- Media3 / ExoPlayer
- MediaSession
- MediaStore para descobrir músicas locais
- SharedPreferences para preferências, progresso, estatísticas e playlists

As principais responsabilidades estão atualmente divididas entre:

- `MainActivity.java`: shell da interface, biblioteca e recursos do player
- `PlaybackService.java`: reprodução em segundo plano e MediaSession
- `UpdateManager.java`: verificação e instalação de atualizações
- `AudioEffectsManager.java`: equalizador
- `AurenAnalytics.java`: estatísticas locais
- `AchievementManager.java`: Journey e conquistas
- `ThemeManager.java`: tema, contraste e cor de destaque
- `SettingsActivity.java`: configurações
- `BluetoothActivity.java`: pesquisa de dispositivos Bluetooth e abertura das definições do Android

A próxima etapa arquitetural depois do primeiro lançamento pode ser separar a grande `MainActivity` em componentes/screens menores. Essa refatoração foi deixada fora da preparação 3.3.0 para reduzir risco de regressão antes do lançamento.

## Desenvolvimento

O build de validação é executado pelo GitHub Actions.

Dependências importantes:

- JDK 17
- Gradle 8.13
- Android SDK 36
- Android Build Tools 35.0.0

O workflow configura essas dependências automaticamente no runner.

## Build local

Com um ambiente Android configurado:

```bash
gradle clean assembleDebug
```

O APK de teste é gerado em:

```
app/build/outputs/apk/debug/app-debug.apk
```

Para uma versão de produção assinada, o ambiente precisa de um keystore persistente e das variáveis usadas pelo projeto.

## Release

O fluxo oficial está documentado em [docs/RELEASING.md](docs/RELEASING.md). O APK anexado à GitHub Release é o mesmo build de produção que o utilizador deve instalar.

Regra principal:

**push para `main` ≠ release pública**

Uma release pública só é publicada pelo workflow de release para uma tag no formato `vX.Y.Z` ou pela linha `release/*`, sempre usando o APK de produção assinado.

A versão da release precisa corresponder ao `versionName` em `app/build.gradle`.

## Atualizações dentro do app

O atualizador consulta o GitHub Releases e compara a versão publicada com `BuildConfig.VERSION_NAME`.

Quando uma nova versão é encontrada:

- o utilizador decide se quer atualizar;
- o APK é baixado;
- o Android abre o instalador;
- caso seja necessário, o utilizador é levado às definições para permitir instalação dessa fonte.

As releases de produção precisam manter a mesma identidade de assinatura para atualizações normais do aplicativo.

## Bluetooth

A tela Bluetooth encontra dispositivos próximos e mostra dispositivos emparelhados. A etapa final de ligação de áudio é controlada pelo Android; o aplicativo abre as definições do sistema para concluir a ligação.

## Estrutura principal

```
.
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── java/com/auren/musicplayer/
│       └── res/
├── .github/workflows/release.yml
├── CHANGELOG.md
├── docs/RELEASING.md
└── README.md
```

## Licença e distribuição

Defina aqui a licença oficial e os termos de distribuição antes de uma publicação pública em lojas ou outros canais.
