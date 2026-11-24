package com.proyecto.servicios.repositorys.gestopago;

import com.proyecto.servicios.entity.gestopago.CatalogoProducto;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.Optional;

public interface CatalogoProductoRepository extends MongoRepository<CatalogoProducto, String> {

    long deleteByServicio(String servicio);

    long deleteByIdNotIn(Collection<String> ids);

    Optional<CatalogoProducto> findFirstByIdServicio(String idServicio);

    Optional<CatalogoProducto> findFirstByIdServicioAndIdProducto(String idServicio, String idProducto);

    boolean existsByIdServicio(String idServicio);
}