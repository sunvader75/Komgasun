# Komgasun

**Cliente Android para explorar y leer tu biblioteca de [Komga](https://komga.org/), en casa o mediante Tailscale.**

Komgasun se conecta a tu propio servidor Komga y permite navegar por bibliotecas, series y libros, leer cómics, PDF y EPUB, y sincronizar el progreso de lectura. La interfaz está en español y está desarrollada con Kotlin y Jetpack Compose.

Komgasun está basada en [Komelia](https://github.com/Snd-R/Komelia), de [Snd-R](https://github.com/Snd-R) y sus colaboradores. Su implementación para Android se ha desarrollado leyendo y tomando como base el código fuente de Komelia, con una estructura y funcionalidades adaptadas a este proyecto. Agradecemos el trabajo de sus autores y conservamos una copia de su licencia Apache License 2.0. Consulta las [atribuciones y notas de procedencia](ATTRIBUTIONS.md). Esta referencia no implica respaldo ni afiliación oficial con sus autores.

> Necesitas un servidor Komga y una cuenta con acceso a sus bibliotecas. La aplicación no incluye libros ni sustituye al servidor.

## Funciones

- Navegación por bibliotecas, series y libros, con filtros de lectura y ordenación de series.
- Sección «Continuar leyendo» debajo de las bibliotecas: muestra las lecturas en progreso de Komga y permite abrir directamente su lector. Su menú ⋮ permite marcar como leído, como no leído o eliminar el archivo, igual que dentro de las bibliotecas. Eliminar requiere confirmar el borrado físico del archivo del servidor; cancelar no cambia nada. Tras una acción confirmada por Komga, la lectura desaparece de esta sección y se actualizan las bibliotecas; si falla, se conserva y se muestra el error. Mientras se procesa una acción, no se puede ejecutar otra sobre el mismo libro. Se actualiza al volver a la pantalla principal y al pulsar actualizar.
- Lector de cómics CBR/CBZ a pantalla completa, sin las barras de estado y navegación del sistema (puedes mostrarlas temporalmente deslizando desde el borde). Al salir se recupera su estado anterior. En apaisado la página ocupa todo el ancho, conserva sus proporciones y permite desplazarse verticalmente para seguir leyendo; en vertical conserva el ajuste habitual. Mantiene el cambio de página horizontal, los controles al tocar, el zoom con dos dedos o doble toque y la precarga de las tres páginas siguientes y la anterior.
- Lector PDF que, al girar a apaisado, ajusta la página al ancho conservando sus proporciones y permite desplazarse verticalmente; en vertical mantiene el ajuste habitual. Se aplica tanto al streaming como al renderizado nativo, conservando el cambio horizontal de página, los controles y el zoom. Incluye precarga de las tres páginas siguientes y la anterior en streaming. También permite descargar el documento para renderizarlo en el dispositivo, priorizando la página actual y con caché de páginas cercanas de hasta 32 MiB.
- Lector EPUB por páginas horizontales: desliza a izquierda/derecha o usa las flechas para avanzar/retroceder, también entre capítulos; no hay desplazamiento vertical. Conserva índice, tamaño de texto y tema claro/oscuro. La posición local es aproximada (capítulo y proporción interna) y se reajusta al cambiar tamaño o ventana. Komga recibe capítulos, nunca páginas físicas dependientes del dispositivo; solo se completa en la última página medida. Los errores muestran una opción para reintentar; una caché inválida provoca una nueva descarga, sin bucles de análisis.
- Sincronización del progreso de lectura con Komga.
- Portadas originales, sin oscurecer ni teñir, tanto en libros como en «Continuar leyendo». Sin leer no añade indicadores; en lectura muestra una barra al pie de la portada y «En lectura · 35 %» debajo cuando hay un total de páginas válido. Sin total se muestra la página sin porcentaje ni barra; en EPUB se muestra «En lectura · Capítulo N», porque los capítulos sincronizados no equivalen al total de páginas de Komga. Solo el estado completado muestra «✓ Leído», destacado en la esquina superior derecha y sin barra; un progreso no completado nunca indica más del 99 %. «No disponible» se muestra por separado y puede coexistir con cualquier estado de lectura. Se conservan las etiquetas de formato y número de páginas.
- Marcar cómics y libros como leídos o no leídos desde el menú ⋮ de cada portada. «Marcar como no leído» reinicia el progreso en Komga. Se necesita conexión: el estado cambia al confirmar el servidor y, si falla, se muestra un error.
- Configuración de una dirección local y otra remota, con cambio manual entre ambas.
- Acciones de eliminación de archivos y series y vaciado de papelera, sujetas a los permisos del servidor.

> **Atención:** las acciones de eliminación afectan al servidor Komga, no solamente a la caché del teléfono. Revisa la confirmación antes de ejecutarlas.

## Requisitos

| Componente | Requisito |
| --- | --- |
| Teléfono o tableta | Android 7.0 (API 24) o posterior |
| Servidor | Komga accesible desde el dispositivo y credenciales válidas |
| Acceso local | Conectividad con la red del servidor |
| Acceso mediante Tailscale | Tailscale configurado y conectado en el teléfono, con acceso al servidor |

No hace falta exponer Komga a Internet para usar la aplicación. Tailscale se configura por separado: seleccionar esa conexión en Komgasun no instala ni activa la VPN.

## Obtener e instalar la aplicación

Puedes consultar la sección [Releases](https://github.com/sunvader75/Komgasun/releases) para comprobar si hay un APK publicado. Si no hay uno disponible, compila el proyecto siguiendo las instrucciones de abajo.

Instala únicamente APK de fuentes de confianza. Si Android lo solicita, autoriza temporalmente la instalación desde la aplicación con la que has abierto el APK.

## Conectar con Komga

1. Comprueba que puedes abrir Komga desde el navegador del teléfono.
2. Abre Komgasun y elige **Local (WiFi)** o **Tailscale (Remoto)**.
3. Introduce la URL completa del servidor, incluido el esquema y el puerto si corresponde.
4. Introduce tu usuario y contraseña de Komga y pulsa **Iniciar sesión**.
5. Abre una biblioteca, selecciona una serie y elige un libro.

Ejemplos de direcciones —sustituye los valores por los de tu instalación—:

| Conexión | Ejemplo |
| --- | --- |
| Red local | `http://192.168.1.100:25600` |
| Tailscale | `http://100.101.102.103:25600` |
| Servidor con HTTPS configurado | `https://komga.ejemplo.com` |

El puerto `25600` es el habitual de Komga, pero tu instalación puede utilizar otro. Si usas una ruta base, inclúyela en la URL, por ejemplo `https://komga.ejemplo.com/komga`.

Para alternar entre casa y acceso remoto, utiliza **Alternar servidor**. En **Configurar direcciones** puedes guardar ambas URL. Están pensadas como dos vías de acceso al mismo servidor y utilizan las mismas credenciales; no son perfiles independientes de cuentas.

### Seguridad de la conexión

- La aplicación admite HTTP y HTTPS. Si omites el esquema, actualmente utiliza HTTP.
- HTTP no cifra las credenciales ni el contenido en una conexión directa. Utilízalo solamente en redes de confianza o dentro de un túnel adecuado.
- Tailscale cifra el tráfico entre sus dispositivos. Si accedes mediante un *subnet router*, el tramo desde ese router hasta Komga no queda cifrado por Tailscale.
- No publiques el puerto HTTP de Komga directamente en Internet. Para acceso público, configura HTTPS; la [documentación de Komga](https://komga.org/docs/installation/https/) explica cómo hacerlo mediante un proxy inverso.
- La aplicación guarda las credenciales en el almacenamiento privado de Android mediante DataStore, sin cifrado adicional específico para la contraseña. Protege el dispositivo y usa una contraseña exclusiva para Komga.

## Compilar desde el código fuente

Necesitas Git, el SDK de Android con la plataforma API 36 y un entorno Java compatible. El proyecto configura JDK 25 de JetBrains para el daemon de Gradle y JDK 17 como toolchain de compilación. Puedes usar Android Studio con soporte para Android Gradle Plugin 9.0.1. Gradle puede descargar las herramientas Java configuradas y las dependencias; necesitas conexión a Internet para la preparación inicial.

```bash
git clone https://github.com/sunvader75/Komgasun.git
cd Komgasun
```

### Con Android Studio

1. Abre la carpeta del proyecto.
2. Configura la ubicación del SDK de Android y permite que Gradle resuelva las versiones Java declaradas en el proyecto.
3. Instala la plataforma API 36 si se solicita y sincroniza Gradle.
4. Ejecuta la configuración `app` en un dispositivo o emulador con Android 7.0 o posterior.

### Desde la terminal

En macOS o Linux:

```bash
./gradlew assembleDebug
```

En Windows:

```powershell
.\gradlew.bat assembleDebug
```

El APK de depuración se genera en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Es una compilación de desarrollo, no un APK de distribución firmado para producción. Si el SDK no se detecta, configura `ANDROID_HOME` o `sdk.dir` en `local.properties`; ese archivo es local y no debe subirse al repositorio.

Para ejecutar las pruebas unitarias:

```bash
./gradlew testDebugUnitTest
```

## Estado y limitaciones actuales

- Los listados solicitan hasta 500 series por biblioteca y hasta 500 libros por serie, sin cargar páginas posteriores. El servidor puede imponer un límite menor.
- La caché de PDF y EPUB no constituye un modo sin conexión completo: no se garantiza la navegación ni la sincronización sin acceso a Komga.
- La caché EPUB y su localizador privado se separan por URL de servidor, cuenta y libro (cambiar de URL también separa la posición local). La caché de otros formatos y el progreso temporal compartido del repositorio no ofrecen esa separación; evita cambiar de sesión mientras lees. Las dos direcciones de conexión están pensadas para la misma instancia.
- Los documentos muy grandes pueden consumir mucha memoria. El lector EPUB carga capítulos completos. La caché del PDF local está limitada a 32 MiB; las páginas visibles pueden ocupar memoria adicional y cada renderizado se limita a unos cuatro millones de píxeles.
- El análisis EPUB admite container/OPF con espacios de nombres predeterminados o prefijados, OPF en la raíz o en subcarpetas y XML UTF-8/UTF-16. Resuelve referencias relativas, `..` dentro del archivo, espacios y Unicode codificados y fragmentos, sin buscar por nombre de archivo ni extraer el ZIP. Conserva capítulos XHTML compuestos solo por imágenes. El WebView carga imágenes ráster, CSS y fuentes locales permitidas mediante un origen HTTPS sintético, sin extraer el archivo ni acceder a red, archivos o contenido externos. La paginación usa columnas CSS y código controlado por la aplicación; el contenido del libro se importa como DOM inerte, con CSP y eliminación de contenido activo. No se ejecutan scripts del libro ni se activan enlaces, formularios o marcos. SVG, multimedia y libros de diseño fijo complejo no están soportados; tablas y bloques grandes pueden requerir una edición reajustable.
- Se rechazan rutas que escapan del ZIP, referencias externas en el manifest, DTD/entidades XML en container/OPF, archivos con `META-INF/encryption.xml` (incluida ofuscación), spine vacío, capítulos ausentes y tipos de capítulo no HTML/XHTML. Los capítulos se conservan como HTML sin exigir XML bien formado: se admiten declaraciones DOCTYPE de EPUB2 y entidades como `&nbsp;`, sin cargar DTD ni resolver entidades durante el análisis. El título se obtiene mediante una heurística limitada a los primeros 64 KiB, sin interpretar entidades; esto no sanea el HTML para el WebView. No se admite DRM ni se promete compatibilidad EPUB completa. No se habilita JavaScript del libro.
- Límites del análisis EPUB: 10.000 entradas ZIP, 8 MiB descomprimidos por recurso y 32 MiB de bytes acumulados durante el análisis (incluidas lecturas repetidas del spine). Cada lectura posterior de recursos está limitada a 8 MiB, sin caché de recursos ilimitada. Un libro válido que exceda estos límites muestra un error. Las pruebas utilizan EPUB sintéticos; no se ha reproducido ningún archivo concreto del usuario.

## Tecnologías y estructura

Kotlin · Jetpack Compose · Material 3 · Navigation 3 · ViewModel y StateFlow · Coroutines · OkHttp · Kotlin Serialization · DataStore · Coil · PdfRenderer · WebView.

```text
app/src/main/java/com/sunvader/komgasun/
├── data/
│   ├── local/          # Preferencias y configuración
│   ├── model/          # Modelos de datos
│   ├── remote/         # Cliente de la API de Komga
│   └── repository/     # Acceso a datos y progreso de lectura
├── theme/              # Tema de la interfaz
└── ui/                 # Acceso, catálogo y lectores de cómic, PDF y EPUB
```

Las pruebas unitarias están en `app/src/test/`.

## Diagnosticar páginas de cómic en carga

En el lector de cómics pulsa **Diagnóstico**, disponible cuando la página actual lleva al menos **1,5 segundos** cargando o sin mostrarse tras un fallo, incluso con los controles ocultos. Durante esa espera no aparece ni el botón ni su fondo; el retardo se reinicia al cambiar de página o comenzar otra carga. El botón y el panel expandido desaparecen cuando se muestra la página correctamente. Puedes expandirlo, **Actualizar tiempos y memoria**, **Copiar diagnóstico** al portapapeles del teléfono y volver a ocultarlo. No reintenta ni descarga páginas al abrirlo. Está disponible también en el APK de depuración, sin ordenador ni logcat.

El informe muestra la página actual, solicitudes visibles y precargas observadas (LOADING pendiente, SUCCESS completada, ERROR o CANCELLED), origen indicado por Coil (MEMORY, DISK, NETWORK o UNKNOWN), tiempo total de solicitud y uso/límite de memoria global de Coil en bytes. Conserva como máximo 64 solicitudes de esta sesión; se descarta al salir del lector. El historial identifica cada solicitud con un ID y su propia página, en orden de inicio (más antigua primero), no de finalización. Inicio y fin se expresan en milisegundos desde el inicio de la sesión; el fin de una solicitud en curso aparece como «pendiente». Actual y Precarga resumen solo la página seleccionada. Los tiempos pendientes se actualizan al pulsar actualizar o copiar, sin sondeo en segundo plano. Una precarga todavía no iniciada no tiene evento; aparece «sin solicitud observada», no como completada.

El tiempo total **no es el tiempo de transferencia**: incluye espera y procesamiento. Respuesta de red, descarga y decodificación no se miden por separado y se indican como no disponibles. El origen NETWORK no permite saber cuánto tardó el servidor; una caché llena no demuestra que sea la causa del bloqueo. No se ha reproducido un cómic concreto que falle: estos datos ayudan a investigar, no atribuyen una causa automáticamente. No se cambian los límites de caché. En cómics, un coordinador por sesión comparte el resultado de la misma solicitud entre precarga y visualización: promover una página no cancela su descarga. Prioriza la actual, limita a dos las cargas simultáneas y conserva resultados solo para la actual, hasta tres siguientes y una anterior; cancela trabajo fuera de esa ventana y al salir. Si al retroceder las dos cargas en curso pasan a ser anticipatorias y la nueva actual aún no tiene solicitud, cancela la de menor prioridad dentro de la ventana para darle sitio: la prioridad de lectura prevalece en ese caso sobre conservar toda precarga cercana. Nunca cancela la solicitud que se promueve a actual; mantiene como máximo dos solicitudes programadas y solo inicia una nueva anticipatoria cuando no hay otra. El PDF conserva su precarga anterior. Los eventos identifican el papel de la solicitud al iniciarse: una precarga promovida mantiene su ID y su etiqueta PRELOAD, sin inventar otra solicitud visible ni otros tiempos. Esto evita solicitudes duplicadas en este recorrido, pero no demuestra haber resuelto todas las posibles causas de lentitud.

La copia contiene solo páginas, métricas y categorías de error (TIMEOUT, NETWORK u OTHER), nunca URL, títulos, credenciales, cabeceras ni mensajes de excepciones. Revisa el contenido antes de compartirlo; el portapapeles pertenece al sistema Android.

## Informar de problemas

Abre una [incidencia](https://github.com/sunvader75/Komgasun/issues) indicando:

- Versión de Android, de Komgasun y de Komga.
- Pasos para reproducir el problema y resultado esperado.
- Tipo de conexión: local, Tailscale o HTTPS.
- Formato del libro y mensaje de error, si corresponde.

No publiques contraseñas, cabeceras `Authorization`, libros privados ni registros o capturas que revelen información sensible. Si detectas una vulnerabilidad, no incluyas secretos ni detalles de explotación en una incidencia pública; consulta si el repositorio dispone de un canal privado en la pestaña **Security**.

## Licencia

### Material procedente de Komelia

[Komelia](https://github.com/Snd-R/Komelia) se distribuye bajo [Apache License 2.0](https://github.com/Snd-R/Komelia/blob/main/LICENSE). Se incluye una copia íntegra en [`licenses/Komelia-Apache-2.0.txt`](licenses/Komelia-Apache-2.0.txt). Esta licencia corresponde al material reutilizado de Komelia; no supone por sí sola que todo el código propio de Komgasun se publique bajo ella.






## Enlaces

- [Komelia: proyecto original en el que se basa Komgasun](https://github.com/Snd-R/Komelia)
- [Komga: documentación oficial](https://komga.org/docs/introduction/)
- [Tailscale: documentación](https://tailscale.com/kb)
- [Código fuente de Komgasun](https://github.com/sunvader75/Komgasun)
