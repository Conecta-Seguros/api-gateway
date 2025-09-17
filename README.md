# API Gateway - Conecta Seguros

## Descripción

El API Gateway es un componente esencial de la plataforma de seguros Conecta Seguros. Actúa como punto de entrada único para todas las solicitudes de los clientes, gestionando el enrutamiento, la seguridad y los aspectos transversales como autenticación, autorización y balanceo de carga. Está construido utilizando Spring Cloud Gateway y sigue una arquitectura de microservicios.

## Tecnologías

- Java 24
- Spring Boot 3.5.3
- Spring Cloud Gateway
- Gradle
- Netflix Eureka (Service Discovery)
- OAuth2 / Keycloak (Autenticación)
- WebFlux (Reactive Programming)

## Arquitectura

El API Gateway implementa un patrón de arquitectura de puerta de enlace que centraliza las siguientes responsabilidades:

1. **Enrutamiento**: Redirige las solicitudes a los microservicios apropiados
2. **Autenticación**: Valida las credenciales de los usuarios
3. **Autorización**: Verifica los permisos de acceso a los recursos
4. **Balanceo de Carga**: Distribuye las solicitudes entre múltiples instancias de servicios
5. **Tolerancia a Fallos**: Implementa políticas de reintento y circuit breaker
6. **CORS**: Gestiona las políticas de intercambio de recursos entre dominios

### Servicios Enrutados

El gateway enruta solicitudes a los siguientes microservicios:

- `clients-service` - Gestión de clientes
- `news-service` - Gestión de noticias
- `products-service` - Gestión de productos
- `discovery-server` - Servidor Eureka

## Configuración de Variables de Entorno

El servicio utiliza un archivo `.env` para configurar las credenciales de autenticación. Debes crear uno con las siguientes variables:

```properties
SPRING_OAUTH2_CLIENT_ID=your_client_id
SPRING_OAUTH2_CLIENT_SECRET=your_client_secret
SPRING_OAUTH2_ISSUER_URI=http://localhost:8181/realms/conecta-seguros
```

## Configuración de Eureka

El servicio se registra con el servidor Eureka:

- URL: `http://eureka:password@localhost:8761/eureka`

## Configuración de Keycloak

El servicio utiliza Keycloak para la autenticación:

- URL de certificados: `http://localhost:8181/realms/conecta-seguros/protocol/openid-connect/certs`

## Ejecución Local

### Prerrequisitos

1. Java 24
2. Docker y Docker Compose (para ejecutar las dependencias)
3. Servidor Eureka
4. Keycloak

### Usando Docker Compose (Recomendado)

1. Asegúrate de tener Docker instalado

2. Desde el directorio raíz del proyecto completo:
   ```bash
   docker-compose up -d
   ```

### Ejecución Directa

1. Configura las variables de entorno en el archivo `.env`

2. Asegúrate de que los servicios Eureka y Keycloak están ejecutándose

3. Ejecuta la aplicación:
   ```bash
   ./gradlew bootRun
   ```

### Ejecución con Gradle Wrapper (Windows)

```bash
gradlew.bat bootRun
```

## Puertos

El API Gateway se ejecuta por defecto en el puerto `8080`.

## Endpoints Públicos

Los siguientes endpoints no requieren autenticación:

- `/eureka/**` - Endpoints del servidor Eureka
- `/login/**` - Endpoints de inicio de sesión
- `/oauth2/**` - Endpoints OAuth2
- `/actuator/**` - Endpoints de monitoreo

Todos los demás endpoints requieren autenticación JWT válida.

## Construcción del Proyecto

Para construir el proyecto:

```bash
./gradlew build
```

O en Windows:

```bash
gradlew.bat build
```

## Empaquetado

Para crear un JAR ejecutable:

```bash
./gradlew bootJar
```

O en Windows:

```bash
gradlew.bat bootJar
```

## Despliegue

El servicio está diseñado para ser desplegado en contenedores Docker. Para crear una imagen Docker:

1. Construye el proyecto:
   ```bash
   ./gradlew bootJar
   ```

2. Crea la imagen Docker (necesitarás un Dockerfile):
   ```bash
   docker build -t api-gateway .
   ```

3. Ejecuta el contenedor:
   ```bash
   docker run -p 8080:8080 --env-file .env api-gateway
   ```

## Monitoreo

El servicio incluye Actuator de Spring Boot para monitoreo:

- Health check: `/actuator/health`
- Métricas: `/actuator/metrics`
- Información: `/actuator/info`

## Contribución

1. Crea una rama para tu funcionalidad (`git checkout -b feature/nueva-funcionalidad`)
2. Realiza tus cambios
3. Ejecuta las pruebas (`./gradlew test`)
4. Confirma tus cambios (`git commit -m 'Añadir nueva funcionalidad'`)
5. Envía la rama (`git push origin feature/nueva-funcionalidad`)
6. Abre un Pull Request

## Licencia

Este proyecto es parte de la plataforma Conecta Seguros y está sujeto a las políticas internas de la empresa.