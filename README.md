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

## Distribuição

**Neste momento, o Auren não depende da Google Play.**

As versões podem ser distribuídas gratuitamente através do GitHub Releases. Cada release publica o APK e o respetivo SHA-256 para verificação. O GitHub permite disponibilizar ficheiros binários como assets das releases e partilhar uma URL estável para a release mais recente. citeturn881013search5turn881013search3

A Google Play fica como uma etapa futura, quando houver condições para criar a conta de desenvolvedor. A inscrição do Play Console atualmente custa US$ 25 uma única vez. citeturn881013search6

## Arquitetura

A Activity é responsável pela interface. O áudio é mantido no `PlaybackService`, que possui o `ExoPlayer` e a `MediaSession`. A Activity comunica-se com esse motor através de `MediaController`.

Não existe instalador APK automático dentro do aplicativo.

## Build

O projeto usa Android Gradle Plugin 8.13.0, Gradle 8.13, Java 17 e API 36.

O workflow de release:

1. Compila um APK assinado com a chave de release quando os secrets de assinatura existem.
2. Caso contrário, compila um APK debug assinado para permitir distribuição e testes sem custo inicial.
3. Publica o APK e o SHA-256 como GitHub Release.

O projeto não precisa pagar a Google Play para continuar a ser desenvolvido, testado e distribuído desta forma.

## Desenvolvimento

O GitHub Actions também executa compilação e lint em separado antes da preparação de uma release.

## Identidade

**Auren Music Player**

Simples. Pessoal. Sempre a evoluir.
