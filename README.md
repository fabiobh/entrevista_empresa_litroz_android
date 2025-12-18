# Relatório - Todo App Android

## O que foi desenvolvido

Criei um app de lista de tarefas para Android seguindo os requisitos do teste. O foco principal foi fazer um app que funciona offline e sincroniza automaticamente quando tem internet.

## Tecnologias usadas

- **Kotlin** com Jetpack Compose para a interface
- **Room** para salvar dados localmente
- **Retrofit** para comunicar com a API
- **Hilt** para injeção de dependência
- **WorkManager** para sincronização em background
- **MockAPI.io** como backend (já que o JsonPlaceholder não salva dados)

Usei Clean Architecture para organizar o código em camadas bem separadas.

## Funcionalidades principais

**Gerenciamento de tarefas:**
- Criar, editar, marcar como concluída e excluir tarefas
- Interface simples e intuitiva

**Funcionamento offline:**
- Todas as operações funcionam sem internet
- Dados ficam salvos no celular
- Mostra se está online ou offline na tela

**Sincronização automática:**
- Quando volta a internet, sincroniza tudo automaticamente
- Funciona em background mesmo com o app fechado
- Se der erro, tenta novamente depois

## Como funciona por dentro

Dividi o código em 3 camadas:

1. **Domain** - regras de negócio e modelos
2. **Data** - acesso aos dados (local e remoto)  
3. **Presentation** - telas e ViewModels

O app sempre salva primeiro no banco local, depois tenta sincronizar com o servidor quando possível. Se houver conflito, a versão mais recente ganha.

## Testes

Escrevi vários tipos de teste:
- Testes unitários para cada camada
- Testes de propriedade usando Kotest
- Testes de integração end-to-end
- Testes de interface

No total foram 9 propriedades testadas automaticamente, cobrindo desde persistência de dados até threading.

## Principais desafios resolvidos

- **Compatibilidade com MockAPI**: Tive que ajustar o formato dos IDs para funcionar com o ObjectId do MongoDB
- **Sincronização inteligente**: Implementei um sistema que sabe quando sincronizar e como resolver conflitos
- **WorkManager com Hilt**: Configurei corretamente a injeção de dependência para jobs em background

## Resultado final

O app ficou funcional e atende todos os requisitos. Funciona bem offline, sincroniza automaticamente e tem uma interface limpa. A arquitetura permite adicionar novas funcionalidades facilmente no futuro.

Todos os testes passam e não encontrei bugs críticos durante o desenvolvimento.