/* Duo · utilidades comunes a todas las paginas */
(() => {
  "use strict";

  const meta = n => document.querySelector(`meta[name="${n}"]`)?.content;
  const CSRF = { header: meta("_csrf_header"), token: meta("_csrf") };

  /** Llamada a la API JSON con la proteccion CSRF puesta. */
  async function api(url, opciones = {}) {
    const metodo = (opciones.method || "GET").toUpperCase();
    const cabeceras = { "Accept": "application/json" };
    if (opciones.body && !(opciones.body instanceof FormData)) cabeceras["Content-Type"] = "application/json";
    if (metodo !== "GET" && CSRF.header && CSRF.token) cabeceras[CSRF.header] = CSRF.token;
    const r = await fetch(url, { ...opciones, method: metodo, headers: cabeceras, credentials: "same-origin" });
    if (r.redirected && r.url.includes("/entrar")) { location.href = r.url; throw new Error("Sesión caducada"); }
    if (!r.ok) {
      let msg = "No se ha podido guardar. Inténtalo de nuevo.";
      try { const j = await r.json(); if (j.message || j.detail) msg = j.message || j.detail; } catch (e) {}
      throw new Error(msg);
    }
    return r.status === 204 ? null : r.json();
  }

  const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const icono = id => `<svg class="ico"><use href="#i-${esc(id)}"/></svg>`;
  const pad = n => String(n).padStart(2, "0");
  const iso = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  const desdeIso = s => { const [a, m, d] = s.split("-").map(Number); return new Date(a, m - 1, (d || 1)); };
  const formatoEuros = new Intl.NumberFormat("es-ES", { style: "currency", currency: "EUR", useGrouping: "always" });
  const euros = n => formatoEuros.format(Number(n) || 0);
  const MESES = ["enero","febrero","marzo","abril","mayo","junio","julio","agosto","septiembre","octubre","noviembre","diciembre"];

  /**
   * Ventana de confirmacion propia (en vez del confirm() del navegador).
   * Devuelve una promesa con true si la persona confirma.
   * html: contenido ya escapado. peligro: boton rojo (para borrar).
   */
  function confirmar({ titulo = "¿Seguro?", html = "", aceptar = "Sí, guardar", peligro = false } = {}) {
    const dlg = document.getElementById("dlg-confirmar");
    if (!dlg) return Promise.resolve(window.confirm(titulo));
    document.getElementById("confirmar-titulo").textContent = titulo;
    document.getElementById("confirmar-texto").innerHTML = html;
    const si = document.getElementById("confirmar-si"), no = document.getElementById("confirmar-no");
    si.textContent = aceptar;
    si.classList.toggle("peligro", peligro);
    return new Promise(resolver => {
      const terminar = valor => { dlg.close(); resolver(valor); };
      si.onclick = () => terminar(true);
      no.onclick = () => terminar(false);
      dlg.oncancel = e => { e.preventDefault(); terminar(false); };
      dlg.onclick = e => { if (e.target === dlg) terminar(false); };
      dlg.showModal();
      no.focus(); // por defecto el foco en "Volver", para que un Enter no confirme sin querer
    });
  }

  window.Duo = { api, esc, icono, pad, iso, desdeIso, euros, MESES, confirmar };

  /* ---------------- Modo claro / oscuro ---------------- */
  function ponerModo(modo, guardar = true) {
    document.documentElement.dataset.modo = modo;
    const txt = modo === "claro" ? "Cambiar a modo oscuro" : "Cambiar a modo claro";
    const b = document.getElementById("cambiar-modo");
    if (b) { b.setAttribute("aria-label", txt); b.title = txt; }
    if (guardar) { try { localStorage.setItem("duo-modo", modo); } catch (e) {} }
  }
  ponerModo(document.documentElement.dataset.modo === "oscuro" ? "oscuro" : "claro", false);
  document.getElementById("cambiar-modo")?.addEventListener("click", () =>
    ponerModo(document.documentElement.dataset.modo === "claro" ? "oscuro" : "claro"));

  /* ---------------- Menu del usuario ---------------- */
  const $ = id => document.getElementById(id);
  const btnUsuario = $("btn-usuario"), menu = $("menu-usuario");
  if (btnUsuario && menu) {
    const abrir = () => { menu.hidden = false; btnUsuario.setAttribute("aria-expanded", "true"); menu.querySelector("[role=menuitem]")?.focus(); };
    const cerrar = (devolverFoco) => { menu.hidden = true; btnUsuario.setAttribute("aria-expanded", "false"); if (devolverFoco) btnUsuario.focus(); };
    btnUsuario.addEventListener("click", () => menu.hidden ? abrir() : cerrar(false));
    document.addEventListener("click", e => { if (!menu.hidden && !e.target.closest(".menu-usuario")) cerrar(false); });
    menu.addEventListener("keydown", e => {
      const items = [...menu.querySelectorAll("[role=menuitem]")];
      const i = items.indexOf(document.activeElement);
      if (e.key === "Escape") { e.preventDefault(); cerrar(true); }
      if (e.key === "ArrowDown") { e.preventDefault(); items[(i + 1) % items.length].focus(); }
      if (e.key === "ArrowUp") { e.preventDefault(); items[(i - 1 + items.length) % items.length].focus(); }
    });
    $("abrir-perfil")?.addEventListener("click", () => { cerrar(false); abrirPerfil(); });
  }

  /* ---------------- Ventana "Mi perfil" ---------------- */
  const dlg = $("dlg-perfil");
  let hayCambios = false;
  function abrirPerfil() {
    if (!dlg) return;
    $("pf-error").hidden = true; $("pf-ok").hidden = true; hayCambios = false;
    dlg.showModal();
  }
  function avisar(ok, msg) {
    $("pf-error").hidden = ok; $("pf-ok").hidden = !ok;
    (ok ? $("pf-ok") : $("pf-error")).textContent = msg;
  }
  if (dlg) {
    $("cerrar-perfil").onclick = () => dlg.close();
    // Si ha cambiado la foto o el nombre, recargar para verlo en toda la pagina
    dlg.addEventListener("close", () => { if (hayCambios) location.reload(); });
    dlg.addEventListener("click", e => { if (e.target === dlg) dlg.close(); });

    $("pf-archivo").addEventListener("change", async e => {
      const archivo = e.target.files[0];
      e.target.value = "";
      if (!archivo) return;
      if (!/^image\/(png|jpeg|webp)$/.test(archivo.type)) { avisar(false, "Elige una imagen JPG, PNG o WebP."); return; }
      try {
        const blob = await recortarCuadrado(archivo, 320);
        const datos = new FormData();
        datos.append("foto", blob, "foto.jpg");
        const perfil = await api("/api/perfil/foto", { method: "POST", body: datos });
        $("pf-foto").innerHTML = `<img src="/usuarios/${encodeURIComponent(perfil.username)}/foto?v=${perfil.fotoVersion}" alt="Tu foto de perfil">`;
        $("pf-quitar").hidden = false;
        hayCambios = true;
        avisar(true, "Foto actualizada.");
      } catch (err) { avisar(false, err.message || "No se ha podido leer la imagen."); }
    });

    $("pf-quitar").onclick = async () => {
      try {
        const perfil = await api("/api/perfil/foto", { method: "DELETE" });
        $("pf-foto").textContent = perfil.inicial;
        $("pf-quitar").hidden = true;
        hayCambios = true;
        avisar(true, "Foto quitada.");
      } catch (err) { avisar(false, err.message); }
    };

    $("pf-datos").addEventListener("submit", async e => {
      e.preventDefault();
      try {
        const perfil = await api("/api/perfil", { method: "PUT", body: JSON.stringify({ nombre: $("pf-nombre").value }) });
        if (!dlg.querySelector("#pf-foto img")) $("pf-foto").textContent = perfil.inicial;
        hayCambios = true;
        avisar(true, "Nombre guardado.");
      } catch (err) { avisar(false, err.message); }
    });
  }

  /* ---------------- Campanita de novedades ---------------- */
  const btnNov = $("btn-novedades"), panelNov = $("panel-novedades");
  if (btnNov && panelNov) {
    const cuerpo = document.body;
    const YO = cuerpo.dataset.yo, OTRO = cuerpo.dataset.otro;
    const nombres = { [YO]: cuerpo.dataset.yoNombre, [OTRO]: cuerpo.dataset.otroNombre };
    const fotos = { [YO]: btnNov.dataset.fotoYo, [OTRO]: btnNov.dataset.fotoOtro };
    const ICONO_ACCION = { ANADIR: "mas", QUITAR: "papelera", CAMBIAR: "lapiz", ACEPTAR: "check", RECHAZAR: "cerrar", BLOQUEAR: "candado" };
    const SECCION = { CALENDARIO: "Calendario", HORARIOS: "Horarios", AHORROS: "Ahorros", REPARTIR: "Repartir", PERFIL: "Perfil", SEGURIDAD: "Seguridad" };
    let lista = [], filtro = "todo";

    const haceCuanto = fecha => {
      const s = Math.round((Date.now() - new Date(fecha).getTime()) / 1000);
      if (s < 60) return "ahora";
      if (s < 3600) return `hace ${Math.floor(s / 60)} min`;
      if (s < 86400) return `hace ${Math.floor(s / 3600)} h`;
      const d = new Date(fecha);
      return `${d.getDate()} ${MESES[d.getMonth()].slice(0, 3)} · ${pad(d.getHours())}:${pad(d.getMinutes())}`;
    };
    const grupo = fecha => {
      const d = new Date(fecha); d.setHours(0, 0, 0, 0);
      const hoy = new Date(); hoy.setHours(0, 0, 0, 0);
      const dif = Math.round((hoy - d) / 864e5);
      return dif === 0 ? "Hoy" : dif === 1 ? "Ayer" : `${d.getDate()} de ${MESES[d.getMonth()]}`;
    };

    function contador(n) {
      const c = $("contador-novedades");
      c.hidden = !n; c.textContent = n > 9 ? "9+" : String(n);
      btnNov.setAttribute("aria-label", n ? `Novedades (${n} sin ver)` : "Novedades");
    }

    function pintarLista() {
      const visibles = lista.filter(n => filtro === "todo" || (filtro === "yo" ? n.usuario === YO : n.usuario !== YO));
      let ultimoGrupo = "";
      $("lista-novedades").innerHTML = visibles.map(n => {
        const g = grupo(n.fecha);
        const cab = g !== ultimoGrupo ? `<li class="grupo">${g}</li>` : "";
        ultimoGrupo = g;
        const quien = nombres[n.usuario] || n.usuario;
        const avatar = fotos[n.usuario] ? `<img src="${esc(fotos[n.usuario])}" alt="">` : esc((nombres[n.usuario] || "?")[0]);
        const accion = (ICONO_ACCION[n.accion] ? n.accion : "CAMBIAR").toLowerCase();
        return cab + `<li class="novedad ${n.nueva ? "nueva" : ""}">
          <span class="quien" style="--c: var(--c-${esc(n.usuario)})">${avatar}<span class="accion ${accion}">${icono(ICONO_ACCION[n.accion] || "lapiz")}</span></span>
          <div><p><b>${esc(quien)}</b> ${esc(n.texto)}</p>
          <small>${esc(SECCION[n.seccion] || n.seccion)} · ${haceCuanto(n.fecha)}${n.privado ? ` · ${icono("candado")} solo tú` : ""}</small></div></li>`;
      }).join("") || `<li class="vacia">${filtro === "todo" ? "Todavía no hay novedades." : "Nada por aquí."}</li>`;
    }

    async function cargar() {
      try {
        const r = await api("/api/novedades");
        lista = r.novedades; contador(r.noLeidas);
        if (!panelNov.hidden) pintarLista();
      } catch (e) { /* sin conexion: se reintenta en el siguiente ciclo */ }
    }

    async function abrirNov() {
      panelNov.hidden = false; btnNov.setAttribute("aria-expanded", "true");
      await cargar(); pintarLista();
      if (lista.some(n => n.nueva)) {
        try { await api("/api/novedades/vistas", { method: "POST", body: "{}" }); contador(0); } catch (e) {}
      }
    }
    function cerrarNov(devolverFoco) {
      panelNov.hidden = true; btnNov.setAttribute("aria-expanded", "false");
      lista = lista.map(n => ({ ...n, nueva: false }));
      if (devolverFoco) btnNov.focus();
    }
    btnNov.addEventListener("click", () => panelNov.hidden ? abrirNov() : cerrarNov(false));
    document.addEventListener("click", e => { if (!panelNov.hidden && !e.target.closest(".novedades")) cerrarNov(false); });
    document.addEventListener("keydown", e => { if (e.key === "Escape" && !panelNov.hidden) cerrarNov(true); });
    $("filtro-novedades").addEventListener("click", e => {
      const b = e.target.closest("button"); if (!b) return;
      filtro = b.dataset.f;
      $("filtro-novedades").querySelectorAll("button").forEach(x => x.setAttribute("aria-pressed", String(x === b)));
      pintarLista();
    });

    cargar();
    setInterval(() => { if (!document.hidden) cargar(); }, 60000);
  }

  /* ---------------- Avisos en el movil (Web Push) ---------------- */
  const hayPush = window.isSecureContext && "serviceWorker" in navigator && "PushManager" in window && "Notification" in window;
  const registro = hayPush ? navigator.serviceWorker.register("/sw.js").catch(() => null) : Promise.resolve(null);
  const suscripcionActual = async () => (await registro)?.pushManager.getSubscription() ?? null;
  const guardarSuscripcion = sub => api("/api/avisos/suscripcion", { method: "POST", body: JSON.stringify(sub.toJSON()) });
  // Si este movil ya tenia los avisos, se recuerda a quien pertenece (por si se entra con otra cuenta)
  if (hayPush && Notification.permission === "granted") {
    suscripcionActual().then(sub => { if (sub) guardarSuscripcion(sub).catch(() => {}); });
  }

  if ($("pf-avisos")) {
    const esIos = /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
    const instalada = matchMedia("(display-mode: standalone)").matches || navigator.standalone === true;
    const estado = $("pf-avisos-estado"), bActivar = $("pf-avisos-activar"), bPrueba = $("pf-avisos-prueba"), bQuitar = $("pf-avisos-quitar");
    const claveBytes = b64 => {
      const s = atob((b64 + "=".repeat((4 - b64.length % 4) % 4)).replace(/-/g, "+").replace(/_/g, "/"));
      return Uint8Array.from(s, c => c.charCodeAt(0));
    };

    async function pintarAvisos() {
      bActivar.hidden = bPrueba.hidden = bQuitar.hidden = true;
      if (!hayPush || !(await registro)) {
        estado.textContent = esIos && !instalada
          ? "En iPhone, primero añade Duo a la pantalla de inicio (botón Compartir → «Añadir a pantalla de inicio»), ábrela desde ese icono y vuelve aquí."
          : "Este navegador no permite avisos. Prueba con Chrome en Android o con Duo instalado en el iPhone.";
        return;
      }
      if (Notification.permission === "denied") {
        estado.textContent = "Los avisos de Duo están bloqueados. Permítelos en los ajustes del navegador o del móvil.";
        return;
      }
      const sub = await suscripcionActual();
      estado.textContent = sub ? "Activados en este dispositivo." : "Desactivados en este dispositivo.";
      bActivar.hidden = !!sub; bPrueba.hidden = bQuitar.hidden = !sub;
    }

    bActivar.onclick = async () => {
      bActivar.disabled = true;
      try {
        if (await Notification.requestPermission() !== "granted") { await pintarAvisos(); return; }
        const { clave } = await api("/api/avisos/clave");
        const reg = await registro;
        const sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: claveBytes(clave) });
        await guardarSuscripcion(sub);
        await pintarAvisos();
        avisar(true, "Avisos activados. Pulsa «Enviar prueba» para comprobarlo.");
      } catch (err) { avisar(false, err.message || "No se han podido activar los avisos."); }
      finally { bActivar.disabled = false; }
    };

    bPrueba.onclick = async () => {
      bPrueba.disabled = true;
      try {
        const r = await api("/api/avisos/prueba", { method: "POST" });
        avisar(r.entregados > 0, r.entregados > 0 ? "Aviso de prueba enviado. Debería llegarte en unos segundos." : "No se ha podido entregar el aviso. Desactívalos y vuelve a activarlos.");
      } catch (err) { avisar(false, err.message); }
      finally { bPrueba.disabled = false; }
    };

    bQuitar.onclick = async () => {
      try {
        const sub = await suscripcionActual();
        if (sub) {
          await api("/api/avisos/suscripcion", { method: "DELETE", body: JSON.stringify({ endpoint: sub.endpoint }) });
          await sub.unsubscribe();
        }
        await pintarAvisos();
        avisar(true, "Avisos desactivados en este dispositivo.");
      } catch (err) { avisar(false, err.message); }
    };

    $("abrir-perfil")?.addEventListener("click", () => pintarAvisos().catch(() => {}));
  }

  /** Recorta la imagen al cuadrado central y la reduce, para que pese poco. */
  function recortarCuadrado(archivo, lado) {
    return new Promise((resolver, rechazar) => {
      const url = URL.createObjectURL(archivo);
      const img = new Image();
      img.onload = () => {
        const corte = Math.min(img.naturalWidth, img.naturalHeight);
        const lienzo = document.createElement("canvas");
        lienzo.width = lienzo.height = lado;
        const ctx = lienzo.getContext("2d");
        ctx.fillStyle = "#fff"; ctx.fillRect(0, 0, lado, lado);
        ctx.drawImage(img, (img.naturalWidth - corte) / 2, (img.naturalHeight - corte) / 2, corte, corte, 0, 0, lado, lado);
        URL.revokeObjectURL(url);
        lienzo.toBlob(b => b ? resolver(b) : rechazar(new Error("No se ha podido procesar la imagen.")), "image/jpeg", 0.88);
      };
      img.onerror = () => { URL.revokeObjectURL(url); rechazar(new Error("No se ha podido leer la imagen.")); };
      img.src = url;
    });
  }
})();
