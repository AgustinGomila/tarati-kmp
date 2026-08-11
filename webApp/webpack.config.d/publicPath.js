// Fija el publicPath de webpack a la raíz del dominio.
//
// Por qué: el engine worker (IA en vivo + análisis) se crea desde un bootstrap
// `blob:` que hace `importScripts('tarati.js')`. Con `output.publicPath = 'auto'`
// (el default), el runtime de webpack calcula el publicPath desde
// `self.location.href`, que dentro de un worker creado por blob es `blob:...`;
// su heurística lo colapsa a "" y el módulo `.wasm` pasa a resolverse como ruta
// relativa contra la base `blob:` → el fetch del wasm falla y el worker muere al
// instanciar. En el dev-server la URL se resolvía distinto, así que el bug solo
// se manifestaba en producción: TODO cómputo del worker (IA y análisis) caía
// siempre al fallback en el hilo principal, y en AI-vs-AI el churn de recreación
// del worker degradaba el ritmo entre jugadas.
//
// La app se sirve siempre desde la raíz del dominio (tarati.tech/ y el dev-server
// en :3000/), donde el hilo principal ya resolvía `auto` → "/". Fijarlo a "/" es
// idéntico para el hilo principal y hace que el worker resuelva el wasm como
// "/<hash>.wasm" (absoluto al origen), independiente de la URL `blob:` del worker.
config.output = config.output || {};
config.output.publicPath = "/";
