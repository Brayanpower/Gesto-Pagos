package com.proyecto.servicios.entity.gestopago;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "catalogo_productos")
@CompoundIndex(name = "idx_servicio_producto", def = "{'idServicio': 1, 'idProducto': 1}")
public class CatalogoProducto {

    @Id
    private String id;

    private String servicio;
    private String nombreProducto;
    @Indexed
    private String idServicio;
    private String idProducto;
    private String idCatTipoServicio;
    private String tipoFront;
    private String hasDigitoVerificador;
    private String precio;
    private String showAyuda;
    private String tipoReferencia;
    private String legend;
}