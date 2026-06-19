package com.smartlearnly.backend.payment.sepay;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.payment.sepay")
public class SePayProperties {
    static final String DEFAULT_QR_URL_TEMPLATE =
            "https://img.vietqr.io/image/{bankName}-{accountNumber}-compact2.png"
                    + "?amount={amount}&addInfo={paymentCode}&accountName={accountName}";

    private String webhookSecret = "";
    private String apiToken = "";
    private String accountNumber = "";
    private String bankName = "";
    private String accountName = "";
    private Duration reconciliationInterval = Duration.ofMinutes(5);
    private String paymentCodePrefix = "SLP";
    private String qrUrlTemplate = DEFAULT_QR_URL_TEMPLATE;
}
