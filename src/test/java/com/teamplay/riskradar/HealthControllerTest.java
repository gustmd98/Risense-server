package com.teamplay.riskradar;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamplay.riskradar.api.HealthController;
import org.junit.jupiter.api.Test;

/** DB 없이 도는 가벼운 단위 테스트 (컨텍스트 로드 X) */
class HealthControllerTest {

    @Test
    void healthReturnsUp() {
        assertThat(new HealthController().health())
            .containsEntry("status", "UP");
    }
}
