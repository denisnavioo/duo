/* Duo · Repartir: quién pagó qué en una quedada y quién debe a quién */
(() => {
  "use strict";
  const { api, esc, icono, iso, desdeIso, euros, MESES, confirmar } = window.Duo;
  const $ = id => document.getElementById(id);
  const cuerpo = document.body;
  const YO = cuerpo.dataset.yo, OTRO = cuerpo.dataset.otro;
  const NOMBRE_YO = cuerpo.dataset.yoNombre, NOMBRE_OTRO = cuerpo.dataset.otroNombre;

  let quedadas = [], conocidas = [], actual = null;
  let gastoEditando = null;

  /* ---------------- Colores por persona ---------------- */
  // La regla de los tres colores: solo vosotros dos lleváis vuestro color; los amigos van en gris neutro
  function color(nombre) {
    const n = nombre.toLowerCase();
    if (n === NOMBRE_YO.toLowerCase()) return `var(--c-${YO})`;
    if (n === NOMBRE_OTRO.toLowerCase()) return `var(--c-${OTRO})`;
    return null;
  }
  const cara = nombre => {
    const c = color(nombre);
    return `<i class="cara${c ? "" : " neutra"}"${c ? ` style="--c:${c}"` : ""} aria-hidden="true">${esc(nombre.charAt(0).toUpperCase())}</i>`;
  };
  const persona = (nombre, personas) => `<span class="persona">${cara(nombre)}${esc(nombre)}</span>`;

  /* ---------------- Utilidades ---------------- */
  let temporizador;
  function mostrarError(msg) {
    const e = document.querySelector("dialog.hoja[open] .form-error")
      || ($("repartir").classList.contains("creando") ? $("cr-error") : $("error"));
    e.textContent = msg; e.hidden = false;
    clearTimeout(temporizador); temporizador = setTimeout(() => e.hidden = true, 4000);
  }
  function avisoOk(msg) {
    const e = $("aviso-ok"); e.textContent = msg; e.hidden = false;
    setTimeout(() => e.hidden = true, 2500);
  }
  async function intentar(fn) { try { await fn(); } catch (err) { mostrarError(err.message); } }
  const fechaCorta = s => { const f = desdeIso(s); return `${f.getDate()} ${MESES[f.getMonth()].slice(0, 3)}`; };
  const soyYo = nombre => nombre.toLowerCase() === NOMBRE_YO.toLowerCase();
  // Solo quien tiene el banco conectado puede comprobar sus Bizums con un movimiento de su cuenta
  let bancoOk = false;
  api("/api/ahorros/banco/estado").then(e => {
    bancoOk = !!(e && e.permitido && e.conectado);
    if (bancoOk && actual) pintarDetalle();
  }).catch(() => {});
  const fechaLarga = s => { const f = desdeIso(s); return `${f.getDate()} de ${MESES[f.getMonth()]}${f.getFullYear() !== new Date().getFullYear() ? " de " + f.getFullYear() : ""}`; };
  const listaNombres = l => l.length <= 1 ? l.join("") : `${l.slice(0, -1).join(", ")} y ${l[l.length - 1]}`;
  const num = v => Number(v) || 0;

  /* ---------------- Tipos de plan ---------------- */
  const TIPOS = {
    COMIDA: { i: "cuenco", n: "Comida", ej: "Cena en el italiano" }, COPAS: { i: "copa", n: "Copas", ej: "Cañas del viernes" },
    OCIO: { i: "diana", n: "Ocio", ej: "Bolera del sábado" }, VIAJE: { i: "maletin", n: "Viaje", ej: "Finde en la costa" },
    FIESTA: { i: "musica", n: "Fiesta", ej: "Cumple de Iris" }, REGALO: { i: "regalo", n: "Regalo", ej: "Regalo para Nora" },
    OTRO: { i: "dividir", n: "Otro", ej: "Bolera del sábado" }
  };
  const tipoIco = t => `<span class="tipo-ico" aria-hidden="true">${icono((TIPOS[t] || TIPOS.OTRO).i)}</span>`;
  const caras = (personas, max = 5) => `<span class="caras" title="${esc(listaNombres(personas))}">${personas.slice(0, max).map(n => cara(n)).join("")}${personas.length > max ? `<i class="cara neutra mas" aria-hidden="true">+${personas.length - max}</i>` : ""}</span>`;

  /* ---------------- Vista 1: todas las quedadas ---------------- */
  async function cargarLista() {
    [quedadas, conocidas] = await Promise.all([api("/api/repartir/quedadas"), api("/api/repartir/personas")]);
    pintarLista();
  }

  function filaQuedada(q) {
    const estado = q.gastos === 0 ? `<small>Sin gastos</small>`
      : num(q.pendiente) > 0 ? `<small class="pendiente">Faltan ${euros(q.pendiente)}</small>`
      : `<small class="saldada">Saldada</small>`;
    return `<li><a class="fila-q" href="#${q.id}">
      ${tipoIco(q.tipo)}
      <span class="texto"><b>${esc(q.nombre)}</b><small>${fechaLarga(q.fecha)} · ${q.gastos} ${q.gastos === 1 ? "gasto" : "gastos"}</small></span>
      ${caras(q.personas)}
      <span class="importe">${q.gastos === 0 ? "" : euros(q.total)}${estado}</span>
      <svg class="ico chevron" aria-hidden="true"><use href="#i-flecha-d"/></svg></a></li>`;
  }

  function pintarLista() {
    const pendientes = quedadas.filter(q => q.gastos === 0 || num(q.pendiente) > 0);
    const saldadas = quedadas.filter(q => !pendientes.includes(q));
    $("lista-pendientes").innerHTML = pendientes.map(filaQuedada).join("") || `<li><span class="vacia">Todo saldado: nadie debe nada a nadie.</span></li>`;
    $("lista-saldadas").innerHTML = saldadas.map(filaQuedada).join("");
    $("n-pendientes").textContent = pendientes.length ? String(pendientes.length) : "";
    $("n-saldadas").textContent = saldadas.length ? String(saldadas.length) : "";
    $("g-saldadas").hidden = saldadas.length === 0;
    $("g-pendientes").hidden = quedadas.length === 0;
    $("vacio").hidden = quedadas.length > 0;

    pintarLateral();
  }

  /* ---------------- Lateral: tus cuentas, con cada uno y el año ---------------- */
  function pintarLateral() {
    const yo = clave(NOMBRE_YO);
    let meDeben = 0, debo = 0;
    const conCada = new Map(); // nombre -> { neto (+ te debe, - le debes), quedadas }
    quedadas.forEach(q => {
      const vistos = new Set();
      q.transferencias.forEach(t => {
        const imp = num(t.importe);
        let otro = null, v = 0;
        if (clave(t.para) === yo) { otro = t.de; v = imp; meDeben += imp; }
        else if (clave(t.de) === yo) { otro = t.para; v = -imp; debo += imp; }
        if (!otro) return;
        const k = clave(otro);
        const c = conCada.get(k) || { nombre: otro, neto: 0, quedadas: 0 };
        c.neto += v;
        if (!vistos.has(k)) { c.quedadas++; vistos.add(k); }
        conCada.set(k, c);
      });
    });

    const en = n => `en ${n} ${n === 1 ? "quedada" : "quedadas"}`;
    const nDeben = quedadas.filter(q => q.transferencias.some(t => clave(t.para) === yo)).length;
    const nDebo = quedadas.filter(q => q.transferencias.some(t => clave(t.de) === yo)).length;
    $("balance").innerHTML = `
      <article class="tarjeta-dato"><header>${icono("hucha")}Te deben</header>
        <div class="cifra ${meDeben > 0 ? "estado-bueno" : ""}">${euros(meDeben)}</div><div class="sub">${meDeben > 0 ? en(nDeben) : "Nadie te debe nada"}</div></article>
      <article class="tarjeta-dato"><header>${icono("cartera")}Debes</header>
        <div class="cifra">${euros(debo)}</div><div class="sub">${debo > 0 ? en(nDebo) : "No debes nada"}</div></article>`;

    const filas = [...conCada.values()].filter(c => Math.abs(c.neto) >= 0.005).sort((a, b) => Math.abs(b.neto) - Math.abs(a.neto));
    $("con-cada").innerHTML = filas.map(c => `
      <li><span class="persona">${cara(c.nombre)}<span class="texto"><b>${esc(c.nombre)}</b>
        <small>${c.neto > 0 ? "Te debe" : "Le debes"} · ${en(c.quedadas)}</small></span></span>
        <span class="importe ${c.neto > 0 ? "le-deben" : ""}">${c.neto > 0 ? "+" : "−"}${euros(Math.abs(c.neto))}</span></li>`).join("")
      || `<li><span class="vacia">No tienes Bizums pendientes con nadie.</span></li>`;

    const anio = new Date().getFullYear();
    const delAnio = quedadas.filter(q => desdeIso(q.fecha).getFullYear() === anio);
    const mio = delAnio.map(q => q.saldos.find(x => clave(x.persona) === yo)).filter(Boolean);
    const total = delAnio.reduce((t, q) => t + num(q.total), 0);
    $("anio").innerHTML = `
      <div><dt>Quedadas</dt><dd>${delAnio.length}</dd></div>
      <div><dt>Gastado entre todos</dt><dd>${euros(total)}</dd></div>
      <div><dt>Has pagado tú</dt><dd>${euros(mio.reduce((t, x) => t + num(x.pagado), 0))}</dd></div>
      <div><dt>Te ha tocado</dt><dd>${euros(mio.reduce((t, x) => t + num(x.leToca), 0))}</dd></div>`;
  }

  /* ---------------- Rutas: lista (#), nueva (#nueva) o una quedada (#id) ---------------- */
  function vista(nombre) {
    $("repartir").classList.toggle("viendo", nombre === "detalle");
    $("repartir").classList.toggle("creando", nombre === "crear");
    window.scrollTo(0, 0);
  }
  async function ruta() {
    const h = location.hash.slice(1);
    if (h === "nueva") { vista("crear"); prepararCrear(); return; }
    if (Number(h)) { await abrir(Number(h)); return; }
    actual = null;
    $("detalle").innerHTML = "";
    vista("lista");
    await cargarLista();
  }
  const ir = h => { if (location.hash === h || (h === "" && !location.hash)) intentar(ruta); else location.hash = h; };

  /* ---------------- Vista 2: una quedada ---------------- */
  async function abrir(id) {
    actual = await api(`/api/repartir/quedadas/${id}`);
    if (!quedadas.length) await cargarLista();
    vista("detalle");
    pintarDetalle();
  }

  /** Al cambiar algo, el servidor devuelve la quedada entera ya recalculada. */
  function actualizar(q) {
    actual = q;
    pintarDetalle();
    cargarLista().then(() => { if (actual) pintarDetalle(); }).catch(() => {});
  }

  function pintarDetalle() {
    const q = actual, P = q.personas;
    const pendiente = q.transferencias.reduce((s, t) => s + num(t.importe), 0);
    const pendienteTxt = pendiente > 0 ? ` · <b class="pendiente">faltan ${euros(pendiente)}</b>` : q.gastos.length ? ` · <b class="saldada">saldada</b>` : "";
    $("repartir").classList.add("viendo");

    const deudas = q.gastos.length === 0
      ? `<p class="vacia">Apuntad el primer gasto y aquí saldrá quién tiene que pasar Bizum a quién.</p>`
      : q.transferencias.length === 0
        ? `<p class="en-paz">${icono("check")}Estáis en paz: nadie debe nada.</p>`
        : `<ul class="filas deudas">${q.transferencias.map((t, i) => `
            <li><span class="deuda">${persona(t.de, P)}${icono("flecha")}${persona(t.para, P)}</span>
              <span class="importe">${euros(t.importe)}</span>
              <button type="button" class="btn" data-pagado="${i}" title="Apuntar que ya se ha pasado el dinero" aria-label="Hecho">${icono("check")}<span>Hecho</span></button></li>`).join("")}</ul>`;

    const gastos = q.gastos.map(g => {
      const entre = g.participantes.length === P.length ? `entre todos` : `entre ${listaNombres(g.participantes)}`;
      return `<li>
        <span class="texto"><button type="button" data-editar="${g.id}" aria-label="Editar ${esc(g.concepto)}">
          <b>${esc(g.concepto)}</b><small>Pagó ${esc(g.pagador)} · ${esc(entre)}</small></button></span>
        <span class="importe">${euros(g.importe)}</span>
        <button class="borrar" type="button" data-borrar-gasto="${g.id}" aria-label="Borrar ${esc(g.concepto)}">${icono("papelera")}</button></li>`;
    }).join("");

    const enUso = new Set(q.gastos.flatMap(g => [g.pagador, ...g.participantes]).concat(q.pagos.flatMap(p => [p.de, p.para])));
    const saldos = q.saldos.map(s => {
      const v = num(s.saldo);
      const texto = v > 0 ? `<span class="importe le-deben">+${euros(v)}<small>le deben</small></span>`
        : v < 0 ? `<span class="importe">${euros(v)}<small>debe</small></span>`
        : `<span class="importe">${euros(0)}<small>en paz</small></span>`;
      return `<li><span class="texto">${persona(s.persona, P)}<small>Pagó ${euros(s.pagado)} · le toca ${euros(s.leToca)}</small></span>
        ${texto}
        <button class="borrar" type="button" data-quitar-persona="${esc(s.persona)}" aria-label="Quitar a ${esc(s.persona)}" ${enUso.has(s.persona) || P.length <= 2 ? "disabled" : ""}>${icono("cerrar")}</button></li>`;
    }).join("");

    const pagos = q.pagos.length === 0 ? "" : `
      <section class="grupo-r" aria-labelledby="t-pagos">
        <h2 class="titulo-grupo" id="t-pagos">Bizums hechos</h2>
        <div class="caja"><ul class="filas deudas pagos-hechos">${q.pagos.map(p => {
          const puedo = bancoOk && (soyYo(p.de) || soyYo(p.para));
          const boton = p.banco
            ? (p.banco.mio ? `<button type="button" class="btn btn-banco quitar" data-quitar-banco="${p.id}" aria-label="Quitar la comprobación del banco">Quitar</button>` : `<span></span>`)
            : puedo ? `<button type="button" class="btn btn-banco" data-banco="${p.id}" title="Elegir el movimiento de tu banco que lo demuestra">${icono("tarjeta")}<span>Comprobar</span></button>` : `<span></span>`;
          const prueba = p.banco ? `<small class="prueba-banco">${icono("check")}<span>${p.banco.mio
            ? `En tu banco el ${fechaCorta(p.banco.fecha)} · ${esc(p.banco.concepto)}`
            : `Comprobado en el banco de ${esc(p.banco.quien)} el ${fechaCorta(p.banco.fecha)}`}</span></small>` : "";
          return `<li class="${p.banco ? "con-banco" : ""}"><span class="deuda">${persona(p.de, P)}${icono("flecha")}${persona(p.para, P)}</span>
            <span class="importe">${euros(p.importe)}</span>${boton}
            <button class="borrar" type="button" data-borrar-pago="${p.id}" aria-label="Deshacer este pago">${icono("papelera")}</button>${prueba}</li>`;
        }).join("")}</ul></div>
      </section>`;

    $("detalle").innerHTML = `
      <div class="cab-quedada">
        <div class="cab-texto">
          <a class="volver" href="#">${icono("flecha-i")}Quedadas</a>
          <div class="selector-q">
            <h1><button type="button" class="titulo-menu" id="btn-quedadas" aria-haspopup="menu" aria-expanded="false" aria-controls="menu-quedadas">
              ${esc(q.nombre)}${icono("flecha-d")}</button></h1>
            <div class="menu" id="menu-quedadas" role="menu" aria-label="Cambiar de quedada" hidden>
              ${quedadas.map(x => `<button type="button" role="menuitem" data-ir="#${x.id}" ${x.id === q.id ? 'aria-current="true"' : ""}>
                <span><b>${esc(x.nombre)}</b><small>${fechaLarga(x.fecha)}</small></span>${x.id === q.id ? icono("check") : ""}</button>`).join("")}
              <button type="button" role="menuitem" data-ir="#nueva" class="menu-nueva">${icono("mas")}Nueva quedada</button>
            </div>
          </div>
          <p>${fechaLarga(q.fecha)} · ${P.length} personas · ${euros(q.total)} en total${pendienteTxt}</p>
        </div>
        <div class="acciones-q">
          ${q.transferencias.length ? `<button type="button" class="btn" id="copiar">${icono("copiar")}Copiar<span class="solo-ancho"> para el grupo</span></button>` : ""}
          <button type="button" class="btn primario" id="anadir-gasto-cab">${icono("mas")}Añadir gasto</button>
          <button type="button" class="btn redondo" id="borrar-quedada" aria-label="Borrar quedada" title="Borrar quedada">${icono("papelera")}</button>
        </div>
      </div>

      <div class="det-cols">
      <div class="det-col">
      <section class="grupo-r" aria-labelledby="t-deudas">
        <h2 class="titulo-grupo" id="t-deudas">Quién debe a quién</h2>
        <div class="caja">${deudas}</div>
      </section>

      <section class="grupo-r" aria-labelledby="t-gastos">
        <h2 class="titulo-grupo" id="t-gastos">Gastos <small>${euros(q.total)}</small></h2>
        <div class="caja">
          <ul class="filas gastos-q">${gastos}</ul>
          <button type="button" class="fila-anadir" id="anadir-gasto">${icono("mas")}Añadir gasto</button>
        </div>
      </section>
      </div>

      <div class="det-col">
      <section class="grupo-r" aria-labelledby="t-saldos">
        <h2 class="titulo-grupo" id="t-saldos">Cada uno</h2>
        <div class="caja">
          <ul class="filas saldos">${saldos}</ul>
          <button type="button" class="fila-anadir" data-hoja="hoja-persona">${icono("mas")}Añadir persona</button>
        </div>
      </section>
      ${pagos}
      </div>
      </div>`;
  }

  /* ---------------- Hojas ---------------- */
  function abrirHoja(id) {
    const hoja = $(id);
    hoja.querySelector(".form-error").hidden = true;
    hoja.showModal();
    hoja.querySelector("input:not([type=date])")?.focus();
  }

  // Píldoras de personas: varias a la vez (aria-pressed) o solo una (aria-checked)
  function pintarOpciones(cont, personas, elegidas, unaSola = false) {
    cont.innerHTML = personas.map(n => `<button type="button" ${unaSola ? `role="radio" aria-checked="${elegidas.includes(n)}"` : `aria-pressed="${elegidas.includes(n)}"`}
      data-nombre="${esc(n)}">${cara(n)}${esc(n)}</button>`).join("");
  }
  const elegidas = cont => [...cont.querySelectorAll('[aria-pressed="true"], [aria-checked="true"]')].map(b => b.dataset.nombre);

  document.addEventListener("click", e => {
    const op = e.target.closest(".opciones button");
    if (op) {
      const cont = op.parentElement;
      if (op.getAttribute("role") === "radio") {
        cont.querySelectorAll("button").forEach(b => b.setAttribute("aria-checked", String(b === op)));
      } else {
        op.setAttribute("aria-pressed", String(op.getAttribute("aria-pressed") !== "true"));
      }
      if (cont.id === "ga-entre" || cont.id === "ga-pagador") textoCadaUno();
      return;
    }
    const abre = e.target.closest("[data-hoja]");
    if (abre) {
      if (abre.dataset.hoja === "hoja-persona") prepararPersona();
      abrirHoja(abre.dataset.hoja);
      return;
    }
    if (e.target.closest("[data-cerrar]")) e.target.closest("dialog").close();
    else if (e.target.matches("dialog.hoja")) e.target.close();
  });

  /* ---------------- Vista 3: nueva quedada ---------------- */
  let gente = [], marcada = new Set(), tipoElegido = "OTRO";
  const clave = n => n.toLowerCase();
  const VOSOTROS = [NOMBRE_YO, NOMBRE_OTRO];

  function genteConocida() {
    return [...VOSOTROS, ...conocidas.filter(n => !VOSOTROS.map(clave).includes(clave(n)))];
  }

  function prepararCrear() {
    $("form-quedada").reset();
    $("cr-error").hidden = true;
    $("qu-fecha").value = iso(new Date());
    tipoElegido = "OTRO";
    // Primero vosotros dos (ya marcados), luego la gente de otras quedadas
    gente = genteConocida();
    marcada = new Set(VOSOTROS.map(clave));
    pintarTipos();
    pintarGente();
    if (!quedadas.length) cargarLista().then(() => { gente = [...new Set([...genteConocida(), ...gente])]; pintarGente(); }).catch(() => {});
    setTimeout(() => $("qu-nombre").focus(), 50);
  }

  function pintarTipos() {
    $("qu-tipo").querySelectorAll("[data-tipo]").forEach(b => b.setAttribute("aria-checked", String(b.dataset.tipo === tipoElegido)));
    $("qu-nombre").placeholder = `Ej. ${TIPOS[tipoElegido].ej}`;
    pintarResumenCrear();
  }

  function pintarGente() {
    $("qu-personas").innerHTML = gente.map(n => {
      const si = marcada.has(clave(n));
      const quien = clave(n) === clave(NOMBRE_YO) ? `<small>Tú</small>` : clave(n) === clave(NOMBRE_OTRO) ? `<small>Tu pareja</small>` : "";
      return `<li><button type="button" role="checkbox" aria-checked="${si}" data-persona="${esc(n)}">
        ${cara(n)}<span class="texto"><b>${esc(n)}</b>${quien}</span><i class="marca" aria-hidden="true">${icono("check")}</i></button></li>`;
    }).join("");
    pintarResumenCrear();
  }

  function pintarResumenCrear() {
    const n = marcada.size;
    $("qu-cuenta").textContent = `${n} ${n === 1 ? "persona" : "personas"}`;
    const nombre = $("qu-nombre").value.trim();
    const quienes = gente.filter(x => marcada.has(clave(x)));
    $("cr-resumen").innerHTML = n < 2
      ? "Marca al menos a dos personas"
      : `${esc(TIPOS[tipoElegido].n)}${nombre ? ` · ${esc(nombre)}` : ""} · ${esc(listaNombres(quienes))}`;
  }

  function anadirGente() {
    const n = $("qu-nueva").value.trim().replace(/\s+/g, " ");
    if (!n) return;
    const ya = gente.find(x => clave(x) === clave(n));
    if (!ya) gente.push(n);
    marcada.add(clave(ya || n));
    pintarGente();
    $("qu-nueva").value = "";
    $("qu-nueva").focus();
  }
  $("qu-anadir").addEventListener("click", anadirGente);
  $("qu-nueva").addEventListener("keydown", e => { if (e.key === "Enter") { e.preventDefault(); anadirGente(); } });
  $("qu-nombre").addEventListener("input", pintarResumenCrear);
  $("qu-tipo").addEventListener("click", e => {
    const b = e.target.closest("[data-tipo]"); if (!b) return;
    tipoElegido = b.dataset.tipo; pintarTipos();
  });
  $("qu-personas").addEventListener("click", e => {
    const b = e.target.closest("[data-persona]"); if (!b) return;
    const k = clave(b.dataset.persona);
    if (marcada.has(k)) marcada.delete(k); else marcada.add(k);
    b.setAttribute("aria-checked", String(marcada.has(k)));
    pintarResumenCrear();
  });

  /* ----- Gasto ----- */
  function prepararGasto(g) {
    gastoEditando = g || null;
    $("form-gasto").reset();
    $("h-gasto").textContent = g ? "Editar gasto" : "Añadir gasto";
    $("ga-guardar").textContent = g ? "Guardar" : "Añadir";
    const P = actual.personas;
    if (g) { $("ga-concepto").value = g.concepto; $("ga-importe").value = g.importe; }
    const yo = P.find(n => n.toLowerCase() === NOMBRE_YO.toLowerCase());
    pintarOpciones($("ga-pagador"), P, [g ? g.pagador : (yo || P[0])], true);
    pintarOpciones($("ga-entre"), P, g ? g.participantes : P);
    textoCadaUno();
    abrirHoja("hoja-gasto");
  }
  function textoCadaUno() {
    const n = elegidas($("ga-entre")).length, imp = num($("ga-importe").value);
    $("ga-cada-uno").innerHTML = n === 0 ? "Elige al menos a una persona."
      : imp > 0 ? `Sale a <b>${euros(Math.floor(imp * 100 / n) / 100)}</b> por persona${n < actual.personas.length ? "" : ". Quita a quien no participó"}.`
      : "Quita a quien no participó.";
  }
  $("ga-importe").addEventListener("input", textoCadaUno);

  /* ----- Persona ----- */
  function prepararPersona() {
    $("form-persona").reset();
    const ya = new Set(actual.personas.map(x => x.toLowerCase()));
    $("personas-conocidas").innerHTML = [NOMBRE_YO, NOMBRE_OTRO, ...conocidas]
      .filter((n, i, l) => !ya.has(n.toLowerCase()) && l.findIndex(x => x.toLowerCase() === n.toLowerCase()) === i)
      .map(n => `<option value="${esc(n)}">`).join("");
  }

  /* ---------------- Formularios ---------------- */
  function enviar(id, fn) {
    $(id).addEventListener("submit", e => {
      e.preventDefault();
      const boton = e.target.querySelector("[type=submit]"); boton.disabled = true;
      intentar(async () => { await fn(); e.target.closest("dialog")?.close(); })
        .finally(() => boton.disabled = false);
    });
  }

  enviar("form-quedada", async () => {
    if ($("qu-nueva").value.trim()) anadirGente();
    if (!$("qu-nombre").value.trim()) { $("qu-nombre").focus(); throw new Error("Ponle un nombre a la quedada"); }
    if (marcada.size < 2) throw new Error("Marca al menos a dos personas");
    const q = await api("/api/repartir/quedadas", { method: "POST", body: JSON.stringify({
      nombre: $("qu-nombre").value, fecha: $("qu-fecha").value, tipo: tipoElegido,
      personas: gente.filter(x => marcada.has(clave(x)))
    }) });
    quedadas = [];
    location.hash = `#${q.id}`;
  });

  enviar("form-gasto", async () => {
    const pagador = elegidas($("ga-pagador"))[0];
    if (!pagador) throw new Error("Elige quién lo pagó");
    const cuerpoJson = JSON.stringify({
      concepto: $("ga-concepto").value, importe: $("ga-importe").value, pagador, participantes: elegidas($("ga-entre"))
    });
    const url = `/api/repartir/quedadas/${actual.id}/gastos`;
    actualizar(gastoEditando
      ? await api(`${url}/${gastoEditando.id}`, { method: "PUT", body: cuerpoJson })
      : await api(url, { method: "POST", body: cuerpoJson }));
  });

  enviar("form-persona", async () => {
    actualizar(await api(`/api/repartir/quedadas/${actual.id}/personas`, { method: "POST", body: JSON.stringify({ nombre: $("pe-nombre").value }) }));
    conocidas = await api("/api/repartir/personas");
  });

  /* ---------------- Acciones del detalle ---------------- */
  document.addEventListener("click", e => {
    const t = e.target;
    const irA = t.closest("[data-ir]");
    if (irA) { ir(irA.dataset.ir); return; }
    if (!actual) return;
    const base = `/api/repartir/quedadas/${actual.id}`;

    if (t.closest("#anadir-gasto, #anadir-gasto-cab")) { prepararGasto(); return; }
    const ed = t.closest("[data-editar]");
    if (ed) { prepararGasto(actual.gastos.find(g => String(g.id) === ed.dataset.editar)); return; }

    const bg = t.closest("[data-borrar-gasto]");
    if (bg) {
      const g = actual.gastos.find(x => String(x.id) === bg.dataset.borrarGasto);
      intentar(async () => {
        if (!await confirmar({ titulo: "¿Borrar gasto?", html: `<p>Se borrará <b>«${esc(g.concepto)}»</b> (${euros(g.importe)}) y se recalcularán las deudas.</p>`, aceptar: "Sí, borrar", peligro: true })) return;
        actualizar(await api(`${base}/gastos/${g.id}`, { method: "DELETE" }));
      });
      return;
    }

    const pg = t.closest("[data-pagado]");
    if (pg) {
      const tr = actual.transferencias[Number(pg.dataset.pagado)];
      intentar(async () => {
        if (!await confirmar({ titulo: "¿Ya está pagado?", html: `<p><b>${esc(tr.de)}</b> le ha pasado <b>${euros(tr.importe)}</b> a <b>${esc(tr.para)}</b>.</p>`, aceptar: "Sí, está hecho" })) return;
        actualizar(await api(`${base}/pagos`, { method: "POST", body: JSON.stringify(tr) }));
      });
      return;
    }

    const bb = t.closest("[data-banco]");
    if (bb) { intentar(() => abrirBanco(actual.pagos.find(x => String(x.id) === bb.dataset.banco))); return; }
    const qb = t.closest("[data-quitar-banco]");
    if (qb) {
      intentar(async () => {
        if (!await confirmar({ titulo: "¿Quitar la comprobación?", html: `<p>El Bizum sigue apuntado como hecho; solo deja de estar enlazado a tu banco.</p>`, aceptar: "Sí, quitar" })) return;
        actualizar(await api(`${base}/pagos/${qb.dataset.quitarBanco}/banco`, { method: "DELETE" }));
      });
      return;
    }

    const bp = t.closest("[data-borrar-pago]");
    if (bp) {
      intentar(async () => {
        if (!await confirmar({ titulo: "¿Deshacer este pago?", html: `<p>Volverá a contar como pendiente.</p>`, aceptar: "Sí, deshacer", peligro: true })) return;
        actualizar(await api(`${base}/pagos/${bp.dataset.borrarPago}`, { method: "DELETE" }));
      });
      return;
    }

    const qp = t.closest("[data-quitar-persona]");
    if (qp && !qp.disabled) {
      intentar(async () => actualizar(await api(`${base}/personas?nombre=${encodeURIComponent(qp.dataset.quitarPersona)}`, { method: "DELETE" })));
      return;
    }

    if (t.closest("#borrar-quedada")) {
      intentar(async () => {
        if (!await confirmar({ titulo: "¿Borrar quedada?", html: `<p>Se borrará <b>«${esc(actual.nombre)}»</b> con todos sus gastos. Lo dejaréis de ver los dos.</p>`, aceptar: "Sí, borrar", peligro: true })) return;
        await api(base, { method: "DELETE" });
        ir("");
      });
      return;
    }

    if (t.closest("#copiar")) {
      const lineas = [`${actual.nombre} (${euros(actual.total)})`, ...actual.transferencias.map(x => `${x.de} → ${x.para}: ${euros(x.importe)}`)];
      intentar(async () => { await navigator.clipboard.writeText(lineas.join("\n")); avisoOk("Copiado. Pégalo en el grupo."); });
    }
  });

  /* ---------------- Menu del titulo (cambiar de quedada) ---------------- */
  function menuQuedadas(abrirlo) {
    const b = $("btn-quedadas"), m = $("menu-quedadas");
    if (!b || !m) return;
    m.hidden = !abrirlo;
    b.setAttribute("aria-expanded", String(abrirlo));
    if (abrirlo) m.querySelector('[role="menuitem"]')?.focus();
  }
  document.addEventListener("click", e => {
    if (e.target.closest("#btn-quedadas")) { menuQuedadas($("menu-quedadas").hidden); return; }
    menuQuedadas(false); // clic fuera o en una opcion: se cierra
  }, true);
  document.addEventListener("keydown", e => {
    if (e.key === "Escape" && $("menu-quedadas") && !$("menu-quedadas").hidden) { menuQuedadas(false); $("btn-quedadas").focus(); }
  });

  /* ---------------- Comprobar un Bizum con el banco ---------------- */
  let pagoBanco = null;
  async function abrirBanco(p) {
    if (!p) return;
    pagoBanco = p;
    const recibido = soyYo(p.para);
    $("hb-texto").innerHTML = recibido
      ? `Bizum de <b>${esc(p.de)}</b> para ti de <b>${euros(p.importe)}</b>. Elige la entrada de tu banco que lo demuestra.`
      : `Tu Bizum a <b>${esc(p.para)}</b> de <b>${euros(p.importe)}</b>. Elige la salida de tu banco que lo demuestra.`;
    $("hb-lista").innerHTML = `<p class="hb-aviso">Buscando en tu banco…</p>`;
    abrirHoja("hoja-banco");
    const lista = await api(`/api/repartir/quedadas/${actual.id}/pagos/${p.id}/banco`);
    const fila = m => `<button type="button" class="hb-mov" data-mov="${m.id}">
        <span class="hb-fecha">${fechaCorta(m.fecha)}</span>
        <span class="hb-concepto">${esc(m.concepto)}</span>
        <span class="hb-importe ${recibido ? "entra" : ""}">${recibido ? "+" : "−"}${euros(m.importe)}</span></button>`;
    const igual = lista.filter(m => m.coincide), otros = lista.filter(m => !m.coincide);
    $("hb-lista").innerHTML = !lista.length
      ? `<p class="hb-aviso">No hay ${recibido ? "entradas" : "salidas"} en tu banco desde una semana antes de la quedada hasta tres meses después. Si el Bizum es de hoy, sincroniza el banco en Ahorros y vuelve a probar.</p>`
      : `${igual.length ? `<h4>Mismo importe</h4>${igual.map(fila).join("")}` : `<p class="hb-aviso">Ningún movimiento de ${euros(p.importe)} justo. Estos son los de esas fechas:</p>`}
         ${otros.length ? (igual.length ? `<details class="hb-otros"><summary>Otros movimientos de esas fechas (${otros.length})</summary>${otros.map(fila).join("")}</details>` : otros.map(fila).join("")) : ""}`;
  }
  $("hb-lista").addEventListener("click", e => {
    const b = e.target.closest("[data-mov]"); if (!b || !pagoBanco) return;
    intentar(async () => {
      b.disabled = true;
      try {
        actualizar(await api(`/api/repartir/quedadas/${actual.id}/pagos/${pagoBanco.id}/banco`, {
          method: "PUT", body: JSON.stringify({ movimientoId: Number(b.dataset.mov) }) }));
        $("hoja-banco").close();
        avisoOk("Bizum comprobado con tu banco");
      } finally { b.disabled = false; }
    });
  });

  /* ---------------- Arranque ---------------- */
  window.addEventListener("hashchange", () => intentar(ruta));
  intentar(ruta);
})();
