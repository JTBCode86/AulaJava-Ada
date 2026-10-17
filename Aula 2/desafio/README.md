# 🛒 Checkout Resilience Demo - Resilience4j & Circuit Breaker

Projeto desenvolvido como parte do portfólio de **System Design** (Ada Tech), com o objetivo de demonstrar padrões de resiliência em arquiteturas de microsserviços utilizando **Spring Boot 3** e **Resilience4j**.

---

## 🎯 Objetivo do Desafio

Garantir alta disponibilidade e proteção contra falhas em cascata em um fluxo crítico de **Checkout**, implementando os padrões de:
1. **Circuit Breaker**: Para monitorar a taxa de falhas em um gateway de pagamento externo e isolar o serviço caso ocorra instabilidade sistêmica.
2. **Fallback**: Para prover uma resposta alternativa e segura ao cliente final quando o serviço principal estiver indisponível.

---

## 🛠️ Tecnologias Utilizadas

* **Java 21** (Microsoft OpenJDK)
* **Spring Boot 3.2.3**
* **Resilience4j** (`resilience4j-spring-boot3`)
* **Spring AOP** (Necessário para interceptação via anotações)
* **Spring Boot Actuator** (Monitoramento de métricas)
* **Maven**

---

## 📂 Arquitetura do Projeto

```text
src/
└── main/
    └── java/
        └── com.ada.checkout.resiliencedemo/
            ├── CheckoutResilienceDemoApplication.java
            ├── controller/
            │   └── CheckoutController.java        # Expõe o endpoint e gerencia o Fallback
            └── service/
                └── ExternalPaymentService.java    # Simula a integração com o Gateway externo
```

## 🚀 Como Executar o Projeto

1. Pré-requisitos: Certifique-se de ter o Java 21 e o Maven configurados no seu ambiente.

2. Clonar ou abrir o projeto no seu IDE (como o IntelliJ IDEA).

3. Executar a aplicação:
 3.1 Abra a classe CheckoutResilienceDemoApplication.java e execute o método main().
 
 ## 🧪 Testando a Resiliência
 
 O serviço externo está configurado por padrão para simular falhas (simulateFailure = true).
 
 1. Testando o Fallback (Circuito Fechado)
Faça uma requisição POST para o endpoint de checkout:

```Bash
curl -X POST "http://localhost:8080/checkout?valor=250.00"
```

* **Comportamento: A chamada falhará no gateway e o Resilience4j acionará o método de fallback de forma transparente:**
>
	⚠️ [FALLBACK ATIVADO] O serviço de pagamento está indisponível no momento...
---

2. Testando a Abertura do Circuit Breaker
Dispare várias requisições em sequência (5 ou mais vezes rapidamente):

```Bash
curl -X POST "http://localhost:8080/checkout?valor=250.00"
```

* **Comportamento: Ao atingir o limiar de falhas configurado, o Circuit Breaker mudará o estado para OPEN, rejeitando chamadas futuras instantaneamente com a mensagem:**
> 
CircuitBreaker 'paymentService' is OPEN and does not permit further calls
---

👨‍💻 Autor
Desenvolvido por Jean Barbosa como parte dos estudos avançados em engenharia de software e arquitetura distribuída.