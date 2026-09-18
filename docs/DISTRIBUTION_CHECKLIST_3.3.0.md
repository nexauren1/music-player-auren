# Nexauren Music Player 3.3.0 — Checklist de distribuição

## Artefatos oficiais
- GitHub Release: v3.3.0
- APK: Nexauren-Music-Player.apk
- SHA-256: Nexauren-Music-Player.apk.sha256
- Application ID: com.auren.musicplayer
- Version code: 364
- Version name: 3.3.0

## Canais
### Uptodown
- [ ] Criar/usar conta de desenvolvedor
- [ ] Criar a organização/ficha Nexauren
- [ ] Enviar APK oficial
- [ ] Preencher ficha com `docs/STORE_LISTING_3.3.0.md`
- [ ] Enviar screenshots reais
- [ ] Rever página publicada

### APKPure
- [ ] Criar/usar conta de desenvolvedor
- [ ] Enviar APK oficial pelo canal de publicação disponibilizado pelo serviço
- [ ] Preencher os dados da ficha
- [ ] Enviar screenshots reais
- [ ] Rever package/version antes de submeter

## Regra de atualização
As futuras versões devem manter:
- o mesmo applicationId;
- uma assinatura de produção compatível;
- versionCode crescente;
- versionName correspondente à release.

Nunca substituir o keystore de produção sem um plano de migração de assinatura.

## O que não publicar
- APK debug
- keystore
- passwords
- tokens
- ficheiros de configuração privados
- dados pessoais
