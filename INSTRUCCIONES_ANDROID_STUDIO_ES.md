# Bruce Trigger 1.0.1 | Proyecto para Android Studio

**Estado: código fuente correspondiente a v1.0.1.** El APK Release firmado se distribuye por separado en los Assets de GitHub. El responsable del proyecto confirmó su funcionamiento básico con Bruce 1.17 en el LILYGO T-Embed CC1101 Plus.

## Abrir y compilar

1. Extrae el ZIP y abre en Android Studio la carpeta `bruce-trigger` que contiene `settings.gradle`.
2. Confirma que tienes instalado Android SDK 35. Este proyecto usa Android Gradle Plugin 8.7.3 y Java 8 como nivel del código. Para ejecutar Gradle, utiliza JDK 17 o posterior y una distribución Gradle 8.9 compatible. El proyecto incluye Gradle Wrapper con distribución 8.9; permite que Android Studio descargue las dependencias habituales.
3. Sincroniza el proyecto. Selecciona **Build > Build Bundle(s) / APK(s) > Build APK(s)** para crear una APK de depuración.
4. Instala la APK de depuración en tu teléfono Android. No sustituye una compilación release firmada para Google Play. No publiques una clave de firma en GitHub ni en otro repositorio.
5. Con el Bruce encendido y sin otro script activo, conecta por un cable USB-C que transmita datos y pulsa **CONNECT**.

## ¿Qué ha cambiado?

- El número de Bruce (`1.16.1`, `1.17` u otro) ya no bloquea por sí solo el acceso a la captura.
- Bruce Trigger comprueba las interfaces de JavaScript al conectar antes de ejecutar la captura.
- Si no está disponible el módulo de almacenamiento, omite el paso de temas y puede intentar la captura normal.
- Si falta el puente JavaScript, conserva la navegación USB, mostrando la pantalla física del LILYGO.
- Si un comando falla o el formato de dibujo no coincide, aplica las mismas protecciones de desconexión/validación que existían en v1.0. No fuerza el uso de un protocolo incompatible.
- La versión Android cambió a **1.0.1 (versionCode 101)**. No cambia el nombre de paquete `com.tabgrinder.brucetrigger`.

## Pruebas adicionales recomendadas antes de distribuir

Comprueba en un T-Embed CC1101 / Plus real con Bruce **1.17** la conexión, visualización, NEXT, PREV, SELECT, EXIT, menús y submenús, temas, juegos compatibles, desconexión/reconexión y comportamiento tras reiniciar. Repite la prueba en **1.16.1** para detectar regresiones. La lista detallada está en `docs/DEVICE_ACCEPTANCE.md`.

**Importante:** una actualización futura de Bruce que cambie los comandos, los paquetes de dibujo o la interfaz JS aún podría exigir una actualización de Bruce Trigger. El cambio elimina el bloqueo artificial por versión, no garantiza compatibilidad ilimitada.

## Pruebas automatizadas incluidas

Desde una terminal con Python 3 y un JDK disponibles:

```powershell
python tools/test.py
python tools/test_usb.py
```

Las pruebas usan respuestas y dispositivos USB simulados; no implican pruebas reales con el firmware. Los resultados del código fuente están documentados en `docs/BRUCE_DYNAMIC_COMPATIBILITY.md`.

## Firma y Play Console

El ZIP **no contiene** las claves privadas de firma. Para actualizar una aplicación existente en Google Play, utiliza tu flujo habitual de firma/upload key, genera un **AAB** de release, incrementa el código de versión si Play Console ya recibió 101 y vuelve a realizar pruebas cerradas según corresponda. Nunca subas un APK de depuración como actualización pública.
