package com.proyecto.servicios.client;

import com.proyecto.servicios.config.GestoPagoSendTxFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "gestoPagoSendTx",
        url = "${gestopago.auth.url}",
        configuration = GestoPagoSendTxFeignConfig.class)
public interface GestoPagoSendTx {

    @PostMapping(value = "/sistema/service/sendTx.do",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<String> sendTx(@RequestBody MultiValueMap<String, String> form);

    @PostMapping(value = "${gestopago.verify.path:/sistema/service/verifyTx.do}",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<String> verifyTx(@RequestBody MultiValueMap<String, String> form);
}
