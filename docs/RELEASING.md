# Processo de Release

## 1. Preparar a versão

Atualize estes dois valores em `app/build.gradle`:

- `versionCode`: inteiro crescente
- `versionName`: versão sem o prefixo `v`

Exemplo:

```gradle
versionCode 364
versionName '3.3.0'
```

## 2. Validar

Antes de criar uma versão:

1. Abra uma PR para `main`.
2. Aguarde o GitHub Actions concluir o build de validação.
3. Teste o APK de debug num dispositivo Android real.
4. Verifique biblioteca, reprodução, reprodução em segundo plano, favoritos, playlists, equalizador, atualizador e Desafio musical.

## 3. Assinatura

Para uma release pública é necessário manter o keystore de produção.

Configure estes GitHub Secrets:

- `AUREN_KEYSTORE_B64`
- `AUREN_KEY_ALIAS`
- `AUREN_KEY_PASS`
- `AUREN_STORE_PASS`

O keystore deve ser o mesmo usado para as releases anteriores do aplicativo. Trocar a chave impede a atualização normal de uma instalação existente.

Nunca coloque o keystore, passwords ou dados privados dentro do repositório.

## 4. Criar a release

Depois de a versão estar pronta:

```bash
git tag v3.3.0
git push origin v3.3.0
```

O workflow reconhece a tag `vX.Y.Z`, valida se ela corresponde ao `versionName`, gera um APK `release` assinado, calcula SHA-256 e publica a GitHub Release.

## 5. O que não acontece

Um push normal para `main` **não** cria uma release pública.

Uma pull request **não** cria uma release pública.

O APK debug gerado durante a validação não deve ser tratado como APK oficial do lançamento.

## 6. Atualizador dentro do app

O `UpdateManager` consulta a release mais recente do repositório.

Por isso:

- releases oficiais devem usar tags de versão limpas;
- a release precisa ter um APK;
- a assinatura deve permanecer consistente entre versões;
- apagar ou substituir a chave de produção compromete atualizações futuras.

## 7. Checklist final

Antes de anunciar o lançamento, confirmar:

- [ ] versão e versionCode corretos;
- [ ] PR validada;
- [ ] build de produção assinado;
- [ ] APK abre e reproduz música num dispositivo real;
- [ ] primeira autorização da biblioteca funciona;
- [ ] notificações/media controls funcionam;
- [ ] atualização interna encontra a nova release;
- [ ] APK e SHA-256 estão anexados à release;
- [ ] descrição da release foi verificada;
- [ ] página oficial da Nexauren aponta para a versão correta.
