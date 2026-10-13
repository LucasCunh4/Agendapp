# Validação da implementação

Validação realizada neste ambiente de nuvem em 13/10/2026.

## Resultados observados

- `bash scripts/cloud-setup.sh`: executado com sucesso, incluindo instalação/reutilização do SDK, ativação do JDK, configurações locais do proxy e truststore, testes, Lint, compilação e verificação da assinatura.
- `./gradlew testDebugUnitTest`: **52 testes executados, 0 falhas, 0 erros, 0 ignorados**.
- `./gradlew lintDebug`: **nenhum problema encontrado**, sem baseline ou desativação de verificações.
- `./gradlew assembleDebug`: sucesso. APK local com 19.480.263 bytes, package `br.com.agendapp`, versão 1.0.0, minSdk 26 e targetSdk 35.
- `apksigner verify --verbose`: assinatura APK v2 válida. Essa versão de assinatura é adequada ao minSdk 26.
- Gradle Wrapper completo com Gradle 8.13 e SHA-256 da distribuição oficial.
- Não foram desativadas verificações TLS, assinaturas ou checksums para instalar os toolchains.

SHA-256 do APK produzido nesta execução:

```
01a6c78ffee517fcf16aa81870ba17335e448539a82ecb6eaefe82825ad58181
```

O hash pode mudar em outra compilação, principalmente porque GitHub Actions aplica seu número de execução ao versionCode. Não se afirma identidade binária entre builds de ambientes diferentes.

## Cobertura dos testes JVM

Recorrência por intervalo/dias/posição mensal, fevereiro bissexto, meses curtos, encerramento, identidades estáveis e ocorrências independentes; alterações e exclusões por escopo com preservação de checklist e quantidade restante; conclusão hierárquica, folhas, movimentação sem ciclos e árvore de 200 níveis; conclusão manual e reabertura; pendências/datas e contagem dos resumos; lembretes relativos/absolutos, cancelamento lógico por conclusão, reagendamento, janela finita, eventos distantes e offsets de horário de verão; exclusão/restauração/expiração, restauração de fragmentos de séries; JSON, versão, referências, ciclos, ausência de IDs, mesclagem explícita e sem duplicação; pesquisa sem acentos e validação de horários.

## O que ainda exige Android

Não houve aparelho físico ou emulador disponível nesta execução. Portanto, não se afirma que foram testados: entrega real de notificações, restauração de alarmes após boot/atualização, permissões e restrições de fabricantes, toque nas notificações, arraste/hit targets, teclado, acessibilidade, rotação, desempenho com uma agenda muito grande, migração de uma instalação real e persistência Room/DataStore entre processos Android.

O README contém um roteiro de teste no celular. Casos de conflito ao restaurar uma série editada após a exclusão são explicitamente interrompidos para evitar substituir alterações; preserve/exporte um backup antes de resolvê-los. A pesquisa representa séries infinitas por um resultado de série com seleção de data, em vez de uma lista infinita de ocorrências.

## GitHub Actions

O workflow `.github/workflows/android-apk.yml` foi criado e usa os mesmos alvos Gradle validados localmente. Uma execução hospedada no GitHub é uma validação separada: integrar o Pull Request à `main` dispara a compilação. Depois, siga **Actions → APK Android → execução verde → Artifacts → Agendapp-debug-N**. A disponibilidade de um artifact remoto não é inferida a partir do APK local.
