package com.example.cast.integration.ai.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.AssertTrue;
import java.net.URI;
/** 기본값은 전송 중지. 콜백 토큰이 비어 있으면 내부 API는 모든 호출을 거절한다. */
@Validated
@ConfigurationProperties("cast.ai")
public record AiProperties(@DefaultValue("false") boolean enabled, URI baseUrl,
        @DefaultValue("") String apiToken, @DefaultValue("") String callbackToken) {
    @AssertTrue(message = "AI 전송 활성화 시 HTTP(S) URL과 32자 이상 API/콜백 토큰이 필요합니다.")
    public boolean isConfigurationValid() {
        if (!callbackToken.isEmpty() && callbackToken.length() < 32) return false;
        return !enabled || (baseUrl != null && baseUrl.getHost() != null
                && ("http".equals(baseUrl.getScheme()) || "https".equals(baseUrl.getScheme()))
                && baseUrl.getUserInfo() == null && baseUrl.getQuery() == null && baseUrl.getFragment() == null
                && apiToken.length() >= 32 && callbackToken.length() >= 32);
    }
    @Override public String toString() { return "AiProperties[enabled=" + enabled + ", tokens=REDACTED]"; }
}
