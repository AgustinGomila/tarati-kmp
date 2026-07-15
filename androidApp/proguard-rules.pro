# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Reempaqueta todas las clases ofuscadas en el paquete raíz — reduce la tabla de
# strings del DEX (nombres de paquete) y satisface el check "Reempaquetar clases"
# de Play Console. Las clases mantenidas por -keep (Room *_Impl, Billing, Play
# Games, etc.) conservan su nombre y quedan fuera del reempaquetado.
-repackageclasses

# Preserva números de línea para que los stack traces de producción sean
# retraceables con el mapping.txt (Play Console / crash reports).
-keepattributes SourceFile,LineNumberTable

# Oculta el nombre del archivo fuente original manteniendo el atributo presente
# (requerido para que los números de línea sobrevivan).
-renamesourcefileattribute SourceFile
