package io.github.yikunli774.ordering.table;

import io.github.yikunli774.ordering.common.api.ApiErrorCode;
import io.github.yikunli774.ordering.common.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Signs a dining-table code into the string a QR code carries: "{code}.{hmac}".
 * Verifying rejects any code whose HMAC does not match, so a customer cannot forge
 * a different table number (e.g. change T01 to T99) by editing the scanned value.
 */
@Component
public class TableCodeSigner {

    private final byte[] secret;

    public TableCodeSigner(@Value("${security.table-code.secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String sign(String code) {
        return code + "." + hmac(code);
    }

    /** Returns the verified table code, or throws if the token is malformed/forged. */
    public String verify(String token) {
        int dot = token == null ? -1 : token.lastIndexOf('.');
        if (dot < 1 || dot == token.length() - 1) {
            throw invalid();
        }
        String code = token.substring(0, dot);
        String signature = token.substring(dot + 1);
        boolean ok = MessageDigest.isEqual(
                hmac(code).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            throw invalid();
        }
        return code;
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(result);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC", e);
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.TABLE_CODE_INVALID, "Invalid table code");
    }
}
