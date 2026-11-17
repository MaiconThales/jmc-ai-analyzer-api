Vou descrever um documento bruto, com este documento vamos ter o que precisamos ter como resultado final.

No fim isso vai ser a documentação, será gerado arquivos de SDD(spec, plan e tasks) que vão ficar localizados em ~/.spec/0.1.0/*.

Estrutura do projeto:
- io.github.maiconthales.jmc_ai_analyzer_api
	- system
	- mcp
	- ia
- Cada um desses projetos terá se necessário:
	- controllers
	- handlers
	- enums
	- entity
	- config
	- service
	- repository

Tecnologias:
- Aqui estamos focando por enquanto apenas no back-end, que vamos ter:
	- Java 17 spring boot
	- postgressql
	- docker

Bibliotecas:
- Utilizar lombok.

O que o projeto vai fazer:
- No momento vamos receber via upload 1 ou vários arquivos jfr do java mission control, e com esses arquivos retornar as informações para montar um dashboard.
- O pacote IA e MCP por enquanto ficará em WIP.
- O projeto deverá gerar um container docker com banco, back-end api e futuramente o mcp e front-end.

Obs.:
- Em caso de dúvidas sempre perguntar.
- Seguir regras do sonar.
- Testes com converage de 90%.