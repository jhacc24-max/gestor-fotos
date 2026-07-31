# Gestor de Fotos (Android · Jetpack Compose)

## Cómo compilarlo
1. Abre la carpeta `gestor-fotos` completa en Android Studio (Hedgehog o más reciente).
2. Deja que sincronice Gradle (necesita internet la primera vez, para bajar uCrop desde JitPack).
3. Ejecuta en un emulador o dispositivo con Android 8.0 (API 26) o superior.
4. Al abrir la app, concede el permiso de fotos cuando lo pida.

Si tu paquete/carpeta de proyecto no se llama `com.example.gestorfotos`, actualiza
`namespace` y `applicationId` en `app/build.gradle.kts`, y el paquete en cada archivo `.kt`.

## Qué hace cada pieza

- **Fotos**: agrupa por fecha las imágenes que aún no están en ningún álbum (al mover una
  foto a un álbum, desaparece de aquí — la decisión que confirmaste).
- **Álbumes**: crear, renombrar, borrar (borrar el álbum no borra las fotos, vuelven a Fotos).
- **Buscar**: consulta contra las etiquetas manuales y el texto detectado dentro de la imagen.
- **Favoritos**: marca con la estrella desde cualquier vista o desde el visor.
- **Papelera**: usa la papelera real de Android 11+ (`MediaStore.createTrashRequest`), con
  aviso de confirmación del sistema y purga automática configurable (30 días).
- **Sugerencias de limpieza**: duplicados por hash perceptual + fotos borrosas por varianza
  del Laplaciano, calculado en segundo plano por `IndexingWorker`.
- **Visor de foto**: rotar, recortar (uCrop), compartir (share sheet nativo), papelera,
  favorito y etiquetas con autocompletado.
- **Deshacer**: cada acción que mueve/oculta fotos deja un Snackbar con "DESHACER" por 4s.

## Limitaciones a resolver antes de producción

- El **OCR** (ML Kit) corre en un `Worker` cada 6h y al abrir la app; en una app real
  conviene además dispararlo justo al detectar una foto nueva (observador de MediaStore).
- El **hash de duplicados** es un aHash simple (8×8) — funciona bien para capturas de
  pantalla repetidas o ráfagas, pero para fotos recortadas/editadas conviene un hash
  perceptual más robusto (pHash con DCT) o embeddings de un modelo de visión.
- El **deshacer de "eliminar álbum"** no restaura qué fotos estaban en él (habría que
  guardar esa lista antes de borrar); está marcado con un comentario en el código.
- La papelera en **Android 9 y anteriores** (API < 30) no tiene equivalente de sistema:
  se implementó como marca en Room, y el archivo real solo se borra al purgar. En la
  práctica, para minSdk 26–29 conviene revisar permisos de escritura en esos casos.
- Falta manejar **rotación de pantalla / proceso muerto** en el flujo de recorte con
  uCrop (guardar `photoId` pendiente en `SavedStateHandle` en vez de una variable local).
