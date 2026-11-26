/* Duo · nota diaria: envio una al dia (sin poder cambiarla) y leo la que me deja el otro */
(() => {
  "use strict";
  const $ = id => document.getElementById(id);
  const form = $("nota-form");
  if (!form) return;

  const { api, pad, esc, confirmar } = window.Duo;
  const OTRO = document.body.dataset.otroNombre;
  const texto = $("nota-texto"), estado = $("nota-estado"), error = $("nota-error");
  let enviada = false;

  const hora = iso => { const d = new Date(iso); return `${pad(d.getHours())}:${pad(d.getMinutes())}`; };

  function contar() {
    $("nota-cuenta").textContent = `Solo 1 al día · ${texto.value.length}/300`;
    $("nota-enviar").disabled = !texto.value.trim();
  }

  function pintar(r) {
    enviada = !!r.mia;
    $("nota-escribir").hidden = enviada;
    $("nota-enviada").hidden = !enviada;
    if (enviada) {
      $("nota-enviada").textContent = r.mia.texto;
      estado.textContent = `Enviada · ${hora(r.mia.actualizada)}`;
    } else {
      estado.textContent = `Para ${OTRO}`;
    }
    const n = r.delOtro;
    $("nota-otro-texto").hidden = !n;
    $("nota-otro-vacia").hidden = !!n;
    $("nota-otro-texto").textContent = n ? n.texto : "";
    $("nota-otro-hora").textContent = n ? hora(n.actualizada) : "";
  }

  async function cargar() {
    try { pintar(await api("/api/notas/hoy")); } catch (e) { /* sin conexion: se reintenta */ }
  }

  texto.addEventListener("input", contar);
  form.addEventListener("submit", async e => {
    e.preventDefault();
    if (enviada || !texto.value.trim()) return;
    error.hidden = true;
    const ok = await confirmar({
      titulo: `¿Enviar la nota a ${OTRO}?`,
      html: `<p>«${esc(texto.value.trim())}»</p><p>Solo puedes mandar una al día y después no se puede cambiar.</p>`,
      aceptar: "Sí, enviar"
    });
    if (!ok) return;
    try {
      pintar(await api("/api/notas/hoy", { method: "POST", body: JSON.stringify({ texto: texto.value }) }));
      texto.value = ""; contar();
    } catch (err) { error.textContent = err.message; error.hidden = false; cargar(); }
  });

  contar();
  cargar();
  // Cada minuto mira si el otro ha escrito (y si ha cambiado el dia)
  setInterval(() => { if (!document.hidden) cargar(); }, 60000);
})();
