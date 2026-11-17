package com.proyecto.servicios.model.gestopago.catproduct;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@NoArgsConstructor
@EqualsAndHashCode
@ToString
@XmlAccessorType(XmlAccessType.FIELD)
public class Productos {

    @XmlElement(name = "producto")
    private List<Producto> listaProductos;

    public List<Producto> getProductos() {
        return listaProductos;
    }

    public void setProductos(List<Producto> listaProductos) {
        this.listaProductos = listaProductos;
    }
}