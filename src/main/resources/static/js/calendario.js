/* Duo · calendario compartido */
(() => {
  "use strict";

  /* ---------------- Datos de la sesion ---------------- */
  const cuerpo = document.body;
  const YO = cuerpo.dataset.yo;
  const OTRO = cuerpo.dataset.otro;
  const nombres = { [YO]: cuerpo.dataset.yoNombre, [OTRO]: cuerpo.dataset.otroNombre, juntos: "Juntos" };
  const meta = n => document.querySelector(`meta[name="${n}"]`)?.content;
  const CSRF = { header: meta("_csrf_header"), token: meta("_csrf") };

  const DIAS = ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"];
  const MESES = ["enero","febrero","marzo","abril","mayo","junio","julio","agosto","septiembre","octubre","noviembre","diciembre"];
  const ICONOS_ELEGIBLES = ["cuenco","cine","pin","ola","pesa","raqueta","unas","libro","maletin","regalo","musica","tijeras","corazon","destello"];

  let actividades = [];   // [{id, titulo, fecha "aaaa-mm-dd", hora "hh:mm", quien, tipoId, tipoNombre, icono, lugar, descripcion, fotos: [id]}]
  let tipos = [];         // [{id, nombre, icono, propio}]

  /* ---------------- Utilidades ---------------- */
  const $ = id => document.getElementById(id);
  const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const icono = id => `<svg class="ico"><use href="#i-${esc(id)}"/></svg>`;
  const pad = n => String(n).padStart(2, "0");
  const iso = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  const desdeIso = s => { const [a, m, d] = s.split("-").map(Number); return new Date(a, m - 1, d); };
  const mismoDia = (a, b) => a.getTime() === b.getTime();
  const lunesDe = d => { const x = new Date(d); x.setDate(x.getDate() - ((x.getDay() + 6) % 7)); return x; };
  const sumarDias = (d, n) => { const x = new Date(d); x.setDate(x.getDate() + n); return x; };
  const diaSemana = d => DIAS[(d.getDay() + 6) % 7];
  const capital = s => s[0].toUpperCase() + s.slice(1);
  const planes = n => `${n} ${n === 1 ? "plan" : "planes"}`;
  const nombreDe = q => q === YO ? "Tú" : nombres[q] || q;

  const hoy = new Date(); hoy.setHours(0, 0, 0, 0);
  const lunesActual = lunesDe(hoy);
  let vista = "semana", filtro = "todos", desplaz = 0, quienForm = "juntos";

  async function api(url, opciones = {}) {
    const cabeceras = { "Accept": "application/json" };
    if (opciones.body) {
      cabeceras["Content-Type"] = "application/json";
      if (CSRF.header && CSRF.token) cabeceras[CSRF.header] = CSRF.token;
    }
    const r = await fetch(url, { ...opciones, headers: cabeceras, credentials: "same-origin" });
    if (r.redirected && r.url.includes("/entrar")) { location.href = r.url; throw new Error("Sesión caducada"); }
    if (!r.ok) {
      let msg = "No se ha podido guardar. Inténtalo de nuevo.";
      try { const j = await r.json(); if (j.message || j.detail) msg = j.message || j.detail; } catch (e) {}
      throw new Error(msg);
    }
    return r.status === 204 ? null : r.json();
  }

  function deDia(fecha, conFiltro = true) {
    const clave = iso(fecha);
    return actividades
      .filter(a => a.fecha === clave && a.estado !== "RECHAZADA")
      .filter(a => !conFiltro || filtro === "todos" || a.quien === filtro)
      .sort((a, b) => a.hora.localeCompare(b.hora));
  }
  const pendiente = a => a.estado === "PENDIENTE";
  const aceptada = a => !a.estado || a.estado === "ACEPTADA";
  function etiqueta(a) {
    if (pendiente(a)) return a.creadoPor === YO ? `Esperando a ${esc(nombres[OTRO])}` : "Por responder";
    return a.quien === "juntos" ? `Juntos ${icono("corazon")}` : (a.quien === YO ? "Tuyo" : esc(nombres[a.quien]));
  }
  const clasesEv = a => esc(a.quien) + (pendiente(a) ? " pendiente" : "");

  /* ---------------- Calendario ---------------- */
  function pintarSemana() {
    const lunes = sumarDias(lunesActual, desplaz * 7);
    const dom = sumarDias(lunes, 6);
    $("rango").textContent = lunes.getMonth() === dom.getMonth()
      ? `${lunes.getDate()} – ${dom.getDate()} ${MESES[dom.getMonth()].slice(0, 3)}`
      : `${lunes.getDate()} ${MESES[lunes.getMonth()].slice(0, 3)} – ${dom.getDate()} ${MESES[dom.getMonth()].slice(0, 3)}`;
    let html = '<div class="semana">';
    for (let i = 0; i < 7; i++) {
      const f = sumarDias(lunes, i);
      const evs = deDia(f);
      html += `<article class="dia vidrio${mismoDia(f, hoy) ? " hoy" : ""}"><header><small>${DIAS[i]}</small><b>${f.getDate()}</b></header>`;
      html += evs.length
        ? evs.map(a => `<button type="button" class="ev ${clasesEv(a)}" data-id="${a.id}" title="${esc(a.tipoNombre || "")}">
            <span class="h"><span>${esc(a.hora)}</span><span class="tp">${icono(a.icono)}</span></span>
            <span class="t">${esc(a.titulo)}</span><span class="q">${etiqueta(a)}</span></button>`).join("")
        : "";
      html += "</article>";
    }
    $("calendario").innerHTML = html + "</div>";
  }

  function pintarMes() {
    const base = new Date(hoy.getFullYear(), hoy.getMonth() + desplaz, 1);
    $("rango").textContent = capital(MESES[base.getMonth()]) + " " + base.getFullYear();
    const inicio = lunesDe(base);
    let html = '<div class="mes vidrio"><div class="cab">' + DIAS.map(d => `<span>${d}</span>`).join("") + '</div><div class="rejilla">';
    for (let i = 0; i < 42; i++) {
      const f = sumarDias(inicio, i);
      if (i === 35 && f.getMonth() !== base.getMonth()) break;
      const evs = deDia(f);
      const clases = ["celda", f.getMonth() !== base.getMonth() ? "fuera" : "", mismoDia(f, hoy) ? "hoy" : ""].join(" ");
      html += `<div class="${clases}"><b>${f.getDate()}</b>` +
        evs.slice(0, 3).map(a => `<button type="button" class="mini ${clasesEv(a)}" data-id="${a.id}" title="${esc(a.tipoNombre || "")}: ${esc(a.hora)} ${esc(a.titulo)}">${icono(a.icono)}<span>${esc(a.hora)} ${esc(a.titulo)}</span></button>`).join("") +
        (evs.length > 3 ? `<span class="mas-ev">+${evs.length - 3} más</span>` : "") + "</div>";
    }
    $("calendario").innerHTML = html + "</div></div>";
  }

  /* ---------------- Panel lateral y cabecera ---------------- */
  function proximos() {
    const lista = [];
    for (let i = 0; i < 7; i++) {
      const f = sumarDias(hoy, i);
      deDia(f).forEach(a => lista.push({ ...a, f }));
    }
    $("proximos").innerHTML = lista.slice(0, 4).map(a => `
      <li class="${clasesEv(a)}" data-id="${a.id}" tabindex="0" role="button" aria-label="Ver ${esc(a.titulo)}"><span class="chip">${icono(a.icono)}</span>
        <div><b>${esc(a.titulo)}</b><small>${diaSemana(a.f)} ${a.f.getDate()} · ${esc(a.hora)}</small></div>
        <span class="etq">${pendiente(a) ? "Pendiente" : a.quien === "juntos" ? "Juntos" : esc(nombreDe(a.quien))}</span></li>`).join("")
      || `<li class="juntos"><span class="chip">${icono("luna")}</span><div><b>Semana tranquila</b><small>Nada planeado</small></div><span></span></li>`;
  }

  function momentoCompartido() {
    if (!$("m-titulo")) return;  // solo en paginas que lo muestran
    const hoyIso = iso(hoy);
    const prox = actividades.filter(a => a.quien === "juntos" && aceptada(a) && a.fecha >= hoyIso)
      .sort((a, b) => (a.fecha + a.hora).localeCompare(b.fecha + b.hora))[0];
    const ruta = $("m-ruta");
    if (!prox) {
      $("m-titulo").textContent = "Aún no hay planes juntos";
      $("m-hora").textContent = "Crea uno desde «Crear actividad»";
      $("m-lugar-caja").hidden = true; ruta.hidden = true;
      return;
    }
    const f = desdeIso(prox.fecha);
    const dif = Math.round((f - hoy) / 864e5);
    $("m-titulo").textContent = prox.titulo;
    $("m-hora").textContent = `${dif === 0 ? "Hoy" : dif === 1 ? "Mañana" : diaSemana(f) + " " + f.getDate() + " " + MESES[f.getMonth()].slice(0, 3)} · ${prox.hora}`;
    $("m-lugar").textContent = prox.lugar || "";
    $("m-lugar-caja").hidden = !prox.lugar;
    ruta.hidden = !prox.lugar;
    if (prox.lugar) ruta.href = "https://www.google.com/maps/search/?api=1&query=" + encodeURIComponent(prox.lugar);
  }

  /** Cuenta los planes de lo que se esta viendo: la semana o el mes mostrados. */
  function contadores() {
    const cuenta = { [YO]: 0, [OTRO]: 0, juntos: 0 };
    let desde, dias;
    if (vista === "semana") { desde = sumarDias(lunesActual, desplaz * 7); dias = 7; }
    else { desde = new Date(hoy.getFullYear(), hoy.getMonth() + desplaz, 1); dias = new Date(desde.getFullYear(), desde.getMonth() + 1, 0).getDate(); }
    for (let i = 0; i < dias; i++) deDia(sumarDias(desde, i), false).forEach(a => { if (a.quien in cuenta) cuenta[a.quien]++; });
    $("pills").innerHTML = [[YO, "Tú"], [OTRO, nombres[OTRO]], ["juntos", "Juntos"]]
      .map(([q, n]) => `<span class="pill" style="--c: var(--c-${esc(q)})"><i></i>${esc(n)} <b>${planes(cuenta[q])}</b></span>`).join("");
  }

  const descartados = new Set();
  function recordatorios() {
    const manana = iso(sumarDias(hoy, 1));
    const lista = actividades
      .filter(a => a.fecha === manana && aceptada(a) && (a.quien === YO || a.quien === "juntos") && !descartados.has(a.id))
      .sort((a, b) => a.hora.localeCompare(b.hora));
    const caja = $("recordatorios");
    hayRecordatorios = lista.length > 0;
    if (!caja) return;
    caja.innerHTML = "";
    lista.forEach(a => {
      const el = document.createElement("div");
      el.className = "recordatorio";
      el.innerHTML = `<svg class="ico campana"><use href="#i-campana"/></svg><b>Mañana</b><span>${esc(a.titulo)}${a.quien === "juntos" ? " (juntos)" : ""} · ${esc(a.hora)}</span>
        <button type="button" aria-label="Descartar recordatorio">${icono("cerrar")}</button>`;
      el.querySelector("button").onclick = () => { descartados.add(a.id); recordatorios(); };
      caja.appendChild(el);
    });
    hayRecordatorios = lista.length > 0;
  }
  let hayRecordatorios = false;

  /* ---------------- Propuestas: aceptar / denegar ---------------- */
  function textoDia(fechaIso) {
    const f = desdeIso(fechaIso), dif = Math.round((f - hoy) / 864e5);
    return dif === 0 ? "Hoy" : dif === 1 ? "Mañana" : `${diaSemana(f)} ${f.getDate()} ${MESES[f.getMonth()].slice(0, 3)}`;
  }
  function avisos() {
    const porResponder = actividades.filter(a => pendiente(a) && a.creadoPor !== YO);
    const rechazadas = actividades.filter(a => a.estado === "RECHAZADA" && a.creadoPor === YO);
    const caja = $("avisos");
    caja.innerHTML =
      porResponder.map(a => `
        <article class="aviso vidrio" data-id="${a.id}">
          <span class="chip">${icono(a.icono)}</span>
          <div>
            <small>${esc(nombres[a.creadoPor] || a.creadoPor)} te propone un plan juntos</small>
            <b>${esc(a.titulo)}</b>
            <span>${textoDia(a.fecha)} · ${esc(a.hora)}${a.lugar ? " · " + esc(a.lugar) : ""}</span>
          </div>
          <div class="botones-aviso">
            <button type="button" class="btn denegar" data-accion="denegar">${icono("cerrar")}Denegar</button>
            <button type="button" class="btn primario" data-accion="aceptar">${icono("check")}Aceptar</button>
          </div>
        </article>`).join("") +
      rechazadas.map(a => `
        <article class="aviso vidrio rechazo" data-id="${a.id}">
          <span class="chip">${icono("cerrar")}</span>
          <div>
            <small>${esc(nombres[OTRO])} ha denegado tu plan</small>
            <b>${esc(a.titulo)}</b>
            <span>${textoDia(a.fecha)} · ${esc(a.hora)}</span>
          </div>
          <div class="botones-aviso">
            <button type="button" class="btn" data-accion="descartar">Entendido</button>
          </div>
        </article>`).join("");
    $("aviso-punto").hidden = !(hayRecordatorios || porResponder.length);
  }
  $("avisos").addEventListener("click", async e => {
    const b = e.target.closest("button[data-accion]"); if (!b) return;
    const id = b.closest(".aviso").dataset.id, accion = b.dataset.accion;
    b.closest(".aviso").querySelectorAll("button").forEach(x => x.disabled = true);
    try {
      if (accion === "descartar") {
        await api(`/api/actividades/${id}/descartar`, { method: "POST", body: "{}" });
      } else {
        await api(`/api/actividades/${id}/responder`, { method: "POST", body: JSON.stringify({ aceptar: accion === "aceptar" }) });
      }
      actividades = await api("/api/actividades");
      pintar();
    } catch (err) {
      alert(err.message);
      b.closest(".aviso")?.querySelectorAll("button").forEach(x => x.disabled = false);
    }
  });

  function pintar() {
    (vista === "semana" ? pintarSemana : pintarMes)();
    proximos();
    momentoCompartido();
    contadores();
    recordatorios();
    avisos();
  }

  /* ---------------- Controles ---------------- */
  const marcar = (grupo, attr, valor) => document.querySelectorAll(`${grupo} button`).forEach(b => b.setAttribute("aria-pressed", String(b.dataset[attr] === valor)));
  $("vistas").addEventListener("click", e => { const v = e.target.dataset.vista; if (!v) return; vista = v; desplaz = 0; marcar("#vistas", "vista", v); pintar(); });
  $("filtros").addEventListener("click", e => { const b = e.target.closest("button"); if (!b) return; filtro = b.dataset.f; marcar("#filtros", "f", filtro); pintar(); });
  $("ant").onclick = () => { desplaz--; pintar(); };
  $("sig").onclick = () => { desplaz++; pintar(); };
  $("hoy").onclick = () => { desplaz = 0; pintar(); };
  const irAlFormulario = () => { $("form-actividad").scrollIntoView({ behavior: "smooth", block: "center" }); $("f-tipo").focus({ preventScroll: true }); };
  $("btn-nuevo").onclick = e => { e.preventDefault(); irAlFormulario(); };
  // Desde otras paginas, "Nueva actividad" llega con ?nueva=1
  if (new URLSearchParams(location.search).has("nueva")) {
    history.replaceState(null, "", location.pathname);
    requestAnimationFrame(irAlFormulario);
  }

  /* ---------------- Selector de fecha mensual ---------------- */
  const btnDia = $("f-dia"), selector = $("selector-fecha");
  let fechaForm = new Date(hoy);
  let mesVisto = new Date(hoy.getFullYear(), hoy.getMonth(), 1);

  function textoFecha(f) {
    const dif = Math.round((f - hoy) / 864e5);
    if (dif === 0) return "Hoy";
    if (dif === 1) return "Mañana";
    return `${diaSemana(f)} ${f.getDate()} ${MESES[f.getMonth()].slice(0, 3)}${f.getFullYear() !== hoy.getFullYear() ? " " + f.getFullYear() : ""}`;
  }
  function pintarSelector() {
    const m = mesVisto.getMonth(), a = mesVisto.getFullYear();
    $("sf-mes").textContent = capital(MESES[m]) + " " + a;
    $("sf-ant").disabled = a === hoy.getFullYear() && m === hoy.getMonth();
    $("sf-semana").innerHTML = DIAS.map(d => `<span>${d.slice(0, 2)}</span>`).join("");
    const huecos = (new Date(a, m, 1).getDay() + 6) % 7;
    const total = new Date(a, m + 1, 0).getDate();
    let html = "<span class='fuera'></span>".repeat(huecos);
    for (let d = 1; d <= total; d++) {
      const f = new Date(a, m, d);
      const clases = [mismoDia(f, hoy) ? "hoy" : "", deDia(f, false).length ? "con-plan" : ""].join(" ");
      html += `<button type="button" class="${clases}" data-d="${d}" aria-pressed="${mismoDia(f, fechaForm)}" aria-label="${d} de ${MESES[m]}" ${f < hoy ? "disabled" : ""}>${d}</button>`;
    }
    $("sf-rejilla").innerHTML = html;
  }
  function elegirFecha(f) { fechaForm = f; $("f-dia-texto").textContent = textoFecha(f); cerrarSelector(); }
  function abrirSelector() {
    mesVisto = new Date(fechaForm.getFullYear(), fechaForm.getMonth(), 1);
    pintarSelector(); selector.hidden = false; btnDia.setAttribute("aria-expanded", "true");
  }
  function cerrarSelector() { selector.hidden = true; btnDia.setAttribute("aria-expanded", "false"); }

  btnDia.addEventListener("click", () => selector.hidden ? abrirSelector() : cerrarSelector());
  $("sf-ant").onclick = () => { mesVisto.setMonth(mesVisto.getMonth() - 1); pintarSelector(); };
  $("sf-sig").onclick = () => { mesVisto.setMonth(mesVisto.getMonth() + 1); pintarSelector(); };
  $("sf-rejilla").addEventListener("click", e => {
    const b = e.target.closest("button"); if (!b || b.disabled) return;
    elegirFecha(new Date(mesVisto.getFullYear(), mesVisto.getMonth(), Number(b.dataset.d)));
  });
  document.querySelector(".sf-pie").addEventListener("click", e => {
    const b = e.target.closest("button"); if (!b) return;
    elegirFecha(sumarDias(hoy, Number(b.dataset.mas)));
  });
  document.addEventListener("click", e => { if (!selector.hidden && !e.target.closest(".fila-fecha")) cerrarSelector(); });
  document.addEventListener("keydown", e => { if (e.key === "Escape" && !selector.hidden) { cerrarSelector(); btnDia.focus(); } });

  /* ---------------- Tipos de plan ---------------- */
  const selTipo = $("f-tipo"), panelTipo = $("nuevo-tipo");
  let tipoAnterior = null, iconoNuevo = ICONOS_ELEGIBLES[0];

  function pintarTipos(seleccionado) {
    selTipo.innerHTML = tipos.map(t => `<option value="${t.id}">${esc(t.nombre)}</option>`).join("")
      + `<option value="__nuevo">+ Crear nuevo tipo de plan…</option>`;
    const id = String(seleccionado ?? tipoAnterior ?? tipos[0]?.id ?? "");
    selTipo.value = id; tipoAnterior = id;
    actualizarIconoTipo();
  }
  function tipoSeleccionado() { return tipos.find(t => String(t.id) === selTipo.value); }
  function actualizarIconoTipo() { const t = tipoSeleccionado(); $("f-tipo-ico").innerHTML = icono(t ? t.icono : "mas"); }
  function pintarIconos() {
    $("nt-iconos").innerHTML = ICONOS_ELEGIBLES
      .map(i => `<button type="button" data-i="${i}" aria-pressed="${i === iconoNuevo}" aria-label="Icono ${i}">${icono(i)}</button>`).join("");
  }
  selTipo.addEventListener("change", () => {
    if (selTipo.value === "__nuevo") { panelTipo.hidden = false; pintarIconos(); actualizarIconoTipo(); $("nt-nombre").focus(); }
    else { tipoAnterior = selTipo.value; panelTipo.hidden = true; actualizarIconoTipo(); }
  });
  $("nt-iconos").addEventListener("click", e => { const b = e.target.closest("button"); if (!b) return; iconoNuevo = b.dataset.i; marcar("#nt-iconos", "i", iconoNuevo); });
  $("nt-cancelar").onclick = () => { panelTipo.hidden = true; pintarTipos(tipoAnterior); };
  $("nt-guardar").onclick = async () => {
    const campo = $("nt-nombre"), nombre = campo.value.trim();
    if (!nombre) { campo.focus(); return; }
    try {
      const nuevo = await api("/api/tipos", { method: "POST", body: JSON.stringify({ nombre, icono: iconoNuevo }) });
      tipos = await api("/api/tipos");
      campo.value = ""; panelTipo.hidden = true; ocultarError();
      pintarTipos(nuevo.id);
    } catch (err) { mostrarError(err.message); }
  };

  /* ---------------- Crear actividad ---------------- */
  const mostrarError = m => { $("f-error").textContent = m; $("f-error").hidden = false; };
  const ocultarError = () => { $("f-error").hidden = true; };

  $("f-quien").addEventListener("click", e => { const q = e.target.dataset.q; if (!q) return; quienForm = q; marcar("#f-quien", "q", q); });
  $("form-actividad").addEventListener("submit", async e => {
    e.preventDefault();
    if (selTipo.value === "__nuevo") { $("nt-nombre").focus(); return; }
    const boton = $("f-enviar");
    boton.disabled = true; ocultarError();
    try {
      await api("/api/actividades", {
        method: "POST",
        body: JSON.stringify({
          tipoId: Number(selTipo.value),
          titulo: $("f-titulo").value.trim(),
          lugar: $("f-lugar").value.trim(),
          fecha: iso(fechaForm),
          hora: $("f-hora").value,
          descripcion: $("f-desc").value.trim(),
          quien: quienForm === "juntos" ? "juntos" : quienForm === "yo" ? YO : OTRO
        })
      });
      actividades = await api("/api/actividades");
      $("f-titulo").value = ""; $("f-lugar").value = ""; $("f-desc").value = "";
      pintar();
    } catch (err) {
      mostrarError(err.message);
    } finally {
      boton.disabled = false;
    }
  });

  /* ---------------- Ventana de un plan: ver, editar, borrar, fotos ---------------- */
  const dlgPlan = $("dlg-plan"), dlgFoto = $("dlg-foto");
  const MAX_FOTOS = 6;
  let planAbierto = null, fotoAbierta = null;

  // Mismo criterio que el servidor: lo tuyo, lo de los dos y lo que has creado tú
  const puedeCambiar = a => a.estado !== "RECHAZADA" && (a.creadoPor === YO || a.quien === YO || a.quien === "juntos");
  const urlFoto = id => `/api/actividades/fotos/${id}`;

  /** Texto escapado con los enlaces http(s) convertidos en enlaces que se abren aparte. */
  function conEnlaces(texto) {
    return esc(texto).replace(/\bhttps?:\/\/[^\s<]+[^\s<.,;:!?)\]'"]/g,
      u => `<a href="${u}" target="_blank" rel="noopener noreferrer">${u.replace(/^https?:\/\/(www\.)?/, "")}</a>`);
  }

  function textoLargoDia(fechaIso) {
    const f = desdeIso(fechaIso), dif = Math.round((f - hoy) / 864e5);
    const base = `${["domingo","lunes","martes","miércoles","jueves","viernes","sábado"][f.getDay()]} ${f.getDate()} de ${MESES[f.getMonth()]}`;
    return dif === 0 ? `Hoy, ${base}` : dif === 1 ? `Mañana, ${base}` : capital(base);
  }

  function verPlan(a) {
    planAbierto = a;
    const editable = puedeCambiar(a);
    dlgPlan.className = "perfil hoja-plan " + clasesEv(a);
    $("pl-tipo").innerHTML = `${icono(a.icono)}<span>${esc(a.tipoNombre || "Plan")}</span>`;
    $("pl-titulo").textContent = a.titulo;
    const quien = pendiente(a)
      ? (a.creadoPor === YO ? `Juntos · esperando a ${esc(nombres[OTRO])}` : `Juntos · te lo propone ${esc(nombres[a.creadoPor] || a.creadoPor)}`)
      : a.quien === "juntos" ? "Juntos" : a.quien === YO ? "Tú" : esc(nombres[a.quien] || a.quien);
    $("pl-datos").innerHTML =
      `<li>${icono("calendario")}<span>${esc(textoLargoDia(a.fecha))} · ${esc(a.hora)}</span></li>` +
      (a.lugar ? `<li>${icono("pin")}<a href="https://www.google.com/maps/search/?api=1&amp;query=${encodeURIComponent(a.lugar)}" target="_blank" rel="noopener noreferrer">${esc(a.lugar)}</a></li>` : "") +
      `<li><i class="punto"></i><span>${quien}</span></li>`;
    const desc = $("pl-desc");
    desc.innerHTML = a.descripcion ? conEnlaces(a.descripcion) : "Sin descripción.";
    desc.classList.toggle("vacia", !a.descripcion);

    const fotos = a.fotos || [];
    $("pl-fotos").innerHTML =
      fotos.map(id => `<button type="button" class="pl-foto" data-foto="${id}" aria-label="Ver foto"><img src="${urlFoto(id)}" alt="" loading="lazy"></button>`).join("") +
      (editable && fotos.length < MAX_FOTOS
        ? `<button type="button" class="pl-foto pl-foto-mas" data-plan="foto">${icono("camara")}<span>Añadir foto</span></button>` : "");
    $("pl-fotos").hidden = !$("pl-fotos").innerHTML;

    let botones = "";
    if (editable) {
      botones += `<button type="button" class="btn denegar" data-plan="borrar">${icono("papelera")}Eliminar</button>`;
      botones += `<button type="button" class="btn" data-plan="editar">${icono("lapiz")}Editar</button>`;
    }
    if (pendiente(a) && a.creadoPor !== YO) {
      botones += `<button type="button" class="btn primario" data-plan="aceptar">${icono("check")}Aceptar</button>`;
    }
    $("pl-acciones").innerHTML = botones;
    $("pl-acciones").hidden = !botones;
    $("pl-error").hidden = true;
    $("pl-ver").hidden = false; $("pl-form").hidden = true;
    if (!dlgPlan.open) dlgPlan.showModal();
  }

  function editarPlan(a) {
    $("pe-tipo").innerHTML = tipos.map(t => `<option value="${t.id}">${esc(t.nombre)}</option>`).join("");
    $("pe-tipo").value = String(a.tipoId);
    // Si el titulo es el nombre del tipo, se deja vacio para que siga al tipo elegido
    $("pe-titulo").value = a.titulo === a.tipoNombre ? "" : a.titulo;
    $("pe-desc").value = a.descripcion || "";
    $("pe-lugar").value = a.lugar || "";
    $("pe-fecha").value = a.fecha;
    $("pe-hora").value = a.hora;
    $("pe-error").hidden = true;
    $("pl-ver").hidden = true; $("pl-form").hidden = false;
    $("pe-titulo").focus();
  }

  async function recargarYVer(id) {
    actividades = await api("/api/actividades");
    pintar();
    const a = actividades.find(x => x.id === id);
    if (a && a.estado !== "RECHAZADA") verPlan(a); else dlgPlan.close();
  }
  const errorPlan = m => { $("pl-error").textContent = m; $("pl-error").hidden = false; };

  function abrirDesde(e) {
    const el = e.target.closest("[data-id]"); if (!el) return;
    const a = actividades.find(x => String(x.id) === el.dataset.id);
    if (a) verPlan(a);
  }
  $("calendario").addEventListener("click", abrirDesde);
  $("proximos").addEventListener("click", abrirDesde);
  $("proximos").addEventListener("keydown", e => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); abrirDesde(e); } });

  dlgPlan.addEventListener("click", async e => {
    if (e.target === dlgPlan || e.target.closest("[data-cerrar]")) { dlgPlan.close(); return; }
    const vistaFoto = e.target.closest("[data-foto]");
    if (vistaFoto) { verFoto(Number(vistaFoto.dataset.foto)); return; }
    const b = e.target.closest("button[data-plan]"); if (!b || !planAbierto) return;
    const a = planAbierto, accion = b.dataset.plan;
    if (accion === "editar") { editarPlan(a); return; }
    if (accion === "foto") { $("pl-archivo").click(); return; }
    try {
      if (accion === "borrar") {
        const ok = await Duo.confirmar({
          titulo: "¿Eliminar este plan?",
          html: `<p><b>${esc(a.titulo)}</b> · ${esc(textoDia(a.fecha))} a las ${esc(a.hora)}</p>` +
                (a.fotos?.length ? `<p>También se borrarán sus fotos.</p>` : "") +
                (a.quien !== YO ? `<p>${esc(nombres[OTRO])} verá en novedades que lo has quitado.</p>` : ""),
          aceptar: "Eliminar", peligro: true
        });
        if (!ok) return;
        await api(`/api/actividades/${a.id}`, { method: "DELETE", body: "{}" });
        dlgPlan.close();
        actividades = await api("/api/actividades");
        pintar();
      } else if (accion === "aceptar") {
        await api(`/api/actividades/${a.id}/responder`, { method: "POST", body: JSON.stringify({ aceptar: true }) });
        await recargarYVer(a.id);
      }
    } catch (ex) { errorPlan(ex.message); }
  });

  $("pe-cancelar").onclick = () => planAbierto && verPlan(planAbierto);
  $("pl-form").addEventListener("submit", async e => {
    e.preventDefault();
    const a = planAbierto, boton = $("pe-guardar");
    boton.disabled = true; $("pe-error").hidden = true;
    try {
      await api(`/api/actividades/${a.id}`, {
        method: "PUT",
        body: JSON.stringify({
          tipoId: Number($("pe-tipo").value),
          titulo: $("pe-titulo").value.trim(),
          descripcion: $("pe-desc").value.trim(),
          lugar: $("pe-lugar").value.trim(),
          fecha: $("pe-fecha").value,
          hora: $("pe-hora").value
        })
      });
      await recargarYVer(a.id);
    } catch (ex) {
      $("pe-error").textContent = ex.message; $("pe-error").hidden = false;
    } finally { boton.disabled = false; }
  });
  dlgPlan.addEventListener("close", () => { if (!dlgFoto.open) planAbierto = null; });

  /** Reduce la foto (lado largo 1600 px, JPG) para que pese poco en el servidor. */
  function reducirFoto(archivo, lado = 1600) {
    return new Promise((resolver, rechazar) => {
      const url = URL.createObjectURL(archivo);
      const img = new Image();
      img.onload = () => {
        const escala = Math.min(1, lado / Math.max(img.naturalWidth, img.naturalHeight));
        const lienzo = document.createElement("canvas");
        lienzo.width = Math.round(img.naturalWidth * escala);
        lienzo.height = Math.round(img.naturalHeight * escala);
        const ctx = lienzo.getContext("2d");
        ctx.fillStyle = "#fff"; ctx.fillRect(0, 0, lienzo.width, lienzo.height);
        ctx.drawImage(img, 0, 0, lienzo.width, lienzo.height);
        URL.revokeObjectURL(url);
        lienzo.toBlob(b => b ? resolver(b) : rechazar(new Error("No se ha podido procesar la foto.")), "image/jpeg", 0.85);
      };
      img.onerror = () => { URL.revokeObjectURL(url); rechazar(new Error("No se ha podido leer la foto.")); };
      img.src = url;
    });
  }

  $("pl-archivo").addEventListener("change", async e => {
    const a = planAbierto, archivos = [...e.target.files];
    e.target.value = "";
    if (!a || !archivos.length) return;
    const huecos = MAX_FOTOS - (a.fotos?.length || 0);
    const mas = document.querySelector(".pl-foto-mas");
    if (mas) { mas.disabled = true; mas.querySelector("span").textContent = "Subiendo…"; }
    try {
      for (const archivo of archivos.slice(0, huecos)) {
        const datos = new FormData();
        datos.append("foto", await reducirFoto(archivo), "foto.jpg");
        const cab = { "Accept": "application/json" };
        if (CSRF.header && CSRF.token) cab[CSRF.header] = CSRF.token;
        const r = await fetch(`/api/actividades/${a.id}/fotos`, { method: "POST", body: datos, headers: cab, credentials: "same-origin" });
        if (!r.ok) {
          let msg = "No se ha podido subir la foto.";
          try { const j = await r.json(); msg = j.message || j.detail || msg; } catch (x) {}
          throw new Error(msg);
        }
      }
      await recargarYVer(a.id);
      if (archivos.length > huecos) errorPlan(`Como mucho ${MAX_FOTOS} fotos por plan; se han subido ${huecos}.`);
    } catch (ex) {
      await recargarYVer(a.id).catch(() => {});
      errorPlan(ex.message);
    }
  });

  function verFoto(id) {
    fotoAbierta = id;
    $("vf-marco").innerHTML = `<img src="${urlFoto(id)}" alt="Foto de ${esc(planAbierto?.titulo || "el plan")}">`;
    $("vf-borrar").hidden = !(planAbierto && puedeCambiar(planAbierto));
    dlgFoto.showModal();
  }
  $("vf-cerrar").onclick = () => dlgFoto.close();
  dlgFoto.addEventListener("click", e => { if (e.target === dlgFoto) dlgFoto.close(); });
  $("vf-borrar").onclick = async () => {
    const ok = await Duo.confirmar({ titulo: "¿Quitar esta foto?", html: "<p>Se borrará del plan para los dos.</p>", aceptar: "Quitar", peligro: true });
    if (!ok) return;
    try {
      await api(`/api/actividades/fotos/${fotoAbierta}`, { method: "DELETE", body: "{}" });
      dlgFoto.close();
      await recargarYVer(planAbierto.id);
    } catch (ex) { dlgFoto.close(); errorPlan(ex.message); }
  };

  /* ---------------- Arranque ---------------- */
  pintar();
  Promise.all([api("/api/actividades"), api("/api/tipos")])
    .then(([a, t]) => {
      actividades = a; tipos = t; pintarTipos(); pintar();
      // Desde el aviso del movil se llega con ?plan=ID: se abre ese plan
      const idAviso = Number(new URLSearchParams(location.search).get("plan"));
      if (idAviso) {
        history.replaceState(null, "", location.pathname);
        const plan = actividades.find(x => x.id === idAviso);
        if (plan) verPlan(plan);
      }
    })
    .catch(err => mostrarError(err.message));

  // Cada minuto mira si el otro ha propuesto o respondido algo
  setInterval(async () => {
    if (document.hidden) return;
    try {
      const nuevas = await api("/api/actividades");
      if (JSON.stringify(nuevas) !== JSON.stringify(actividades)) { actividades = nuevas; pintar(); }
    } catch (e) {}
  }, 60000);
})();
