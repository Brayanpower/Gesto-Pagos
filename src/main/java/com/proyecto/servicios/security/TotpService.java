package com.proyecto.servicios.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class TotpService {

    private static final String DEFAULT_ISSUER = "SistemaBancario";
    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final String OTP_ALGORITHM_PARAM = "SHA1";
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int[] BASE32_LOOKUP = construirTablaBase32();

    private static final int SECRET_BYTE_LENGTH = 20;
    private static final int CODE_DIGITS = 6;
    private static final int DIGITS_MODULUS = 1_000_000;
    private static final long TIME_STEP_SECONDS = 30L;
    private static final long TIME_STEP_WINDOW = 1L;
    private static final int BASE32_BITS_PER_CHAR = 5;
    private static final int BITS_PER_BYTE = 8;
    private static final int BASE32_BLOCK_CHARS = 8;
    private static final int BYTE_MASK = 0xFF;
    private static final int BASE32_MASK = 0x1F;
    private static final int TRUNCATE_OFFSET_MASK = 0x0F;
    private static final int POSITIVE_INT_MASK = 0x7F;
    private static final int COUNTER_BYTE_LENGTH = Long.BYTES;

    private static final Pattern OTP_CODE_PATTERN = Pattern.compile("^\\d{6}$");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private String totpIssuer;

    public TotpService() {
        this(DEFAULT_ISSUER);
    }

    @Autowired
    public TotpService(@Value("${app.auth.totp.issuer:SistemaBancario}") String totpIssuer) {
        this.totpIssuer = normalizarIssuer(totpIssuer);
    }

    public String generarSecretoBase32() {
        byte[] secretBytes = new byte[SECRET_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(secretBytes);
        return encodeBase32(secretBytes);
    }

    public String construirOtpAuthUri(String correo, String secretoBase32) {
        if (correo == null || correo.isBlank()) {
            throw new IllegalArgumentException("El correo no puede ser nulo o vacio");
        }
        String normalizedSecret = normalizarSecretoBase32(secretoBase32);
        decodeNormalizedBase32(normalizedSecret);

        String issuer = normalizarIssuer(totpIssuer);
        String encodedIssuer = urlEncode(issuer);
        String label = encodedIssuer + ":" + urlEncode(correo.trim());

        return "otpauth://totp/" + label
                + "?secret=" + normalizedSecret
                + "&issuer=" + encodedIssuer
                + "&algorithm=" + OTP_ALGORITHM_PARAM
                + "&digits=" + CODE_DIGITS
                + "&period=" + TIME_STEP_SECONDS;
    }

    public String generarCodigoEnInstante(String secretoBase32, long epochSeconds) {
        byte[] secretBytes = decodeBase32(secretoBase32);
        long timeStep = Math.floorDiv(epochSeconds, TIME_STEP_SECONDS);
        return generarCodigoParaPaso(secretBytes, timeStep);
    }

    public String generarCodigoActual(String secretoBase32) {
        return generarCodigoEnInstante(secretoBase32, Instant.now().getEpochSecond());
    }

    public Optional<Long> verificarCodigoConPaso(String secretoBase32, String codigo, Long ultimoPasoUsado) {
        return verificarCodigoEnInstante(secretoBase32, codigo, Instant.now().getEpochSecond(), ultimoPasoUsado);
    }

    public Optional<Long> verificarCodigoEnInstante(
            String secretoBase32,
            String codigo,
            long epochSeconds,
            Long ultimoPasoUsado) {
        if (secretoBase32 == null || secretoBase32.isBlank() || !esFormatoCodigoValido(codigo)) {
            return Optional.empty();
        }
        byte[] secretBytes = decodeBase32(secretoBase32);
        byte[] providedBytes = codigo.getBytes(StandardCharsets.UTF_8);
        long currentStep = Math.floorDiv(epochSeconds, TIME_STEP_SECONDS);

        for (long offset = -TIME_STEP_WINDOW; offset <= TIME_STEP_WINDOW; offset++) {
            long candidateStep = currentStep + offset;
            if (esPasoPermitido(candidateStep, ultimoPasoUsado)
                    && codigoCoincideEnPaso(secretBytes, candidateStep, providedBytes)) {
                return Optional.of(candidateStep);
            }
        }
        return Optional.empty();
    }

    private boolean esFormatoCodigoValido(String codigo) {
        return codigo != null && OTP_CODE_PATTERN.matcher(codigo).matches();
    }

    private boolean esPasoPermitido(long candidateStep, Long ultimoPasoUsado) {
        return ultimoPasoUsado == null || candidateStep > ultimoPasoUsado;
    }

    private boolean codigoCoincideEnPaso(byte[] secretBytes, long candidateStep, byte[] providedBytes) {
        String expectedCode = generarCodigoParaPaso(secretBytes, candidateStep);
        return MessageDigest.isEqual(expectedCode.getBytes(StandardCharsets.UTF_8), providedBytes);
    }

    private String generarCodigoParaPaso(byte[] secretBytes, long timeStep) {
        byte[] counterBytes = ByteBuffer.allocate(COUNTER_BYTE_LENGTH).putLong(timeStep).array();
        byte[] hash = calcularHmacSha1(secretBytes, counterBytes);
        int offset = hash[hash.length - 1] & TRUNCATE_OFFSET_MASK;
        int binary = ((hash[offset] & POSITIVE_INT_MASK) << 24)
                | ((hash[offset + 1] & BYTE_MASK) << 16)
                | ((hash[offset + 2] & BYTE_MASK) << 8)
                | (hash[offset + 3] & BYTE_MASK);
        int otp = binary % DIGITS_MODULUS;
        return String.format(Locale.ROOT, "%0" + CODE_DIGITS + "d", otp);
    }

    private byte[] calcularHmacSha1(byte[] secretBytes, byte[] counterBytes) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, HMAC_ALGORITHM));
            return mac.doFinal(counterBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("No fue posible inicializar el algoritmo TOTP HMAC-SHA1", ex);
        }
    }

    private String encodeBase32(byte[] data) {
        if (data.length == 0) {
            return "";
        }
        int expectedLength = (data.length * BITS_PER_BYTE + BASE32_BITS_PER_CHAR - 1) / BASE32_BITS_PER_CHAR;
        StringBuilder encoded = new StringBuilder(expectedLength);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : data) {
            buffer = (buffer << BITS_PER_BYTE) | (value & BYTE_MASK);
            bitsLeft += BITS_PER_BYTE;
            while (bitsLeft >= BASE32_BITS_PER_CHAR) {
                int index = (buffer >> (bitsLeft - BASE32_BITS_PER_CHAR)) & BASE32_MASK;
                encoded.append(BASE32_ALPHABET.charAt(index));
                bitsLeft -= BASE32_BITS_PER_CHAR;
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (BASE32_BITS_PER_CHAR - bitsLeft)) & BASE32_MASK;
            encoded.append(BASE32_ALPHABET.charAt(index));
        }
        return encoded.toString();
    }

    private byte[] decodeBase32(String secretoBase32) {
        String normalized = normalizarSecretoBase32(secretoBase32);
        return decodeNormalizedBase32(normalized);
    }

    private String normalizarSecretoBase32(String secretoBase32) {
        if (secretoBase32 == null || secretoBase32.isBlank()) {
            throw new IllegalArgumentException("El secreto Base32 no puede ser nulo o vacio");
        }
        String upper = secretoBase32.toUpperCase(Locale.ROOT);
        StringBuilder compact = new StringBuilder(upper.length());
        for (int i = 0; i < upper.length(); i++) {
            char ch = upper.charAt(i);
            if (!Character.isWhitespace(ch)) {
                compact.append(ch);
            }
        }
        int end = compact.length();
        while (end > 0 && compact.charAt(end - 1) == '=') {
            end--;
        }
        String normalized = compact.substring(0, end);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("El secreto Base32 no puede estar vacio");
        }
        return normalized;
    }

    private byte[] decodeNormalizedBase32(String normalized) {
        int remainder = normalized.length() % BASE32_BLOCK_CHARS;
        if (remainder == 1 || remainder == 3 || remainder == 6) {
            throw new IllegalArgumentException("Longitud invalida para secreto Base32");
        }
        int expectedBytes = (normalized.length() * BASE32_BITS_PER_CHAR) / BITS_PER_BYTE;
        ByteArrayOutputStream output = new ByteArrayOutputStream(expectedBytes);
        int buffer = 0;
        int bitsLeft = 0;
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            int value = (ch < BASE32_LOOKUP.length) ? BASE32_LOOKUP[ch] : -1;
            if (value < 0) {
                throw new IllegalArgumentException("Caracter Base32 invalido");
            }
            buffer = (buffer << BASE32_BITS_PER_CHAR) | value;
            bitsLeft += BASE32_BITS_PER_CHAR;
            if (bitsLeft >= BITS_PER_BYTE) {
                output.write((buffer >> (bitsLeft - BITS_PER_BYTE)) & BYTE_MASK);
                bitsLeft -= BITS_PER_BYTE;
            }
        }
        byte[] decoded = output.toByteArray();
        if (decoded.length == 0) {
            throw new IllegalArgumentException("El secreto Base32 decodificado no puede estar vacio");
        }
        return decoded;
    }

    private static int[] construirTablaBase32() {
        int[] table = new int[128];
        Arrays.fill(table, -1);
        for (int i = 0; i < BASE32_ALPHABET.length(); i++) {
            table[BASE32_ALPHABET.charAt(i)] = i;
        }
        return table;
    }

    private String normalizarIssuer(String issuer) {
        return (issuer == null || issuer.isBlank()) ? DEFAULT_ISSUER : issuer.trim();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
