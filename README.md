# DHL WEAR - Aplicación Dual para Android y Wear OS

## Descripción
DHL WEAR es una aplicación de seguimiento de paquetes para Android y Wear OS que permite a los repartidores (Wear OS) y receptores (Android) sincronizar y monitorear el estado de los paquetes en tiempo real, sin necesidad de conexión a Internet o servidores externos.

## Características principales

### En el reloj (Wear OS) - Modo Repartidor:
- Ver el estado actual del paquete
- Cambiar el estado del paquete (Pendiente → En camino → Entregado)
- Confirmar la entrega mediante un gesto (girar la muñeca)
- Comunicación bidireccional con la aplicación móvil emparejada

### En el teléfono (Android) - Modo Receptor:
- Ver el estado del paquete en tiempo real
- Recibir notificaciones cuando el paquete está "En camino" o "Entregado"
- Confirmar la recepción del paquete
- Valorar el servicio de entrega

## Tecnologías utilizadas
- Android SDK
- Wear OS SDK
- Wearable Data Layer API para comunicación entre dispositivos
- Sensores de movimiento en Wear OS (acelerómetro)

## Requisitos
- Android Studio
- Dispositivo Android con API 24+ (Android 7.0 Nougat o superior)
- Dispositivo o emulador Wear OS con API 30+ (Wear OS 3.0 o superior)
- Emparejamiento entre el dispositivo Android y Wear OS

## Estructura del proyecto
- **mobile**: Módulo para la aplicación de Android
- **wear**: Módulo para la aplicación de Wear OS

## Cómo ejecutar el proyecto
1. Clona este repositorio
2. Abre el proyecto en Android Studio
3. Sincroniza el proyecto con Gradle
4. Ejecuta la aplicación en un dispositivo Android y un dispositivo Wear OS emparejado

## Comunicación entre dispositivos
La aplicación utiliza la API de Wearable Data Layer de Google para establecer una comunicación bidireccional entre el teléfono y el reloj:
- **DataClient**: Para enviar y recibir datos estructurados entre dispositivos
- **MessageClient**: Para enviar mensajes de confirmación de gestos y otros eventos

## Uso de sensores
La aplicación utiliza el acelerómetro del reloj para detectar gestos de confirmación de entrega:
- El repartidor puede confirmar la entrega con un simple giro de muñeca
- El servicio de sensores funciona incluso cuando la aplicación está en segundo plano
