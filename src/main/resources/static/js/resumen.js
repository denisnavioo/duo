/* Duo · Resumen: lo mas importante de todo de un vistazo */
(() => {
  "use strict";

  const { api, esc, icono, iso, desdeIso, euros, MESES, pad } = window.Duo;
  const $ = id => document.getElementById(id);
  const cuerpo = document.body;
  const YO = cuerpo.dataset.yo, OTRO = cuerpo.dataset.otro;
  const nombres = { [YO]: cuerpo.dataset.yoNombre, [OTRO]: cuerpo.dataset.otroNombre, juntos: "Juntos" };

  const DIAS = ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"];
  const DIAS_LARGOS = ["domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"];
  const hoy = new Date(); hoy.setHours(0, 0, 0, 0);
  const sumarDias = (d, n) => { const x = new Date(d); x.setDate(x.getDate() + n); return x; };
  const lunes = sumarDias(hoy, -((hoy.getDay() + 6) % 7));
  const diaSemana = d => DIAS[(d.getDay() + 6) % 7];
  const num = v => Number(v) || 0;

  $("r-fecha").textContent = `${DIAS_LARGOS[hoy.getDay()]}, ${hoy.getDate()} de ${MESES[hoy.getMonth()]}`
    .replace(/^./, c => c.toUpperCase());

  let actividades = [];
  const pendiente = a => a.estado === "PENDIENTE";
  const aceptada = a => !a.estado || a.estado === "ACEPTADA";
  const quienTexto = a => pendiente(a)
    ? (a.creadoPor === YO ? `Esperando a ${nombres[OTRO]}` : "Por responder")
    : a.quien === "juntos" ? "Juntos" : a.quien === YO ? "Tú" : nombres[a.quien] || a.quien;

  function textoDia(f) {
    const dif = Math.round((f - hoy) / 864e5);
    return dif === 0 ? "Hoy" : dif === 1 ? "Mañana" : `${diaSemana(f)} ${f.getDate()} ${MESES[f.getMonth()].slice(0, 3)}`;
  }
  function cuantoFalta(fechaIso, hora) {
    const [h, m] = hora.split(":").map(Number);
    const cuando = desdeIso(fechaIso); cuando.setHours(h, m, 0, 0);
    const min = Math.round((cuando - new Date()) / 60000);
    if (min < 0) return "Ahora";
    if (min < 60) return `En ${min} min`;
    if (min < 24 * 60) { const hh = Math.floor(min / 60), mm = min % 60; return `En ${hh} h${mm ? ` ${mm} min` : ""}`; }
    const dias = Math.round((desdeIso(fechaIso) - hoy) / 864e5);
    return dias === 1 ? "Mañana" : `En ${dias} días`;
  }

  /* ---------------- Proximo plan juntos ---------------- */
  function pintarPlan() {
    const ahora = new Date(), hoyIso = iso(hoy), horaAhora = `${pad(ahora.getHours())}:${pad(ahora.getMinutes())}`;
    const prox = actividades
      .filter(a => a.quien === "juntos" && aceptada(a))
      .filter(a => a.fecha > hoyIso || (a.fecha === hoyIso && a.hora >= horaAhora))
      .sort((a, b) => (a.fecha + a.hora).localeCompare(b.fecha + b.hora))[0];
    const caja = $("r-plan-cuerpo");
    caja.classList.remove("cargando");
    if (!prox) {
      caja.className = "plan-vacio";
      caja.style.display = "";
      caja.innerHTML = `<p>No tenéis ningún plan juntos a la vista.</p>
        <a class="btn" href="/calendario?nueva=1">${icono("mas")}Proponer un plan</a>`;
      return;
    }
    const f = desdeIso(prox.fecha);
    caja.className = "plan-contenido";
    caja.style.display = "contents";
    caja.innerHTML = `
      <div class="hoja-fecha" aria-hidden="true"><small>${DIAS_LARGOS[f.getDay()]}</small><b>${f.getDate()}</b></div>
      <div class="plan-texto">
        <h3>${esc(prox.titulo)}</h3>
        <p><span>${icono("reloj")}${textoDia(f)} · ${esc(prox.hora)}</span>
          ${prox.lugar ? `<span>${icono("pin")}${esc(prox.lugar)}</span>` : ""}
          <span class="falta">${cuantoFalta(prox.fecha, prox.hora)}</span></p>
      </div>
      ${prox.lugar ? `<div class="plan-botones"><a class="btn" target="_blank" rel="noopener"
          href="https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(prox.lugar)}">${icono("ruta")}Cómo llegar</a></div>` : ""}`;
  }

  /* ---------------- Propuestas y recordatorios ---------------- */
  function pintarAvisos() {
    const porResponder = actividades.filter(a => pendiente(a) && a.creadoPor !== YO);
    $("avisos").innerHTML = porResponder.map(a => `
      <article class="aviso" data-id="${a.id}">
        <span class="chip">${icono(a.icono)}</span>
        <div>
          <small>${esc(nombres[a.creadoPor] || a.creadoPor)} te propone un plan juntos</small>
          <b>${esc(a.titulo)}</b>
          <span>${textoDia(desdeIso(a.fecha))} · ${esc(a.hora)}${a.lugar ? " · " + esc(a.lugar) : ""}</span>
        </div>
        <div class="botones-aviso">
          <button type="button" class="btn denegar" data-accion="denegar">Denegar</button>
          <button type="button" class="btn primario" data-accion="aceptar">${icono("check")}Aceptar</button>
        </div>
      </article>`).join("");

    const manana = iso(sumarDias(hoy, 1));
    const lista = actividades.filter(a => a.fecha === manana && aceptada(a) && (a.quien === YO || a.quien === "juntos"))
      .sort((a, b) => a.hora.localeCompare(b.hora));
    $("recordatorios").innerHTML = lista.map(a => `
      <div class="recordatorio">${icono("campana").replace('class="ico"', 'class="ico campana"')}<b>Mañana</b>
        <span>${esc(a.titulo)}${a.quien === "juntos" ? " (juntos)" : ""} · ${esc(a.hora)}</span></div>`).join("");
    const punto = $("aviso-punto");
    if (punto) punto.hidden = !(porResponder.length || lista.length);
  }
  $("avisos").addEventListener("click", async e => {
    const b = e.target.closest("button[data-accion]"); if (!b) return;
    const caja = b.closest(".aviso");
    caja.querySelectorAll("button").forEach(x => x.disabled = true);
    try {
      await api(`/api/actividades/${caja.dataset.id}/responder`, { method: "POST", body: JSON.stringify({ aceptar: b.dataset.accion === "aceptar" }) });
      await cargarActividades();
    } catch (err) {
      alert(err.message);
      caja.querySelectorAll("button").forEach(x => x.disabled = false);
    }
  });

  /* ---------------- La semana de los dos ---------------- */
  const filaEvento = a => `<li class="evento ${esc(a.quien)}${pendiente(a) ? " pendiente" : ""}">
      <span class="h">${esc(a.hora)}</span><span class="t">${esc(a.titulo)}</span><span class="q">${esc(quienTexto(a))}</span></li>`;

  function pintarSemana() {
    const cuenta = { [YO]: 0, [OTRO]: 0, juntos: 0 };
    const dias = DIAS.map((d, i) => {
      const f = sumarDias(lunes, i), clave = iso(f);
      const evs = actividades.filter(a => a.fecha === clave && a.estado !== "RECHAZADA").sort((a, b) => a.hora.localeCompare(b.hora));
      evs.forEach(a => { if (a.quien in cuenta) cuenta[a.quien]++; });
      return { d, f, evs, hoy: f.getTime() === hoy.getTime(), pasado: f < hoy };
    });

    let html = "";
    if (!dias.some(x => x.evs.length)) {
      // Semana sin nada: lo dice en una linea y enseña lo siguiente que hay
      const finSemana = iso(sumarDias(lunes, 6));
      const siguientes = actividades.filter(a => a.fecha > finSemana && a.estado !== "RECHAZADA")
        .sort((a, b) => (a.fecha + a.hora).localeCompare(b.fecha + b.hora)).slice(0, 4);
      html = `<li class="libres"><p class="semana-vacia">Nada planeado esta semana.</p>` +
        (siguientes.length ? `<p class="proximamente-titulo">Después</p><ul class="proximamente">${siguientes.map(a => {
          const f = desdeIso(a.fecha);
          return filaEvento({ ...a, hora: `${diaSemana(f)} ${f.getDate()}` }).replace('class="h"', 'class="h" title="' + esc(a.hora) + '"');
        }).join("")}</ul>` : "") + `</li>`;
    } else {
      // Los dias libres seguidos se juntan en una sola fila ("Lun – Mié · libres")
      for (let i = 0; i < dias.length; i++) {
        const x = dias[i];
        if (!x.evs.length && !x.hoy) {
          let j = i;
          while (j + 1 < dias.length && !dias[j + 1].evs.length && !dias[j + 1].hoy) j++;
          const rango = j > i ? `${dias[i].d} ${dias[i].f.getDate()} – ${dias[j].d} ${dias[j].f.getDate()}` : `${x.d} ${x.f.getDate()}`;
          html += `<li class="libres${dias[j].pasado ? " pasado" : ""}"><span><b>${rango}</b>${j > i ? "Libres" : "Libre"}</span></li>`;
          i = j;
          continue;
        }
        const clase = x.hoy ? "hoy" : x.pasado ? "pasado" : "";
        html += `<li class="${clase}"><div class="dia-n"><small>${x.d}</small><b${x.hoy ? ' aria-label="Hoy"' : ""}>${x.f.getDate()}</b></div>` +
          (x.evs.length ? `<ul>${x.evs.map(filaEvento).join("")}</ul>` : `<p class="libre">Nada hoy</p>`) + `</li>`;
      }
    }
    $("r-semana").innerHTML = html;
    const planes = n => `${n} ${n === 1 ? "plan" : "planes"}`;
    $("pills").innerHTML = !dias.some(x => x.evs.length) ? "" : [[YO, "Tú"], [OTRO, nombres[OTRO]], ["juntos", "Juntos"]]
      .map(([q, n]) => `<span class="pill" style="--c: var(--c-${esc(q)})"><i></i>${esc(n)} <b>${planes(cuenta[q])}</b></span>`).join("");
  }

  async function cargarActividades() {
    actividades = await api("/api/actividades");
    pintarPlan(); pintarAvisos(); pintarSemana();
  }

  /* ---------------- Ahorro del mes (solo lo ves tu) ---------------- */
  async function cargarAhorro() {
    const caja = $("r-ahorro");
    const mes = `${hoy.getFullYear()}-${pad(hoy.getMonth() + 1)}`;
    $("r-ahorro-nombre").textContent = `Tu ahorro de ${MESES[hoy.getMonth()]}`;
    try {
      const d = await api(`/api/ahorros/mes/${mes}`);
      const ingresos = d.ingresos.reduce((s, i) => s + num(i.importe), 0);
      if (!ingresos) {
        caja.innerHTML = `<p class="vacio-texto">Aún no has apuntado lo que ganas este mes.</p>
          <a class="btn" href="/ahorros">${icono("mas")}Añadir ingresos</a>`;
        return;
      }
      const partida = k => ingresos * num(d.reparto[k]) / 100;
      const objetivo = partida("ahorro"), ahorrado = num(d.ahorradoEnElMes);
      const presupuesto = partida("variables"), gastado = d.gastos.reduce((s, g) => s + num(g.importe), 0);
      const pctA = objetivo > 0 ? Math.min(100, ahorrado / objetivo * 100) : 0;
      const pctG = presupuesto > 0 ? Math.min(100, gastado / presupuesto * 100) : (gastado > 0 ? 100 : 0);
      const pasado = gastado > presupuesto;
      caja.innerHTML = `<div class="cifras">
        <div class="cifra-fila"><div class="textos"><span>Ingresos</span><b>${euros(ingresos)}</b></div></div>
        <div class="cifra-fila juntos"><div class="textos"><span>Ahorrado</span><b>${euros(ahorrado)} <small>de ${euros(objetivo)}</small></b></div>
          <div class="progreso" role="progressbar" aria-label="Ahorrado" aria-valuenow="${Math.round(pctA)}" aria-valuemin="0" aria-valuemax="100"><i style="width:${pctA}%"></i></div></div>
        <div class="cifra-fila" style="--c: var(--c-naranja)"><div class="textos"><span>Gastos variables</span><b>${euros(gastado)} <small>de ${euros(presupuesto)}</small></b></div>
          <div class="progreso${pasado ? " pasado" : ""}" role="progressbar" aria-label="Gastos variables" aria-valuenow="${Math.round(pctG)}" aria-valuemin="0" aria-valuemax="100"><i style="width:${pctG}%"></i></div></div>
      </div>
      <p class="nota-privada">${icono("candado")}Solo lo ves tú</p>`;
    } catch (e) {
      caja.innerHTML = `<p class="vacio-texto">No se han podido cargar tus ahorros.</p>`;
    }
  }

  cargarActividades().catch(() => {
    $("r-plan-cuerpo").className = "plan-vacio";
    $("r-plan-cuerpo").innerHTML = "<p>No se ha podido cargar el calendario. Recarga la página.</p>";
  });
  cargarAhorro();
  // Cada minuto se actualiza "en cuanto falta" y lo que haya cambiado el otro
  setInterval(() => { if (!document.hidden) cargarActividades().catch(() => {}); }, 60000);
})();
