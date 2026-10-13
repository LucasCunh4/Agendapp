# Agendapp

Agenda pessoal Android, em português, com Kotlin e Jetpack Compose. Funciona offline, sem login, servidor, Firebase ou permissão de internet. Android 8.0 (API 26) ou superior.

## Gerar e instalar usando somente o celular

1. Abra este repositório no navegador do celular e entre na sua conta GitHub. Se estiver recebendo o projeto por Pull Request, abra a aba **Pull requests**, revise e toque em **Merge pull request** para integrar à `main`.
2. Entre em **Actions → APK Android**. Todo push na `main` compila automaticamente. Para gerar manualmente, toque em **Run workflow**, escolha `main` e confirme. No navegador, pode ser necessário ativar **Site para computador** para ver o botão; isso não exige um computador.
3. Aguarde a execução ficar verde. Abra essa execução e role até **Artifacts**. Baixe **Agendapp-debug-N** (é necessário estar conectado ao GitHub). O artifact permanece disponível por 30 dias; se expirar, execute o workflow novamente.
4. Abra o ZIP baixado no aplicativo de arquivos do Android e extraia `app-debug.apk`. Instale o APK. Quando solicitado, autorize **Instalar apps desconhecidos** apenas para o navegador ou gerenciador de arquivos usado. O Play Protect pode pedir confirmação por ser um APK de teste fora da loja.
5. Abra o Agendapp. Em **Menu → Configurações**, permita notificações. No Android 13+, aceite a permissão. No Android 12+, abra **Configurar alarmes exatos** e autorize o Agendapp se desejar lembretes pontuais. Sem essa autorização, o app usa alarmes inexatos e informa que podem atrasar.
6. Para atualizar, gere/baixe o artifact da execução mais recente e instale sobre o app existente. A chave de debug é fixa entre builds deste repositório, portanto os dados são mantidos. Não desinstale para atualizar. Exporte um backup JSON antes de qualquer atualização importante.

O workflow executa testes JVM, Android Lint, `./gradlew assembleDebug` e verificação de assinatura. Falhas impedem a publicação do APK. Relatórios são publicados separadamente como **Agendapp-relatorios-N**. Pull Requests também são validados.

### APK de teste e distribuição

O APK debug é instalável diretamente e não exige Play Store. A chave `tools/agendapp-debug.keystore` é uma **chave pública de teste**, com os valores padrão do Android (`android`), usada exclusivamente para permitir atualizações entre APKs debug. Ela **não protege uma distribuição comercial**. Uma versão de distribuição precisa de assinatura privada própria, armazenamento seguro da chave, política de versões e revisão das exigências da loja. Mudar a assinatura impede a atualização direta: faça backup, desinstale e importe após instalar a distribuição. Não use a chave de teste em produção.

## Funcionalidades

- Agenda diária com setas, gesto horizontal, calendário mensal com pontos dourados, criação na data selecionada e cartões que expandem na própria tela.
- Eventos sem horário primeiro, compromissos por horário em seguida e concluídos ao final. Nome vazio aparece como **Sem título**; término isolado aparece como **Até HH:mm**. Observações e local vazios não ocupam os detalhes.
- Pendências anteriores expandem na agenda, mantendo suas datas. Pesquisa ignora caixa e acentos: eventos armazenados são encontrados em qualquer data; séries recorrentes aparecem como resultados próprios para consultar uma ocorrência em qualquer data, inclusive além da janela de alarmes, sem listar uma série infinita.
- Etapas de profundidade arbitrária com notas, horário, lembretes e conclusão. A agenda inicia subníveis recolhidos; a notificação expande o caminho até a etapa. O progresso conta somente folhas. Níveis profundos mostram o número do nível, limitando apenas a indentação visual, nunca a profundidade dos dados.
- No editor, digite um nome e toque em `+` para adicionar uma etapa. Use **+ Subetapa** ou segure o ícone de arraste: o destino recebe destaque; mova à direita para aninhar, ao centro para reordenar e à esquerda para subir um nível. Ciclos são rejeitados. Abra o ícone de ajustes para notas, horário e lembretes próprios.
- Conclusão de uma etapa marca descendentes e recalcula ancestrais. Eventos com todas as folhas concluídas são concluídos automaticamente. Conclusão manual permite manter as etapas pendentes. Alterar uma etapa pendente reabre o evento. **Reabrir** mantém os estados do checklist e restaura somente lembretes elegíveis; nenhum evento é concluído pela passagem do horário.
- Recorrências diárias, semanais, mensais, anuais e personalizadas por intervalo, dias da semana ou posição mensal, com fim por data/quantidade ou sem fim. A data inicial precisa corresponder aos dias/posição escolhidos; o editor informa quando é necessário ajustar a data. Dias 29–31 usam o último dia do mês quando necessário; uma quinta ocorrência de dia da semana inexistente é pulada. Anos não bissextos usam 28/02 para séries de 29/02.
- Editar/excluir uma recorrência oferece **Somente esta**, **Esta e as próximas** (padrão) e **Toda a série**. Apenas a ocorrência usa uma exceção; as outras opções versionam regras. Edições preservam ocorrências concluídas e os checklists já registrados, incluindo estados parciais. Os campos gerais dos registros em andamento são atualizados no escopo escolhido; seus checklists são preservados, a ocorrência escolhida recebe a edição completa e eventos ainda não materializados usam a nova regra. Alterar a regra com escopo **Somente esta** não modifica a série. Remover a recorrência com escopo ampliado encerra a regra e mantém a ocorrência editada como evento independente.
- Resumos de hoje/amanhã independentes, configuráveis em horas e minutos, incluindo dias vazios e eventos concluídos. A contagem é feita no disparo, sem nomes de compromissos ou etapas.
- Múltiplos lembretes relativos e absolutos, sem limite na interface. Início novo adiciona 15 minutos antes, que pode ser removido. Término isolado e etapas não ativam lembretes automaticamente. Sem horário não há alarme individual. Instantes passados são ignorados. Lembretes absolutos da série pertencem apenas à ocorrência de origem; relativos seguem cada ocorrência.
- Lixeira de eventos/séries/etapas por 30 dias, com restauração e exclusão definitiva confirmada. Descendentes acompanham a etapa excluída. Se o evento ou ancestral estiver ausente, a restauração explica qual deve ser restaurado primeiro. Fragmentos de séries são reunidos se não houver alterações conflitantes; se houver, a operação para e orienta preservar um backup, sem substituir dados silenciosamente.
- Backup JSON completo, versionado, selecionável e copiável, incluindo regras, exceções, checklist, configurações e lixeira. Importação substitutiva exige confirmação. Mesclagem mostra o número de conflitos e exige escolha explícita entre versão local/importada; relações inválidas ou séries sobrepostas impedem a transação inteira. Exporte as versões antes de resolver conflitos. Atualizações da base e configurações são atômicas em Room; DataStore mantém o espelho de preferências para a interface.
- Modos claro/escuro com branco, cinza e dourado fosco.

## Notificações offline e limites do Android

AlarmManager agenda uma janela de 14 dias, usando URIs UUID únicas para cada lembrete. Para respeitar o limite de alarmes por aplicativo do Android, mantém até 200 disparos mais próximos e repõe a fila a cada disparo; a quantidade de lembretes configurados não é limitada. Disparos já vencidos que ainda estão sendo entregues são preservados durante a renovação, e recibos evitam notificações duplicadas. Um alarme de renovação diária e WorkManager de manutenção a cada 12 horas renovam essa janela. A manutenção também limpa a lixeira na abertura. Boot, atualização, mudança de hora/fuso e concessão de alarme exato reconstruem os alarmes. Voltar ao aplicativo reavalia permissões. Alterações cancelam/reconstroem alarmes; o receiver verifica novamente os dados antes de notificar para evitar alertas de itens concluídos ou excluídos.

Horários são interpretados no fuso atual do aparelho; em lacunas de horário de verão são deslocados para o primeiro horário válido correspondente, e em sobreposição usa-se o primeiro offset, conforme `java.time`. Lembretes absolutos representam data/hora locais, ajustadas junto com o fuso do dispositivo.

O app usa `SCHEDULE_EXACT_ALARM` com autorização explícita, apropriada a uma agenda; não usa a permissão restrita `USE_EXACT_ALARM`. WorkManager realiza manutenção e **não substitui alarmes exatos**. Economia de bateria, restrições do fabricante, ausência de permissão e longos períodos de inatividade podem afetar a entrega. Não há promessa de notificações após **Forçar parada**: abra o aplicativo novamente.

### Roteiro de teste no celular

1. Ative as permissões e envie uma notificação de teste em Configurações.
2. Crie um evento para daqui a alguns minutos, remova o lembrete de 15 minutos e adicione **Na hora**. Ative modo avião, saia do app normalmente e aguarde. Toque na notificação: a data/evento devem abrir expandidos.
3. Repita com uma subetapa; confira o caminho expandido. Conclua/exclua outro evento antes do alarme e confirme que ele não chega. Reabra/restaure e confira somente lembretes futuros.
4. Configure os resumos da manhã/noite para poucos minutos à frente; teste dias com 0, 1 e vários eventos, incluídos concluídos e recorrentes.
5. Reinicie o celular e teste novamente. Altere o fuso/horário e confira reconstrução. Teste com e sem alarmes exatos, modo escuro, arraste, teclado e diferentes tamanhos de fonte.
6. Exporte JSON, copie-o para uma nota segura, substitua/mescle o backup e confira etapas, lixeira, estados e recorrências. Desinstalar elimina a base local; exporte antes.

Notificações e gestos em aparelho físico não são considerados validados apenas porque a compilação e os testes JVM passaram.

## Arquitetura e arquivos

- `domain/Models.kt`: DTOs serializáveis, UUIDs, regras, ocorrências e lixeira.
- `domain/Recurrence.kt`: geração sob demanda e filtragem de agenda/pendências.
- `domain/Checklist.kt`, `Engine.kt`: integridade, conclusão, movimentação, edição/exclusão/restauração.
- `domain/ReminderPlanner.kt`: cálculo puro de alarmes e janela futura.
- `domain/BackupCodec.kt`: formato JSON v1, validação e mesclagem explícita.
- `data/Repository.kt`: documento agregado Room, transações e preferências DataStore. É um agregado local transacional; versões futuras devem fornecer migrações, sem destruição automática da base.
- `notifications/`: AlarmManager, canais, receivers de sistema e WorkManager.
- `ui/`: ViewModel/StateFlow, Navigation Compose e telas reais.
- `app/src/test/`: testes JVM de domínio, sem dependência de um aparelho.
- `.github/workflows/android-apk.yml`: compilação automatizada e artifacts.

## Compilação por terminal na nuvem

Versões: Gradle 8.13 (Wrapper completo com SHA-256), AGP 8.9.2, Kotlin/Compose compiler 2.1.20, KSP 2.1.20-1.0.32, SDK/build-tools 35, bytecode Java 17. GitHub Actions usa JDK 17 e runner Ubuntu 24.04, reutilizando o SDK oficial já instalado no runner; este ambiente pode usar JDK 21.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Relatório de testes: `app/build/reports/tests/testDebugUnitTest/index.html`. Resultados Lint: `app/build/reports/lint-results-debug.html`.

Neste ambiente Codex, `bash scripts/cloud-setup.sh` instala/reutiliza os toolchains oficiais, valida downloads, configura o proxy existente e compila/testa. `source scripts/cloud-env.sh` ativa os caminhos em terminais futuros. Ambos usam `/workspace/Agendapp`, caches fora do checkout e `local.properties` ignorado. Nenhuma credencial é salva nos scripts. Na GitHub Actions esses scripts não são necessários.

Use o checkout existente: tarefas Codex já são isoladas e não precisam de worktrees adicionais. Não envie `local.properties`, caches, APKs ou chaves privadas de distribuição ao Git.
