package com.kpl.agent.controller;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void okResponseContainsSuccessAndData() {
        ApiResponse<Map<String, String>> response = ApiResponse.ok(Map.of("reply", "hello"));

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("ok");
        assertThat(response.data()).containsEntry("reply", "hello");
    }

    @Test
    void failResponseContainsMessage() {
        ApiResponse<Void> response = ApiResponse.fail("bad request");

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("bad request");
        assertThat(response.data()).isNull();
    }
}
