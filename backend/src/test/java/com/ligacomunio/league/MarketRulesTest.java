package com.ligacomunio.league;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MarketRulesTest {

	private static final BigDecimal VALOR = new BigDecimal("10000000");

	@Test
	void elMaximoEsElValorMasElVeintePorCiento() {
		assertThat(MarketRules.maximo(VALOR, 20)).isEqualByComparingTo("12000000");
		assertThat(MarketRules.minimo(VALOR)).isEqualByComparingTo("10000000");
	}

	@Test
	void elMaximoSeRedondeaHaciaAbajoAEurosEnteros() {
		assertThat(MarketRules.maximo(new BigDecimal("1000003"), 20)).isEqualByComparingTo("1200003");
		assertThat(MarketRules.maximo(new BigDecimal("1000001.50"), 20)).isEqualByComparingTo("1200001");
	}

	@Test
	void losLimitesExactosSeAceptan() {
		assertThat(MarketRules.motivoDeRechazo(new BigDecimal("10000000"), VALOR, 20)).isNull();
		assertThat(MarketRules.motivoDeRechazo(new BigDecimal("12000000"), VALOR, 20)).isNull();
		assertThat(MarketRules.motivoDeRechazo(new BigDecimal("11000000.00"), VALOR, 20)).isNull();
	}

	@Test
	void porDebajoDelValorOPorEncimaDelTopeSeRechaza() {
		assertThat(MarketRules.motivoDeRechazo(new BigDecimal("9999999"), VALOR, 20)).contains("entre").contains("20 %");
		assertThat(MarketRules.motivoDeRechazo(new BigDecimal("12000001"), VALOR, 20)).contains("entre");
	}

	@Test
	void unImporteConDecimalesSeRechaza() {
		assertThat(MarketRules.motivoDeRechazo(new BigDecimal("10500000.50"), VALOR, 20)).contains("entero");
	}
}
