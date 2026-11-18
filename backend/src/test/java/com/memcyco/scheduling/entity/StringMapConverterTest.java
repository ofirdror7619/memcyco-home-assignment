package com.memcyco.scheduling.entity;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StringMapConverterTest {
    private final StringMapConverter converter = new StringMapConverter();

    @Test
    void roundTripsParametersAndTreatsMissingValuesAsEmpty() {
        Map<String, String> parameters = Map.of("message", "hello");

        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(parameters)))
                .containsExactlyEntriesOf(parameters);
        assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("{}");
        assertThat(converter.convertToEntityAttribute(" ")).isEmpty();
    }

    @Test
    void rejectsInvalidStoredJson() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Stored task parameters are invalid");
    }
}
