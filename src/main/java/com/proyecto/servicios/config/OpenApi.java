package com.proyecto.servicios.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApi {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sistema Bancario Core - API REST")
                        .version("2.0.0")
                        .description("API REST para la administración integral del Sistema Bancario: Gestión de Clientes, Domicilios, Cuentas Bancarias, Usuarios de Acceso, Autenticación JWT y Catálogos.")
                        .contact(new Contact()
                                .name("Equipo de Ingeniería Bancaria")
                                .email("soporte@banco.com"))
                        .license(new License().name("Apache 2.0").url("https://springdoc.org")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Servidor Local de Desarrollo")
                ))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .name("BearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Ingrese su token JWT obtenido en el endpoint /auth/login")))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"));
    }
}
