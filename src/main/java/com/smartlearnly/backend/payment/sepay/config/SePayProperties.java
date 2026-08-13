package com.smartlearnly.backend.payment.sepay.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.payment.sepay")
public class SePayProperties {
    public static final String DEFAULT_QR_URL_TEMPLATE = "https://vietqr.app/img"
            + "?acc={accountNumber}"
            + "&bank={bankName}"
            + "&amount={amount}"
            + "&des={transferContent}"
            + "&template=compact"
            + "&showinfo=true";
    public static final String DEFAULT_API_BASE_URL = "https://userapi.sepay.vn";

    private String webhookSecret = "";
    private String apiToken = "";
    private String apiBaseUrl = DEFAULT_API_BASE_URL;
    private String accountNumber = "";
    private String bankName = "";
    private String accountName = "";
    private Duration reconciliationInterval = Duration.ofMinutes(5);
    private String paymentCodePrefix = "SLP";
    private String transferContentTemplate = "";
    private String qrUrlTemplate = DEFAULT_QR_URL_TEMPLATE;
}
