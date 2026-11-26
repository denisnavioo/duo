/* Duo · Ahorros */
(() => {
  "use strict";
  const { api, esc, icono, iso, desdeIso, euros, MESES } = window.Duo;
  const $ = id => document.getElementById(id);
  const cuerpo = document.body;
  const YO = cuerpo.dataset.yo, OTRO = cuerpo.dataset.otro;
  const nombres = { [YO]: cuerpo.dataset.yoNombre, [OTRO]: cuerpo.dataset.otroNombre };

  const hoy = new Date(); hoy.setHours(0, 0, 0, 0);
  let mesVisto = new Date(hoy.getFullYear(), hoy.getMonth(), 1);
  const claveMes = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
  const nombreMes = d => `${MESES[d.getMonth()]} ${d.getFullYear()}`;
  const num = v => Number(v) || 0;
  const fechaCorta = s => { const f = desdeIso(s); return `${f.getDate()} ${MESES[f.getMonth()].slice(0, 3)}`; };

  let datosMes = null, subs = [], movs = [], objetivos = [], bancoEstado = null, bancoPendientes = [], bancoReglas = [];

  const REPARTO = [
    { k: "ahorro", n: "Ahorro", c: "var(--c-verde)" },
    { k: "variables", n: "Gastos variables", c: "var(--c-naranja)" },
    { k: "fijos", n: "Gastos fijos", c: "var(--c-gris)" },
    { k: "ocio", n: "Ocio", c: "var(--c-turquesa)" }
  ];
  const FRECUENCIA = { MENSUAL: "al mes", TRIMESTRAL: "cada 3 meses", ANUAL: "al año" };

  /* ---------------- Errores ---------------- */
  let temporizadorError;
  function mostrarError(msg) {
    // Con una hoja abierta, el error se ve dentro de ella (el aviso flotante quedaria debajo)
    const e = document.querySelector("dialog.hoja[open] .form-error") || $("error");
    e.textContent = msg; e.hidden = false;
    clearTimeout(temporizadorError); temporizadorError = setTimeout(() => e.hidden = true, 4000);
  }
  async function intentar(fn) { try { await fn(); } catch (err) { mostrarError(err.message); } }

  /* ---------------- Calculos ---------------- */
  const totalIngresos = () => datosMes.ingresos.reduce((s, i) => s + num(i.importe), 0);
  const totalGastos = () => datosMes.gastos.reduce((s, g) => s + num(g.importe), 0);
  const partida = k => totalIngresos() * num(datosMes.reparto[k]) / 100;
  const subsMensual = () => subs.reduce((s, x) => s + num(x.costeMensual), 0);
  const subsSinUso = () => subs.filter(x => !x.enUso).reduce((s, x) => s + num(x.costeMensual), 0);

  /* ---------------- Reparto ---------------- */
  function pintarReparto(soloCalculos = false) {
    const r = datosMes.reparto;
    const totalPct = REPARTO.reduce((s, x) => s + num(r[x.k]), 0);
    $("barra-reparto").innerHTML = REPARTO.map(x => `<i style="--c:${x.c}; width:${num(r[x.k])}%"></i>`).join("");
    if (!soloCalculos) {
      $("reparto").innerHTML = REPARTO.map(x => `
        <div class="linea" style="--c:${x.c}"><i></i><label for="rp-${x.k}">${x.n}</label>
          <span class="pct"><input id="rp-${x.k}" data-k="${x.k}" type="number" min="0" max="100" step="0.5" value="${num(r[x.k])}"></span>
          <span class="eur" id="rp-eur-${x.k}"></span></div>`).join("");
    }
    REPARTO.forEach(x => { $(`rp-eur-${x.k}`).textContent = euros(partida(x.k)); });
    const restante = totalIngresos() * (100 - totalPct) / 100;
    $("reparto-estado").innerHTML = Math.abs(totalPct - 100) < 0.01
      ? `<span class="estado-bueno">100%</span>`
      : `<span class="estado-malo">${totalPct}% (debe sumar 100)</span>`;
    $("reparto-datos").innerHTML = `
      <dt>Dinero sin repartir</dt><dd class="${Math.abs(restante) > 0.004 ? "estado-malo" : ""}">${euros(restante)}</dd>
      <dt>Dinero al mes en la cuenta</dt><dd>${euros(totalIngresos() - partida("ahorro"))}</dd>`;
  }
  let temporizadorReparto;
  $("reparto").addEventListener("input", e => {
    const k = e.target.dataset.k; if (!k) return;
    datosMes.reparto[k] = num(e.target.value);
    pintarReparto(true); pintarCategorias();
    clearTimeout(temporizadorReparto);
    temporizadorReparto = setTimeout(() => intentar(async () => {
      const r = datosMes.reparto;
      datosMes.reparto = await api(`/api/ahorros/mes/${claveMes(mesVisto)}/reparto`, {
        method: "PUT", body: JSON.stringify({ ahorro: num(r.ahorro), variables: num(r.variables), fijos: num(r.fijos), ocio: num(r.ocio) })
      });
      $("aviso-copiar").hidden = true;
    }), 600);
  });

  /* ---------------- Banco conectado por PSD2 ---------------- */
  const fechaBanco = valor => valor ? new Date(valor).toLocaleString("es-ES", { dateStyle: "medium", timeStyle: "short" }) : "";
  function pintarBanco() {
    if (!bancoEstado) return;
    // De momento solo algunas cuentas pueden conectar el banco: al resto no se le muestra
    $("banco-seccion").hidden = !bancoEstado.permitido;
    $("banco-revision").hidden = true;
    if (!bancoEstado.permitido) return;
    const mensaje = $("banco-mensaje"), conectado = bancoEstado.conectado;
    $("banco-conectar").hidden = !bancoEstado.disponible || conectado;
    $("banco-sincronizar").hidden = !conectado;
    $("banco-desconectar").hidden = !conectado;
    if (!bancoEstado.disponible) {
      mensaje.textContent = "No disponible todavía: falta configurar Enable Banking (mira el README).";
    } else if (conectado) {
      let texto = `${bancoEstado.banco || "Banco"} conectado`;
      if (bancoEstado.validoHasta) {
        const dias = Math.ceil((new Date(bancoEstado.validoHasta) - new Date()) / 864e5);
        texto += ` · permiso hasta ${new Date(bancoEstado.validoHasta).toLocaleDateString("es-ES")}`;
        if (dias <= 7) texto += " · reconecta pronto para que no se interrumpa";
      }
      if (bancoEstado.ultimaSincronizacion) texto += ` · última actualización: ${fechaBanco(bancoEstado.ultimaSincronizacion)}`;
      mensaje.textContent = texto;
      if (bancoEstado.ultimoError) mensaje.textContent += ` · Aviso: ${bancoEstado.ultimoError}`;
    } else {
      mensaje.textContent = bancoEstado.estado === "REVISAR"
        ? `Hay que volver a autorizar la conexión.${bancoEstado.ultimoError ? " " + bancoEstado.ultimoError : ""}`
        : "Conecta tu banco para importar movimientos y actualizar el saldo de la cuenta.";
    }
    const pendientes = $("banco-revision");
    pendientes.hidden = bancoPendientes.length === 0;
    $("banco-revision-total").textContent = bancoPendientes.length ? `${bancoPendientes.length} movimientos` : "";
    const categorias = [...new Set([...(datosMes?.categorias || []), "Comida", "Cenas", "Compra", "Transporte", "Ocio", "Sin categoría"])];
    $("banco-revision-lista").innerHTML = bancoPendientes.map(m => `
      <article class="banco-revision-fila" data-revision="${m.id}">
        <input type="checkbox" class="revision-sel" data-sel aria-label="Seleccionar ${esc(m.concepto)}">
        <div class="banco-revision-texto"><b>${esc(m.concepto)}</b><small>${fechaCorta(m.fecha)} · ${euros(m.importe)}</small></div>
        <label class="sr-solo" for="banco-cat-${m.id}">Categoría de ${esc(m.concepto)}</label>
        <select id="banco-cat-${m.id}" data-categoria>${categorias.map(c => `<option>${esc(c)}</option>`).join("")}</select>
        <button type="button" class="btn" data-accion="CATEGORIA">Guardar</button>
        <button type="button" class="btn" data-accion="CATEGORIA" data-crear-regla="true">Guardar + regla</button>
        <button type="button" class="btn" data-accion="IGNORAR">Ignorar</button>
      </article>`).join("");
    const loteCat = $("revision-lote-cat"), elegida = loteCat.value;
    loteCat.innerHTML = categorias.map(c => `<option>${esc(c)}</option>`).join("");
    if (categorias.includes(elegida)) loteCat.value = elegida;
    $("revision-lote").hidden = bancoPendientes.length < 2;
    pintarLote();
    $("banco-reglas-lista").innerHTML = bancoReglas.map(r => `<li><span>«${esc(r.contiene)}» · ${r.accion === "IGNORAR" ? "ignorar" : r.accion === "FIJO" ? "gasto fijo" : esc(r.categoria)}</span><span class="banco-regla-botones"><button type="button" class="btn" data-editar-regla="${r.id}">Editar</button><button type="button" class="borrar" data-borrar-regla="${r.id}" aria-label="Eliminar regla">${icono("papelera")}</button></span></li>`).join("")
      || `<li class="banco-sin-reglas">Aún no tienes reglas guardadas.</li>`;
  }

  $("banco-conectar").addEventListener("click", () => intentar(async () => {
    const r = await api("/api/ahorros/banco/conectar", { method: "POST" });
    window.location.assign(r.url);
  }));
  $("banco-sincronizar").addEventListener("click", () => intentar(async () => {
    const b = $("banco-sincronizar"); b.disabled = true;
    try { await api("/api/ahorros/banco/sincronizar", { method: "POST" }); await recargar(); }
    finally { b.disabled = false; }
  }));
  $("banco-desconectar").addEventListener("click", () => intentar(async () => {
    if (!window.confirm("¿Desconectar el banco? Los movimientos ya importados se conservarán.")) return;
    await api("/api/ahorros/banco/desconectar", { method: "DELETE" }); await recargar();
  }));
  /* Selección en lote de "Por revisar": marcar varios (o todos) y guardarlos o ignorarlos de una vez */
  const marcadas = () => [...document.querySelectorAll("#banco-revision-lista [data-sel]:checked")];
  function pintarLote() {
    const todas = document.querySelectorAll("#banco-revision-lista [data-sel]").length, n = marcadas().length;
    const todo = $("revision-todo");
    todo.checked = todas > 0 && n === todas; todo.indeterminate = n > 0 && n < todas;
    $("revision-cuenta").textContent = n ? `${n} de ${todas}` : "";
    $("revision-lote-guardar").disabled = $("revision-lote-ignorar").disabled = n === 0;
  }
  $("revision-todo").addEventListener("change", e => {
    document.querySelectorAll("#banco-revision-lista [data-sel]").forEach(c => { c.checked = e.target.checked; });
    pintarLote();
  });
  $("banco-revision-lista").addEventListener("change", e => { if (e.target.matches("[data-sel]")) pintarLote(); });
  async function resolverLote(accion) {
    const ids = marcadas().map(c => c.closest("[data-revision]").dataset.revision);
    if (!ids.length) return;
    if (accion === "IGNORAR" && !window.confirm(`¿Ignorar ${ids.length} movimientos? No contarán en tus gastos.`)) return;
    const categoria = $("revision-lote-cat").value;
    const botones = [$("revision-lote-guardar"), $("revision-lote-ignorar")];
    botones.forEach(b => { b.disabled = true; });
    try {
      for (const id of ids) {
        const pendiente = bancoPendientes.find(m => String(m.id) === id);
        await api(`/api/ahorros/banco/revisar/${id}`, { method: "POST", body: JSON.stringify({
          accion, categoria, crearRegla: false, contiene: pendiente?.concepto
        }) });
      }
    } finally { await recargar(); }
  }
  $("revision-lote-guardar").addEventListener("click", () => intentar(() => resolverLote("CATEGORIA")));
  $("revision-lote-ignorar").addEventListener("click", () => intentar(() => resolverLote("IGNORAR")));

  $("banco-revision-lista").addEventListener("click", e => {
    const boton = e.target.closest("[data-accion]"); if (!boton) return;
    const fila = boton.closest("[data-revision]");
    intentar(async () => {
      const pendiente = bancoPendientes.find(m => String(m.id) === fila.dataset.revision);
      await api(`/api/ahorros/banco/revisar/${fila.dataset.revision}`, { method: "POST", body: JSON.stringify({
        accion: boton.dataset.accion, categoria: fila.querySelector("[data-categoria]").value,
        crearRegla: boton.dataset.crearRegla === "true", contiene: pendiente?.concepto
      }) });
      await recargar();
    });
  });
  $("banco-form-regla").addEventListener("submit", e => {
    e.preventDefault(); const f = e.currentTarget;
    intentar(async () => {
      const id = f.querySelector('[name="id"]').value;
      await api(id ? `/api/ahorros/banco/reglas/${id}` : "/api/ahorros/banco/reglas", {
        method: id ? "PUT" : "POST", body: JSON.stringify({ contiene: f.contiene.value, accion: f.accion.value, categoria: f.categoria.value })
      });
      f.reset(); f.querySelector('[name="id"]').value = "";
      f.querySelector('[type="submit"]').textContent = "Añadir regla";
      f.querySelector(".banco-regla-categoria").hidden = false; $("banco-cancelar-regla").hidden = true;
      await recargar();
    });
  });
  $("banco-reglas-lista").addEventListener("click", e => {
    const editar = e.target.closest("[data-editar-regla]");
    if (editar) {
      const regla = bancoReglas.find(r => String(r.id) === editar.dataset.editarRegla), form = $("banco-form-regla");
      if (!regla) return;
      form.querySelector('[name="id"]').value = regla.id; form.contiene.value = regla.contiene;
      form.accion.value = regla.accion; form.categoria.value = regla.categoria || "";
      form.querySelector(".banco-regla-categoria").hidden = regla.accion !== "CATEGORIA";
      form.querySelector('[type="submit"]').textContent = "Guardar cambios"; $("banco-cancelar-regla").hidden = false;
      form.closest("details").open = true; form.contiene.focus(); return;
    }
    const b = e.target.closest("[data-borrar-regla]"); if (!b) return;
    intentar(async () => { await api(`/api/ahorros/banco/reglas/${b.dataset.borrarRegla}`, { method: "DELETE" }); await recargar(); });
  });
  $("banco-form-regla").querySelector('[name="accion"]').addEventListener("change", e => {
    $("banco-form-regla").querySelector(".banco-regla-categoria").hidden = e.target.value !== "CATEGORIA";
  });
  $("banco-cancelar-regla").addEventListener("click", () => {
    const form = $("banco-form-regla"); form.reset(); form.querySelector('[name="id"]').value = "";
    form.querySelector('[type="submit"]').textContent = "Añadir regla"; $("banco-cancelar-regla").hidden = true;
    form.querySelector(".banco-regla-categoria").hidden = false;
  });

  /* ---------------- Suscripciones ---------------- */
  function pintarSubs() {
    $("subs-total").textContent = `${euros(subsMensual())}/mes`;
    const sinUso = subsSinUso();
    const consejo = $("subs-consejo");
    consejo.hidden = sinUso <= 0;
    if (sinUso > 0) {
      const n = subs.filter(x => !x.enUso).length;
      consejo.innerHTML = `${icono("aviso")}<span>Tienes ${n} ${n === 1 ? "gasto fijo que no usas: cancelándolo" : "gastos fijos que no usas: cancelándolos"} ahorrarías <b>${euros(sinUso)}</b> al mes (${euros(sinUso * 12)} al año).</span>`;
    }
    const en7 = iso(new Date(hoy.getTime() + 7 * 864e5));
    $("lista-subs").innerHTML = subs.map(s => {
      const pronto = s.siguienteCobro <= en7;
      const dif = Math.round((desdeIso(s.siguienteCobro) - hoy) / 864e5);
      const cuando = dif === 0 ? "hoy" : dif === 1 ? "mañana" : fechaCorta(s.siguienteCobro);
      return `<li class="${s.enUso ? "" : "sin-uso"} ${pronto ? "pronto" : ""}">
        <span class="icono-sub">${icono(pronto ? "campana" : "tarjeta")}</span>
        <span class="texto"><b>${esc(s.nombre)}</b><small>Próximo cobro: ${cuando}${s.nota ? " · " + esc(s.nota) : ""}</small></span>
        <span class="importe">${euros(s.precio)} <small style="font-weight:500;color:var(--texto-suave)">${FRECUENCIA[s.frecuencia] || ""}</small></span>
        <button class="interruptor" data-sub="${s.id}" aria-pressed="${s.enUso}" title="¿Lo uso?"><i></i>Lo uso</button>
        <button class="borrar" data-borrar="suscripciones/${s.id}" aria-label="Borrar">${icono("papelera")}</button></li>`;
    }).join("") || `<li class="vacia">Añade tus suscripciones y gastos fijos (Netflix, transporte, gimnasio...).</li>`;
  }
  $("lista-subs").addEventListener("click", e => {
    const b = e.target.closest("[data-sub]"); if (!b) return;
    intentar(async () => {
      const actual = subs.find(s => String(s.id) === b.dataset.sub);
      const nueva = await api(`/api/ahorros/suscripciones/${actual.id}`, { method: "PATCH", body: JSON.stringify({ enUso: !actual.enUso }) });
      subs = subs.map(s => s.id === nueva.id ? nueva : s);
      pintarSubs(); pintarCobros(); pintarPlan(); pintarTuDinero(); pintarCategorias();
    });
  });

  /* ---------------- Cuentas de ahorro: sugerencias para la hoja "Apuntar movimiento" ---------------- */
  function pintarCuentas() {
    $("lista-cuentas").innerHTML = [...new Set(movs.map(m => m.cuenta))].map(c => `<option value="${esc(c)}">`).join("");
  }

  /* ---------------- Objetivos en comun ---------------- */
  function pintarObjetivos() {
    $("objetivos").innerHTML = objetivos.map(o => {
      const meta = num(o.meta), aportado = num(o.aportado);
      const por = { [YO]: 0, [OTRO]: 0 };
      o.aportaciones.forEach(a => por[a.usuario] = (por[a.usuario] || 0) + num(a.cantidad));
      const pct = q => meta > 0 ? Math.max(0, Math.min(100, por[q] / meta * 100)) : 0;
      const falta = meta - aportado;
      let ritmo = "";
      if (o.fechaLimite && falta > 0) {
        const f = desdeIso(o.fechaLimite);
        const meses = Math.max(1, (f.getFullYear() - hoy.getFullYear()) * 12 + f.getMonth() - hoy.getMonth());
        ritmo = ` · ${euros(falta / meses)}/mes entre los dos hasta ${fechaCorta(o.fechaLimite)}`;
      }
      return `<article class="objetivo ${falta <= 0 ? "cumplido" : ""}" data-obj="${o.id}">
        <header><b>${esc(o.nombre)}</b><span>${euros(aportado)} de ${euros(meta)}</span></header>
        <div class="barra-obj"><i style="--c:var(--c-${esc(YO)}); width:${pct(YO)}%"></i><i style="--c:var(--c-${esc(OTRO)}); width:${pct(OTRO)}%"></i></div>
        <div class="quien">
          <span style="--c:var(--c-${esc(YO)})">Tú <strong>${euros(por[YO])}</strong></span>
          <span style="--c:var(--c-${esc(OTRO)})">${esc(nombres[OTRO])} <strong>${euros(por[OTRO])}</strong></span>
          <span style="--c:transparent">${falta > 0 ? `Faltan <strong>${euros(falta)}</strong>${ritmo}` : `<strong class="estado-bueno">¡Conseguido!</strong>`}</span>
        </div>
        <form class="aportar">
          <input type="number" step="0.01" placeholder="Aportar €" required aria-label="Cantidad a aportar">
          <button class="btn primario" type="submit">${icono("mas")}Aportar</button>
          <button class="borrar" type="button" data-borrar-obj="${o.id}" aria-label="Borrar objetivo">${icono("papelera")}</button>
        </form>
      </article>`;
    }).join("") || `<p class="filas"><span class="vacia">Cread una meta para ahorrar juntos (un viaje, un piso, un capricho...).</span></p>`;
  }
  $("objetivos").addEventListener("submit", e => {
    e.preventDefault();
    const art = e.target.closest("[data-obj]");
    const cantidad = num(e.target.querySelector("input").value);
    intentar(async () => {
      const nuevo = await api(`/api/ahorros/objetivos/${art.dataset.obj}/aportaciones`, { method: "POST", body: JSON.stringify({ cantidad, fecha: iso(hoy) }) });
      objetivos = objetivos.map(o => o.id === nuevo.id ? nuevo : o);
      pintarObjetivos();
    });
  });
  $("objetivos").addEventListener("click", e => {
    const b = e.target.closest("[data-borrar-obj]"); if (!b) return;
    const o = objetivos.find(x => String(x.id) === b.dataset.borrarObj);
    intentar(async () => {
      const ok = await window.Duo.confirmar({
        titulo: "¿Borrar el objetivo?",
        html: `<p>Se borrará <b>«${esc(o.nombre)}»</b> con todas sus aportaciones. Lo dejaréis de ver los dos.</p>`,
        aceptar: "Sí, borrar", peligro: true
      });
      if (!ok) return;
      await api(`/api/ahorros/objetivos/${o.id}`, { method: "DELETE" });
      objetivos = objetivos.filter(x => x.id !== o.id);
      pintarObjetivos();
    });
  });

  /* ================= Vista "Mi plan" ================= */
  // Ajustes que se ponen una vez (lo que tengo, lo que entra, fijos y apartados) y
  // a partir de ellos se calculan solos los meses desde este en adelante.
  const MESES_PLAN = 12;
  const SEMANAS_MES = 52 / 12;
  const COLORES_AP = ["var(--ap-1)", "var(--ap-2)", "var(--ap-3)", "var(--ap-4)", "var(--ap-5)"];
  const colorAp = i => COLORES_AP[i % COLORES_AP.length];
  let plan = null, apartados = [], apartadosCargados = false, totales = {};


  const totalSaldos = () => plan.saldos.reduce((s, x) => s + num(x.cantidad), 0);
  // "Ahora no cobro": el plan cuenta con 0 € al mes, pero se guarda la cifra para cuando vuelvas a cobrar
  const ingresoPlan = () => plan.sinIngresos ? 0 : num(plan.ingresoMensual);
  const subsEnUso = () => subs.filter(s => s.enUso);
  const fijosMedia = () => subsEnUso().reduce((s, x) => s + num(x.costeMensual), 0);
  const pctTotal = () => apartados.reduce((s, a) => s + num(a.porcentaje), 0);
  const pctGuardado = () => apartados.filter(a => a.ahorro).reduce((s, a) => s + num(a.porcentaje), 0)
                          + Math.max(0, 100 - pctTotal());   // lo que no se asigna, se queda

  /** Lo que se cobra de verdad una suscripcion en un mes concreto (las trimestrales y anuales solo su mes). */
  function cargoEnMes(s, mes) {
    if (s.frecuencia === "MENSUAL") return num(s.precio);
    const base = desdeIso(s.siguienteCobro);
    const paso = s.frecuencia === "TRIMESTRAL" ? 3 : 12;
    const dif = (mes.getFullYear() - base.getFullYear()) * 12 + mes.getMonth() - base.getMonth();
    return ((dif % paso) + paso) % paso === 0 ? num(s.precio) : 0;
  }

  /**
   * Los meses desde el actual (lo usan las dos vistas, asi dan las mismas cifras).
   * Cada mes empieza con lo que dejo el anterior. Lo apuntado en "Mes a mes" manda sobre el plan:
   * si ese mes hay ingresos apuntados se usan en vez del ingreso del plan, y si los gastos variables
   * apuntados pasan de lo previsto para gastar, se resta lo apuntado.
   */
  function calcularMeses(cuantos = MESES_PLAN) {
    const meses = [];
    let saldo = totalSaldos();
    for (let i = 0; i < cuantos; i++) {
      const mes = new Date(hoy.getFullYear(), hoy.getMonth() + i, 1);
      const real = totales[claveMes(mes)] || {};
      const ingresoApuntado = real.ingresos !== null && real.ingresos !== undefined;
      const ingreso = ingresoApuntado ? num(real.ingresos) : ingresoPlan();
      const cobros = subsEnUso().map(s => ({ s, v: cargoEnMes(s, mes) })).filter(x => x.v > 0);
      const fijos = cobros.reduce((t, x) => t + x.v, 0);
      const libre = ingreso - fijos;
      const partes = apartados.map(a => libre > 0 ? libre * num(a.porcentaje) / 100 : 0);
      const previsto = libre > 0 ? libre * (100 - pctGuardado()) / 100 : 0;   // para gastar
      const gastado = num(real.gastos);
      const gastos = Math.max(previsto, gastado);
      const guardas = libre - gastos;
      const inicio = saldo;
      saldo += guardas;
      meses.push({ mes, clave: claveMes(mes), inicio, ingreso, ingresoApuntado, fijos, cobros, libre, partes,
                   previsto, gastado, gastos, guardas, fin: saldo });
    }
    return meses;
  }

  function pintarPlanResumen(meses) {
    const ingreso = ingresoPlan(), fijos = fijosMedia(), libre = ingreso - fijos;
    const vacio = ingreso === 0 && plan.saldos.length === 0;
    $("ahorros-pagina").classList.toggle("ahorro-sin-datos", vacio);   // sin datos, los meses serian todo ceros: se ocultan
    // Barra: a donde va lo que entra (fijos en gris, cada apartado con su color, lo no repartido vacio)
    const tramos = ingreso > 0 ? [
      `<i style="--c:var(--c-gris); width:${Math.min(100, fijos / ingreso * 100)}%" title="Gastos fijos"></i>`,
      ...apartados.map((a, i) => `<i style="--c:${colorAp(i)}; width:${Math.max(0, libre) / ingreso * num(a.porcentaje)}%" title="${esc(a.nombre)}"></i>`)
    ].join("") : "";
    const finAnio = meses[meses.length - 1].fin;
    $("plan-resumen").innerHTML = vacio ? `
      <article class="tarjeta-dato empezar">
        <header>${icono("hucha")}Empieza por tus ajustes</header>
        <div class="sub">Pon lo que tienes ahora en cada cuenta y lo que te entra al mes. Con eso, Duo calcula solo los próximos meses.</div>
      </article>` : `
      <article class="tarjeta-dato">
        <header>${icono("cartera")}Tienes ahora</header>
        <div class="cifra">${euros(totalSaldos())}</div>
        <div class="sub">Entre ${plan.saldos.length} ${plan.saldos.length === 1 ? "cuenta" : "cuentas"}</div>
      </article>
      <article class="tarjeta-dato">
        <header>${icono("grafica")}Libre cada mes</header>
        <div class="cifra ${libre < 0 ? "estado-malo" : ""}">${euros(libre)}</div>
        <div class="sub">Entran <b>${euros(ingreso)}</b> · fijos <b>${euros(fijos)}</b> de media</div>
        <div class="barra-reparto barra-flujo">${tramos}</div>
      </article>
      <article class="tarjeta-dato">
        <header>${icono("hucha")}En un año tendrás</header>
        <div class="cifra ${finAnio < totalSaldos() ? "estado-malo" : "estado-bueno"}">${euros(finAnio)}</div>
        <div class="sub">${finAnio >= totalSaldos() ? "Ahorras" : "Pierdes"} <b>${euros(Math.abs(finAnio - totalSaldos()))}</b> en ${MESES_PLAN} meses siguiendo el plan</div>
      </article>`;
  }

  function pintarSaldos() {
    $("tengo-total").textContent = plan.saldos.length ? euros(totalSaldos()) : "";
    $("lista-saldos").innerHTML = plan.saldos.map(s => `
      <li><button type="button" class="fila-boton" data-saldo="${s.id}">
        <span class="texto"><b>${esc(s.nombre)}</b><small>Actualizado ${fechaCorta(s.actualizado)}</small></span>
        <span class="importe ${num(s.cantidad) < 0 ? "negativo" : ""}">${euros(s.cantidad)}</span>
        ${icono("lapiz")}</button></li>`).join("")
      || `<li class="vacia">Pon cuánto hay ahora en cada cuenta (nómina, ahorro, efectivo...).</li>`;
  }


  function pintarApartados(soloCalculos = false) {
    const libre = Math.max(0, ingresoPlan() - fijosMedia());
    const total = pctTotal();
    $("barra-apartados").innerHTML = apartados.map((a, i) => `<i style="--c:${colorAp(i)}; width:${num(a.porcentaje)}%"></i>`).join("");
    if (!soloCalculos) {
      $("apartados").innerHTML = apartados.map((a, i) => `
        <div class="apartado" style="--c:${colorAp(i)}" data-i="${i}">
          <i class="punto"></i>
          <input class="ap-nombre" value="${esc(a.nombre)}" maxlength="30" aria-label="Nombre del apartado" required>
          <button type="button" class="ap-hucha" aria-pressed="${a.ahorro}" title="${a.ahorro ? "Es para guardar" : "Es para gastar"}" aria-label="Es para guardar">${icono("hucha")}</button>
          <span class="pct"><input class="ap-pct" type="number" min="0" max="100" step="1" inputmode="decimal" value="${num(a.porcentaje)}" aria-label="Porcentaje de ${esc(a.nombre)}"></span>
          <span class="eur" data-eur="${i}"></span>
          <button type="button" class="borrar" data-quitar-ap="${i}" aria-label="Quitar ${esc(a.nombre)}">${icono("papelera")}</button>
          <input class="ap-rango" type="range" min="0" max="100" step="1" value="${num(a.porcentaje)}" aria-hidden="true" tabindex="-1">
        </div>`).join("");
    }
    apartados.forEach((a, i) => {
      const mes = libre * num(a.porcentaje) / 100;
      const el = document.querySelector(`[data-eur="${i}"]`);
      if (el) el.innerHTML = `${euros(mes)}<small>${a.ahorro ? "se guarda" : `${euros(mes / SEMANAS_MES)}/semana`}</small>`;
    });
    const sinAsignar = 100 - total;
    $("apartados-estado").innerHTML = total > 100.001
      ? `<span class="estado-malo">${total}% (te pasas)</span>`
      : Math.abs(sinAsignar) < 0.001 ? `<span class="estado-bueno">100%</span>` : `${total}%`;
    const guardas = libre * pctGuardado() / 100;
    $("apartados-datos").innerHTML = `
      ${sinAsignar > 0.001 ? `<dt>Sin repartir (se queda en la cuenta)</dt><dd>${euros(libre * sinAsignar / 100)}</dd>` : ""}
      <dt>Guardas al mes</dt><dd class="estado-bueno">${euros(guardas)}</dd>
      <dt>Para gastar al mes</dt><dd>${euros(libre - guardas)}</dd>`;
    $("btn-apartado").hidden = apartados.length >= 12;
  }

  function pintarMeses(meses) {
    const mayus = s => s.charAt(0).toUpperCase() + s.slice(1);
    const nombre = d => `${mayus(MESES[d.getMonth()])}${d.getFullYear() !== hoy.getFullYear() ? " " + d.getFullYear() : ""}`;
    $("plan-meses").innerHTML = meses.map((m, i) => {
      const extra = m.cobros.filter(x => x.s.frecuencia !== "MENSUAL");
      return `<details class="mes-plan" ${i === 0 ? "open" : ""}>
        <summary>
          <span class="nombre">${i === 0 ? "Este mes" : nombre(m.mes)}${i === 0 ? `<small>${nombre(m.mes)}</small>` : ""}</span>
          <span class="variacion ${m.guardas < 0 ? "estado-malo" : "estado-bueno"}">${m.guardas >= 0 ? "+" : ""}${euros(m.guardas)}</span>
          <span class="tendras"><small>Tendrás</small>${euros(m.fin)}</span>
        </summary>
        <dl class="datos detalle-mes">
          <dt>Empiezas con</dt><dd>${euros(m.inicio)}</dd>
          <dt>Entra${m.ingresoApuntado ? "<small>Lo apuntado en «Mes a mes»</small>" : ""}</dt><dd>+${euros(m.ingreso)}</dd>
          <dt>Gastos fijos${extra.length ? `<small>Este mes también ${extra.map(x => esc(x.s.nombre)).join(", ")}</small>` : ""}</dt><dd>−${euros(m.fijos)}</dd>
          ${apartados.map((a, j) => `<dt class="con-punto" style="--c:${colorAp(j)}">${esc(a.nombre)}${a.ahorro ? " <small>se guarda</small>" : ""}</dt><dd>${euros(m.partes[j])}</dd>`).join("")}
          ${m.gastado > m.previsto + 0.004 ? `<dt class="estado-malo">Te has pasado gastando<small>Apuntado ${euros(m.gastado)} de ${euros(m.previsto)} previstos</small></dt><dd class="estado-malo">−${euros(m.gastado - m.previsto)}</dd>` : ""}
          <dt class="total">Al final del mes</dt><dd class="total">${euros(m.fin)}</dd>
        </dl>
      </details>`;
    }).join("");
  }

  /* ================= Pestaña "Este mes" ================= */
  const mayus = s => s.charAt(0).toUpperCase() + s.slice(1);
  const reducido = () => window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const entre = (v, a, b) => Math.max(a, Math.min(b, v));
  const conSigno = v => `${v >= 0 ? "+" : "−"}${euros(Math.abs(v))}`;

  /** El mes que se esta viendo, calculado con el plan (null si cae fuera de los 12 meses). */
  function mesDelPlan() {
    if (!plan) return null;
    return calcularMeses().find(m => m.clave === claveMes(mesVisto)) || null;
  }

  /* ---- Arriba, la pieza de color: lo que te quedara a fin de mes y como evoluciona ---- */
  function pintarTuDinero() {
    if (!plan || !datosMes) return;
    const sinCuentas = plan.saldos.length === 0;
    const nombre = MESES[mesVisto.getMonth()];
    const meses = calcularMeses();
    const k = meses.findIndex(m => m.clave === claveMes(mesVisto));
    const gastado = totalGastos(), entrado = totalIngresos();
    $("evolucion").hidden = sinCuentas;

    if (sinCuentas) {
      $("dinero-mes").innerHTML = `
        <div class="hero-principal">
          <small>En ${nombre} llevas gastado</small>
          <b class="hero-cifra">${euros(gastado)}</b>
          <span class="hero-linea">Han entrado ${euros(entrado)}</span>
        </div>
        <div class="hero-invitar">
          <p>Pon lo que tienes en tus cuentas y lo que te entra al mes, y aquí verás cuánto te quedará a fin de mes y cómo va subiendo o bajando.</p>
          <button type="button" class="btn hero-btn" data-ir="plan" data-a="sec-tengo">Poner mis cuentas</button>
        </div>`;
      return;
    }

    if (k < 0) {
      const pasado = mesVisto < meses[0].mes;
      $("dinero-mes").innerHTML = `
        <div class="hero-principal">
          <small>${pasado ? `En ${nombre} gastaste` : mayus(nombreMes(mesVisto))}</small>
          <b class="hero-cifra">${pasado ? euros(gastado) : "—"}</b>
          <span class="hero-linea">${pasado ? `Entraron ${euros(entrado)}. Lo que tenías entonces no se guarda: se calcula desde este mes.` : "Se calcula hasta dentro de un año."}</span>
        </div>`;
    } else {
      const m = meses[k], variacion = m.fin - m.inicio;
      const pasado = m.gastado > m.previsto + 0.004;
      $("dinero-mes").innerHTML = `
        <div class="hero-principal">
          <small>${k === 0 ? "Te quedará" : "Tendrás"} a fin de ${nombre}</small>
          <b class="hero-cifra">${euros(m.fin)}</b>
          <span class="hero-variacion ${variacion < 0 ? "baja" : ""}">${conSigno(variacion)} este mes</span>
        </div>
        <dl class="hero-datos">
          <div><dt>${k === 0 ? "Tienes ahora" : "Empiezas con"}</dt><dd>${euros(m.inicio)}</dd></div>
          <div><dt>Entra${m.ingresoApuntado ? "" : plan.sinIngresos ? "<small>ahora no cobras</small>" : "<small>según tu plan</small>"}</dt><dd>+${euros(m.ingreso)}</dd></div>
          <div><dt>Gastos fijos</dt><dd>−${euros(m.fijos)}</dd></div>
          <div class="${pasado ? "pasado" : ""}"><dt>Para gastar${pasado ? "<small>te has pasado</small>" : ""}</dt><dd>−${euros(m.gastos)}</dd></div>
        </dl>`;
    }

    // Grafica: lo que tendras al final de cada mes; se toca para ir a ese mes
    const valores = meses.map(m => m.fin);
    const min = Math.min(...valores, meses[0].inicio, 0), max = Math.max(...valores, meses[0].inicio);
    const alto = v => max - min < 0.01 ? 60 : 12 + (v - min) / (max - min) * 88;
    $("evolucion").innerHTML = meses.map((m, i) => `
      <button type="button" class="barra-mes ${i === k ? "actual" : ""} ${m.guardas < 0 ? "baja" : ""}" data-ir-mes="${m.clave}"
              aria-label="${mayus(nombreMes(m.mes))}: tendrás ${euros(m.fin)}" aria-pressed="${i === k}" style="--i:${i}">
        <span class="valor">${euros(m.fin)}</span>
        <i style="height:${alto(m.fin)}%"></i>
        <small><span class="largo">${MESES[m.mes.getMonth()].slice(0, 3)}</span><span class="corto">${MESES[m.mes.getMonth()].charAt(0)}</span></small>
      </button>`).join("");
    // Las barras crecen solo la primera vez; luego se repintan quietas (al escribir en el plan, al cambiar de mes...)
    if (!graficaVista) { graficaVista = true; setTimeout(() => $("evolucion").classList.add("quieta"), 1400); }
  }
  let graficaVista = false;
  $("evolucion").addEventListener("click", e => {
    const b = e.target.closest("[data-ir-mes]"); if (!b) return;
    mesVisto = desdeIso(b.dataset.irMes + "-01");
    cambiarMes();
  });

  /* ---- En que gastas: presupuesto del mes y categorias (cada una abre sus movimientos) ---- */
  const COLORES_CAT = ["var(--c-naranja)", "var(--c-turquesa)", "var(--ap-4)", "var(--ap-5)", "var(--c-verde)"];
  const ICONOS_CAT = [
    [/comida|super|mercad|aliment|restaur|desayun|almuerz/, "cuenco"],
    [/cena|bar|copa|caf|bebida|fiesta/, "copa"],
    [/transp|gasolin|metro|bus|tren|taxi|uber|cabify|coche|parking|peaje/, "ruta"],
    [/ocio|cine|concierto|juego|plan|entrada/, "cine"],
    [/ropa|capricho|regalo|amazon|tienda/, "regalo"],
    [/gimnas|deporte|salud|farmac|medic/, "pesa"],
    [/casa|alquiler|luz|agua|internet|hogar|movil|telefon/, "casa"],
    [/viaje|vuelo|hotel|vacacion/, "maletin"],
    [/compra/, "cartera"],
    [/peluq|belleza|unas/, "tijeras"],
    [/libro|estudi|clase|curso/, "libro"]
  ];
  const sinAcentos = s => String(s || "").normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
  const iconoCat = c => (ICONOS_CAT.find(([r]) => r.test(sinAcentos(c))) || [null, "tarjeta"])[1];
  let coloresCat = {};
  const colorCat = c => coloresCat[c] || "var(--c-gris)";

  function pintarCategorias() {
    if (!datosMes) return;
    const por = {};
    datosMes.gastos.forEach(g => por[g.categoria] = (por[g.categoria] || 0) + num(g.importe));
    const cats = Object.entries(por).sort((a, b) => b[1] - a[1]);
    coloresCat = Object.fromEntries(cats.map(([c], i) => [c, COLORES_CAT[i] || "var(--c-gris)"]));
    const gastado = totalGastos();
    const m = mesDelPlan();
    const previsto = m && (ingresoPlan() > 0 || m.ingresoApuntado) ? m.previsto : partida("variables");
    const queda = previsto - gastado;
    $("gastas-total").textContent = gastado > 0 ? euros(gastado) : "";

    let hoyMarca = "";
    if (claveMes(mesVisto) === claveMes(hoy) && previsto > 0) {
      const dias = new Date(hoy.getFullYear(), hoy.getMonth() + 1, 0).getDate();
      hoyMarca = `<span class="marca-hoy" style="left:${hoy.getDate() / dias * 100}%" title="Hoy: llevas el ${Math.round(hoy.getDate() / dias * 100)} % del mes"></span>`;
    }
    $("presupuesto").innerHTML = previsto > 0 ? `
      <div class="presupuesto-textos">
        <span><b>${euros(gastado)}</b> de ${euros(previsto)} para gastar</span>
        <span class="${queda < 0 ? "estado-malo" : "estado-bueno"}">${queda >= 0 ? `Te quedan ${euros(queda)}` : `Te has pasado ${euros(-queda)}`}</span>
      </div>
      <div class="progreso grueso ${queda < 0 ? "pasado" : ""}" role="img" aria-label="Llevas gastado el ${Math.round(gastado / previsto * 100)} % de lo previsto"><i style="width:${entre(gastado / previsto * 100, 0, 100)}%"></i>${hoyMarca}</div>`
      : plan?.sinIngresos
        ? `<div class="presupuesto-textos"><span><b>${euros(gastado)}</b> gastado este mes</span></div><p class="ayuda-caja">Ahora no cobras: lo que gastes sale de lo que tienes ahorrado.</p>`
        : `<p class="ayuda-caja">Pon lo que entra al mes en tu plan y aquí verás cuánto te queda para gastar.</p>`;

    $("barra-cats").innerHTML = gastado > 0 ? cats.map(([c, v]) => `<i style="--c:${colorCat(c)}; flex-grow:${v}"></i>`).join("") : "";
    $("lista-cats").innerHTML = cats.map(([c, v]) => {
      const n = datosMes.gastos.filter(g => g.categoria === c).length;
      return `<li><button type="button" class="fila-cat" data-cat="${esc(c)}" style="--c:${colorCat(c)}" aria-label="${esc(c)}: ${euros(v)}. Ver sus movimientos">
        <span class="chip-cat">${icono(iconoCat(c))}</span>
        <span class="texto"><b>${esc(c)}</b><small>${n} ${n === 1 ? "movimiento" : "movimientos"} · ${Math.round(v / gastado * 100)} %</small></span>
        <span class="importe">${euros(v)}</span>
        <span class="flecha">${icono("flecha-d")}</span></button></li>`;
    }).join("") || `<li class="vacia">Aún no hay gastos en ${MESES[mesVisto.getMonth()]}.</li>`;
    $("categorias").innerHTML = (datosMes.categorias || []).map(c => `<option value="${esc(c)}">`).join("");
  }
  $("lista-cats").addEventListener("click", e => {
    const b = e.target.closest("[data-cat]"); if (!b) return;
    filtro = { tipo: "gasto", cat: b.dataset.cat, q: "" };
    $("buscar-mov").value = "";
    pintarMovimientos(); irA("movimientos");
  });

  /* ---- Proximos cobros de los gastos fijos que usas ---- */
  function pintarCobros() {
    const prox = subs.filter(s => s.enUso).sort((a, b) => a.siguienteCobro.localeCompare(b.siguienteCobro)).slice(0, 4);
    $("proximos-cobros").innerHTML = prox.map(s => {
      const f = desdeIso(s.siguienteCobro), dif = Math.round((f - hoy) / 864e5);
      const cuando = dif <= 0 ? "Hoy" : dif === 1 ? "Mañana" : dif < 7 ? `En ${dif} días` : `${FRECUENCIA[s.frecuencia] ? mayus(FRECUENCIA[s.frecuencia]) : ""}`;
      return `<li class="${dif <= 3 ? "pronto" : ""}">
        <span class="fecha-cobro" title="${fechaCorta(s.siguienteCobro)} ${f.getFullYear()}"><b>${f.getDate()}</b><small>${MESES[f.getMonth()].slice(0, 3)}${f.getFullYear() !== hoy.getFullYear() ? ` ${String(f.getFullYear()).slice(2)}` : ""}</small></span>
        <span class="texto"><b>${esc(s.nombre)}</b><small>${cuando}</small></span>
        <span class="importe">${euros(s.precio)}</span></li>`;
    }).join("") || `<li class="vacia">Sin gastos fijos apuntados. <button type="button" class="enlace-boton" data-ir="plan" data-a="sec-subs">Añadir uno</button></li>`;
  }

  /* ---- Tus metas, en corto (el detalle esta en "Plan y metas") ---- */
  const TEXTO_META = { "sin-datos": "Sin calcular", conseguida: "¡Conseguida!", bien: "Vas bien", justo: "Por poco", no: "No llegas" };
  function pintarMetasMini() {
    if (!plan) return;
    $("metas-mini").innerHTML = (plan.metas || []).slice(0, 3).map(m => {
      const e = evaluarMeta(m);
      return `<li class="meta-${e.estado}"><button type="button" class="fila-meta" data-ir="plan" data-a="sec-metas">
        <span class="texto"><b>${esc(m.nombre)}</b><small>${euros(e.meta)} para ${nombreMesLargo(e.fecha)}</small></span>
        <span class="estado-meta">${TEXTO_META[e.estado]}</span>
        <span class="barra-meta"><i class="prevision" style="width:${entre(e.enFecha / e.meta * 100, 0, 100)}%"></i><i class="ahora" style="width:${entre(e.tienes / e.meta * 100, 0, 100)}%"></i></span>
      </button></li>`;
    }).join("") || `<li class="vacia">Ponte una meta con cantidad y fecha y te diremos si llegas. <button type="button" class="enlace-boton" data-hoja="hoja-meta">Nueva meta</button></li>`;
  }

  /* ================= Pestaña "Movimientos": una sola lista con filtros ================= */
  let filtro = { tipo: "todo", cat: null, q: "" };
  const formatoDia = new Intl.DateTimeFormat("es-ES", { weekday: "long", day: "numeric", month: "short" });

  function pintarMovimientos() {
    if (!datosMes) return;
    const clave = claveMes(mesVisto), q = sinAcentos(filtro.q.trim());
    let items = [
      ...datosMes.gastos.map(g => ({ tipo: "gasto", fecha: g.fecha, titulo: g.descripcion || g.categoria, cat: g.categoria,
        sub: [g.descripcion ? g.categoria : "", g.origenBanco ? "Del banco" : ""].filter(Boolean).join(" · "),
        importe: -num(g.importe), borrar: `gastos/${g.id}` })),
      ...datosMes.ingresos.map(i => ({ tipo: "ingreso", fecha: null, titulo: i.concepto, sub: "", importe: num(i.importe), borrar: `ingresos/${i.id}` })),
      ...movs.filter(m => m.fecha.startsWith(clave)).map(m => ({ tipo: "ahorro", fecha: m.fecha, titulo: m.cuenta,
        sub: m.nota || (num(m.cantidad) < 0 ? "Sacado del ahorro" : "Al ahorro"), importe: num(m.cantidad), borrar: `movimientos/${m.id}` }))
    ];
    if (filtro.tipo !== "todo") items = items.filter(x => x.tipo === filtro.tipo);
    if (filtro.cat) items = items.filter(x => x.cat === filtro.cat);
    if (q) items = items.filter(x => sinAcentos(`${x.titulo} ${x.sub} ${x.cat || ""}`).includes(q));

    document.querySelectorAll("#filtro-tipo [data-tipo]").forEach(b => b.setAttribute("aria-pressed", String(!filtro.cat && b.dataset.tipo === filtro.tipo)));
    const fc = $("filtro-cat");
    fc.hidden = !filtro.cat;
    if (filtro.cat) {
      fc.style.setProperty("--c", colorCat(filtro.cat));
      fc.innerHTML = `<i></i>${esc(filtro.cat)}${icono("cerrar")}`;
      fc.setAttribute("aria-label", `Quitar el filtro ${filtro.cat}`);
    }

    const suma = t => items.filter(x => x.tipo === t).reduce((s, x) => s + x.importe, 0);
    const partes = [];
    if (items.some(x => x.tipo === "gasto") || filtro.tipo === "gasto") partes.push(`gastado <b>${euros(-suma("gasto"))}</b>`);
    if (items.some(x => x.tipo === "ingreso")) partes.push(`entrado <b>${euros(suma("ingreso"))}</b>`);
    if (items.some(x => x.tipo === "ahorro")) partes.push(`al ahorro <b>${euros(suma("ahorro"))}</b>`);
    $("totales-movs").innerHTML = `${items.length} ${items.length === 1 ? "movimiento" : "movimientos"} en ${MESES[mesVisto.getMonth()]}${partes.length ? ` · ${partes.join(" · ")}` : ""}`;

    // Primero los ingresos del mes (no llevan dia) y luego dia a dia, de lo mas nuevo a lo mas viejo
    const grupos = [];
    const ingresos = items.filter(x => !x.fecha);
    if (ingresos.length) grupos.push({ titulo: `Ingresos de ${MESES[mesVisto.getMonth()]}`, items: ingresos });
    const porDia = new Map();
    items.filter(x => x.fecha).sort((a, b) => b.fecha.localeCompare(a.fecha)).forEach(x => {
      if (!porDia.has(x.fecha)) porDia.set(x.fecha, []);
      porDia.get(x.fecha).push(x);
    });
    porDia.forEach((lista, fecha) => {
      const dif = Math.round((hoy - desdeIso(fecha)) / 864e5);
      grupos.push({ titulo: dif === 0 ? "Hoy" : dif === 1 ? "Ayer" : mayus(formatoDia.format(desdeIso(fecha))), items: lista });
    });

    const fila = x => {
      const color = x.tipo === "gasto" ? colorCat(x.cat) : x.tipo === "ingreso" ? "var(--ok)" : "var(--c-turquesa)";
      const ico = x.tipo === "gasto" ? iconoCat(x.cat) : x.tipo === "ingreso" ? "cartera" : "hucha";
      return `<li class="mov mov-${x.tipo}" style="--c:${color}">
        <span class="chip-cat">${icono(ico)}</span>
        <span class="texto"><b>${esc(x.titulo)}</b>${x.sub ? `<small>${esc(x.sub)}</small>` : ""}</span>
        <span class="importe ${x.importe > 0 ? "positivo" : ""}">${conSigno(x.importe)}</span>
        <button type="button" class="borrar" data-borrar="${x.borrar}" aria-label="Borrar ${esc(x.titulo)}">${icono("papelera")}</button></li>`;
    };
    const vacio = filtro.q.trim() ? `Nada coincide con «${esc(filtro.q.trim())}».`
      : filtro.cat ? `No hay gastos de ${esc(filtro.cat)} este mes.`
      : { gasto: "Sin gastos este mes.", ingreso: "Aún no has apuntado ingresos este mes.", ahorro: "Este mes no has movido dinero a tus cuentas de ahorro." }[filtro.tipo]
        || `Aún no hay movimientos en ${MESES[mesVisto.getMonth()]}. Llegan solos del banco o los apuntas con «Apuntar».`;
    $("lista-movimientos").innerHTML = grupos.map(g => {
      const neto = g.items.reduce((s, x) => s + x.importe, 0);
      return `<section class="dia-movs"><h3><span>${esc(g.titulo)}</span><span class="${neto > 0 ? "positivo" : ""}">${conSigno(neto)}</span></h3>
        <ul class="filas">${g.items.map(fila).join("")}</ul></section>`;
    }).join("") || `<p class="vacio-movs">${vacio}</p>`;
  }
  $("filtro-tipo").addEventListener("click", e => {
    const b = e.target.closest("[data-tipo]");
    if (b) filtro = { ...filtro, tipo: b.dataset.tipo, cat: null };
    else if (e.target.closest("#filtro-cat")) filtro = { ...filtro, cat: null };
    else return;
    pintarMovimientos();
  });
  $("buscar-mov").addEventListener("input", e => { filtro.q = e.target.value; pintarMovimientos(); });

  // "Apuntar": un menu con lo que se puede apuntar a mano (lo normal es que llegue del banco)
  const btnApuntar = $("btn-apuntar"), menuApuntar = $("menu-apuntar");
  function menuApuntarAbierto(abrir) {
    menuApuntar.hidden = !abrir; btnApuntar.setAttribute("aria-expanded", String(abrir));
    if (abrir) menuApuntar.querySelector("[role=menuitem]").focus();
  }
  btnApuntar.addEventListener("click", () => menuApuntarAbierto(menuApuntar.hidden));
  menuApuntar.addEventListener("click", e => { if (e.target.closest("[data-hoja]")) menuApuntarAbierto(false); });
  document.addEventListener("click", e => { if (!menuApuntar.hidden && !e.target.closest(".apuntar")) menuApuntarAbierto(false); });
  menuApuntar.addEventListener("keydown", e => {
    const items = [...menuApuntar.querySelectorAll("[role=menuitem]")], i = items.indexOf(document.activeElement);
    if (e.key === "Escape") { menuApuntarAbierto(false); btnApuntar.focus(); }
    else if (e.key === "ArrowDown" || e.key === "ArrowUp") { e.preventDefault(); items[(i + (e.key === "ArrowDown" ? 1 : -1) + items.length) % items.length].focus(); }
  });

  /* ================= Pestañas ================= */
  const PESTANAS = ["mes", "movimientos", "plan"];
  let pestana = "mes";
  function ponerPestana(p, foco = false) {
    if (!PESTANAS.includes(p)) p = "mes";
    const cambia = p !== pestana;
    pestana = p;
    PESTANAS.forEach(x => {
      const activa = x === p, b = $(`pt-${x}`);
      b.setAttribute("aria-selected", String(activa)); b.tabIndex = activa ? 0 : -1;
      $(`tab-${x}`).hidden = !activa;
    });
    document.querySelector(".pestanas-ahorro").style.setProperty("--activa", PESTANAS.indexOf(p));
    if (foco) $(`pt-${p}`).focus();
    try { localStorage.setItem("duo-ahorros-pestana", p); } catch (e) { /* sin almacenamiento */ }
    history.replaceState(null, "", p === "mes" ? location.pathname : `#${p}`);
    // Si estabas mas abajo, vuelve al principio de la pestaña (con la barra de pestañas a la vista)
    if (cambia) {
      const barra = document.querySelector(".pestanas-ahorro");
      const arriba = document.querySelector(".titular").getBoundingClientRect().bottom + window.scrollY - (parseFloat(getComputedStyle(barra).top) || 0);
      if (window.scrollY > arriba) window.scrollTo({ top: arriba });
    }
  }
  function irA(p, idSeccion) {
    ponerPestana(p);
    const el = idSeccion && $(idSeccion);
    if (!el) return;
    if (el.tagName === "DETAILS") el.open = true;
    el.scrollIntoView({ behavior: reducido() ? "auto" : "smooth", block: "start" });
  }
  document.querySelector(".pestanas-ahorro").addEventListener("click", e => {
    const b = e.target.closest("[data-pestana]"); if (b) ponerPestana(b.dataset.pestana);
  });
  document.querySelector(".pestanas-ahorro").addEventListener("keydown", e => {
    const i = PESTANAS.indexOf(pestana);
    const n = { ArrowRight: i + 1, ArrowLeft: i - 1, Home: 0, End: PESTANAS.length - 1 }[e.key];
    if (n === undefined) return;
    e.preventDefault(); ponerPestana(PESTANAS[(n + PESTANAS.length) % PESTANAS.length], true);
  });
  document.addEventListener("click", e => {
    const b = e.target.closest("[data-ir]"); if (b) irA(b.dataset.ir, b.dataset.a);
  });
  {
    let inicial = location.hash.slice(1);
    if (!PESTANAS.includes(inicial)) { try { inicial = localStorage.getItem("duo-ahorros-pestana") || "mes"; } catch (e) { inicial = "mes"; } }
    pestana = null; ponerPestana(inicial);
  }
  window.addEventListener("hashchange", () => { const p = location.hash.slice(1); if (PESTANAS.includes(p) && p !== pestana) ponerPestana(p); });

  /* ---- Mis metas: tener X € para una fecha, ¿llego o no? ---- */
  const nombreMesLargo = d => `${MESES[d.getMonth()]} ${d.getFullYear() !== hoy.getFullYear() ? d.getFullYear() : ""}`.trim();

  /** Con el plan, cuanto tendre a final del mes de la meta y si llego. Margen de "por poco": 5 %. */
  function evaluarMeta(m) {
    const meta = num(m.cantidad), tienes = totalSaldos();
    const fecha = desdeIso(m.fecha);
    const n = Math.max(1, Math.min(121, (fecha.getFullYear() - hoy.getFullYear()) * 12 + fecha.getMonth() - hoy.getMonth() + 1));
    const meses = calcularMeses(n);
    const enFecha = meses[meses.length - 1].fin;
    const alcanza = meses.find(x => x.fin >= meta);
    const falta = meta - enFecha;
    const sinDatos = ingresoPlan() === 0 && plan.saldos.length === 0;
    let estado = sinDatos ? "sin-datos" : tienes >= meta ? "conseguida" : enFecha >= meta ? "bien" : enFecha >= meta * 0.95 ? "justo" : "no";
    return { meta, tienes, fecha, n, enFecha, alcanza, falta, extraMes: Math.max(0, falta) / n, estado };
  }

  function pintarMetas() {
    const lista = plan.metas || [];
    $("plan-metas").innerHTML = lista.map(m => {
      const e = evaluarMeta(m);
      const pctAhora = Math.max(0, Math.min(100, e.tienes / e.meta * 100));
      const pctFecha = Math.max(0, Math.min(100, e.enFecha / e.meta * 100));
      const cuando = nombreMesLargo(e.fecha);
      const textos = {
        "sin-datos": ["Sin calcular", "Rellena tus ajustes (lo que tienes y lo que entra) para saber si llegas."],
        conseguida: ["¡Conseguida!", `Ya tienes ${euros(e.tienes)}: ${euros(e.tienes - e.meta)} más de lo que querías.`],
        bien: ["Vas bien", e.alcanza && e.alcanza.mes < new Date(e.fecha.getFullYear(), e.fecha.getMonth(), 1)
          ? `La consigues en ${nombreMesLargo(e.alcanza.mes)}, antes de tiempo. A final de ${cuando} tendrás ${euros(e.enFecha)}, ${euros(e.enFecha - e.meta)} de margen.`
          : `La consigues justo a tiempo: a final de ${cuando} tendrás ${euros(e.enFecha)}, ${euros(e.enFecha - e.meta)} de margen.`],
        justo: ["Por poco", `A final de ${cuando} tendrás ${euros(e.enFecha)}: te faltarían ${euros(e.falta)}. Guarda ${euros(e.extraMes)} más al mes para asegurarla.`],
        no: ["No llegas", `A final de ${cuando} tendrás ${euros(e.enFecha)}: te faltan ${euros(e.falta)}. Tendrías que guardar ${euros(e.extraMes)} más al mes.`]
      }[e.estado];
      return `<article class="meta meta-${e.estado}">
        <button type="button" class="meta-cabeza" data-meta="${m.id}" aria-label="Cambiar la meta ${esc(m.nombre)}">
          <span class="texto"><b>${esc(m.nombre)}</b><small>${euros(e.meta)} para ${fechaCorta(m.fecha)}${e.fecha.getFullYear() !== hoy.getFullYear() ? " " + e.fecha.getFullYear() : ""}</small></span>
          <span class="estado-meta">${textos[0]}</span>
          ${icono("lapiz")}
        </button>
        <div class="barra-meta" role="img" aria-label="Tienes ${euros(e.tienes)} de ${euros(e.meta)}; en la fecha tendrás ${euros(e.enFecha)}">
          <i class="prevision" style="width:${pctFecha}%"></i><i class="ahora" style="width:${pctAhora}%"></i>
        </div>
        <div class="leyenda-meta"><span class="ahora">Ahora ${euros(e.tienes)}</span><span class="prevision">${cuando}: ${euros(e.enFecha)}</span></div>
        <p>${textos[1]}</p>
      </article>`;
    }).join("") || `<p class="ayuda-caja metas-vacio">Ponte una meta con cantidad y fecha (un coche, un viaje, un colchón para imprevistos...) y Duo te dirá si llegas con tu plan.</p>`;
  }
  $("plan-metas").addEventListener("click", e => {
    const b = e.target.closest("[data-meta]"); if (!b) return;
    const m = plan.metas.find(x => String(x.id) === b.dataset.meta);
    $("h-meta").textContent = "Cambiar meta";
    $("me-id").value = m.id; $("me-nombre").value = m.nombre; $("me-cantidad").value = num(m.cantidad); $("me-fecha").value = m.fecha;
    $("me-quitar").hidden = false;
    $("hoja-meta").querySelector(".form-error").hidden = true;
    $("hoja-meta").showModal();
  });
  $("me-quitar").onclick = () => intentar(async () => {
    await api(`/api/ahorros/plan/metas/${$("me-id").value}`, { method: "DELETE" });
    $("hoja-meta").close(); await recargar();
  });
  $("hoja-meta").addEventListener("close", () => {
    $("form-meta").reset(); $("me-id").value = ""; $("me-quitar").hidden = true; $("h-meta").textContent = "Nueva meta";
  });
  $("me-fecha").min = iso(hoy);

  function pintarPlan() {
    if (!plan) return;
    const ingreso = $("pl-ingreso");
    if (document.activeElement !== ingreso) ingreso.value = num(plan.ingresoMensual) || "";
    const sin = !!plan.sinIngresos;
    $("pl-sin-ingresos").checked = sin;
    ingreso.disabled = sin;
    ingreso.closest(".fila-dato").classList.toggle("en-pausa", sin);
    $("sin-ingresos-ayuda").textContent = sin
      ? `Tu plan cuenta con 0 € al mes. Desmárcalo cuando vuelvas a cobrar y volverá a contar con ${euros(plan.ingresoMensual)}.`
      : "Márcalo si dejas de cobrar una temporada. El plan contará con 0 € al mes hasta que lo desmarques.";
    $("sec-apartados").hidden = sin;
    const meses = calcularMeses();
    pintarPlanResumen(meses); pintarSaldos(); pintarApartados(); pintarMeses(meses); pintarMetas();
  }
  function repintarCalculos() {
    const meses = calcularMeses();
    pintarPlanResumen(meses); pintarApartados(true); pintarMeses(meses); pintarMetas();
    pintarTuDinero(); pintarCategorias(); pintarMetasMini();
  }

  // Lo que entra al mes: se guarda al dejar de escribir
  let temporizadorIngreso;
  $("pl-ingreso").addEventListener("input", e => {
    const v = e.target.value === "" ? 0 : num(e.target.value);
    if (v < 0) return;
    plan.ingresoMensual = v;
    repintarCalculos();
    clearTimeout(temporizadorIngreso);
    temporizadorIngreso = setTimeout(() => intentar(async () => {
      plan = await api("/api/ahorros/plan", { method: "PUT", body: JSON.stringify({ ingresoMensual: v }) });
    }), 600);
  });

  $("pl-sin-ingresos").addEventListener("change", e => {
    const marcado = e.target.checked;
    intentar(async () => {
      try {
        plan = await api("/api/ahorros/plan", { method: "PUT", body: JSON.stringify({ sinIngresos: marcado }) });
      } catch (err) { e.target.checked = !marcado; throw err; }
      pintarPlan(); pintarTuDinero(); pintarCategorias(); pintarMetasMini();
    });
  });

  // Apartados: se edita la lista en pantalla y se guarda entera
  let temporizadorApartados;
  function guardarApartados() {
    clearTimeout(temporizadorApartados);
    if (pctTotal() > 100.001 || apartados.some(a => !a.nombre.trim()) || !apartados.length) return;
    temporizadorApartados = setTimeout(() => intentar(async () => {
      await api("/api/ahorros/plan/apartados", { method: "PUT", body: JSON.stringify(apartados) });
    }), 600);
  }
  $("apartados").addEventListener("input", e => {
    const fila = e.target.closest("[data-i]"); if (!fila) return;
    const a = apartados[Number(fila.dataset.i)];
    if (e.target.classList.contains("ap-nombre")) {
      a.nombre = e.target.value;
      repintarCalculos();
    } else if (e.target.classList.contains("ap-pct") || e.target.classList.contains("ap-rango")) {
      a.porcentaje = Math.max(0, Math.min(100, num(e.target.value)));
      fila.querySelector(e.target.classList.contains("ap-pct") ? ".ap-rango" : ".ap-pct").value = a.porcentaje;
      repintarCalculos();
    }
    guardarApartados();
  });
  $("apartados").addEventListener("click", e => {
    const hucha = e.target.closest(".ap-hucha");
    const quitar = e.target.closest("[data-quitar-ap]");
    if (hucha) {
      const a = apartados[Number(hucha.closest("[data-i]").dataset.i)];
      a.ahorro = !a.ahorro;
      pintarApartados(); repintarCalculos(); guardarApartados();
    } else if (quitar) {
      if (apartados.length <= 1) { mostrarError("Deja al menos un apartado"); return; }
      apartados.splice(Number(quitar.dataset.quitarAp), 1);
      pintarApartados(); repintarCalculos(); guardarApartados();
    }
  });
  $("btn-apartado").onclick = () => {
    apartados.push({ nombre: "Nuevo apartado", porcentaje: Math.max(0, Math.min(10, 100 - pctTotal())), ahorro: false });
    pintarApartados(); repintarCalculos(); guardarApartados();
    const filas = document.querySelectorAll("#apartados .ap-nombre");
    const ultima = filas[filas.length - 1]; ultima.focus(); ultima.select();
  };

  // Lo que tengo: tocar una cuenta abre la hoja para cambiar lo que hay
  $("lista-saldos").addEventListener("click", e => {
    const b = e.target.closest("[data-saldo]"); if (!b) return;
    const s = plan.saldos.find(x => String(x.id) === b.dataset.saldo);
    $("h-saldo").textContent = "Actualizar cuenta";
    $("sa-id").value = s.id; $("sa-nombre").value = s.nombre; $("sa-cantidad").value = num(s.cantidad);
    $("sa-quitar").hidden = false;
    $("hoja-saldo").querySelector(".form-error").hidden = true;
    $("hoja-saldo").showModal();
    $("sa-cantidad").focus(); $("sa-cantidad").select();
  });
  $("sa-quitar").onclick = () => intentar(async () => {
    await api(`/api/ahorros/plan/saldos/${$("sa-id").value}`, { method: "DELETE" });
    $("hoja-saldo").close(); await recargar();
  });
  $("hoja-saldo").addEventListener("close", () => {
    $("form-saldo").reset(); $("sa-id").value = ""; $("sa-quitar").hidden = true; $("h-saldo").textContent = "Añadir cuenta";
  });

  /* ---------------- Borrar (ingresos, gastos, suscripciones, movimientos) ---------------- */
  document.querySelector(".contenido").addEventListener("click", e => {
    const b = e.target.closest("[data-borrar]"); if (!b) return;
    intentar(async () => {
      await api(`/api/ahorros/${b.dataset.borrar}`, { method: "DELETE" });
      await recargar();
    });
  });

  /* ---------------- Formularios ---------------- */
  function enviar(formId, fn) {
    $(formId).addEventListener("submit", e => {
      e.preventDefault();
      const boton = e.target.querySelector("[type=submit]");
      boton.disabled = true;
      intentar(async () => { await fn(e.target); e.target.closest("dialog")?.close(); await recargar(); })
        .finally(() => boton.disabled = false);
    });
  }

  /* ---------------- Hojas para añadir (como en iOS) ---------------- */
  document.addEventListener("click", e => {
    const abrir = e.target.closest("[data-hoja]");
    if (abrir) {
      const hoja = $(abrir.dataset.hoja);
      hoja.querySelector(".form-error").hidden = true;
      hoja.showModal();
      hoja.querySelector("input:not([type=date]), input")?.focus();
      return;
    }
    if (e.target.closest("[data-cerrar]")) e.target.closest("dialog").close();
    else if (e.target.matches("dialog.hoja")) e.target.close();  // clic fuera de la hoja
  });
  enviar("form-ingreso", async f => {
    await api(`/api/ahorros/mes/${claveMes(mesVisto)}/ingresos`, { method: "POST",
      body: JSON.stringify({ concepto: $("in-concepto").value, importe: num($("in-importe").value) }) });
    f.reset(); $("in-concepto").focus();
  });
  enviar("form-gasto", async f => {
    await api("/api/ahorros/gastos", { method: "POST", body: JSON.stringify({
      fecha: $("ga-fecha").value, categoria: $("ga-categoria").value, descripcion: $("ga-descripcion").value, importe: num($("ga-importe").value) }) });
    const fecha = $("ga-fecha").value; f.reset(); $("ga-fecha").value = fecha; $("ga-categoria").focus();
  });
  enviar("form-sub", async f => {
    await api("/api/ahorros/suscripciones", { method: "POST", body: JSON.stringify({
      nombre: $("su-nombre").value, precio: num($("su-precio").value), frecuencia: $("su-frecuencia").value, cobro: $("su-cobro").value }) });
    f.reset(); $("su-cobro").value = iso(hoy);
  });
  enviar("form-mov", async f => {
    await api("/api/ahorros/movimientos", { method: "POST", body: JSON.stringify({
      fecha: $("mo-fecha").value, cantidad: num($("mo-cantidad").value), cuenta: $("mo-cuenta").value }) });
    const fecha = $("mo-fecha").value; f.reset(); $("mo-fecha").value = fecha;
  });
  enviar("form-meta", async () => {
    const id = $("me-id").value;
    const datos = JSON.stringify({ nombre: $("me-nombre").value, cantidad: num($("me-cantidad").value), fecha: $("me-fecha").value });
    await api(id ? `/api/ahorros/plan/metas/${id}` : "/api/ahorros/plan/metas", { method: id ? "PUT" : "POST", body: datos });
  });
  enviar("form-saldo", async () => {
    const id = $("sa-id").value;
    const datos = JSON.stringify({ nombre: $("sa-nombre").value, cantidad: num($("sa-cantidad").value) });
    await api(id ? `/api/ahorros/plan/saldos/${id}` : "/api/ahorros/plan/saldos", { method: id ? "PUT" : "POST", body: datos });
  });
  enviar("form-obj", async f => {
    await api("/api/ahorros/objetivos", { method: "POST", body: JSON.stringify({
      nombre: $("ob-nombre").value, meta: num($("ob-meta").value), fechaLimite: $("ob-fecha").value || null }) });
    f.reset();
  });

  $("btn-copiar").onclick = () => intentar(async () => {
    datosMes = await api(`/api/ahorros/mes/${claveMes(mesVisto)}/copiar`, { method: "POST", body: "{}" });
    pintarTodo();
  });

  /* ---------------- Mes ---------------- */
  function fechaPorDefecto() {
    // Dentro del mes que se esta viendo: hoy si es el mes actual, si no el dia 1
    return claveMes(mesVisto) === claveMes(hoy) ? iso(hoy) : iso(mesVisto);
  }
  $("mes-ant").onclick = () => { mesVisto = new Date(mesVisto.getFullYear(), mesVisto.getMonth() - 1, 1); cambiarMes(); };
  $("mes-sig").onclick = () => { mesVisto = new Date(mesVisto.getFullYear(), mesVisto.getMonth() + 1, 1); cambiarMes(); };
  function cambiarMes() {
    $("ga-fecha").value = fechaPorDefecto(); $("mo-fecha").value = fechaPorDefecto();
    intentar(recargar);
  }

  function pintarTodo() {
    $("mes-nombre").textContent = nombreMes(mesVisto);
    const anterior = datosMes.mesAnteriorConDatos;
    $("aviso-copiar").hidden = !anterior;
    if (anterior) $("aviso-copiar-texto").textContent = `Puedes empezar con los ingresos y el reparto de ${nombreMes(desdeIso(anterior + "-01"))}.`;
    pintarReparto(); pintarSubs(); pintarCuentas(); pintarObjetivos();
    pintarPlan(); pintarTuDinero(); pintarCategorias(); pintarCobros(); pintarMetasMini(); pintarMovimientos(); pintarBanco();
  }

  async function recargar() {
    let listaTotales;
    [datosMes, subs, movs, objetivos, plan, listaTotales, bancoEstado, bancoPendientes, bancoReglas] = await Promise.all([
      api(`/api/ahorros/mes/${claveMes(mesVisto)}`),
      api("/api/ahorros/suscripciones"),
      api("/api/ahorros/movimientos"),
      api("/api/ahorros/objetivos"),
      api("/api/ahorros/plan"),
      api(`/api/ahorros/totales?desde=${claveMes(hoy)}&meses=${MESES_PLAN}`),
      api("/api/ahorros/banco/estado"),
      api("/api/ahorros/banco/revisar"),
      api("/api/ahorros/banco/reglas")
    ]);
    totales = Object.fromEntries(listaTotales.map(t => [t.mes, t]));
    // Los apartados se editan en pantalla y se guardan solos: tras cargarlos una vez manda lo de la pantalla
    if (!apartadosCargados) { apartados = plan.apartados.map(a => ({ ...a, porcentaje: num(a.porcentaje) })); apartadosCargados = true; }

    pintarTodo();
  }

  /* Grupos desplegables: cada uno recuerda en este navegador si lo dejaste abierto o cerrado */
  document.querySelectorAll("details.plegable[id]").forEach(d => {
    try { if (localStorage.getItem(`duo-plegado-${d.id}`) === "cerrado") d.open = false; } catch (e) { /* sin almacenamiento: abierto */ }
    d.addEventListener("toggle", () => {
      try { localStorage.setItem(`duo-plegado-${d.id}`, d.open ? "abierto" : "cerrado"); } catch (e) { /* no pasa nada */ }
    });
  });

  $("ga-fecha").value = fechaPorDefecto(); $("mo-fecha").value = fechaPorDefecto(); $("su-cobro").value = iso(hoy);
  intentar(recargar);
})();
