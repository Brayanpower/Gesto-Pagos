package com.proyecto.servicios.model.banco;

import com.proyecto.servicios.entity.cuentas.EstatusCuenta;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CuentaActualizacionRequest {

    @NotNull(message = "El nuevo estatus de la cuenta es obligatorio")
    private EstatusCuenta estatus;

    @Size(max = 255, message = "La observacion de revision no debe exceder 255 caracteres")
    private String observacionRevision;

    public CuentaActualizacionRequest(EstatusCuenta estatus) {
        this.estatus = estatus;
    }
}
