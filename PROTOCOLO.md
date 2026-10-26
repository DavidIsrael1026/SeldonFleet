# Protocolo de Classificação e Saneamento de Telemetria — Seldon Fleet

Este documento estabelece o **pré-registro metodológico** (conforme Seção 4.4 e `REQ-10` da especificação técnica) para a ingestão, normalização de unidades, derivação matemática e classificação de saúde operacional dos dispositivos móveis contidos no arquivo local `assets/frota.json`.

---

## 1. Grandezas Derivadas e Regra de Classificação Adotada

Para comparar de maneira justa aparelhos com capacidades nominais de bateria distintas (ex: `3900 mAh` vs. `6000 mAh`), o domínio calcula duas grandezas intermediárias após a normalização do consumo energético para miliampere-hora (`energiaMah`):

### 1.1 Fórmulas das Grandezas Derivadas
1. **Dreno Absoluto (`mAh/h`):** Corrente média equivalente drenada por hora de tela ligada:
   $$\text{drenoAbsoluto} = \frac{\text{energiaMah}}{\text{screenOnMinutes} \div 60,0}$$

2. **Dreno Relativo (`%/h`):** Percentual da capacidade total da bateria consumido por hora de tela ligada (grandeza utilizada na classificação):
   $$\text{drenoRelativo} = \left( \frac{\text{drenoAbsoluto}}{\text{batteryCapacityMah}} \right) \times 100,0$$

### 1.2 Limiares e Ordem Estrita de Precedência
Adota-se a Regra de Referência (Seção 4.2), avaliada em **ordem estrita de precedência** (a primeira condição verdadeira define o estado em `DeviceStatus`):

| Ordem | Estado no Domínio (`DeviceStatus`) | Rótulo na UI | Condição de Disparo (Qualquer uma basta) |
| :---: | :--- | :--- | :--- |
| **1º** | `INDETERMINADO` | `SEM DADOS` | `batteryCapacityMah == null` ou `<= 0` **OU** `screenOnMinutes == null` ou `<= 0` **OU** unidade `mWh` com `nominalVoltage == null` ou `<= 0` |
| **2º** | `CRITICO` | `CRÍTICO` | `drenoRelativo >= 12,0 %/h` **OU** `crashCount7d >= 3` **OU** `jankFramesPct >= 15,0 %` |
| **3º** | `ATENCAO` | `ATENÇÃO` | `drenoRelativo >= 7,0 %/h` **OU** `crashCount7d >= 1` **OU** `jankFramesPct >= 8,0 %` |
| **4º** | `OK` | `OK` | Nenhuma das condições anteriores foi atingida. |

**Regra de desempate para a justificativa textual (`REQ-06`):** Caso um dispositivo apresente mais de um sinal violado dentro do mesmo nível de severidade, o motivo principal é reportado priorizando: (1) Dreno Relativo, (2) Contagem de Falhas (`crashCount7d`) e (3) Quadros Perdidos (`jankFramesPct`), listando todos os sinais violados daquele estado no texto explicativo.

---

## 2. Tratamento dos Defeitos Presentes no Arquivo (`Seção 3.3`)

### 2.1 Defeito 1 — Unidade de Energia Inconsistente (`mAh` vs. `mWh` — `REQ-02`)
* **Problema:** Parte dos registros de `frota.json` informa `energyConsumed` em `mAh` (carga elétrica) e outra parte em `mWh` (energia elétrica). Comparar ambos sem converter distorce o dreno em aproximadamente $3,85\times$.
* **Tratamento Adotado:** Na fronteira de entrada (`DeviceMapper`), antes de qualquer cálculo de domínio:
    * Se `energyUnit == "mAh"`: mantém-se `energiaMah = energyConsumed`.
    * Se `energyUnit == "mWh"` e `nominalVoltage > 0`: converte-se pela relação física $Q = E / V$:
      $$\text{energiaMah} = \frac{\text{energyConsumed}}{\text{nominalVoltage}}$$
    * Se `energyUnit == "mWh"` sem `nominalVoltage` válido (`null` ou `<= 0`), `energiaMah` é definido como `null`, levando o domínio a classificar o registro como `INDETERMINADO`.

### 2.2 Defeito 2 — Registros Incompletos / Divisores Nulos (`REQ-03`)
* **Problema:** O arquivo contém registros com `batteryCapacityMah = 0` (ou ausente) e `screenOnMinutes = 0`. Ambos atuam como denominadores nas fórmulas de dreno, o que causaria divisão por zero (`NaN` ou `Infinity`).
* **Decisão Adotada:** Entre as alternativas defensáveis da Seção 3.3, decidiu-se **manter os registros na listagem e classificá-los explicitamente como `INDETERMINADO` (rotulado como `SEM DADOS` na interface)**, bloqueando a divisão matemática no início de `ClassifyDevice`.
* **Raciocínio:** Excluir o registro ocultaria do operador que há um aparelho enviando telemetria defeituosa e alteraria o total de aparelhos da frota. Classificá-lo como `INDETERMINADO` evita `NaN`/`Infinity`, preserva a soma das contagens igual ao total de registros do arquivo (`REQ-05`) e permite explicar na tela de detalhe exatamente qual campo veio zerado ou ausente (`REQ-06`).

---

## 3. Ameaças à Validade da Conclusão (`REQ-10`)

Mesmo com a implementação matematicamente exata, a regra de classificação possui limitações físicas e operacionais reais:

1. **Consumo em Segundo Plano com Tela Desligada (*Screen-Off Background Drain*):** A fórmula de Dreno Absoluto divide toda a energia consumida (`energyConsumed`) exclusivamente pelo tempo de tela ligada (`screenOnMinutes`). Isso assume que o consumo com a tela apagada foi zero. Se um aparelho passar várias horas com a tela desligada executando tarefas intensas em segundo plano (ex: download via 5G com sinal fraco, roteamento Wi-Fi ou GPS ativo impedindo o modo *Doze*) e tiver poucos minutos de tela ligada, todo esse consumo será dividido por um denominador pequeno. O resultado será uma superestimação severa do `drenoRelativo`, gerando um **falso `CRITICO`** em um aparelho fisicamente saudável.
2. **Tensão Nominal Fixa vs. Curva Real de Descarga da Bateria:** A conversão de `mWh` para `mAh` utiliza um valor fixo (`nominalVoltage`, ex: `3,85 V`). Fisicamente, a tensão de uma célula de íons de lítio varia conforme o nível de carga (de `~4,35 V` cheia até `~3,0 V` descarregada) e a corrente instantânea. Usar uma tensão fixa introduz uma margem de erro de até `~8%` na conversão para `mAh`, o que pode deslocar aparelhos próximos às fronteiras (`7%/h` ou `12%/h`) entre categorias vizinhas.