# Auren Music Player

Auren Music Player é um reprodutor de música Android focado em uma experiência simples, local e rápida.

## Estado atual

- Biblioteca de música local através do MediaStore.
- Reprodução com Media3/ExoPlayer.
- MediaSessionService para reprodução em segundo plano.
- Controles de mídia do Android através da sessão multimédia.
- Pesquisa, favoritos, histórico e playlists locais.
- Velocidade e pitch.
- Temporizador de sono e repetição A-B.
- Interface clara e responsiva, com navegação inferior e player dedicado.

## Arquitetura

A Activity é responsável pela interface. O áudio é mantido no `PlaybackService`, que possui o `ExoPlayer` e a `MediaSession`. A Activity comunica-se com esse motor através de `MediaController`.

Não existe instalador APK automático no aplicativo. As versões oficiais devem ser distribuídas pelo Google Play.

## Build

O projeto usa Android Gradle Plugin 8.13.0, Gradle 8.13 e Java 17.

O release é preparado para Android 16 / API 36 e gera:

- Android App Bundle (AAB) para Google Play.
- APK assinado para testes/distribuição direta.

Os builds são realizados no GitHub Actions. O release oficial é acionado por uma tag `v*`.

## Identidade

**Auren Music Player**

Simples. Pessoal. Sempre a evoluir.
