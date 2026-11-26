/* Duo · Horarios */
(() => {
  "use strict";
  const { api, esc, confirmar } = window.Duo;
  const $ = id => document.getElementById(id);
  const cuerpo = document.body;
  const YO = cuerpo.dataset.yo, OTRO = cuerpo.dataset.otro;
  const nombres = { [YO]: cuerpo.dataset.yoNombre, [OTRO]: cuerpo.dataset.otroNombre };
  const DIAS = ["", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"];
  const DIAS_CORTOS = ["", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"];
  const TIPOS = { CLASE: "Clase", PRACTICAS: "Prácticas", TRABAJO: "Trabajo", OTRO: "Otro" };

  let bloques = [], vista = YO, editando = null;

  const min = h => { const [a, b] = h.split(":").map(Number); return a * 60 + b; };
  const horas = m => { const h = m / 60; return (Number.isInteger(h) ? h : h.toFixed(1).replace(".", ",")) + " h"; };
  const diaHoy = () => ((new Date().getDay() + 6) % 7) + 1;
  const visibles = () => bloques.filter(b => b.usuario === vista);

  /* ---------------- Rejilla semanal ---------------- */
  function pintarRejilla() {
    const lista = visibles();
    const dias = [1, 2, 3, 4, 5];
    if (lista.some(b => b.dia === 6) || lista.some(b => b.dia === 7)) dias.push(6, 7);
    let desde = 8, hasta = 15;
    if (lista.length) {
      desde = Math.min(8, Math.floor(Math.min(...lista.map(b => min(b.inicio))) / 60));
      hasta = Math.max(15, Math.ceil(Math.max(...lista.map(b => min(b.fin))) / 60));
    }
    const n = hasta - desde, H = 52;
    const rejilla = $("rejilla");
    rejilla.style.setProperty("--dias", dias.length);
    rejilla.style.setProperty("--n", n);

    let html = `<div></div>` + dias.map(d => `<div class="cab-dia ${d === diaHoy() ? "hoy" : ""}">${DIAS_CORTOS[d]}</div>`).join("");
    html += `<div class="horas">` + Array.from({ length: n + 1 }, (_, i) => `<span style="top:${i * H}px">${desde + i}:00</span>`).join("") + `</div>`;
    const ahora = new Date(), minAhora = ahora.getHours() * 60 + ahora.getMinutes();
    dias.forEach(d => {
      html += `<div class="col ${d === diaHoy() ? "hoy" : ""}">`;
      lista.filter(b => b.dia === d).forEach(b => {
        const top = (min(b.inicio) - desde * 60) / 60 * H, alto = (min(b.fin) - min(b.inicio)) / 60 * H;
        html += bloqueHtml(b, `top:${top}px; height:${alto - 2}px`);
      });
      if (d === diaHoy() && minAhora >= desde * 60 && minAhora <= hasta * 60) {
        html += `<div class="ahora" style="top:${(minAhora - desde * 60) / 60 * H}px" title="Ahora"></div>`;
      }
      html += `</div>`;
    });
    rejilla.innerHTML = html;
  }

  function bloqueHtml(b, estilo) {
    const mio = b.usuario === YO;
    const extra = [b.detalle, b.lugar].filter(Boolean).map(esc).join(" · ");
    const etiqueta = `${esc(b.titulo)} ${b.inicio}–${b.fin}`;
    const attrs = `class="bloque ${esc(b.usuario)} ${b.tipo.toLowerCase()}${mio ? " editable" : ""}${editando === b.id ? " seleccionado" : ""}"
      style="${estilo}" data-id="${b.id}" title="${etiqueta}${extra ? " · " + extra : ""}"`;
    const contenido = `<span class="h">${b.inicio}–${b.fin}</span><b>${esc(b.titulo)}</b>${extra ? `<small>${extra}</small>` : ""}`;
    return mio ? `<button type="button" ${attrs} aria-label="Editar ${etiqueta}">${contenido}</button>` : `<div ${attrs}>${contenido}</div>`;
  }

  /* ---------------- Lista (pantallas pequenas) ---------------- */
  function pintarLista() {
    const lista = visibles();
    $("lista").innerHTML = [1, 2, 3, 4, 5, 6, 7].map(d => {
      const del = lista.filter(b => b.dia === d).sort((a, b) => a.inicio.localeCompare(b.inicio));
      if (!del.length && d > 5) return "";
      return `<div class="dia-lista"><h5>${DIAS[d]}${d === diaHoy() ? " · hoy" : ""}</h5>` +
        (del.length ? del.map(b => bloqueHtml(b, "")).join("") : `<p class="libre">Libre</p>`) + `</div>`;
    }).join("");
  }

  /* ---------------- Resumen de horas ---------------- */
  function pintarResumen() {
    const personas = [vista];
    $("resumen-horas").innerHTML = personas.map(u => {
      const suyos = bloques.filter(b => b.usuario === u);
      const total = t => suyos.filter(b => t.includes(b.tipo)).reduce((s, b) => s + min(b.fin) - min(b.inicio), 0);
      const clase = total(["CLASE"]), trabajo = total(["PRACTICAS", "TRABAJO"]), otro = total(["OTRO"]);
      const nombre = u === YO ? "Tú" : nombres[u];
      return `<span class="pill" style="--c: var(--c-${esc(u)})"><i></i>${esc(nombre)}: clase <b>${horas(clase)}</b></span>` +
        `<span class="pill" style="--c: var(--c-${esc(u)})"><i></i>prácticas/trabajo <b>${horas(trabajo)}</b></span>` +
        (otro ? `<span class="pill" style="--c: var(--c-${esc(u)})"><i></i>otros <b>${horas(otro)}</b></span>` : "") +
        `<span class="pill" style="--c: var(--c-${esc(u)})"><i></i>total semana <b>${horas(clase + trabajo + otro)}</b></span>`;
    }).join("");
  }

  /* ---------------- Formulario ---------------- */
  const chips = () => [...$("f-dias").querySelectorAll("button")];
  function diasMarcados() { return chips().filter(b => b.getAttribute("aria-pressed") === "true").map(b => Number(b.dataset.d)); }
  $("f-dias").addEventListener("click", e => {
    const b = e.target.closest("button"); if (!b) return;
    if (editando) chips().forEach(x => x.setAttribute("aria-pressed", "false"));  // al editar, un solo dia
    b.setAttribute("aria-pressed", String(editando ? true : b.getAttribute("aria-pressed") !== "true"));
  });

  function limpiarFormulario() {
    editando = null;
    $("form-bloque").reset();
    $("f-inicio").value = "09:00"; $("f-fin").value = "10:00";
    chips().forEach(x => x.setAttribute("aria-pressed", "false"));
    $("f-dias").classList.remove("uno-solo");
    $("form-titulo").textContent = "Añadir a mi horario";
    $("f-guardar").innerHTML = `<svg class="ico"><use href="#i-mas"/></svg>Añadir`;
    $("acciones-edicion").hidden = true;
    $("f-error").hidden = true;
  }

  function editar(b) {
    editando = b.id;
    chips().forEach(x => x.setAttribute("aria-pressed", String(Number(x.dataset.d) === b.dia)));
    $("f-dias").classList.add("uno-solo");
    $("f-inicio").value = b.inicio; $("f-fin").value = b.fin;
    $("f-tipo").value = b.tipo; $("f-titulo").value = b.titulo;
    $("f-detalle").value = b.detalle || ""; $("f-lugar").value = b.lugar || "";
    $("form-titulo").textContent = "Editar bloque";
    $("f-guardar").innerHTML = `<svg class="ico"><use href="#i-check"/></svg>Guardar cambios`;
    $("acciones-edicion").hidden = false;
    $("f-error").hidden = true;
    pintarRejilla(); pintarLista();
    $("form-bloque").scrollIntoView({ behavior: "smooth", block: "nearest" });
  }

  document.querySelector(".rejilla-caja").addEventListener("click", e => {
    const el = e.target.closest(".bloque.editable"); if (!el) return;
    const b = bloques.find(x => String(x.id) === el.dataset.id);
    if (b) editar(b);
  });

  $("form-bloque").addEventListener("submit", async e => {
    e.preventDefault();
    const dias = diasMarcados();
    if (!dias.length) { mostrarError("Elige al menos un día."); return; }
    const datos = { inicio: $("f-inicio").value, fin: $("f-fin").value, tipo: $("f-tipo").value,
                    titulo: $("f-titulo").value, detalle: $("f-detalle").value, lugar: $("f-lugar").value };
    if (editando) {
      // Segunda confirmacion antes de guardar los cambios, por si ha sido un clic sin querer
      const antes = bloques.find(x => x.id === editando);
      const despues = { ...datos, dia: dias[0] };
      const campos = [
        ["Día", DIAS[antes.dia], DIAS[despues.dia]],
        ["Horas", `${antes.inicio}–${antes.fin}`, `${despues.inicio}–${despues.fin}`],
        ["Tipo", TIPOS[antes.tipo], TIPOS[despues.tipo]],
        ["Título", antes.titulo, despues.titulo.trim()],
        ["Detalle", antes.detalle || "—", despues.detalle.trim() || "—"],
        ["Aula / sitio", antes.lugar || "—", despues.lugar.trim() || "—"]
      ].filter(([, a, d]) => a !== d);
      if (!campos.length) { mostrarError("No has cambiado nada."); return; }
      const ok = await confirmar({
        titulo: "¿Guardar los cambios?",
        html: `<p>Vas a cambiar <b>«${esc(antes.titulo)}»</b> del ${esc(DIAS[antes.dia].toLowerCase())}:</p>
          <ul class="cambios">${campos.map(([n, a, d]) => `<li><span>${esc(n)}</span><div><del>${esc(a)}</del><ins>${esc(d)}</ins></div></li>`).join("")}</ul>`,
        aceptar: "Sí, guardar cambios"
      });
      if (!ok) return;
    }
    try {
      if (editando) await api(`/api/horarios/${editando}`, { method: "PUT", body: JSON.stringify({ ...datos, dia: dias[0] }) });
      else await api("/api/horarios", { method: "POST", body: JSON.stringify({ ...datos, dias }) });
      limpiarFormulario();
      await recargar();
    } catch (err) { mostrarError(err.message); }
  });
  $("f-cancelar").onclick = () => { limpiarFormulario(); pintarRejilla(); pintarLista(); };
  $("f-borrar").onclick = async () => {
    const b = bloques.find(x => x.id === editando); if (!b) return;
    const ok = await confirmar({
      titulo: "¿Borrar este bloque?",
      html: `<p>Se quitará <b>«${esc(b.titulo)}»</b> del ${esc(DIAS[b.dia].toLowerCase())} (${b.inicio}–${b.fin}) de tu horario. ${esc(nombres[OTRO])} verá que lo has quitado.</p>`,
      aceptar: "Sí, borrar", peligro: true
    });
    if (!ok) return;
    try { await api(`/api/horarios/${b.id}`, { method: "DELETE" }); limpiarFormulario(); await recargar(); }
    catch (err) { mostrarError(err.message); }
  };
  function mostrarError(m) { $("f-error").textContent = m; $("f-error").hidden = false; }

  /* ---------------- Vista ---------------- */
  $("vista").addEventListener("click", e => {
    const b = e.target.closest("button"); if (!b) return;
    vista = b.dataset.v;
    document.querySelectorAll("#vista button").forEach(x => x.setAttribute("aria-pressed", String(x === b)));
    limpiarFormulario();
    pintar();
  });

  function pintar() {
    const mia = vista === YO;
    $("form-bloque").hidden = !mia;
    $("caja-solo-lectura").hidden = vista !== OTRO;
    $("texto-solo-lectura").textContent = `Estás viendo el horario de ${nombres[OTRO]}. Solo ${nombres[OTRO]} puede cambiarlo desde su cuenta.`;
    pintarResumen(); pintarRejilla(); pintarLista();
  }

  async function recargar() { bloques = await api("/api/horarios"); pintar(); }
  recargar().catch(err => mostrarError(err.message));
})();
