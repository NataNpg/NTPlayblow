# NTPlayblow

Player de vídeo e listas M3U para Android e Android TV, com visual preto, prata e ciano.
Código próprio, escrito do zero em Kotlin com Media3 (ExoPlayer).

## Telas
- **Login**: usuário e senha validados no servidor NTPlay (conta criada pelo painel admin). Sessão fica salva; vencida ou bloqueada volta ao login.
- **Início**: Minhas Listas, Todos os Vídeos, Favoritos, Adicionar, relógio.
- **Canais**: categorias (`group-title` do M3U), canais numerados, favoritos, player.
- **Player**: barra com progresso, ±10 s, play/pause, anterior/próximo, AO VIVO, formato de tela, tela cheia.
- **Perfis**: até 4, cada um com favoritos, lista e "parei aqui" próprios.
- **Configurações**: informações, formato de tela, controle parental (PIN), limpar assistidos, limpar cache, receber lista por QR, sair da conta.

## Como o login funciona
Mesmo protocolo do painel admin (`ntsync`): a chave sai da senha por PBKDF2-HMAC-SHA1 (40000 voltas); as listas da conta vêm criptografadas com AES-GCM pela própria senha. O app só **lê** do servidor, nunca grava.

## QR
Configurações → "Receber lista por QR": a TV mostra um QR; o celular (mesmo Wi-Fi) abre a página e envia o link da lista M3U. O endereço leva um código aleatório.

## Compilar
Requer JDK 17+, Android SDK 35 e Gradle 8.13 (ou abra no Android Studio).

```
gradle assembleDebug
```
APK em `app/build/outputs/apk/debug/`. É assinado com a chave de debug; para publicar, use uma assinatura de release.

## Limites conhecidos
Sem guia de programação (EPG), sem capas/logos de canais, sem catálogo de filmes e séries. Testado apenas por compilação.
